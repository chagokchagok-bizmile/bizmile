package com.chagokchagok.bizmile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagokchagok.bizmile.BizmileApp
import com.chagokchagok.bizmile.data.PlaceEntity
import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.data.toEntity
import com.chagokchagok.bizmile.data.toPlace
import com.chagokchagok.bizmile.data.toReport
import com.chagokchagok.bizmile.export.TripLogExporter
import com.chagokchagok.bizmile.handover.HandoverInspector
import com.chagokchagok.bizmile.handover.HandoverReport
import com.chagokchagok.bizmile.trip.Place
import com.chagokchagok.bizmile.trip.PlaceKind
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.vehicle.SignalDiagnostic
import com.chagokchagok.bizmile.vehicle.SignalSource
import com.chagokchagok.bizmile.vehicle.SourceStatus
import com.chagokchagok.bizmile.vehicle.VehicleDataSource
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

data class ExportResult(val path: String, val rowCount: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class BizmileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BizmileApp
    private val db get() = app.db
    private val exporter = TripLogExporter()
    private val inspector = HandoverInspector()

    val source: StateFlow<VehicleDataSource> = app.source
    val pleosAvailable: Boolean get() = app.pleosAvailable
    val simulator get() = app.simulated

    val snapshot: StateFlow<VehicleSnapshot> = app.source.flatMapLatest { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.source.value.snapshot.value)
    val status: StateFlow<SourceStatus> = app.source.flatMapLatest { it.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.source.value.status.value)
    val diagnostics: StateFlow<List<SignalDiagnostic>> = app.source.flatMapLatest { it.diagnostics }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val phase = app.recorder.phase
    val liveTrip = app.recorder.liveTrip

    val trips: StateFlow<List<TripEntity>> = db.trips().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val places: StateFlow<List<Place>> = db.places().all().map { list -> list.map { it.toPlace() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val handovers: StateFlow<List<HandoverReport>> = db.handovers().recent().map { list -> list.map { it.toReport() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _export = MutableStateFlow<ExportResult?>(null)
    val export: StateFlow<ExportResult?> = _export.asStateFlow()

    private val _inspecting = MutableStateFlow(false)
    val inspecting: StateFlow<Boolean> = _inspecting.asStateFlow()

    fun useSource(kind: SignalSource) = app.useSource(kind)

    fun confirm(trip: TripEntity, purpose: Purpose) {
        viewModelScope.launch { app.recorder.confirm(trip.id, purpose, trip.memo) }
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch { db.trips().delete(trip.id) }
    }

    /** The car checks itself and the result is stored as the handover record. */
    fun runHandoverInspection() {
        if (_inspecting.value) return
        viewModelScope.launch {
            _inspecting.value = true
            val src = app.source.value
            src.refresh()
            delay(1_200) // let the one-shot SDK reads come back
            val report = inspector.inspect(snapshot.value)
            db.handovers().insert(report.toEntity(src.kind))
            _inspecting.value = false
        }
    }

    fun exportMonth(month: YearMonth) {
        viewModelScope.launch {
            val all = trips.value
            val dir = File(getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir, "exports")
            val file = withContext(Dispatchers.IO) { exporter.write(dir, month, all) }
            _export.value = ExportResult(file.absolutePath, exporter.tripsOf(month, all).size)
        }
    }

    fun clearExport() {
        _export.value = null
    }

    fun tripsOf(month: YearMonth, all: List<TripEntity>) = exporter.tripsOf(month, all)

    /** Register the car's current position as a named place. */
    fun addPlaceHere(name: String, kind: PlaceKind, purpose: Purpose) {
        val here = snapshot.value.location ?: return
        viewModelScope.launch {
            db.places().insert(
                PlaceEntity(
                    name = name, lat = here.lat, lng = here.lng, radiusMeters = 200f,
                    kind = kind.name, defaultPurpose = purpose.name, defaultMemo = "",
                ),
            )
        }
    }

    fun deletePlace(id: Long) {
        viewModelScope.launch { db.places().delete(id) }
    }

    fun clearSimulatedTrips() {
        viewModelScope.launch { db.trips().deleteBySource(SignalSource.SIMULATED.name) }
    }
}
