package com.example.openscad.model

import kotlin.math.abs
import kotlin.math.sqrt

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vector3(x * s, y * s, z * s)
    operator fun div(s: Float) = if (s != 0f) Vector3(x / s, y / s, z / s) else this

    fun dot(v: Vector3): Float = x * v.x + y * v.y + z * v.z
    fun cross(v: Vector3): Vector3 = Vector3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.00001f) this / len else Vector3(0f, 0f, 1f)
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val UP = Vector3(0f, 0f, 1f)
    }
}

data class Color4(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
) {
    companion object {
        val DEFAULT = Color4(0.23f, 0.51f, 0.96f, 1.0f) // #3B82F6
        val RED = Color4(0.9f, 0.2f, 0.2f)
        val GREEN = Color4(0.2f, 0.8f, 0.2f)
        val BLUE = Color4(0.2f, 0.5f, 0.9f)
        val YELLOW = Color4(0.95f, 0.75f, 0.1f)
        val ORANGE = Color4(0.95f, 0.5f, 0.1f)
        val GRAY = Color4(0.6f, 0.6f, 0.65f)
    }
}

data class Triangle3(
    val v1: Vector3,
    val v2: Vector3,
    val v3: Vector3,
    val normal: Vector3 = calculateNormal(v1, v2, v3),
    val color: Color4 = Color4.DEFAULT
) {
    companion object {
        fun calculateNormal(p1: Vector3, p2: Vector3, p3: Vector3): Vector3 {
            val edge1 = p2 - p1
            val edge2 = p3 - p1
            return edge1.cross(edge2).normalized()
        }
    }
}

class CsgMesh(
    val triangles: List<Triangle3> = emptyList()
) {
    val isEmpty: Boolean get() = triangles.isEmpty()
    val triangleCount: Int get() = triangles.size

    fun computeBoundingBox(): Pair<Vector3, Vector3> {
        if (triangles.isEmpty()) return Pair(Vector3.ZERO, Vector3.ZERO)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        for (t in triangles) {
            for (v in listOf(t.v1, t.v2, t.v3)) {
                if (v.x < minX) minX = v.x
                if (v.y < minY) minY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.x > maxX) maxX = v.x
                if (v.y > maxY) maxY = v.y
                if (v.z > maxZ) maxZ = v.z
            }
        }
        return Pair(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
    }

    fun computeVolumeMm3(): Float {
        // Divergence theorem method for closed 3D mesh volume
        var vol = 0f
        for (t in triangles) {
            val v1 = t.v1
            val v2 = t.v2
            val v3 = t.v3
            vol += v1.dot(v2.cross(v3)) / 6f
        }
        return abs(vol)
    }

    fun transform(matrixTransform: (Vector3) -> Vector3, colorOverride: Color4? = null): CsgMesh {
        val newTriangles = triangles.map { t ->
            val nv1 = matrixTransform(t.v1)
            val nv2 = matrixTransform(t.v2)
            val nv3 = matrixTransform(t.v3)
            val nColor = colorOverride ?: t.color
            Triangle3(nv1, nv2, nv3, Triangle3.calculateNormal(nv1, nv2, nv3), nColor)
        }
        return CsgMesh(newTriangles)
    }

    companion object {
        val EMPTY = CsgMesh(emptyList())
    }
}
