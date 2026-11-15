package com.chagokchagok.bizmile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.data.awaitsDriver
import com.chagokchagok.bizmile.data.purposeOrNull
import com.chagokchagok.bizmile.data.signalSource
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.ui.BigButton
import com.chagokchagok.bizmile.ui.Chip
import com.chagokchagok.bizmile.ui.EmptyNote
import com.chagokchagok.bizmile.ui.ExportResult
import com.chagokchagok.bizmile.ui.Panel
import com.chagokchagok.bizmile.ui.Stat
import com.chagokchagok.bizmile.ui.color
import com.chagokchagok.bizmile.ui.fmtDay
import com.chagokchagok.bizmile.ui.fmtKm
import com.chagokchagok.bizmile.ui.fmtTime
import com.chagokchagok.bizmile.ui.theme.Biz
import com.chagokchagok.bizmile.vehicle.SignalSource
import java.time.YearMonth

/** Monthly mileage log in the layout a company asks for: cluster odometer before / after each trip. */
@Composable
fun LogScreen(
    month: YearMonth,
    trips: List<TripEntity>,
    export: ExportResult?,
    onConfirm: (TripEntity, Purpose) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val pendingCount = trips.count { it.awaitsDriver }

    fun km(p: Purpose) = trips.filter { it.purposeOrNull == p && !it.awaitsDriver }.sumOf { it.distanceKm.toDouble() }.toFloat()

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Text("${month.monthValue}월 운행일지", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Stat("업무", fmtKm(km(Purpose.BUSINESS)), unit = "km", valueColor = Biz.Primary)
                Stat("출퇴근", fmtKm(km(Purpose.COMMUTE)), unit = "km", valueColor = Biz.Commute)
                Stat("개인", fmtKm(km(Purpose.PERSONAL)), unit = "km", valueColor = Biz.Personal)
                BigButton(
                    "CSV 내보내기", onExport,
                    container = Biz.Primary, content = Biz.OnPrimary, enabled = trips.isNotEmpty(),
                )
            }
            if (pendingCount > 0) {
                Text(
                    "확인이 필요한 운행 ${pendingCount}건 — 행을 누르면 바로 분류할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Biz.Warn,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (export != null) {
                Text(
                    "${export.rowCount}건 저장됨 · ${export.path}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Biz.Ok,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Panel(Modifier.fillMaxWidth().weight(1f), padding = 0.dp) {
            HeaderRow()
            if (trips.isEmpty()) {
                EmptyNote("이번 달에 기록된 운행이 없습니다.", Modifier.padding(horizontal = 24.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(trips.sortedByDescending { it.startTimeMillis }, key = { it.id }) { trip ->
                        LogRowView(
                            trip = trip,
                            expanded = editing == trip.id,
                            onClick = { editing = if (editing == trip.id) null else trip.id },
                            onPick = { purpose ->
                                onConfirm(trip, purpose)
                                editing = null
                            },
                        )
                    }
                }
            }
        }
    }
}

private val COLS = listOf(1.5f, 1.9f, 2.9f, 1.4f, 1.4f, 1.4f, 1.1f, 1.3f)

@Composable
private fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth().background(Biz.SurfaceHigh).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labels = listOf("일자", "시간", "출발지 → 도착지", "목적", "주행 전(km)", "주행 후(km)", "거리(km)", "구분")
        labels.forEachIndexed { i, label ->
            Cell(label, COLS[i], color = Biz.Muted, end = i in 4..6)
        }
    }
}

@Composable
private fun LogRowView(trip: TripEntity, expanded: Boolean, onClick: () -> Unit, onPick: (Purpose) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (trip.awaitsDriver) Biz.Warn.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cell(fmtDay(trip.startTimeMillis), COLS[0], color = Biz.Muted)
            Cell("${fmtTime(trip.startTimeMillis)}–${fmtTime(trip.endTimeMillis)}", COLS[1], color = Biz.Muted)
            Cell("${trip.startPlace ?: "미등록 장소"} → ${trip.endPlace ?: "미등록 장소"}", COLS[2])
            Cell(trip.memo.ifBlank { "–" }, COLS[3], color = Biz.Muted)
            Cell(fmtKm(trip.startOdometerKm), COLS[4], end = true)
            Cell(fmtKm(trip.endOdometerKm), COLS[5], end = true)
            Cell(fmtKm(trip.distanceKm), COLS[6], end = true)
            Row(Modifier.weight(COLS[7]), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PurposeChip(trip)
                if (trip.signalSource == SignalSource.SIMULATED) {
                    Text("시뮬", style = MaterialTheme.typography.labelMedium, color = Biz.Muted)
                }
            }
        }
        if (expanded) {
            Row(
                Modifier.fillMaxWidth().background(Biz.SurfaceHigh).padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(trip.reason.ifBlank { "구분 변경" }, style = MaterialTheme.typography.bodyMedium, color = Biz.Muted, modifier = Modifier.weight(1f))
                Purpose.entries.forEach { p ->
                    BigButton(
                        p.label, { onPick(p) }, Modifier.width(150.dp),
                        container = p.color(), content = Biz.OnPrimary,
                        selected = trip.purposeOrNull == p, minHeight = 60.dp,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Biz.Line))
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, color: Color = Biz.Text, end: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(weight).padding(end = 12.dp),
    )
}
