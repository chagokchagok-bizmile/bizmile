package com.chagokchagok.bizmile

import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.export.TripLogExporter
import com.chagokchagok.bizmile.handover.CheckLevel
import com.chagokchagok.bizmile.handover.HandoverInspector
import com.chagokchagok.bizmile.handover.HandoverVerdict
import com.chagokchagok.bizmile.trip.Place
import com.chagokchagok.bizmile.trip.PlaceKind
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.trip.ReviewState
import com.chagokchagok.bizmile.trip.TripClassifier
import com.chagokchagok.bizmile.trip.TripDraft
import com.chagokchagok.bizmile.vehicle.Gear
import com.chagokchagok.bizmile.vehicle.GeoPoint
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import com.chagokchagok.bizmile.vehicle.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class ClassifierAndHandoverTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val classifier = TripClassifier(zone)

    private val home = Place(1, "집", GeoPoint(37.5112, 127.0981), 200f, PlaceKind.HOME, Purpose.PERSONAL)
    private val hq = Place(2, "본사", GeoPoint(37.4979, 127.0276), 200f, PlaceKind.WORK, Purpose.BUSINESS)
    private val site = Place(3, "판교현장", GeoPoint(37.4020, 127.1086), 300f, PlaceKind.CLIENT, Purpose.BUSINESS, "현장 점검")
    private val places = listOf(home, hq, site)

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun trip(start: Long, from: GeoPoint?, to: GeoPoint?) =
        TripDraft("t", start, start + 30 * 60_000L, 100f, 123.4f, from, to)

    // 2026-11-13 is a Friday, 2026-11-14 a Saturday.

    @Test
    fun `home to workplace on a weekday is commute without asking`() {
        val c = classifier.classify(trip(millis(2026, 11, 13, 8, 31), home.location, hq.location), home, hq)
        assertEquals(Purpose.COMMUTE, c.purpose)
        assertEquals(ReviewState.AUTO, c.state)
    }

    @Test
    fun `arriving at a registered client site pre-selects business for one-tap confirm`() {
        val c = classifier.classify(trip(millis(2026, 11, 13, 14, 5), hq.location, site.location), hq, site)
        assertEquals(Purpose.BUSINESS, c.purpose)
        assertEquals(ReviewState.SUGGESTED, c.state)
        assertEquals("현장 점검", c.memo)
    }

    @Test
    fun `unregistered destination is asked`() {
        val c = classifier.classify(trip(millis(2026, 11, 13, 17, 20), site.location, GeoPoint(37.3, 127.1)), site, null)
        assertNull(c.purpose)
        assertEquals(ReviewState.NEEDS_REVIEW, c.state)
    }

    @Test
    fun `home to workplace on a weekend is not assumed to be commute`() {
        val c = classifier.classify(trip(millis(2026, 11, 14, 10, 0), home.location, hq.location), home, hq)
        assertEquals(ReviewState.SUGGESTED, c.state)
    }

    @Test
    fun `place matching respects radius`() {
        assertEquals(hq, TripClassifier.matchPlace(GeoPoint(37.4985, 127.0280), places)) // ~75 m away
        assertNull(TripClassifier.matchPlace(GeoPoint(37.5050, 127.0276), places))       // ~790 m away
        assertNull(TripClassifier.matchPlace(null, places))
    }

    // --- handover ---

    private val healthy = VehicleSnapshot(
        timestampMillis = 0, gear = Gear.PARK, odometerKm = 12_503.7f, socPercent = 62, rangeKm = 280f,
        doorsLocked = true, openWindows = emptyList(), warnings = emptySet(), vin = "VIN",
    )

    @Test
    fun `healthy car is ready`() {
        val report = HandoverInspector().inspect(healthy)
        assertEquals(HandoverVerdict.READY, report.verdict)
        assertTrue(report.items.all { it.level == CheckLevel.OK })
    }

    @Test
    fun `low battery and tire warning need attention`() {
        val report = HandoverInspector().inspect(
            healthy.copy(socPercent = 18, warnings = setOf(Warning.TIRE_PRESSURE), openWindows = listOf("운전석")),
        )
        assertEquals(HandoverVerdict.NEEDS_ATTENTION, report.verdict)
        assertEquals(
            setOf("battery", "warnings", "windows"),
            report.items.filter { it.level == CheckLevel.WARN }.map { it.key }.toSet(),
        )
    }

    @Test
    fun `values the vehicle did not report are unknown, not ok`() {
        val report = HandoverInspector().inspect(healthy.copy(doorsLocked = null, warnings = null))
        assertEquals(HandoverVerdict.INCOMPLETE, report.verdict)
        assertEquals(CheckLevel.UNKNOWN, report.items.first { it.key == "doors" }.level)
    }

    // --- export ---

    @Test
    fun `csv has cluster odometer before and after and escapes commas`() {
        val start = millis(2026, 11, 13, 14, 5)
        val entity = TripEntity(
            id = "t", vin = "VIN", startTimeMillis = start, endTimeMillis = start + 37 * 60_000L,
            startOdometerKm = 12_480.3f, endOdometerKm = 12_503.7f,
            startPlace = "본사", endPlace = "○○건설 판교현장",
            startLat = null, startLng = null, endLat = null, endLng = null, stopovers = 0,
            purpose = "BUSINESS", reviewState = "CONFIRMED", reason = "", memo = "현장 점검, 자재 확인",
            source = "SIMULATED",
        )
        val exporter = TripLogExporter(zone)
        val csv = exporter.toCsv(exporter.rows(YearMonth.of(2026, 11), listOf(entity)))
        val lines = csv.split("\r\n")
        assertEquals(2, lines.size)
        assertEquals(
            "11/13,14:05,14:42,본사,○○건설 판교현장,\"현장 점검, 자재 확인\",12480.3,12503.7,23.4,업무",
            lines[1],
        )
        assertTrue(exporter.rows(YearMonth.of(2026, 10), listOf(entity)).isEmpty())
    }
}
