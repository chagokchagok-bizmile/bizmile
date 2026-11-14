package com.chagokchagok.bizmile.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * The single seam between the app and the vehicle.
 *
 * [PleosVehicleDataSource] reads the real Pleos Connect SDKs; [SimulatedVehicleDataSource]
 * produces the same shape of data from a script so the trip logic can be exercised and
 * unit-tested without a vehicle service.
 */
interface VehicleDataSource {
    val kind: SignalSource
    val snapshot: StateFlow<VehicleSnapshot>
    val status: StateFlow<SourceStatus>
    val diagnostics: StateFlow<List<SignalDiagnostic>>

    fun start()
    fun stop()

    /** Re-read values that have no change callback (used right before a handover report). */
    fun refresh() {}

    /** A runtime permission was granted or denied. */
    fun onPermissionsChanged() {}
}
