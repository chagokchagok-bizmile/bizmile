package com.chagokchagok.bizmile.data

import com.chagokchagok.bizmile.handover.CheckLevel
import com.chagokchagok.bizmile.handover.HandoverItem
import com.chagokchagok.bizmile.handover.HandoverReport
import com.chagokchagok.bizmile.trip.Place
import com.chagokchagok.bizmile.trip.PlaceKind
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.trip.ReviewState
import com.chagokchagok.bizmile.vehicle.GeoPoint
import com.chagokchagok.bizmile.vehicle.SignalSource

fun PlaceEntity.toPlace() = Place(
    id = id,
    name = name,
    location = GeoPoint(lat, lng),
    radiusMeters = radiusMeters,
    kind = runCatching { PlaceKind.valueOf(kind) }.getOrDefault(PlaceKind.OTHER),
    defaultPurpose = runCatching { Purpose.valueOf(defaultPurpose) }.getOrDefault(Purpose.BUSINESS),
    defaultMemo = defaultMemo,
)

val TripEntity.purposeOrNull: Purpose?
    get() = purpose?.let { runCatching { Purpose.valueOf(it) }.getOrNull() }

val TripEntity.review: ReviewState
    get() = runCatching { ReviewState.valueOf(reviewState) }.getOrDefault(ReviewState.NEEDS_REVIEW)

val TripEntity.signalSource: SignalSource
    get() = runCatching { SignalSource.valueOf(source) }.getOrDefault(SignalSource.SIMULATED)

/** Still waiting for the driver's one tap. */
val TripEntity.awaitsDriver: Boolean
    get() = review == ReviewState.SUGGESTED || review == ReviewState.NEEDS_REVIEW

fun HandoverReport.toEntity(source: SignalSource) = HandoverEntity(
    createdAtMillis = createdAtMillis,
    vin = vin,
    odometerKm = odometerKm,
    verdict = verdict.name,
    items = items.joinToString("\n") { listOf(it.key, it.label, it.value, it.level.name).joinToString("\t") },
    source = source.name,
)

fun HandoverEntity.toReport() = HandoverReport(
    createdAtMillis = createdAtMillis,
    vin = vin,
    odometerKm = odometerKm,
    items = items.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size < 4) null
        else HandoverItem(p[0], p[1], p[2], runCatching { CheckLevel.valueOf(p[3]) }.getOrDefault(CheckLevel.UNKNOWN))
    },
)
