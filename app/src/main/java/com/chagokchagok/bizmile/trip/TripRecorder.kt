package com.chagokchagok.bizmile.trip

import com.chagokchagok.bizmile.data.BizmileDatabase
import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.data.review
import com.chagokchagok.bizmile.data.toPlace
import com.chagokchagok.bizmile.vehicle.SignalSource
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feeds vehicle readings into [TripSegmenter] and stores finished trips.
 * The driver does nothing while this runs; the only manual step is the one-tap purpose.
 *
 * Not thread-safe: call from a single coroutine context (the app uses the main dispatcher).
 */
class TripRecorder(
    private val db: BizmileDatabase,
    private val classifier: TripClassifier = TripClassifier(),
) {
    private var segmenter = TripSegmenter()

    private val _phase = MutableStateFlow(TripPhase.IDLE)
    val phase: StateFlow<TripPhase> = _phase.asStateFlow()

    private val _liveTrip = MutableStateFlow<TripDraft?>(null)
    /** The trip currently being driven (null when idle or stopped). */
    val liveTrip: StateFlow<TripDraft?> = _liveTrip.asStateFlow()

    /** Start from a clean state, e.g. when the data source changes. */
    fun reset() {
        segmenter = TripSegmenter()
        publish()
    }

    suspend fun onSnapshot(snapshot: VehicleSnapshot, source: SignalSource) {
        val events = segmenter.onSnapshot(snapshot)
        publish()
        for (event in events) {
            if (event is TripEvent.Ended) store(event.trip, snapshot.vin, source)
        }
    }

    /** The driver's one tap. Also closes the merge window for that trip. */
    suspend fun confirm(tripId: String, purpose: Purpose, memo: String) {
        db.trips().setPurpose(tripId, purpose.name, ReviewState.CONFIRMED.name, memo)
        if (segmenter.current?.id == tripId) {
            segmenter.finalizeStopped()
            publish()
        }
    }

    private suspend fun store(trip: TripDraft, vin: String?, source: SignalSource) {
        val places = db.places().list().map { it.toPlace() }
        val startPlace = TripClassifier.matchPlace(trip.startLocation, places)
        val endPlace = TripClassifier.matchPlace(trip.endLocation, places)
        val classification = classifier.classify(trip, startPlace, endPlace)

        // A resumed trip that the driver already classified keeps that answer.
        val existing = db.trips().byId(trip.id)
        val keep = existing?.takeIf { it.review == ReviewState.CONFIRMED }

        db.trips().upsert(
            TripEntity(
                id = trip.id,
                vin = vin,
                startTimeMillis = trip.startTimeMillis,
                endTimeMillis = trip.endTimeMillis,
                startOdometerKm = trip.startOdometerKm,
                endOdometerKm = trip.endOdometerKm,
                startPlace = startPlace?.name,
                endPlace = endPlace?.name,
                startLat = trip.startLocation?.lat,
                startLng = trip.startLocation?.lng,
                endLat = trip.endLocation?.lat,
                endLng = trip.endLocation?.lng,
                stopovers = trip.stopovers,
                purpose = keep?.purpose ?: classification.purpose?.name,
                reviewState = keep?.reviewState ?: classification.state.name,
                reason = classification.reason,
                memo = keep?.memo ?: classification.memo,
                source = source.name,
            ),
        )
    }

    private fun publish() {
        _phase.value = segmenter.phase
        _liveTrip.value = segmenter.current.takeIf { segmenter.phase == TripPhase.DRIVING }
    }
}
