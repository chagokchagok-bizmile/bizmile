package com.chagokchagok.bizmile.vehicle

/** Gear position, reduced to what trip detection needs. */
enum class Gear(val label: String) {
    PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("–");

    val isMoving: Boolean get() = this == DRIVE || this == REVERSE
}

data class GeoPoint(val lat: Double, val lng: Double)

/** Where the values on screen come from. Always shown to the user. */
enum class SignalSource(val label: String) {
    PLEOS("Pleos 차량 데이터"),
    SIMULATED("시뮬레이션 데이터"),
}

/** A warning light the handover report cares about. */
enum class Warning(val label: String) {
    TIRE_PRESSURE("타이어 공기압 경고"),
    ENGINE("엔진/구동 시스템 경고"),
    EV_SYSTEM("EV 시스템 경고"),
    LOW_BATTERY("배터리 부족 경고"),
    LOW_VOLT_BATTERY("12V 배터리 경고"),
    BRAKE_ABS("브레이크/ABS 경고"),
    AIRBAG("에어백 경고"),
    MASTER("통합 경고등"),
    WASHER_FLUID("워셔액 부족"),
    ENGINE_OIL("엔진오일 레벨 이상"),
    BRAKE_FLUID("브레이크액 레벨 이상"),
}

/**
 * One reading of everything the app uses from the vehicle.
 * A null field means "the vehicle has not given us this value" — it is never guessed.
 */
data class VehicleSnapshot(
    val timestampMillis: Long,
    val gear: Gear = Gear.UNKNOWN,
    val odometerKm: Float? = null,
    val socPercent: Int? = null,
    val rangeKm: Float? = null,
    val doorsLocked: Boolean? = null,
    /** Names of windows that are not fully closed; null when window state is unavailable. */
    val openWindows: List<String>? = null,
    /** Active warning lights; null when telltale state is unavailable. */
    val warnings: Set<Warning>? = null,
    val location: GeoPoint? = null,
    val vin: String? = null,
)

sealed interface SourceStatus {
    data object Connecting : SourceStatus
    data object Live : SourceStatus
    data class Unavailable(val reason: String) : SourceStatus
}

/** Result of one SDK read, kept for the "SDK 연결 상태" diagnostics list. */
data class SignalDiagnostic(
    val api: String,
    val ok: Boolean,
    val detail: String,
)
