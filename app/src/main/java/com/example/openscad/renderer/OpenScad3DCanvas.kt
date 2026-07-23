package com.example.openscad.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.openscad.model.CsgMesh
import com.example.openscad.model.RenderSettings
import com.example.openscad.model.Triangle3
import com.example.openscad.model.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CameraState {
    var rotX by mutableFloatStateOf(25f) // Elevation
    var rotZ by mutableFloatStateOf(45f) // Azimuth
    var distance by mutableFloatStateOf(80f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    fun reset() {
        rotX = 25f
        rotZ = 45f
        distance = 80f
        panX = 0f
        panY = 0f
    }

    fun setTopView() {
        rotX = 90f
        rotZ = 0f
    }

    fun setFrontView() {
        rotX = 0f
        rotZ = 0f
    }

    fun setRightView() {
        rotX = 0f
        rotZ = 90f
    }

    fun setIsometricView() {
        rotX = 35.26f
        rotZ = 45f
    }
}

@Composable
fun OpenScad3DCanvas(
    mesh: CsgMesh,
    settings: RenderSettings,
    cameraState: CameraState = remember { CameraState() },
    modifier: Modifier = Modifier
) {
    val lightDir = remember { Vector3(0.5f, 0.7f, 1.0f).normalized() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF18181B)) // Slate 900
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    cameraState.distance = (cameraState.distance / zoom).coerceIn(10f, 500f)
                    cameraState.panX += pan.x * 0.1f
                    cameraState.panY += pan.y * 0.1f
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    cameraState.rotZ = (cameraState.rotZ + dragAmount.x * 0.5f) % 360f
                    cameraState.rotX = (cameraState.rotX - dragAmount.y * 0.5f).coerceIn(-89f, 89f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f + cameraState.panX
            val centerY = height / 2f + cameraState.panY

            val rx = (cameraState.rotX * PI / 180.0).toFloat()
            val rz = (cameraState.rotZ * PI / 180.0).toFloat()

            val cosX = cos(rx)
            val sinX = sin(rx)
            val cosZ = cos(rz)
            val sinZ = sin(rz)

            // Projection function
            fun project(v: Vector3): Pair<Offset, Float> {
                // World -> Camera rotation (Z then X)
                val x1 = v.x * cosZ - v.y * sinZ
                val y1 = v.x * sinZ + v.y * cosZ
                val z1 = v.z

                val x2 = x1
                val y2 = y1 * cosX - z1 * sinX
                val z2 = y1 * sinX + z1 * cosX

                val scale = (width / (2f * cameraState.distance)) * (if (settings.isPerspective) (200f / (z2 + 200f)).coerceAtLeast(0.1f) else 1f)

                val screenX = centerX + x2 * scale
                val screenY = centerY - z2 * scale // Y-up in 3D, Y-down in 2D

                return Pair(Offset(screenX, screenY), y2) // Return depth y2 for Z-buffer sorting
            }

            // Draw Ground Grid
            if (settings.showGrid) {
                drawGrid(::project, gridRadius = 40f, step = 10f)
            }

            // Draw Coordinate Axes
            if (settings.showAxes) {
                drawAxes(::project, axisLen = 25f)
            }

            // Render Mesh Triangles
            if (!mesh.isEmpty) {
                val (minB, maxB) = mesh.computeBoundingBox()
                val boundSizeX = maxB.x - minB.x
                val boundSizeY = maxB.y - minB.y
                val boundSizeZ = maxB.z - minB.z

                val maxXCut = minB.x + boundSizeX * settings.sliceX
                val maxYCut = minB.y + boundSizeY * settings.sliceY
                val maxZCut = minB.z + boundSizeZ * settings.sliceZ

                // Filter by slicing plane
                val visibleTriangles = mesh.triangles.filter { t ->
                    t.v1.x <= maxXCut && t.v2.x <= maxXCut && t.v3.x <= maxXCut &&
                    t.v1.y <= maxYCut && t.v2.y <= maxYCut && t.v3.y <= maxYCut &&
                    t.v1.z <= maxZCut && t.v2.z <= maxZCut && t.v3.z <= maxZCut
                }

                // Project and Painter's algorithm depth sort (far to near)
                val projectedTris = visibleTriangles.map { t ->
                    val (p1, d1) = project(t.v1)
                    val (p2, d2) = project(t.v2)
                    val (p3, d3) = project(t.v3)
                    val avgDepth = (d1 + d2 + d3) / 3f
                    Triple(t, listOf(p1, p2, p3), avgDepth)
                }.sortedBy { it.third } // sort by depth

                val baseColorHex = settings.materialColorHex

                for ((t, pts, _) in projectedTris) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        lineTo(pts[1].x, pts[1].y)
                        lineTo(pts[2].x, pts[2].y)
                        close()
                    }

                    // Phong / Diffuse Directional Lighting
                    val dotLight = max(0.15f, t.normal.dot(lightDir))
                    val r = (t.color.r * dotLight).coerceIn(0f, 1f)
                    val g = (t.color.g * dotLight).coerceIn(0f, 1f)
                    val b = (t.color.b * dotLight).coerceIn(0f, 1f)

                    val faceColor = Color(r, g, b, t.color.a)

                    // Fill face
                    drawPath(path, color = faceColor)

                    // Wireframe outline
                    if (settings.showWireframe) {
                        drawPath(path, color = Color(0x80FFFFFF), style = Stroke(width = 1f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(
    project: (Vector3) -> Pair<Offset, Float>,
    gridRadius: Float,
    step: Float
) {
    var x = -gridRadius
    while (x <= gridRadius) {
        val (p1, _) = project(Vector3(x, -gridRadius, 0f))
        val (p2, _) = project(Vector3(x, gridRadius, 0f))
        val color = if (x == 0f) Color(0x6000E5FF) else Color(0x20FFFFFF)
        drawLine(color, p1, p2, strokeWidth = if (x == 0f) 2f else 1f)
        x += step
    }

    var y = -gridRadius
    while (y <= gridRadius) {
        val (p1, _) = project(Vector3(-gridRadius, y, 0f))
        val (p2, _) = project(Vector3(gridRadius, y, 0f))
        val color = if (y == 0f) Color(0x6000E5FF) else Color(0x20FFFFFF)
        drawLine(color, p1, p2, strokeWidth = if (y == 0f) 2f else 1f)
        y += step
    }
}

private fun DrawScope.drawAxes(
    project: (Vector3) -> Pair<Offset, Float>,
    axisLen: Float
) {
    val (o, _) = project(Vector3.ZERO)

    // X Axis - Red
    val (x, _) = project(Vector3(axisLen, 0f, 0f))
    drawLine(Color(0xFFEF4444), o, x, strokeWidth = 3f)

    // Y Axis - Green
    val (y, _) = project(Vector3(0f, axisLen, 0f))
    drawLine(Color(0xFF22C55E), o, y, strokeWidth = 3f)

    // Z Axis - Blue
    val (z, _) = project(Vector3(0f, 0f, axisLen))
    drawLine(Color(0xFF3B82F6), o, z, strokeWidth = 3f)
}
