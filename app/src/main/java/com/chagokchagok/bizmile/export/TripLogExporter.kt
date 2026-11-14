package com.chagokchagok.bizmile.export

import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.data.purposeOrNull
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the company-submission mileage log. */
data class LogRow(
    val date: String,
    val depart: String,
    val arrive: String,
    val from: String,
    val to: String,
    val memo: String,
    val odometerBefore: String,
    val odometerAfter: String,
    val distance: String,
    val purpose: String,
)

/** Builds the monthly mileage log (운행일지) and writes it as an Excel-readable CSV. */
class TripLogExporter(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.KOREA)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

    fun tripsOf(month: YearMonth, trips: List<TripEntity>): List<TripEntity> =
        trips.filter { YearMonth.from(Instant.ofEpochMilli(it.startTimeMillis).atZone(zone)) == month }
            .sortedBy { it.startTimeMillis }

    fun rows(month: YearMonth, trips: List<TripEntity>): List<LogRow> =
        tripsOf(month, trips).map { t ->
            val start = Instant.ofEpochMilli(t.startTimeMillis).atZone(zone)
            val end = Instant.ofEpochMilli(t.endTimeMillis).atZone(zone)
            LogRow(
                date = dateFmt.format(start),
                depart = timeFmt.format(start),
                arrive = timeFmt.format(end),
                from = t.startPlace ?: "미등록 장소",
                to = t.endPlace ?: "미등록 장소",
                memo = t.memo,
                odometerBefore = km(t.startOdometerKm),
                odometerAfter = km(t.endOdometerKm),
                distance = km(t.distanceKm),
                purpose = t.purposeOrNull?.label ?: "미분류",
            )
        }

    fun toCsv(rows: List<LogRow>): String {
        val header = listOf(
            "일자", "출발", "도착", "출발지", "도착지", "목적",
            "주행 전 계기판(km)", "주행 후 계기판(km)", "주행거리(km)", "구분",
        )
        val lines = rows.map {
            listOf(
                it.date, it.depart, it.arrive, it.from, it.to, it.memo,
                it.odometerBefore, it.odometerAfter, it.distance, it.purpose,
            )
        }
        return (listOf(header) + lines).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
    }

    /** Writes the CSV with a UTF-8 BOM so Excel opens the Korean text correctly. */
    fun write(dir: File, month: YearMonth, trips: List<TripEntity>): File {
        dir.mkdirs()
        val file = File(dir, "bizmile_운행일지_$month.csv")
        file.writeText("﻿" + toCsv(rows(month, trips)), Charsets.UTF_8)
        return file
    }

    private fun km(value: Float) = String.format(Locale.US, "%.1f", value)

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
