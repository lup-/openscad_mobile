package com.example.openscad.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scad_projects")
data class ScadProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val code: String,
    val description: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isPreset: Boolean = false,
    val category: String = "General"
)

data class RenderSettings(
    val showWireframe: Boolean = false,
    val showGrid: Boolean = true,
    val showAxes: Boolean = true,
    val showNormals: Boolean = false,
    val isPerspective: Boolean = true,
    val fnDefault: Int = 32,
    val materialColorHex: String = "#3B82F6",
    val sliceX: Float = 1.0f, // 0.0 to 1.0 clipping plane
    val sliceY: Float = 1.0f,
    val sliceZ: Float = 1.0f
)

data class RenderResult(
    val mesh: CsgMesh,
    val triangleCount: Int,
    val vertexCount: Int,
    val boundingBoxX: Float,
    val boundingBoxY: Float,
    val boundingBoxZ: Float,
    val estimatedVolumeMm3: Float,
    val renderTimeMs: Long,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
