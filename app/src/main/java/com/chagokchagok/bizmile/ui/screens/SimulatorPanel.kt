package com.chagokchagok.bizmile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chagokchagok.bizmile.ui.BigButton
import com.chagokchagok.bizmile.ui.Chip
import com.chagokchagok.bizmile.ui.Panel
import com.chagokchagok.bizmile.ui.theme.Biz
import com.chagokchagok.bizmile.vehicle.Gear
import com.chagokchagok.bizmile.vehicle.SimRoute
import com.chagokchagok.bizmile.vehicle.SimulatedVehicleDataSource
import com.chagokchagok.bizmile.vehicle.VehicleSnapshot
import com.chagokchagok.bizmile.vehicle.Warning

/**
 * Development-only controls, shown only when the app runs on simulated data.
 * On a Pleos Connect device the same changes are made from the Pleos Virtual Console instead.
 */
@Composable
fun SimulatorPanel(
    sim: SimulatedVehicleDataSource,
    snapshot: VehicleSnapshot,
    scriptRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val h = 54.dp
    Panel(modifier.fillMaxWidth(), padding = 16.dp, color = Biz.Bg) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("개발용 시뮬레이터", Biz.Warn)
                Text(
                    "차량 대신 값을 넣는 패널입니다. 실제 차량·Pleos 에뮬레이터에서는 표시되지 않습니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Biz.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("운행 재생")
                SimRoute.LEGS.forEach { leg ->
                    BigButton(leg.label, { sim.drive(leg) }, Modifier.weight(1f), enabled = !scriptRunning, minHeight = h)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("기어")
                listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE).forEach { g ->
                    BigButton(
                        g.label, { sim.setGear(g) }, Modifier.width(72.dp),
                        selected = snapshot.gear == g, enabled = !scriptRunning, minHeight = h,
                    )
                }
                BigButton("+1 km", { sim.addDistance(1f) }, Modifier.weight(1f), enabled = !scriptRunning && snapshot.gear.isMoving, minHeight = h)
                BigButton("+5 km", { sim.addDistance(5f) }, Modifier.weight(1f), enabled = !scriptRunning && snapshot.gear.isMoving, minHeight = h)
                BigButton("15분 경과", { sim.waitMinutes(15) }, Modifier.weight(1f), enabled = !scriptRunning && !snapshot.gear.isMoving, minHeight = h)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("반납 상태")
                BigButton("배터리 18%", { sim.setSoc(18) }, Modifier.weight(1f), selected = snapshot.socPercent == 18, minHeight = h)
                BigButton("배터리 80%", { sim.setSoc(80) }, Modifier.weight(1f), selected = snapshot.socPercent == 80, minHeight = h)
                BigButton(
                    "타이어 경고", { sim.toggleWarning(Warning.TIRE_PRESSURE) }, Modifier.weight(1f),
                    selected = snapshot.warnings.orEmpty().contains(Warning.TIRE_PRESSURE), minHeight = h,
                )
                BigButton(
                    "운전석 창문 열림", { sim.toggleWindow("운전석") }, Modifier.weight(1f),
                    selected = snapshot.openWindows.orEmpty().contains("운전석"), minHeight = h,
                )
                BigButton(
                    "도어 잠금", { sim.setDoorsLocked(snapshot.doorsLocked != true) }, Modifier.weight(1f),
                    selected = snapshot.doorsLocked == true, minHeight = h,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Biz.Muted, modifier = Modifier.width(84.dp))
}
