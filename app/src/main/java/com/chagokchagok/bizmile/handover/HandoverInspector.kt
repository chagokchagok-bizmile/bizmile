package com.chagokchagok.bizmile.handover

import com.chagokchagok.bizmile.vehicle.VehicleSnapshot

enum class CheckLevel { OK, WARN, UNKNOWN }

data class HandoverItem(
    val key: String,
    val label: String,
    val value: String,
    val level: CheckLevel,
)

enum class HandoverVerdict(val label: String) {
    READY("사용 가능"),
    NEEDS_ATTENTION("점검 필요"),
    INCOMPLETE("일부 항목 확인 불가"),
}

data class HandoverReport(
    val createdAtMillis: Long,
    val vin: String?,
    val odometerKm: Float?,
    val items: List<HandoverItem>,
) {
    val verdict: HandoverVerdict
        get() = when {
            items.any { it.level == CheckLevel.WARN } -> HandoverVerdict.NEEDS_ATTENTION
            items.any { it.level == CheckLevel.UNKNOWN } -> HandoverVerdict.INCOMPLETE
            else -> HandoverVerdict.READY
        }
}

/**
 * The car inspects itself at return time. Every line is a value read from the vehicle;
 * anything the vehicle did not report is shown as "확인 불가" rather than assumed fine.
 */
class HandoverInspector(private val lowBatteryPercent: Int = 30) {

    fun inspect(s: VehicleSnapshot): HandoverReport {
        val items = buildList {
            add(
                when (val soc = s.socPercent) {
                    null -> unknown("battery", "배터리 잔량")
                    else -> HandoverItem(
                        "battery", "배터리 잔량",
                        if (soc < lowBatteryPercent) "$soc% · 다음 사용 전 충전 필요" else "$soc%",
                        if (soc < lowBatteryPercent) CheckLevel.WARN else CheckLevel.OK,
                    )
                },
            )
            add(
                when (val range = s.rangeKm) {
                    null -> unknown("range", "주행 가능 거리")
                    else -> HandoverItem("range", "주행 가능 거리", "${range.toInt()} km", CheckLevel.OK)
                },
            )
            add(
                when (s.doorsLocked) {
                    null -> unknown("doors", "도어 잠금")
                    true -> HandoverItem("doors", "도어 잠금", "잠김", CheckLevel.OK)
                    false -> HandoverItem("doors", "도어 잠금", "잠기지 않음", CheckLevel.WARN)
                },
            )
            add(
                when (val open = s.openWindows) {
                    null -> unknown("windows", "창문")
                    else -> if (open.isEmpty()) {
                        HandoverItem("windows", "창문", "모두 닫힘", CheckLevel.OK)
                    } else {
                        HandoverItem("windows", "창문", "열림: ${open.joinToString(", ")}", CheckLevel.WARN)
                    }
                },
            )
            add(
                when (val w = s.warnings) {
                    null -> unknown("warnings", "경고등")
                    else -> if (w.isEmpty()) {
                        HandoverItem("warnings", "경고등", "이상 없음", CheckLevel.OK)
                    } else {
                        HandoverItem("warnings", "경고등", w.joinToString(", ") { it.label }, CheckLevel.WARN)
                    }
                },
            )
        }
        return HandoverReport(s.timestampMillis, s.vin, s.odometerKm, items)
    }

    private fun unknown(key: String, label: String) =
        HandoverItem(key, label, "확인 불가 (차량에서 값을 받지 못함)", CheckLevel.UNKNOWN)
}
