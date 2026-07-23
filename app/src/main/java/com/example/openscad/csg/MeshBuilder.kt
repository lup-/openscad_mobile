package com.example.openscad.csg

import com.example.openscad.model.Color4
import com.example.openscad.model.CsgMesh
import com.example.openscad.model.Triangle3
import com.example.openscad.model.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MeshBuilder {

    fun createCube(
        size: Vector3 = Vector3(10f, 10f, 10f),
        center: Boolean = false,
        color: Color4 = Color4.DEFAULT
    ): CsgMesh {
        val w = size.x
        val h = size.y
        val d = size.z

        val x0 = if (center) -w / 2f else 0f
        val x1 = x0 + w
        val y0 = if (center) -h / 2f else 0f
        val y1 = y0 + h
        val z0 = if (center) -d / 2f else 0f
        val z1 = z0 + d

        // 8 vertices
        val v000 = Vector3(x0, y0, z0)
        val v100 = Vector3(x1, y0, z0)
        val v110 = Vector3(x1, y1, z0)
        val v010 = Vector3(x0, y1, z0)

        val v001 = Vector3(x0, y0, z1)
        val v101 = Vector3(x1, y0, z1)
        val v111 = Vector3(x1, y1, z1)
        val v011 = Vector3(x0, y1, z1)

        val tris = mutableListOf<Triangle3>()

        // Helper quad (2 triangles CCW)
        fun addQuad(a: Vector3, b: Vector3, c: Vector3, d: Vector3) {
            tris.add(Triangle3(a, b, c, color = color))
            tris.add(Triangle3(a, c, d, color = color))
        }

        // Bottom (Z = z0) - facing down
        addQuad(v000, v010, v110, v100)
        // Top (Z = z1) - facing up
        addQuad(v001, v101, v111, v011)
        // Front (Y = y0)
        addQuad(v000, v100, v101, v001)
        // Back (Y = y1)
        addQuad(v110, v010, v011, v111)
        // Left (X = x0)
        addQuad(v010, v000, v001, v011)
        // Right (X = x1)
        addQuad(v100, v110, v111, v101)

        return CsgMesh(tris)
    }

    fun createSphere(
        radius: Float = 10f,
        fn: Int = 32,
        color: Color4 = Color4.DEFAULT
    ): CsgMesh {
        val segments = fn.coerceAtLeast(8)
        val rings = (segments / 2).coerceAtLeast(4)

        val tris = mutableListOf<Triangle3>()

        for (r in 0 until rings) {
            val phi1 = (r.toDouble() / rings) * PI
            val phi2 = ((r + 1).toDouble() / rings) * PI

            val z1 = radius * cos(phi1).toFloat()
            val r1 = radius * sin(phi1).toFloat()
            val z2 = radius * cos(phi2).toFloat()
            val r2 = radius * sin(phi2).toFloat()

            for (s in 0 until segments) {
                val theta1 = (s.toDouble() / segments) * 2.0 * PI
                val theta2 = ((s + 1).toDouble() / segments) * 2.0 * PI

                val x11 = r1 * cos(theta1).toFloat()
                val y11 = r1 * sin(theta1).toFloat()

                val x12 = r1 * cos(theta2).toFloat()
                val y12 = r1 * sin(theta2).toFloat()

                val x21 = r2 * cos(theta1).toFloat()
                val y21 = r2 * sin(theta1).toFloat()

                val x22 = r2 * cos(theta2).toFloat()
                val y22 = r2 * sin(theta2).toFloat()

                val p11 = Vector3(x11, y11, z1)
                val p12 = Vector3(x12, y12, z1)
                val p21 = Vector3(x21, y21, z2)
                val p22 = Vector3(x22, y22, z2)

                if (r != 0) {
                    tris.add(Triangle3(p11, p12, p22, color = color))
                }
                if (r != rings - 1) {
                    tris.add(Triangle3(p11, p22, p21, color = color))
                }
            }
        }
        return CsgMesh(tris)
    }

    fun createCylinder(
        height: Float = 10f,
        r1: Float = 5f,
        r2: Float = 5f,
        center: Boolean = false,
        fn: Int = 32,
        color: Color4 = Color4.DEFAULT
    ): CsgMesh {
        val slices = fn.coerceAtLeast(6)
        val z0 = if (center) -height / 2f else 0f
        val z1 = z0 + height

        val tris = mutableListOf<Triangle3>()
        val bottomCenter = Vector3(0f, 0f, z0)
        val topCenter = Vector3(0f, 0f, z1)

        for (i in 0 until slices) {
            val a1 = (i.toDouble() / slices) * 2.0 * PI
            val a2 = ((i + 1).toDouble() / slices) * 2.0 * PI

            val cos1 = cos(a1).toFloat()
            val sin1 = sin(a1).toFloat()
            val cos2 = cos(a2).toFloat()
            val sin2 = sin(a2).toFloat()

            val b1 = Vector3(r1 * cos1, r1 * sin1, z0)
            val b2 = Vector3(r1 * cos2, r1 * sin2, z0)
            val t1 = Vector3(r2 * cos1, r2 * sin1, z1)
            val t2 = Vector3(r2 * cos2, r2 * sin2, z1)

            // Bottom cap (facing down)
            if (r1 > 0f) {
                tris.add(Triangle3(bottomCenter, b2, b1, color = color))
            }

            // Top cap (facing up)
            if (r2 > 0f) {
                tris.add(Triangle3(topCenter, t1, t2, color = color))
            }

            // Side quads
            if (r1 > 0f || r2 > 0f) {
                tris.add(Triangle3(b1, b2, t2, color = color))
                tris.add(Triangle3(b1, t2, t1, color = color))
            }
        }

        return CsgMesh(tris)
    }

    fun createPolyhedron(
        points: List<Vector3>,
        faces: List<List<Int>>,
        color: Color4 = Color4.DEFAULT
    ): CsgMesh {
        val tris = mutableListOf<Triangle3>()
        for (face in faces) {
            if (face.size < 3) continue
            // Triangulate face using fan
            val p0 = points.getOrNull(face[0]) ?: continue
            for (i in 1 until face.size - 1) {
                val p1 = points.getOrNull(face[i]) ?: continue
                val p2 = points.getOrNull(face[i + 1]) ?: continue
                tris.add(Triangle3(p0, p1, p2, color = color))
            }
        }
        return CsgMesh(tris)
    }

    fun linearExtrude(
        polygonPoints: List<Vector3>,
        height: Float = 10f,
        center: Boolean = false,
        twistDegree: Float = 0f,
        scaleFactor: Float = 1.0f,
        color: Color4 = Color4.DEFAULT
    ): CsgMesh {
        if (polygonPoints.size < 3) return CsgMesh.EMPTY
        val tris = mutableListOf<Triangle3>()

        val z0 = if (center) -height / 2f else 0f
        val z1 = z0 + height

        val n = polygonPoints.size
        // Triangulate bottom and top caps
        val bottomPts = polygonPoints.map { Vector3(it.x, it.y, z0) }
        
        val twistRad = (twistDegree * PI / 180.0).toFloat()
        val topPts = polygonPoints.map { p ->
            val rx = p.x * scaleFactor
            val ry = p.y * scaleFactor
            val cosT = cos(twistRad)
            val sinT = sin(twistRad)
            Vector3(
                rx * cosT - ry * sinT,
                rx * sinT + ry * cosT,
                z1
            )
        }

        // Bottom cap (CCW looking up -> reverse for CCW looking down)
        for (i in 1 until n - 1) {
            tris.add(Triangle3(bottomPts[0], bottomPts[i + 1], bottomPts[i], color = color))
        }

        // Top cap
        for (i in 1 until n - 1) {
            tris.add(Triangle3(topPts[0], topPts[i], topPts[i + 1], color = color))
        }

        // Sides
        for (i in 0 until n) {
            val next = (i + 1) % n
            val b1 = bottomPts[i]
            val b2 = bottomPts[next]
            val t1 = topPts[i]
            val t2 = topPts[next]

            tris.add(Triangle3(b1, b2, t2, color = color))
            tris.add(Triangle3(b1, t2, t1, color = color))
        }

        return CsgMesh(tris)
    }
}
