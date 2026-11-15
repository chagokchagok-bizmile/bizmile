package com.chagokchagok.bizmile.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reads the vehicle through the Pleos Connect SDKs (Vehicle 2.3.2, FusedLocation 1.1.3).
 * Read-only: no setter is ever called.
 *
 *   Gear.registerGearSelection                → trip start / end
 *   Odometer.registerDistanceDriven           → instrument-cluster distance
 *   EvBattery.registerEvBatteryStateOfCharge  → state of charge
 *   Odometer.getRangeRemainingDistance        → remaining range
 *   Door.checkDoorLockCapabilities + registerDoorLockState        → locked at return
 *   Window.checkWindowPositionCapabilities + registerWindowPosition → windows left open
 *   Safety.hasLowWasherFluidWarning / get*LevelState, Tire.*      → items needing attention
 *   Safety.registerTelltales                  → warning lights (when the platform grants it)
 *   CarInfo.getVINInfo                        → which car
 *   FusedLocation.registerFusedLocationCallback → departure / arrival position
 *
 * Door and window areas are not hard-coded: the app asks the vehicle which areas it supports
 * (capability check) and subscribes to exactly those.
 *
 * Every call is isolated: a value the vehicle refuses stays null and is listed in
 * [diagnostics]; the rest keep working.
 */
class PleosVehicleDataSource(private val context: Context) : VehicleDataSource {

    override val kind = SignalSource.PLEOS

    private val _snapshot = MutableStateFlow(VehicleSnapshot(System.currentTimeMillis()))
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Connecting)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<SignalDiagnostic>>(emptyList())
    override val diagnostics: StateFlow<List<SignalDiagnostic>> = _diagnostics.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var started = false

    // Areas reported by the vehicle's capability check, with the listener registered for each.
    private val doorListeners = mutableMapOf<DoorArea, DoorLockStateListener>()
    private val windowListeners = mutableMapOf<WindowArea, WindowPositionListener>()
    private val doorLocks = mutableMapOf<String, Boolean>()
    private val windowPositions = mutableMapOf<String, Int>()

    private var telltaleWarnings: Set<Warning>? = null
    /** Result of each non-telltale check: key → active warning, or null when that item is fine. */
    private val checkedWarnings = mutableMapOf<String, Warning?>()

    // --- listeners (kept as fields so the same instance is used to unregister) ---

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            report("Gear.registerGearSelection", true, "${value?.name}")
            update { it.copy(gear = value.toGear()) }
        }
        override fun onFailed(e: Exception) = fail("Gear.registerGearSelection", e)
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            report("Odometer.registerDistanceDriven", true, "$value")
            if (value != null) update { it.copy(odometerKm = value) }
        }
        override fun onFailed(e: Exception) = fail("Odometer.registerDistanceDriven", e)
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            report("EvBattery.registerEvBatteryStateOfCharge", true, "$value")
            if (value != null) update { it.copy(socPercent = value) }
            readRange()
        }
        override fun onFailed(e: Exception) = fail("EvBattery.registerEvBatteryStateOfCharge", e)
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(value: Telltales?) = onTelltales("Safety.registerTelltales", value)
        override fun onFailed(e: Exception) = fail("Safety.registerTelltales", e)
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            report("FusedLocation.registerFusedLocationCallback", true, "confidence ${location.confidence}")
            update { it.copy(location = GeoPoint(location.latitude, location.longitude)) }
        }
        override fun onError(e: Exception) = fail("FusedLocation", e)
    }

    /** Keeps the timestamp moving so the segmenter's idle timeout works without a gear signal. */
    private val ticker = object : Runnable {
        override fun run() {
            if (!started) return
            update { it }
            main.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun start() {
        if (started) return
        started = true
        _status.value = SourceStatus.Connecting
        try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            _status.value = SourceStatus.Live
            subscribe(v)
            readOnce(v)
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK init failed", t)
            _status.value = SourceStatus.Unavailable(t.message ?: t.javaClass.simpleName)
        }
        startLocation()
        main.postDelayed(ticker, TICK_MILLIS)
    }

    override fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(ticker)
        vehicle?.let { v ->
            guard("unregister") {
                v.getGear().unregisterGearSelection(gearListener)
                v.getOdometer().unregisterDistanceDriven(odometerListener)
                v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
                v.getSafety().unregisterTelltales(telltaleListener)
                doorListeners.forEach { (area, l) -> v.getDoor().unregisterDoorLockState(area, l) }
                windowListeners.forEach { (area, l) -> v.getWindow().unregisterWindowPosition(area, l) }
            }
            guard("release") { v.release() }
        }
        vehicle = null
        doorListeners.clear()
        windowListeners.clear()
        fused?.let { f ->
            guard("FusedLocation.release") {
                f.unregisterFusedLocationCallback(locationListener)
                f.release()
            }
        }
        fused = null
    }

    override fun refresh() {
        vehicle?.let { readOnce(it) }
    }

    private fun subscribe(v: Vehicle) {
        guard("Gear.registerGearSelection") { v.getGear().registerGearSelection(gearListener) }
        guard("Odometer.registerDistanceDriven") { v.getOdometer().registerDistanceDriven(odometerListener) }
        guard("EvBattery.registerEvBatteryStateOfCharge") {
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        }
        guard("Safety.registerTelltales") { v.getSafety().registerTelltales(telltaleListener) }
        guard("Door.checkDoorLockCapabilities") {
            v.getDoor().checkDoorLockCapabilities(
                { areas: List<DoorArea> ->
                    report("Door.checkDoorLockCapabilities", true, "지원 영역 ${areas.size}개")
                    areas.forEach { area ->
                        val listener = object : DoorLockStateListener {
                            override fun onDoorLockStateUpdated(value: Boolean?) = onDoorLock(area, value)
                            override fun onFailed(e: Exception) = fail("Door.registerDoorLockState", e)
                        }
                        doorListeners[area] = listener
                        guard("Door.registerDoorLockState") { v.getDoor().registerDoorLockState(area, listener) }
                    }
                    readDoors(v)
                },
                { e -> fail("Door.checkDoorLockCapabilities", e) },
            )
        }
        guard("Window.checkWindowPositionCapabilities") {
            v.getWindow().checkWindowPositionCapabilities(
                { areas: Map<WindowArea, *> ->
                    report("Window.checkWindowPositionCapabilities", true, "지원 영역 ${areas.size}개")
                    areas.keys.forEach { area ->
                        val listener = object : WindowPositionListener {
                            override fun onWindowPositionUpdated(value: Int?) = onWindowPosition(area, value)
                            override fun onFailed(e: Exception) = fail("Window.registerWindowPosition", e)
                        }
                        windowListeners[area] = listener
                        guard("Window.registerWindowPosition") { v.getWindow().registerWindowPosition(area, listener) }
                    }
                    readWindows(v)
                },
                { e -> fail("Window.checkWindowPositionCapabilities", e) },
            )
        }
    }

    private fun readDoors(v: Vehicle) {
        doorListeners.keys.toList().forEach { area ->
            guard("Door.getDoorLockState") {
                v.getDoor().getDoorLockState(area, { locked -> onDoorLock(area, locked) }, { e -> fail("Door.getDoorLockState", e) })
            }
        }
    }

    private fun readWindows(v: Vehicle) {
        windowListeners.keys.toList().forEach { area ->
            guard("Window.getCurrentWindowPosition") {
                v.getWindow().getCurrentWindowPosition(
                    area, { pos -> onWindowPosition(area, pos) }, { e -> fail("Window.getCurrentWindowPosition", e) },
                )
            }
        }
    }

    /** Checks available with the public CAR_SAFETY permission, used when telltales are not granted. */
    private fun readSafetyChecks(v: Vehicle) {
        guard("Safety.hasLowWasherFluidWarning") {
            v.getSafety().hasLowWasherFluidWarning(
                { low -> onCheck("Safety.hasLowWasherFluidWarning", low?.let { if (it) Warning.WASHER_FLUID else null }, low != null, "$low") },
                { e -> fail("Safety.hasLowWasherFluidWarning", e) },
            )
        }
        guard("Safety.getEngineOilLevelState") {
            v.getSafety().getEngineOilLevelState(
                { st -> onCheck("Safety.getEngineOilLevelState", if (st.isAbnormal()) Warning.ENGINE_OIL else null, st != null, "${st?.name}") },
                { e -> fail("Safety.getEngineOilLevelState", e) },
            )
        }
        guard("Safety.getBrakeFluidLevelState") {
            v.getSafety().getBrakeFluidLevelState(
                { st -> onCheck("Safety.getBrakeFluidLevelState", if (st.isAbnormal()) Warning.BRAKE_FLUID else null, st != null, "${st?.name}") },
                { e -> fail("Safety.getBrakeFluidLevelState", e) },
            )
        }
        guard("Tire.checkTirePressureCapabilities") {
            v.getTire().checkTirePressureCapabilities(
                { _, areas: List<WheelArea> ->
                    report("Tire.checkTirePressureCapabilities", true, "지원 영역 ${areas.size}개")
                    areas.forEachIndexed { i, area -> readTire(v, area, i) }
                },
                { e -> fail("Tire.checkTirePressureCapabilities", e) },
            )
        }
    }

    private fun readTire(v: Vehicle, area: WheelArea, index: Int) {
        guard("Tire.getTirePressure") {
            v.getTire().getLowTirePressure(
                area,
                { threshold ->
                    v.getTire().getTirePressure(
                        area,
                        { pressure ->
                            val low = threshold != null && pressure != null && pressure < threshold
                            onCheck(
                                "Tire.getTirePressure#$index", if (low) Warning.TIRE_PRESSURE else null,
                                threshold != null && pressure != null, "$pressure (기준 $threshold)",
                            )
                        },
                        { e -> fail("Tire.getTirePressure#$index", e) },
                    )
                },
                { e -> fail("Tire.getLowTirePressure#$index", e) },
            )
        }
    }

    private fun readOnce(v: Vehicle) {
        guard("Gear.getCurrentGearSelectionState") {
            v.getGear().getCurrentGearSelectionState(
                { g -> report("Gear.getCurrentGearSelectionState", true, "${g?.name}"); update { it.copy(gear = g.toGear()) } },
                { e -> fail("Gear.getCurrentGearSelectionState", e) },
            )
        }
        guard("Odometer.getCurrentDistanceDriven") {
            v.getOdometer().getCurrentDistanceDriven(
                { km -> report("Odometer.getCurrentDistanceDriven", true, "$km"); if (km != null) update { it.copy(odometerKm = km) } },
                { e -> fail("Odometer.getCurrentDistanceDriven", e) },
            )
        }
        guard("EvBattery.getEvBatteryStateOfCharge") {
            v.getEvBattery().getEvBatteryStateOfCharge(
                { soc -> report("EvBattery.getEvBatteryStateOfCharge", true, "$soc"); if (soc != null) update { it.copy(socPercent = soc) } },
                { e -> fail("EvBattery.getEvBatteryStateOfCharge", e) },
            )
        }
        readRange()
        guard("CarInfo.getVINInfo") {
            v.getCarInfo().getVINInfo(
                { vin -> report("CarInfo.getVINInfo", true, if (vin.isNullOrBlank()) "(빈 값)" else "수신"); if (!vin.isNullOrBlank()) update { it.copy(vin = vin) } },
                { e -> fail("CarInfo.getVINInfo", e) },
            )
        }
        guard("Safety.getTelltalesInfo") {
            v.getSafety().getTelltalesInfo(
                { t -> onTelltales("Safety.getTelltalesInfo", t) },
                { e -> fail("Safety.getTelltalesInfo", e) },
            )
        }
        readDoors(v)
        readWindows(v)
        readSafetyChecks(v)
    }

    private fun readRange() {
        val v = vehicle ?: return
        guard("Odometer.getRangeRemainingDistance") {
            v.getOdometer().getRangeRemainingDistance(
                // The vehicle reports remaining range in metres.
                { m -> report("Odometer.getRangeRemainingDistance", true, "$m m"); if (m != null) update { it.copy(rangeKm = m / 1000f) } },
                { e -> fail("Odometer.getRangeRemainingDistance", e) },
            )
        }
    }

    /** FUSED_LOCATION is a runtime permission; called again once the driver grants it. */
    override fun onPermissionsChanged() {
        if (started && fused == null) startLocation()
    }

    private fun startLocation() {
        if (context.checkSelfPermission(FUSED_LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            report("FusedLocation", false, "위치 권한이 허용되지 않음")
            return
        }
        guard("FusedLocation") {
            val f = FusedLocation(context)
            if (!f.isSupported()) {
                report("FusedLocation.isSupported", false, "이 환경에서 지원되지 않음")
                return@guard
            }
            f.initialize()
            fused = f
            report("FusedLocation", true, "초기화됨")
            f.getLastKnownLocation(locationListener)
            f.registerFusedLocationCallback(locationListener)
        }
    }

    private fun onDoorLock(area: DoorArea, locked: Boolean?) {
        val key = area.values.joinToString("+") { it.name }
        report("Door lock [$key]", locked != null, "$locked")
        if (locked == null) return
        val allLocked = synchronized(doorLocks) {
            doorLocks[key] = locked
            doorLocks.values.all { it }
        }
        update { it.copy(doorsLocked = allLocked) }
    }

    private fun onWindowPosition(area: WindowArea, position: Int?) {
        val key = area.values.joinToString("+") { it.name }
        report("Window position [$key]", position != null, "$position")
        if (position == null) return
        val open = synchronized(windowPositions) {
            windowPositions[key] = position
            windowPositions.filterValues { it > 0 }.keys.map { windowLabel(it) }.sorted()
        }
        update { it.copy(openWindows = open) }
    }

    private fun onTelltales(api: String, value: Telltales?) {
        val active = value?.telltales.orEmpty()
            .filter { it.state != TelltaleState.OFF }
            .mapNotNull { it.type.toWarning() }
            .toSet()
        report(api, value != null, if (value == null) "null" else "${value.telltales.size}개 수신, 경고 ${active.size}개")
        if (value == null) return
        synchronized(checkedWarnings) { telltaleWarnings = active }
        publishWarnings()
    }

    private fun onCheck(api: String, warning: Warning?, answered: Boolean, detail: String) {
        report(api, answered, detail)
        if (!answered) return
        synchronized(checkedWarnings) { checkedWarnings[api] = warning }
        publishWarnings()
    }

    /** Warnings stay null (= "확인 불가") until at least one source has answered. */
    private fun publishWarnings() {
        val merged = synchronized(checkedWarnings) {
            if (telltaleWarnings == null && checkedWarnings.isEmpty()) null
            else telltaleWarnings.orEmpty() + checkedWarnings.values.filterNotNull()
        }
        if (merged != null) update { it.copy(warnings = merged) }
    }

    private fun update(block: (VehicleSnapshot) -> VehicleSnapshot) {
        _snapshot.update { block(it).copy(timestampMillis = System.currentTimeMillis()) }
    }

    private inline fun guard(api: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            fail(api, t)
        }
    }

    private fun fail(api: String, t: Throwable) {
        Log.w(TAG, "$api failed: ${t.message}")
        report(api, false, t.message ?: t.javaClass.simpleName)
    }

    private fun report(api: String, ok: Boolean, detail: String) {
        _diagnostics.update { list -> list.filterNot { it.api == api } + SignalDiagnostic(api, ok, detail) }
    }

    companion object {
        private const val TAG = "BizmilePleos"
        private const val TICK_MILLIS = 5_000L
        const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val VEHICLE_SERVICE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"

        /** True when the Pleos Vehicle Service is installed, i.e. on a Pleos Connect head unit or AVD. */
        fun isVehicleServicePresent(context: Context): Boolean = try {
            context.packageManager.queryIntentServices(Intent(VEHICLE_SERVICE_ACTION), 0).isNotEmpty()
        } catch (_: Throwable) {
            false
        }

        private fun VehicleGear?.toGear(): Gear = when (this) {
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            else -> Gear.DRIVE // GEAR_DRIVE and the numbered forward gears
        }

        private fun OilLevelState?.isAbnormal(): Boolean =
            this == OilLevelState.LOW || this == OilLevelState.CRITICALLY_LOW || this == OilLevelState.ERROR

        private fun windowLabel(key: String): String = key.split("+").joinToString("+") {
            when (it) {
                WindowValue.ROW_1_LEFT.name -> "운전석"
                WindowValue.ROW_1_RIGHT.name -> "동승석"
                WindowValue.ROW_2_LEFT.name -> "뒷좌석 좌"
                WindowValue.ROW_2_RIGHT.name -> "뒷좌석 우"
                WindowValue.ROOF_TOP_1.name, WindowValue.ROOF_TOP_2.name -> "선루프"
                else -> it
            }
        }

        private fun TelltaleType.toWarning(): Warning? = when (this) {
            TelltaleType.TPMS -> Warning.TIRE_PRESSURE
            TelltaleType.ENGINE, TelltaleType.ENGINE_OIL_PRESSURE, TelltaleType.ENGINE_TEMPERATURE -> Warning.ENGINE
            TelltaleType.EV_SYSTEM, TelltaleType.POWER_DOWN -> Warning.EV_SYSTEM
            TelltaleType.LOW_EV_BATTERY, TelltaleType.LOW_FUEL -> Warning.LOW_BATTERY
            TelltaleType.LOW_VOLT_BATTERY -> Warning.LOW_VOLT_BATTERY
            TelltaleType.ABS, TelltaleType.ESC -> Warning.BRAKE_ABS
            TelltaleType.AIRBAG -> Warning.AIRBAG
            TelltaleType.MASTER_WARNING -> Warning.MASTER
            else -> null // lamps, drive modes, parking brake etc. are not handover issues
        }
    }
}
