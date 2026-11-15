package com.chagokchagok.bizmile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.ui.theme.Biz
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- formatting ---

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
private val dayFmt = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREA)
private val stampFmt = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREA)

fun fmtTime(millis: Long): String = timeFmt.format(Instant.ofEpochMilli(millis).atZone(zone))
fun fmtDay(millis: Long): String = dayFmt.format(Instant.ofEpochMilli(millis).atZone(zone))
fun fmtStamp(millis: Long): String = stampFmt.format(Instant.ofEpochMilli(millis).atZone(zone))
fun fmtKm(km: Float): String = String.format(Locale.US, "%,.1f", km)
fun fmtKmOrDash(km: Float?): String = km?.let { fmtKm(it) } ?: "–"

fun Purpose.color(): Color = when (this) {
    Purpose.BUSINESS -> Biz.Primary
    Purpose.PERSONAL -> Biz.Personal
    Purpose.COMMUTE -> Biz.Commute
}

fun TripEntity.route(): String = "${startPlace ?: "미등록 장소"}  →  ${endPlace ?: "미등록 장소"}"

// --- building blocks ---

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 24.dp,
    color: Color = Biz.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Biz.Muted, modifier = modifier)
}

/** Large touch target (min 72dp tall) for use from the driver's seat. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Biz.SurfaceHigh,
    content: Color = Biz.Text,
    selected: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = 72.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (enabled) container else Biz.Surface)
            .then(if (selected) Modifier.border(3.dp, Biz.Text, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) content else Biz.Muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) Biz.Bg else color,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, unit: String = "", valueColor: Color = Biz.Text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Biz.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Biz.Muted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = Biz.Text) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Biz.Muted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Biz.Muted,
        modifier = modifier.padding(PaddingValues(vertical = 12.dp)),
    )
}
