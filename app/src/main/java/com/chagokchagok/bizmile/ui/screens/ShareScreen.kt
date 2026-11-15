package com.chagokchagok.bizmile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagokchagok.bizmile.trip.Place
import com.chagokchagok.bizmile.trip.PlaceKind
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.trip.TripClassifier
import com.chagokchagok.bizmile.trip.TripPhase
import com.chagokchagok.bizmile.ui.BigButton
import com.chagokchagok.bizmile.ui.Chip
import com.chagokchagok.bizmile.ui.EmptyNote
import com.chagokchagok.bizmile.ui.KeyValueRow
import com.chagokchagok.bizmile.ui.Panel
import com.chagokchagok.bizmile.ui.SectionTitle
import com.chagokchagok.bizmile.ui.color
import com.chagokchagok.bizmile.ui.theme.Biz
import com.chagokchagok.bizmile.vehicle.SignalDiagnostic
import com.chagokchagok.bizmile.vehicle.SignalSource
import com.chagokchagok.bizmile.vehicle.SourceStatus
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * What a corporate car would share with colleagues: when it is usable, not where exactly it is.
 * This build only shows the snapshot; sending it to a company server is not implemented yet.
 */
fun buildShareSnapshot(snapshot: VehicleSnapshot, phase: TripPhase, places: List<Place>): JSONObject {
    val place = TripClassifier.matchPlace(snapshot.location, places)?.name
    return JSONObject().apply {
        // Only the last 4 characters, and only when the vehicle returned a well-formed VIN.
        val vin = snapshot.vin?.takeIf { v -> v.length == 17 && v.all { it.isLetterOrDigit() } }
        put("vehicle", vin?.let { "…" + it.takeLast(4) } ?: JSONObject.NULL)
        put("state", if (phase == TripPhase.DRIVING) "운행 중" else "주차")
        put("place", if (phase == TripPhase.DRIVING) "이동 중" else (place ?: "등록되지 않은 장소"))
        put("batteryPercent", snapshot.socPercent ?: JSONObject.NULL)
        put("rangeKm", snapshot.rangeKm?.toInt() ?: JSONObject.NULL)
        put("warnings", snapshot.warnings?.let { w -> JSONArray(w.map { it.label }) } ?: JSONObject.NULL)
        put("doorsLocked", snapshot.doorsLocked ?: JSONObject.NULL)
        put("updatedAt", Instant.ofEpochMilli(snapshot.timestampMillis).toString())
    }
}

@Composable
fun ShareScreen(
    snapshot: VehicleSnapshot,
    phase: TripPhase,
    places: List<Place>,
    sourceKind: SignalSource,
    status: SourceStatus,
    diagnostics: List<SignalDiagnostic>,
    pleosAvailable: Boolean,
    onUseSource: (SignalSource) -> Unit,
    onAddPlaceHere: (String, PlaceKind, Purpose) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onClearSimulated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("법인차 상태 공유", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Chip("미리보기 · 서버 전송 미구현", Biz.Warn)
                }
                Text(
                    "‘언제 쓸 수 있나’만 공유합니다. 아래가 사내에 전달될 내용 전부입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Biz.Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                Text(
                    buildShareSnapshot(snapshot, phase, places).toString(2),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Biz.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Biz.Bg)
                        .padding(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                KeyValueRow("공유함", "운행 상태 · 등록 장소명 · 배터리 · 경고등", Biz.Ok)
                KeyValueRow("공유 안 함", "정확한 좌표 · 이동 경로 · 개인 운행 내역", Biz.Muted)
            }

            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("등록 방문지")
                Spacer(Modifier.height(6.dp))
                if (places.isEmpty()) EmptyNote("등록된 방문지가 없습니다.")
                places.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("반경 ${p.radiusMeters.toInt()}m", style = MaterialTheme.typography.bodyMedium, color = Biz.Muted)
                        Chip("기본: ${p.defaultPurpose.label}", p.defaultPurpose.color())
                        BigButton("삭제", { onDeletePlace(p.id) }, minHeight = 48.dp)
                    }
                }
                val here = TripClassifier.matchPlace(snapshot.location, places)
                Spacer(Modifier.height(10.dp))
                BigButton(
                    text = when {
                        snapshot.location == null -> "현재 위치를 받지 못해 등록할 수 없음"
                        here != null -> "현재 위치는 ‘${here.name}’(으)로 등록되어 있음"
                        else -> "현재 위치를 업무 방문지로 등록"
                    },
                    onClick = { onAddPlaceHere("방문지 ${places.size + 1}", PlaceKind.CLIENT, Purpose.BUSINESS) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snapshot.location != null && here == null,
                    minHeight = 60.dp,
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("차량 데이터 출처")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        "Pleos SDK", { onUseSource(SignalSource.PLEOS) }, Modifier.weight(1f),
                        selected = sourceKind == SignalSource.PLEOS, enabled = pleosAvailable, minHeight = 60.dp,
                    )
                    BigButton(
                        "시뮬레이션", { onUseSource(SignalSource.SIMULATED) }, Modifier.weight(1f),
                        selected = sourceKind == SignalSource.SIMULATED, minHeight = 60.dp,
                    )
                }
                Text(
                    when {
                        !pleosAvailable -> "이 기기에는 Pleos Vehicle Service가 없어 시뮬레이션 데이터로 동작합니다."
                        status is SourceStatus.Unavailable -> "Pleos SDK 초기화 실패: ${status.reason}"
                        sourceKind == SignalSource.PLEOS -> "Pleos Vehicle SDK 2.3.2 · FusedLocation SDK 1.1.3에서 읽는 중"
                        else -> "시뮬레이션 데이터 사용 중 (Pleos Vehicle Service 사용 가능)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Biz.Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(Modifier.height(12.dp))
                BigButton("시뮬레이션으로 만든 운행 기록 삭제", onClearSimulated, Modifier.fillMaxWidth(), minHeight = 56.dp)
            }

            if (sourceKind == SignalSource.PLEOS) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("SDK 호출 결과")
                    Spacer(Modifier.height(6.dp))
                    if (diagnostics.isEmpty()) EmptyNote("아직 응답이 없습니다.")
                    diagnostics.sortedBy { it.api }.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(if (d.ok) "✓" else "✕", color = if (d.ok) Biz.Ok else Biz.Danger, style = MaterialTheme.typography.bodyMedium)
                            Text(d.api, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.5f))
                            Text(d.detail, style = MaterialTheme.typography.bodyMedium, color = Biz.Muted, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
