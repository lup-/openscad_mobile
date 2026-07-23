package com.example.openscad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openscad.model.RenderResult
import com.example.openscad.model.RenderSettings
import com.example.openscad.renderer.CameraState
import com.example.openscad.renderer.OpenScad3DCanvas
import java.util.Locale

@Composable
fun ViewerScreen(
    renderResult: RenderResult?,
    renderSettings: RenderSettings,
    isRendering: Boolean,
    onSettingsChange: ((RenderSettings) -> RenderSettings) -> Unit,
    onExportStlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraState = remember { CameraState() }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showStatsPanel by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        if (renderResult != null) {
            OpenScad3DCanvas(
                mesh = renderResult.mesh,
                settings = renderSettings,
                cameraState = cameraState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Render Progress Indicator
        if (isRendering) {
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF38BDF8),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Rendering 3D CSG...", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // Top Controls Bar: Camera Presets
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { cameraState.setIsometricView() },
                label = { Text("ISO", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xCC1E293B), labelColor = Color.White)
            )
            AssistChip(
                onClick = { cameraState.setTopView() },
                label = { Text("TOP", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xCC1E293B), labelColor = Color.White)
            )
            AssistChip(
                onClick = { cameraState.setFrontView() },
                label = { Text("FRONT", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xCC1E293B), labelColor = Color.White)
            )
            AssistChip(
                onClick = { cameraState.setRightView() },
                label = { Text("RIGHT", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xCC1E293B), labelColor = Color.White)
            )
            IconButton(
                onClick = { cameraState.reset() },
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xCC1E293B), CircleShape)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Camera", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Top Right Action Buttons: Toggle Settings & Stats
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { showStatsPanel = !showStatsPanel },
                modifier = Modifier
                    .size(36.dp)
                    .background(if (showStatsPanel) Color(0xFF0284C7) else Color(0xCC1E293B), CircleShape)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Model Stats", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = { showSettingsPanel = !showSettingsPanel },
                modifier = Modifier
                    .size(36.dp)
                    .background(if (showSettingsPanel) Color(0xFF0284C7) else Color(0xCC1E293B), CircleShape)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "View Settings", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Stats Floating Overlay (Top Left below camera buttons)
        if (showStatsPanel && renderResult != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xE60F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp, start = 12.dp)
                    .width(220.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("MODEL STATS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Triangles: ${renderResult.triangleCount}", fontSize = 12.sp, color = Color.White)
                    Text(
                        text = String.format(Locale.US, "Size: %.1f × %.1f × %.1f mm", renderResult.boundingBoxX, renderResult.boundingBoxY, renderResult.boundingBoxZ),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = String.format(Locale.US, "Volume: %.2f cm³", renderResult.estimatedVolumeMm3 / 1000f),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text("Render Time: ${renderResult.renderTimeMs} ms", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        }

        // Settings Floating Panel (Right side)
        if (showSettingsPanel) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp)
                    .width(260.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DISPLAY SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wireframe Overlay", fontSize = 12.sp, color = Color.White)
                        Switch(
                            checked = renderSettings.showWireframe,
                            onCheckedChange = { checked -> onSettingsChange { it.copy(showWireframe = checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ground Grid", fontSize = 12.sp, color = Color.White)
                        Switch(
                            checked = renderSettings.showGrid,
                            onCheckedChange = { checked -> onSettingsChange { it.copy(showGrid = checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3D Axes", fontSize = 12.sp, color = Color.White)
                        Switch(
                            checked = renderSettings.showAxes,
                            onCheckedChange = { checked -> onSettingsChange { it.copy(showAxes = checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Cross-Section Cut (X Axis)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Slider(
                        value = renderSettings.sliceX,
                        onValueChange = { v -> onSettingsChange { it.copy(sliceX = v) } },
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF0284C7))
                    )
                }
            }
        }

        // Export STL Floating Action Button
        ExtendedFloatingActionButton(
            onClick = onExportStlClick,
            icon = { Icon(Icons.Default.Download, contentDescription = "Export STL") },
            text = { Text("Export STL", fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF0284C7),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}
