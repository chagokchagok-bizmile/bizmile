package com.chagokchagok.bizmile.trip

import com.chagokchagok.bizmile.vehicle.GeoPoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Purpose(val label: String) {
    BUSINESS("업무"), PERSONAL("개인"), COMMUTE("출퇴근")
}

enum class PlaceKind(val label: String) {
    HOME("집"), WORK("근무지"), CLIENT("방문지"), OTHER("기타")
}

data class Place(
    val id: Long,
    val name: String,
    val location: GeoPoint,
    val radiusMeters: Float,
    val kind: PlaceKind,
    val defaultPurpose: Purpose,
    val defaultMemo: String = "",
)

/** How sure the app is about a trip's purpose. */
enum class ReviewState(val label: String) {
    /** Rule matched with high confidence; no question asked. */
    AUTO("자동 분류"),
    /** A default is pre-selected; the driver confirms or changes it with one tap. */
    SUGGESTED("확인 대기"),
    /** No rule matched; the driver has to choose. */
    NEEDS_REVIEW("확인 필요"),
    /** The driver chose. */
    CONFIRMED("확인 완료"),
}

data class Classification(
    val purpose: Purpose?,
    val state: ReviewState,
    val reason: String,
    val memo: String = "",
)

/**
 * "Only ask about the ambiguous ones":
 *   home ↔ workplace on a weekday      → 출퇴근, automatic
 *   arriving at a registered place     → that place's default, pre-selected for one-tap confirm
 *   anything else                      → ask
 */
class TripClassifier(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun classify(trip: TripDraft, startPlace: Place?, endPlace: Place?): Classification {
        val day = Instant.ofEpochMilli(trip.startTimeMillis).atZone(zone).dayOfWeek
        val weekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY

        val kinds = setOf(startPlace?.kind, endPlace?.kind)
        if (weekday && kinds == setOf(PlaceKind.HOME, PlaceKind.WORK)) {
            return Classification(Purpose.COMMUTE, ReviewState.AUTO, "집 ↔ 근무지 · 평일")
        }
        if (endPlace != null) {
            return Classification(
                purpose = endPlace.defaultPurpose,
                state = ReviewState.SUGGESTED,
                reason = "등록된 방문지 ‘${endPlace.name}’ 도착",
                memo = endPlace.defaultMemo,
            )
        }
        val reason = when {
            trip.endLocation == null -> "도착 위치를 받지 못함"
            !weekday -> "미등록 장소 · 주말"
            else -> "미등록 장소"
        }
        return Classification(null, ReviewState.NEEDS_REVIEW, reason)
    }

    companion object {
        /** The registered place whose radius contains [point]; the nearest one if several do. */
        fun matchPlace(point: GeoPoint?, places: List<Place>): Place? {
            if (point == null) return null
            return places
                .map { it to distanceMeters(point, it.location) }
                .filter { (place, d) -> d <= place.radiusMeters }
                .minByOrNull { it.second }
                ?.first
        }

        fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLng = Math.toRadians(b.lng - a.lng)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
