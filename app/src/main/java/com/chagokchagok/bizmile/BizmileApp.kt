package com.chagokchagok.bizmile

import android.app.Application
import com.chagokchagok.bizmile.data.BizmileDatabase
import com.chagokchagok.bizmile.data.PlaceEntity
import com.chagokchagok.bizmile.trip.PlaceKind
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.trip.TripRecorder
import com.chagokchagok.bizmile.vehicle.PleosVehicleDataSource
import com.chagokchagok.bizmile.vehicle.SignalSource
import com.chagokchagok.bizmile.vehicle.SimRoute
import com.chagokchagok.bizmile.vehicle.SimulatedVehicleDataSource
import com.chagokchagok.bizmile.vehicle.VehicleDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Process-wide wiring. Recording lives here rather than in an Activity so trips keep being
 * recorded while another app (e.g. navigation) is on screen, for as long as the process lives.
 */
class BizmileApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var db: BizmileDatabase
        private set
    lateinit var recorder: TripRecorder
        private set
    lateinit var simulated: SimulatedVehicleDataSource
        private set

    private var pleos: PleosVehicleDataSource? = null

    /** True when the Pleos Vehicle Service exists on this device. */
    var pleosAvailable = false
        private set

    private lateinit var _source: MutableStateFlow<VehicleDataSource>
    val source: StateFlow<VehicleDataSource> get() = _source.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        db = BizmileDatabase.create(this)
        recorder = TripRecorder(db)
        simulated = SimulatedVehicleDataSource(appScope)

        pleosAvailable = PleosVehicleDataSource.isVehicleServicePresent(this)
        val initial: VehicleDataSource =
            if (pleosAvailable) PleosVehicleDataSource(this).also { pleos = it } else simulated
        _source = MutableStateFlow(initial)
        initial.start()

        appScope.launch { seedExamplePlaces() }
        appScope.launch {
            _source.collectLatest { src ->
                recorder.reset()
                src.snapshot.collect { recorder.onSnapshot(it, src.kind) }
            }
        }
    }

    fun useSource(kind: SignalSource) {
        val current = _source.value
        if (current.kind == kind) return
        if (kind == SignalSource.PLEOS && !pleosAvailable) return
        current.stop()
        val next: VehicleDataSource = when (kind) {
            SignalSource.PLEOS -> pleos ?: PleosVehicleDataSource(this).also { pleos = it }
            SignalSource.SIMULATED -> simulated
        }
        next.start()
        _source.value = next
    }

    /** Example registered places so the auto-classification rules have something to match. */
    private suspend fun seedExamplePlaces() {
        if (db.places().list().isNotEmpty()) return
        listOf(
            PlaceEntity(
                name = "집", lat = SimRoute.HOME.lat, lng = SimRoute.HOME.lng, radiusMeters = 200f,
                kind = PlaceKind.HOME.name, defaultPurpose = Purpose.PERSONAL.name, defaultMemo = "",
            ),
            PlaceEntity(
                name = "본사", lat = SimRoute.HQ.lat, lng = SimRoute.HQ.lng, radiusMeters = 200f,
                kind = PlaceKind.WORK.name, defaultPurpose = Purpose.BUSINESS.name, defaultMemo = "본사 복귀",
            ),
            PlaceEntity(
                name = "○○건설 판교현장", lat = SimRoute.PANGYO_SITE.lat, lng = SimRoute.PANGYO_SITE.lng,
                radiusMeters = 300f, kind = PlaceKind.CLIENT.name, defaultPurpose = Purpose.BUSINESS.name,
                defaultMemo = "현장 점검",
            ),
        ).forEach { db.places().insert(it) }
    }
}
