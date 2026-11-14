package com.chagokchagok.bizmile

import com.chagokchagok.bizmile.trip.TripEvent
import com.chagokchagok.bizmile.trip.TripPhase
import com.chagokchagok.bizmile.trip.TripSegmenter
import com.chagokchagok.bizmile.vehicle.Gear
import com.chagokchagok.bizmile.vehicle.GeoPoint
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSegmenterTest {

    private val minute = 60_000L
    private var ids = 0
    private fun segmenter() = TripSegmenter(newId = { "trip-${++ids}" })

    private fun snap(atMin: Long, gear: Gear, odo: Float?, loc: GeoPoint? = null) =
        VehicleSnapshot(timestampMillis = atMin * minute, gear = gear, odometerKm = odo, location = loc)

    private fun TripSegmenter.feed(vararg s: VehicleSnapshot): List<TripEvent> = s.flatMap { onSnapshot(it) }

    @Test
    fun `park to drive to park records one trip with odometer at both ends`() {
        val hq = GeoPoint(37.4979, 127.0276)
        val site = GeoPoint(37.4020, 127.1086)
        val s = segmenter()

        val events = s.feed(
            snap(0, Gear.PARK, 12_480.3f, hq),
            snap(1, Gear.DRIVE, 12_480.3f, hq),
            snap(20, Gear.DRIVE, 12_492.0f),
            snap(38, Gear.DRIVE, 12_503.7f, site),
            snap(38, Gear.PARK, 12_503.7f, site),
        )

        assertTrue(events[0] is TripEvent.Started)
        val ended = events.filterIsInstance<TripEvent.Ended>().single().trip
        assertEquals(12_480.3f, ended.startOdometerKm, 0.001f)
        assertEquals(12_503.7f, ended.endOdometerKm, 0.001f)
        assertEquals(23.4f, ended.distanceKm, 0.01f)
        assertEquals(1 * minute, ended.startTimeMillis)
        assertEquals(38 * minute, ended.endTimeMillis)
        assertEquals(hq, ended.startLocation)
        assertEquals(site, ended.endLocation)
        assertEquals(TripPhase.STOPPED, s.phase)
    }

    @Test
    fun `staying in park records nothing`() {
        val s = segmenter()
        val events = s.feed(snap(0, Gear.PARK, 100f), snap(30, Gear.PARK, 100f))
        assertTrue(events.isEmpty())
        assertEquals(TripPhase.IDLE, s.phase)
    }

    @Test
    fun `re-parking a few metres is discarded`() {
        val s = segmenter()
        val events = s.feed(
            snap(0, Gear.PARK, 100f),
            snap(1, Gear.REVERSE, 100f),
            snap(2, Gear.PARK, 100.03f),
        )
        assertTrue(events.last() is TripEvent.Discarded)
        assertEquals(TripPhase.IDLE, s.phase)
        assertNull(s.current)
    }

    @Test
    fun `short stop within merge window continues the same trip`() {
        val s = segmenter()
        val events = s.feed(
            snap(0, Gear.PARK, 100f),
            snap(1, Gear.DRIVE, 100f),
            snap(10, Gear.PARK, 105f),   // fuel stop
            snap(15, Gear.DRIVE, 105f),  // leaves 5 min later
            snap(30, Gear.PARK, 118f),
        )
        val ended = events.filterIsInstance<TripEvent.Ended>()
        assertEquals(2, ended.size)
        assertEquals(ended[0].trip.id, ended[1].trip.id)
        assertEquals(1, ended[1].trip.stopovers)
        assertEquals(18f, ended[1].trip.distanceKm, 0.01f)
        assertEquals(1, events.filterIsInstance<TripEvent.Resumed>().size)
    }

    @Test
    fun `departure after merge window is a new trip`() {
        val s = segmenter()
        val events = s.feed(
            snap(0, Gear.PARK, 100f),
            snap(1, Gear.DRIVE, 100f),
            snap(10, Gear.PARK, 105f),
            snap(45, Gear.DRIVE, 105f),
            snap(60, Gear.PARK, 112f),
        )
        val ended = events.filterIsInstance<TripEvent.Ended>().map { it.trip }
        assertEquals(2, ended.size)
        assertTrue(ended[0].id != ended[1].id)
        assertEquals(105f, ended[1].startOdometerKm, 0.001f)
        assertEquals(0, ended[1].stopovers)
    }

    @Test
    fun `finalizing a stopped trip prevents merging`() {
        val s = segmenter()
        s.feed(snap(0, Gear.PARK, 100f), snap(1, Gear.DRIVE, 100f), snap(10, Gear.PARK, 105f))
        s.finalizeStopped()
        val events = s.feed(snap(12, Gear.DRIVE, 105f))
        assertTrue(events.single() is TripEvent.Started)
    }

    @Test
    fun `without a gear signal the odometer alone starts and ends a trip`() {
        val s = segmenter()
        val events = s.feed(
            snap(0, Gear.UNKNOWN, 100f),
            snap(1, Gear.UNKNOWN, 100.5f),  // starts moving
            snap(5, Gear.UNKNOWN, 103f),
            snap(6, Gear.UNKNOWN, 103f),    // stopped
            snap(8, Gear.UNKNOWN, 103f),
            snap(9, Gear.UNKNOWN, 103f),    // 4 min without movement → ended
        )
        val started = events.filterIsInstance<TripEvent.Started>().single().trip
        assertEquals(100f, started.startOdometerKm, 0.001f)
        val ended = events.filterIsInstance<TripEvent.Ended>().single().trip
        assertEquals(3f, ended.distanceKm, 0.01f)
        // the trip ended when the car last moved, not when the timeout fired
        assertEquals(5 * minute, ended.endTimeMillis)
    }

    @Test
    fun `a red light does not end a trip when gear stays in drive`() {
        val s = segmenter()
        val events = s.feed(
            snap(0, Gear.PARK, 100f),
            snap(1, Gear.DRIVE, 100f),
            snap(5, Gear.DRIVE, 102f),
            snap(12, Gear.DRIVE, 102f),  // 7 min standstill in D (traffic)
            snap(20, Gear.DRIVE, 108f),
        )
        assertTrue(events.none { it is TripEvent.Ended })
        assertEquals(TripPhase.DRIVING, s.phase)
    }

    @Test
    fun `missing odometer never starts a trip`() {
        val s = segmenter()
        val events = s.feed(snap(0, Gear.PARK, null), snap(1, Gear.DRIVE, null))
        assertTrue(events.isEmpty())
    }
}
