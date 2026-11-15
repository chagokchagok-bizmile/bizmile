package com.chagokchagok.bizmile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chagokchagok.bizmile.data.awaitsDriver
import com.chagokchagok.bizmile.ui.screens.DriveScreen
import com.chagokchagok.bizmile.ui.screens.DrivingScreen
import com.chagokchagok.bizmile.ui.screens.HandoverScreen
import com.chagokchagok.bizmile.ui.screens.LogScreen
import com.chagokchagok.bizmile.ui.screens.ShareScreen
import com.chagokchagok.bizmile.ui.screens.SimulatorPanel
import com.chagokchagok.bizmile.ui.theme.Biz
import com.chagokchagok.bizmile.vehicle.SignalSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private enum class Tab(val label: String, val icon: ImageVector) {
    DRIVE("운행", Icons.Filled.PlayArrow),
    LOG("운행일지", Icons.Filled.DateRange),
    HANDOVER("인수인계", Icons.Filled.CheckCircle),
    SHARE("공유·설정", Icons.Filled.Share),
}

@Composable
fun BizmileRoot(vm: BizmileViewModel) {
    val source by vm.source.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val liveTrip by vm.liveTrip.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val handovers by vm.handovers.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val inspecting by vm.inspecting.collectAsStateWithLifecycle()
    val scriptRunning by vm.simulator.scriptRunning.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.DRIVE) }
    val driving = snapshot.gear.isMoving
    val month = YearMonth.from(Instant.ofEpochMilli(snapshot.timestampMillis).atZone(ZoneId.systemDefault()))
    val monthTrips = vm.tripsOf(month, trips)
    val pendingCount = trips.count { it.awaitsDriver }

    Row(
        Modifier.fillMaxSize().background(Biz.Bg).safeDrawingPadding().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Navigation is disabled while the car is moving.
        Column(
            Modifier.width(132.dp).fillMaxHeight().alpha(if (driving) 0.35f else 1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("BIZMILE", style = MaterialTheme.typography.titleMedium, color = Biz.Primary, modifier = Modifier.padding(8.dp))
            Tab.entries.forEach { t ->
                NavItem(
                    tab = t,
                    selected = tab == t && !driving,
                    enabled = !driving,
                    badge = if (t == Tab.LOG && pendingCount > 0) pendingCount.toString() else null,
                    onClick = { tab = t },
                )
            }
            Spacer(Modifier.weight(1f))
            Chip(
                if (source.kind == SignalSource.PLEOS) "Pleos SDK" else "시뮬레이션",
                if (source.kind == SignalSource.PLEOS) Biz.Ok else Biz.Warn,
            )
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (driving) {
                    DrivingScreen(snapshot, liveTrip, places)
                } else {
                    when (tab) {
                        Tab.DRIVE -> DriveScreen(snapshot, trips, places, vm::confirm)
                        Tab.LOG -> LogScreen(month, monthTrips, export, vm::confirm, onExport = { vm.exportMonth(month) })
                        Tab.HANDOVER -> HandoverScreen(handovers, inspecting, vm::runHandoverInspection)
                        Tab.SHARE -> ShareScreen(
                            snapshot = snapshot,
                            phase = phase,
                            places = places,
                            sourceKind = source.kind,
                            status = status,
                            diagnostics = diagnostics,
                            pleosAvailable = vm.pleosAvailable,
                            onUseSource = vm::useSource,
                            onAddPlaceHere = vm::addPlaceHere,
                            onDeletePlace = vm::deletePlace,
                            onClearSimulated = vm::clearSimulatedTrips,
                        )
                    }
                }
            }
            if (source.kind == SignalSource.SIMULATED && (tab == Tab.DRIVE || tab == Tab.HANDOVER || driving)) {
                SimulatorPanel(vm.simulator, snapshot, scriptRunning)
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, enabled: Boolean, badge: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(if (selected) Biz.SurfaceHigh else Biz.Bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, contentDescription = null, tint = if (selected) Biz.Primary else Biz.Muted, modifier = Modifier.size(32.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tab.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Biz.Text else Biz.Muted)
            if (badge != null) Chip(badge, Biz.Warn, filled = true)
        }
    }
}
