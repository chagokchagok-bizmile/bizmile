package com.chagokchagok.bizmile.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stand-in for the vehicle when no Pleos Vehicle Service is present (plain emulator, phone).
 * It plays the role of the Pleos Virtual Console: gear, odometer, battery, doors, windows
 * and warning lights can be changed by hand or by a scripted drive.
 *
 * Time is a virtual clock that starts at "now" and is advanced by the simulated driving,
 * so a 37-minute trip can be replayed in a few seconds and still be logged as 37 minutes.
 */
class SimulatedVehicleDataSource(private val scope: CoroutineScope) : VehicleDataSource {

    override val kind = SignalSource.SIMULATED

    private var virtualNow = System.currentTimeMillis()

    private val _snapshot = MutableStateFlow(
        VehicleSnapshot(
            timestampMillis = virtualNow,
            gear = Gear.PARK,
            odometerKm = 12_480.3f,
            socPercent = 78,
            rangeKm = 351f,
            doorsLocked = false,
            openWindows = emptyList(),
            warnings = emptySet(),
            location = SimRoute.HQ,
            vin = "SIM-VIN-0003",
        ),
    )
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Live)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override val diagnostics: StateFlow<List<SignalDiagnostic>> = MutableStateFlow(emptyList())

    private val _driving = MutableStateFlow(false)
    /** True while a scripted drive is being replayed. */
    val scriptRunning: StateFlow<Boolean> = _driving.asStateFlow()

    private var script: Job? = null

    override fun start() {}

    override fun stop() {
        script?.cancel()
        _driving.value = false
    }

    // --- manual controls (the in-app "virtual console") ---

    fun setGear(gear: Gear) = emit(advanceMillis = 5_000) { it.copy(gear = gear) }

    /** Drive [km] further at roughly city speed; only moves when in D or R. */
    fun addDistance(km: Float) {
        if (!_snapshot.value.gear.isMoving) return
        emit(advanceMillis = (km / AVERAGE_KMH * 3_600_000).toLong()) {
            it.copy(
                odometerKm = (it.odometerKm ?: 0f) + km,
                socPercent = it.socPercent?.let { soc -> (soc - (km / KM_PER_PERCENT)).toInt().coerceAtLeast(1) },
                rangeKm = it.rangeKm?.let { r -> (r - km).coerceAtLeast(0f) },
            )
        }
    }

    fun setSoc(percent: Int) = emit {
        it.copy(socPercent = percent.coerceIn(0, 100), rangeKm = percent.coerceIn(0, 100) * KM_PER_PERCENT)
    }

    fun setDoorsLocked(locked: Boolean) = emit { it.copy(doorsLocked = locked) }

    fun toggleWindow(name: String) = emit {
        val open = it.openWindows.orEmpty()
        it.copy(openWindows = if (name in open) open - name else open + name)
    }

    fun toggleWarning(warning: Warning) = emit {
        val w = it.warnings.orEmpty()
        it.copy(warnings = if (warning in w) w - warning else w + warning)
    }

    fun moveTo(point: GeoPoint) = emit { it.copy(location = point) }

    /** Let virtual time pass while parked (e.g. to step past the trip merge window). */
    fun waitMinutes(minutes: Int) = emit(advanceMillis = minutes * 60_000L) { it }

    /**
     * Replay a drive: shift to D, move along a straight line to [leg]'s destination while the
     * odometer climbs by exactly [SimLeg.distanceKm], then shift to P.
     */
    fun drive(leg: SimLeg) {
        if (_driving.value) return
        script = scope.launch {
            _driving.value = true
            try {
                val from = _snapshot.value.location ?: leg.destination
                setDoorsLocked(false)
                setGear(Gear.DRIVE)
                delay(STEP_REAL_MILLIS)
                val steps = 12
                val stepMillis = leg.minutes * 60_000L / steps
                repeat(steps) { i ->
                    val f = (i + 1) / steps.toFloat()
                    emit(advanceMillis = stepMillis) {
                        it.copy(
                            odometerKm = (it.odometerKm ?: 0f) + leg.distanceKm / steps,
                            socPercent = it.socPercent?.let { soc ->
                                if ((i + 1) % 3 == 0) (soc - (leg.distanceKm / KM_PER_PERCENT / 4)).toInt().coerceAtLeast(1) else soc
                            },
                            rangeKm = it.rangeKm?.let { r -> (r - leg.distanceKm / steps).coerceAtLeast(0f) },
                            location = GeoPoint(
                                from.lat + (leg.destination.lat - from.lat) * f,
                                from.lng + (leg.destination.lng - from.lng) * f,
                            ),
                        )
                    }
                    delay(STEP_REAL_MILLIS)
                }
                setGear(Gear.PARK)
            } finally {
                _driving.value = false
            }
        }
    }

    private fun emit(advanceMillis: Long = 1_000, block: (VehicleSnapshot) -> VehicleSnapshot) {
        _snapshot.update {
            virtualNow += advanceMillis
            block(it).copy(timestampMillis = virtualNow)
        }
    }

    companion object {
        private const val AVERAGE_KMH = 38f
        private const val KM_PER_PERCENT = 4.5f
        private const val STEP_REAL_MILLIS = 450L
        val WINDOW_NAMES = listOf("운전석", "동승석", "뒷좌석 좌", "뒷좌석 우")
    }
}

data class SimLeg(val label: String, val destination: GeoPoint, val distanceKm: Float, val minutes: Int)

/** Fixed points used by the scripted drives; they match the example places seeded on first run. */
object SimRoute {
    val HOME = GeoPoint(37.5112, 127.0981)
    val HQ = GeoPoint(37.4979, 127.0276)
    val PANGYO_SITE = GeoPoint(37.4020, 127.1086)
    val UNREGISTERED = GeoPoint(37.3219, 127.1265)

    val LEGS = listOf(
        SimLeg("집 → 본사", HQ, 18.2f, 34),
        SimLeg("→ 판교현장", PANGYO_SITE, 23.4f, 37),
        SimLeg("→ 미등록 장소", UNREGISTERED, 11.8f, 22),
        SimLeg("→ 본사 복귀", HQ, 21.9f, 41),
    )
}
