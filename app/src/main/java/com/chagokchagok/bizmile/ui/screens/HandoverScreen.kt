package com.chagokchagok.bizmile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chagokchagok.bizmile.handover.CheckLevel
import com.chagokchagok.bizmile.handover.HandoverItem
import com.chagokchagok.bizmile.handover.HandoverReport
import com.chagokchagok.bizmile.handover.HandoverVerdict
import com.chagokchagok.bizmile.ui.BigButton
import com.chagokchagok.bizmile.ui.Chip
import com.chagokchagok.bizmile.ui.EmptyNote
import com.chagokchagok.bizmile.ui.Panel
import com.chagokchagok.bizmile.ui.SectionTitle
import com.chagokchagok.bizmile.ui.fmtKmOrDash
import com.chagokchagok.bizmile.ui.fmtStamp
import com.chagokchagok.bizmile.ui.theme.Biz

private fun HandoverVerdict.color(): Color = when (this) {
    HandoverVerdict.READY -> Biz.Ok
    HandoverVerdict.NEEDS_ATTENTION -> Biz.Warn
    HandoverVerdict.INCOMPLETE -> Biz.Muted
}

private fun CheckLevel.color(): Color = when (this) {
    CheckLevel.OK -> Biz.Ok
    CheckLevel.WARN -> Biz.Warn
    CheckLevel.UNKNOWN -> Biz.Muted
}

private fun CheckLevel.mark(): String = when (this) {
    CheckLevel.OK -> "✓"
    CheckLevel.WARN -> "!"
    CheckLevel.UNKNOWN -> "?"
}

/** Return-time self inspection: the car reports its own state for the next driver. */
@Composable
fun HandoverScreen(
    reports: List<HandoverReport>,
    inspecting: Boolean,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = reports.firstOrNull()
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1.6f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("반납 인수인계 리포트", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "사람이 점검표를 쓰지 않습니다. 차가 직접 읽은 값만 기록합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Biz.Muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                BigButton(
                    if (inspecting) "점검 중…" else "반납 점검 실행", onInspect,
                    container = Biz.Primary, content = Biz.OnPrimary, enabled = !inspecting,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (latest == null) {
                EmptyNote("아직 점검 기록이 없습니다. 주차 후 ‘반납 점검 실행’을 누르세요.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Chip(latest.verdict.label, latest.verdict.color(), filled = true)
                    Text(
                        "${fmtStamp(latest.createdAtMillis)} · 계기판 ${fmtKmOrDash(latest.odometerKm)} km",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Biz.Muted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    latest.items.forEach { ItemRow(it) }
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            SectionTitle("점검 이력")
            Spacer(Modifier.height(8.dp))
            if (reports.isEmpty()) {
                EmptyNote("기록 없음")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    reports.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(fmtStamp(r.createdAtMillis), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            val issues = r.items.count { it.level == CheckLevel.WARN }
                            if (issues > 0) Text("이상 ${issues}건", style = MaterialTheme.typography.bodyMedium, color = Biz.Warn)
                            Chip(r.verdict.label, r.verdict.color())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: HandoverItem) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(item.level.color().copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.level.mark(), style = MaterialTheme.typography.titleLarge, color = item.level.color(), fontWeight = FontWeight.Bold)
        }
        Text(item.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            item.value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.level == CheckLevel.OK) Biz.Text else item.level.color(),
            modifier = Modifier.weight(2f),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Biz.Line))
}
