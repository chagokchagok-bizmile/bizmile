package com.chagokchagok.bizmile.trip

import com.chagokchagok.bizmile.vehicle.Gear
import com.chagokchagok.bizmile.vehicle.GeoPoint
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import kotlin.math.max

/** A trip as measured by the vehicle: instrument-cluster odometer at both ends. */
data class TripDraft(
    val id: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val startOdometerKm: Float,
    val endOdometerKm: Float,
    val startLocation: GeoPoint?,
    val endLocation: GeoPoint?,
    /** Short stops (fuel, convenience store) merged into this trip. */
    val stopovers: Int = 0,
) {
    val distanceKm: Float get() = max(0f, endOdometerKm - startOdometerKm)
}

sealed interface TripEvent {
    data class Started(val trip: TripDraft) : TripEvent
    /** The trip ended (gear to P, or no movement for [TripSegmenter.Config.idleEndMillis]). */
    data class Ended(val trip: TripDraft) : TripEvent
    /** A trip that had ended continued within the merge window; same [TripDraft.id]. */
    data class Resumed(val trip: TripDraft) : TripEvent
    /** Movement too short to be a trip (e.g. re-parking). */
    data class Discarded(val trip: TripDraft) : TripEvent
}

enum class TripPhase { IDLE, DRIVING, STOPPED }

/**
 * Splits a stream of vehicle readings into trips.
 *
 *   IDLE ──(gear leaves P, or odometer rises)──▶ DRIVING ──(gear P / idle timeout)──▶ STOPPED
 *   STOPPED ──(moves again within mergeWindow)──▶ DRIVING   (same trip, stopovers + 1)
 *   STOPPED ──(mergeWindow passes)──▶ IDLE
 *
 * Gear is the primary signal. If the vehicle never reports a gear, the odometer alone is
 * used: a rise starts a trip and [Config.idleEndMillis] without a rise ends it.
 *
 * Pure Kotlin, no Android or SDK types, so it is covered by plain JUnit tests.
 */
class TripSegmenter(
    private val config: Config = Config(),
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    data class Config(
        /** Trips shorter than this are discarded. */
        val minTripKm: Float = 0.1f,
        /** A new departure within this time after a stop continues the same trip. */
        val mergeWindowMillis: Long = 10 * 60_000L,
        /** Without a gear signal: no odometer change for this long ends the trip. */
        val idleEndMillis: Long = 3 * 60_000L,
    )

    var phase: TripPhase = TripPhase.IDLE
        private set

    /** The trip being driven, or the one that just ended and may still be resumed. */
    var current: TripDraft? = null
        private set

    private var lastOdometer: Float? = null
    private var lastMoveMillis: Long = 0L
    private var gearSeen = false

    fun onSnapshot(s: VehicleSnapshot): List<TripEvent> {
        val out = mutableListOf<TripEvent>()
        val now = s.timestampMillis
        val odo = s.odometerKm
        if (s.gear != Gear.UNKNOWN) gearSeen = true

        val prevOdo = lastOdometer
        val odometerRose = odo != null && prevOdo != null && odo > prevOdo
        if (odo != null) lastOdometer = odo
        if (odometerRose) lastMoveMillis = now

        when (phase) {
            TripPhase.IDLE -> {
                if (shouldStart(s, odometerRose) && odo != null) {
                    // If the rise itself triggered the start, the trip began at the previous reading.
                    val startOdo = if (odometerRose && prevOdo != null) prevOdo else odo
                    start(now, startOdo, s.location, out)
                }
            }

            TripPhase.DRIVING -> {
                val trip = current ?: return out
                val updated = trip.copy(
                    endTimeMillis = now,
                    endOdometerKm = odo ?: trip.endOdometerKm,
                    endLocation = s.location ?: trip.endLocation,
                )
                current = updated
                val parked = s.gear == Gear.PARK
                val idleTimeout = !gearSeen && now - lastMoveMillis >= config.idleEndMillis
                if (parked || idleTimeout) end(updated, idleTimeout, out)
            }

            TripPhase.STOPPED -> {
                val trip = current
                if (trip == null) {
                    phase = TripPhase.IDLE
                } else if (now - trip.endTimeMillis > config.mergeWindowMillis) {
                    current = null
                    phase = TripPhase.IDLE
                    if (shouldStart(s, odometerRose) && odo != null) {
                        start(now, if (odometerRose && prevOdo != null) prevOdo else odo, s.location, out)
                    }
                } else if (shouldStart(s, odometerRose)) {
                    val resumed = trip.copy(
                        endTimeMillis = now,
                        endOdometerKm = odo ?: trip.endOdometerKm,
                        endLocation = s.location ?: trip.endLocation,
                        stopovers = trip.stopovers + 1,
                    )
                    current = resumed
                    phase = TripPhase.DRIVING
                    lastMoveMillis = now
                    out += TripEvent.Resumed(resumed)
                }
            }
        }
        return out
    }

    /**
     * Close the merge window early, e.g. once the driver has classified the trip
     * or returned the car. The next departure is then always a new trip.
     */
    fun finalizeStopped() {
        if (phase == TripPhase.STOPPED) {
            current = null
            phase = TripPhase.IDLE
        }
    }

    private fun shouldStart(s: VehicleSnapshot, odometerRose: Boolean): Boolean =
        s.gear.isMoving || (odometerRose && s.gear != Gear.PARK)

    private fun start(now: Long, odo: Float, loc: GeoPoint?, out: MutableList<TripEvent>) {
        val trip = TripDraft(
            id = newId(),
            startTimeMillis = now,
            endTimeMillis = now,
            startOdometerKm = odo,
            endOdometerKm = max(odo, lastOdometer ?: odo),
            startLocation = loc,
            endLocation = loc,
        )
        current = trip
        phase = TripPhase.DRIVING
        lastMoveMillis = now
        out += TripEvent.Started(trip)
    }

    private fun end(trip: TripDraft, byIdleTimeout: Boolean, out: MutableList<TripEvent>) {
        // With an idle timeout the car actually stopped when it last moved, not now.
        val ended = if (byIdleTimeout) trip.copy(endTimeMillis = lastMoveMillis) else trip
        if (ended.distanceKm < config.minTripKm) {
            current = null
            phase = TripPhase.IDLE
            out += TripEvent.Discarded(ended)
        } else {
            current = ended
            phase = TripPhase.STOPPED
            out += TripEvent.Ended(ended)
        }
    }
}
