package com.example.openscad.csg

import com.example.openscad.model.Color4
import com.example.openscad.model.CsgMesh
import com.example.openscad.model.Triangle3
import com.example.openscad.model.Vector3
import kotlin.math.abs

object CsgEngine {

    const val EPSILON = 1e-4f
    const val MAX_BSP_DEPTH = 30

    class Plane(
        val normal: Vector3,
        val w: Float
    ) {
        companion object {
            fun fromPoints(a: Vector3, b: Vector3, c: Vector3): Plane? {
                val n = (b - a).cross(c - a).normalized()
                if (n.length() < 0.1f || n.x.isNaN() || n.y.isNaN() || n.z.isNaN()) return null
                return Plane(n, n.dot(a))
            }
        }

        fun flipped(): Plane = Plane(normal * -1f, -w)

        fun splitPolygon(
            poly: Polygon,
            coplanarFront: MutableList<Polygon>,
            coplanarBack: MutableList<Polygon>,
            front: MutableList<Polygon>,
            back: MutableList<Polygon>
        ) {
            val COPLANAR = 0
            val FRONT = 1
            val BACK = 2
            val SPANNING = 3

            var polygonType = 0
            val types = ArrayList<Int>(poly.vertices.size)

            for (v in poly.vertices) {
                val t = normal.dot(v) - w
                val type = when {
                    t < -EPSILON -> BACK
                    t > EPSILON -> FRONT
                    else -> COPLANAR
                }
                polygonType = polygonType or type
                types.add(type)
            }

            when (polygonType) {
                COPLANAR -> {
                    if (normal.dot(poly.plane.normal) > 0) {
                        coplanarFront.add(poly)
                    } else {
                        coplanarBack.add(poly)
                    }
                }
                FRONT -> front.add(poly)
                BACK -> back.add(poly)
                SPANNING -> {
                    val f = ArrayList<Vector3>()
                    val b = ArrayList<Vector3>()

                    for (i in poly.vertices.indices) {
                        val j = (i + 1) % poly.vertices.size
                        val ti = types[i]
                        val tj = types[j]
                        val vi = poly.vertices[i]
                        val vj = poly.vertices[j]

                        if (ti != BACK) f.add(vi)
                        if (ti != FRONT) b.add(vi)

                        if ((ti or tj) == SPANNING) {
                            val denom = normal.dot(vj - vi)
                            val t = if (abs(denom) > 1e-6f) ((w - normal.dot(vi)) / denom).coerceIn(0f, 1f) else 0.5f
                            val v = vi + (vj - vi) * t
                            f.add(v)
                            b.add(v)
                        }
                    }

                    val cleanF = cleanVertices(f)
                    val cleanB = cleanVertices(b)

                    if (cleanF.size >= 3) front.add(Polygon(cleanF, poly.color, poly.plane))
                    if (cleanB.size >= 3) back.add(Polygon(cleanB, poly.color, poly.plane))
                }
            }
        }

        private fun cleanVertices(verts: List<Vector3>): List<Vector3> {
            if (verts.size < 3) return verts
            val cleaned = mutableListOf<Vector3>()
            for (v in verts) {
                if (cleaned.isEmpty() || (cleaned.last() - v).length() > 1e-4f) {
                    cleaned.add(v)
                }
            }
            if (cleaned.size > 1 && (cleaned.first() - cleaned.last()).length() <= 1e-4f) {
                cleaned.removeAt(cleaned.size - 1)
            }
            return cleaned
        }
    }

    class Polygon(
        val vertices: List<Vector3>,
        val color: Color4 = Color4.DEFAULT,
        val plane: Plane = Plane.fromPoints(
            vertices.getOrElse(0) { Vector3.ZERO },
            vertices.getOrElse(1) { Vector3.ZERO },
            vertices.getOrElse(2) { Vector3.ZERO }
        ) ?: Plane(Vector3.UP, 0f)
    ) {
        fun flipped(): Polygon {
            return Polygon(vertices.reversed(), color, plane.flipped())
        }

        fun toTriangles(): List<Triangle3> {
            val tris = mutableListOf<Triangle3>()
            if (vertices.size < 3) return tris
            val p0 = vertices[0]
            for (i in 1 until vertices.size - 1) {
                val p1 = vertices[i]
                val p2 = vertices[i + 1]
                // Skip degenerate collinear or zero-area triangles
                val cross = (p1 - p0).cross(p2 - p0)
                if (cross.length() > 1e-5f) {
                    tris.add(Triangle3(p0, p1, p2, color = color))
                }
            }
            return tris
        }
    }

    class BspNode(polygons: List<Polygon> = emptyList()) {
        var plane: Plane? = null
        var front: BspNode? = null
        var back: BspNode? = null
        val polygons = mutableListOf<Polygon>()

        init {
            if (polygons.isNotEmpty()) build(polygons)
        }

        fun clone(depth: Int = 0): BspNode {
            val node = BspNode()
            node.plane = plane
            node.polygons.addAll(polygons)
            if (depth < MAX_BSP_DEPTH) {
                node.front = front?.clone(depth + 1)
                node.back = back?.clone(depth + 1)
            }
            return node
        }

        fun invert(depth: Int = 0) {
            if (depth >= MAX_BSP_DEPTH) return
            for (i in polygons.indices) {
                polygons[i] = polygons[i].flipped()
            }
            plane = plane?.flipped()
            front?.invert(depth + 1)
            back?.invert(depth + 1)
            val temp = front
            front = back
            back = temp
        }

        fun clipPolygons(polys: List<Polygon>, depth: Int = 0): List<Polygon> {
            if (polys.isEmpty()) return emptyList()
            if (depth >= MAX_BSP_DEPTH) return polys

            val p = plane ?: return polys.toList()
            val f = mutableListOf<Polygon>()
            val b = mutableListOf<Polygon>()

            for (poly in polys) {
                p.splitPolygon(poly, f, b, f, b)
            }

            val frontResult = front?.clipPolygons(f, depth + 1) ?: f
            val backResult = back?.clipPolygons(b, depth + 1) ?: emptyList()

            return frontResult + backResult
        }

        fun clipTo(bsp: BspNode, depth: Int = 0) {
            if (depth >= MAX_BSP_DEPTH) return
            val newPolys = bsp.clipPolygons(polygons)
            polygons.clear()
            polygons.addAll(newPolys)
            front?.clipTo(bsp, depth + 1)
            back?.clipTo(bsp, depth + 1)
        }

        fun allPolygons(depth: Int = 0): List<Polygon> {
            val list = mutableListOf<Polygon>()
            list.addAll(polygons)
            if (depth < MAX_BSP_DEPTH) {
                front?.let { list.addAll(it.allPolygons(depth + 1)) }
                back?.let { list.addAll(it.allPolygons(depth + 1)) }
            }
            return list
        }

        fun build(polys: List<Polygon>, depth: Int = 0) {
            if (polys.isEmpty()) return
            if (depth >= MAX_BSP_DEPTH) {
                polygons.addAll(polys)
                return
            }

            if (plane == null) {
                var bestCandidate = polys[0]
                var bestScore = Int.MAX_VALUE

                val candidates = if (polys.size <= 8) polys else {
                    val step = polys.size / 6
                    listOf(
                        polys[0],
                        polys[step],
                        polys[2 * step],
                        polys[3 * step],
                        polys[4 * step],
                        polys[5 * step]
                    )
                }

                for (cand in candidates) {
                    val cp = cand.plane
                    if (abs(cp.normal.length() - 1.0f) > 0.1f) continue
                    var fCount = 0
                    var bCount = 0
                    for (p in polys) {
                        val v0 = p.vertices.getOrElse(0) { Vector3.ZERO }
                        val d = cp.normal.dot(v0) - cp.w
                        if (d > EPSILON) fCount++
                        else if (d < -EPSILON) bCount++
                    }
                    val score = abs(fCount - bCount)
                    if (score < bestScore) {
                        bestScore = score
                        bestCandidate = cand
                    }
                }
                plane = bestCandidate.plane
            }
            val p = plane!!
            val f = mutableListOf<Polygon>()
            val b = mutableListOf<Polygon>()

            for (poly in polys) {
                p.splitPolygon(poly, polygons, polygons, f, b)
            }

            if (f.isNotEmpty() && f.size < polys.size) {
                if (front == null) front = BspNode()
                front!!.build(f, depth + 1)
            } else if (f.isNotEmpty()) {
                polygons.addAll(f)
            }

            if (b.isNotEmpty() && b.size < polys.size) {
                if (back == null) back = BspNode()
                back!!.build(b, depth + 1)
            } else if (b.isNotEmpty()) {
                polygons.addAll(b)
            }
        }
    }

    private fun meshToPolygons(mesh: CsgMesh): List<Polygon> {
        return mesh.triangles.map { t ->
            Polygon(listOf(t.v1, t.v2, t.v3), t.color)
        }
    }

    private fun polygonsToMesh(polygons: List<Polygon>): CsgMesh {
        val tris = mutableListOf<Triangle3>()
        for (p in polygons) {
            tris.addAll(p.toTriangles())
        }
        return CsgMesh(tris)
    }

    fun union(a: CsgMesh, b: CsgMesh): CsgMesh {
        if (a.isEmpty) return b
        if (b.isEmpty) return a

        try {
            val aNode = BspNode(meshToPolygons(a))
            val bNode = BspNode(meshToPolygons(b))

            aNode.clipTo(bNode)
            bNode.clipTo(aNode)
            bNode.invert()
            bNode.clipTo(aNode)
            bNode.invert()
            aNode.build(bNode.allPolygons())

            return polygonsToMesh(aNode.allPolygons())
        } catch (t: Throwable) {
            // Fallback concat on CSG error or stack overflow
            return CsgMesh(a.triangles + b.triangles)
        }
    }

    fun subtract(a: CsgMesh, b: CsgMesh): CsgMesh {
        if (a.isEmpty) return CsgMesh.EMPTY
        if (b.isEmpty) return a

        try {
            val aNode = BspNode(meshToPolygons(a))
            val bNode = BspNode(meshToPolygons(b))

            aNode.invert()
            aNode.clipTo(bNode)
            bNode.clipTo(aNode)
            bNode.invert()
            bNode.clipTo(aNode)
            bNode.invert()
            aNode.build(bNode.allPolygons())
            aNode.invert()

            return polygonsToMesh(aNode.allPolygons())
        } catch (t: Throwable) {
            // Return base mesh if subtract fails
            return a
        }
    }

    fun intersect(a: CsgMesh, b: CsgMesh): CsgMesh {
        if (a.isEmpty || b.isEmpty) return CsgMesh.EMPTY

        try {
            val aNode = BspNode(meshToPolygons(a))
            val bNode = BspNode(meshToPolygons(b))

            aNode.invert()
            bNode.clipTo(aNode)
            bNode.invert()
            aNode.clipTo(bNode)
            bNode.clipTo(aNode)
            aNode.build(bNode.allPolygons())
            aNode.invert()

            return polygonsToMesh(aNode.allPolygons())
        } catch (t: Throwable) {
            return a
        }
    }

    fun unionAll(meshes: List<CsgMesh>): CsgMesh {
        if (meshes.isEmpty()) return CsgMesh.EMPTY
        var result = meshes[0]
        for (i in 1 until meshes.size) {
            result = union(result, meshes[i])
        }
        return result
    }

    fun subtractAll(base: CsgMesh, subtracts: List<CsgMesh>): CsgMesh {
        var result = base
        for (sub in subtracts) {
            result = subtract(result, sub)
        }
        return result
    }

    fun intersectAll(meshes: List<CsgMesh>): CsgMesh {
        if (meshes.isEmpty()) return CsgMesh.EMPTY
        var result = meshes[0]
        for (i in 1 until meshes.size) {
            result = intersect(result, meshes[i])
        }
        return result
    }
}
