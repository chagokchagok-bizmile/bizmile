package com.chagokchagok.bizmile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chagokchagok.bizmile.data.TripEntity
import com.chagokchagok.bizmile.data.awaitsDriver
import com.chagokchagok.bizmile.data.purposeOrNull
import com.chagokchagok.bizmile.data.review
import com.chagokchagok.bizmile.trip.Place
import com.chagokchagok.bizmile.trip.Purpose
import com.chagokchagok.bizmile.trip.ReviewState
import com.chagokchagok.bizmile.trip.TripClassifier
import com.chagokchagok.bizmile.trip.TripDraft
import com.chagokchagok.bizmile.ui.BigButton
import com.chagokchagok.bizmile.ui.Chip
import com.chagokchagok.bizmile.ui.EmptyNote
import com.chagokchagok.bizmile.ui.Panel
import com.chagokchagok.bizmile.ui.SectionTitle
import com.chagokchagok.bizmile.ui.Stat
import com.chagokchagok.bizmile.ui.color
import com.chagokchagok.bizmile.ui.fmtDay
import com.chagokchagok.bizmile.ui.fmtKm
import com.chagokchagok.bizmile.ui.fmtKmOrDash
import com.chagokchagok.bizmile.ui.fmtTime
import com.chagokchagok.bizmile.ui.route
import com.chagokchagok.bizmile.ui.theme.Biz
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot

/**
 * Shown instead of every other screen while the car is in D or R.
 * Nothing here can be tapped: the trip records itself.
 */
@Composable
fun DrivingScreen(snapshot: VehicleSnapshot, liveTrip: TripDraft?, places: List<Place>, modifier: Modifier = Modifier) {
    val startPlace = TripClassifier.matchPlace(liveTrip?.startLocation, places)?.name
    Panel(modifier.fillMaxSize(), padding = 40.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Chip("● 운행 기록 중", Biz.Ok, filled = true)
            Text("주행 중에는 조작이 필요 없습니다", style = MaterialTheme.typography.titleMedium, color = Biz.Muted)
        }
        Spacer(Modifier.weight(1f))
        if (liveTrip != null) {
            Text("이번 운행", style = MaterialTheme.typography.titleMedium, color = Biz.Muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtKm(liveTrip.distanceKm), style = MaterialTheme.typography.displaySmall, color = Biz.Text)
                Text("  km", style = MaterialTheme.typography.titleLarge, color = Biz.Muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Stat("출발", fmtTime(liveTrip.startTimeMillis))
                Stat("출발지", startPlace ?: "미등록 장소")
                Stat("출발 시 계기판", fmtKm(liveTrip.startOdometerKm), unit = "km")
                Stat("현재 계기판", fmtKmOrDash(snapshot.odometerKm), unit = "km")
            }
        } else {
            Text("차량 주행거리를 기다리는 중", style = MaterialTheme.typography.headlineMedium, color = Biz.Muted)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "정차(P) 하면 운행이 저장되고, 업무 / 개인만 한 번 선택하면 됩니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = Biz.Muted,
        )
    }
}

/** Parked view: the one-tap card for the trip that just ended, then today's trips. */
@Composable
fun DriveScreen(
    snapshot: VehicleSnapshot,
    trips: List<TripEntity>,
    places: List<Place>,
    onConfirm: (TripEntity, Purpose) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = trips.firstOrNull { it.awaitsDriver }
    val here = TripClassifier.matchPlace(snapshot.location, places)?.name
    val today = trips.filter { fmtDay(it.startTimeMillis) == fmtDay(snapshot.timestampMillis) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (pending != null) {
            ClassifyCard(pending, onConfirm)
        } else {
            Panel(Modifier.fillMaxWidth()) {
                Text("기록 준비 완료", style = MaterialTheme.typography.titleLarge)
                Text(
                    "기어를 D로 옮기면 운행이 자동으로 시작됩니다. 앱을 열어둘 필요는 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Biz.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("차량에서 읽은 값")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("계기판 누적 주행거리", fmtKmOrDash(snapshot.odometerKm), Modifier.weight(1.4f), unit = "km")
                Stat("기어", snapshot.gear.label, Modifier.weight(0.6f))
                Stat(
                    "배터리", snapshot.socPercent?.toString() ?: "–", Modifier.weight(0.8f), unit = "%",
                    valueColor = if ((snapshot.socPercent ?: 100) < 30) Biz.Warn else Biz.Text,
                )
                Stat(
                    "현재 위치",
                    here ?: if (snapshot.location == null) "수신 안 됨" else "미등록 장소",
                    Modifier.weight(1.4f),
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            val business = today.filter { it.purposeOrNull == Purpose.BUSINESS }.sumOf { it.distanceKm.toDouble() }.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("오늘 운행 ${today.size}건", Modifier.weight(1f))
                Text("업무 ${fmtKm(business)} km", style = MaterialTheme.typography.titleMedium, color = Biz.Primary)
            }
            if (today.isEmpty()) {
                EmptyNote("아직 기록된 운행이 없습니다.")
            } else {
                Spacer(Modifier.height(8.dp))
                today.forEach { TripLine(it) }
            }
        }
    }
}

@Composable
private fun ClassifyCard(trip: TripEntity, onConfirm: (TripEntity, Purpose) -> Unit) {
    val suggested = trip.purposeOrNull
    Panel(Modifier.fillMaxWidth(), padding = 28.dp, color = Biz.SurfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("방금 운행", style = MaterialTheme.typography.titleMedium, color = Biz.Muted)
            Text(
                "${fmtTime(trip.startTimeMillis)} → ${fmtTime(trip.endTimeMillis)}  ·  ${fmtKm(trip.distanceKm)} km",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(trip.route(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
        Text(
            "계기판 ${fmtKm(trip.startOdometerKm)} → ${fmtKm(trip.endOdometerKm)} km" +
                if (trip.stopovers > 0) "  ·  경유 ${trip.stopovers}회" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = Biz.Muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(
                "업무", { onConfirm(trip, Purpose.BUSINESS) }, Modifier.weight(1f),
                container = Biz.Primary, content = Biz.OnPrimary,
                selected = suggested == Purpose.BUSINESS, minHeight = 96.dp,
            )
            BigButton(
                "개인", { onConfirm(trip, Purpose.PERSONAL) }, Modifier.weight(1f),
                container = Biz.Personal, content = Biz.OnPrimary,
                selected = suggested == Purpose.PERSONAL, minHeight = 96.dp,
            )
            BigButton(
                "출퇴근", { onConfirm(trip, Purpose.COMMUTE) }, Modifier.weight(0.6f),
                selected = suggested == Purpose.COMMUTE, minHeight = 96.dp,
            )
        }
        Text(
            if (suggested != null) "기본 선택: ${suggested.label} — ${trip.reason}" else "자동 분류 안 됨 — ${trip.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = Biz.Muted,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
fun TripLine(trip: TripEntity, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(fmtTime(trip.startTimeMillis), style = MaterialTheme.typography.bodyLarge, color = Biz.Muted)
        Text(trip.route(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        Text("${fmtKm(trip.distanceKm)} km", style = MaterialTheme.typography.bodyLarge)
        PurposeChip(trip)
    }
}

@Composable
fun PurposeChip(trip: TripEntity) {
    val purpose = trip.purposeOrNull
    when {
        trip.review == ReviewState.NEEDS_REVIEW || purpose == null -> Chip("확인 필요", Biz.Warn)
        trip.review == ReviewState.SUGGESTED -> Chip("${purpose.label} ?", purpose.color())
        else -> Chip(purpose.label, purpose.color(), filled = true)
    }
}
