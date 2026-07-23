package com.example.openscad.parser

import com.example.openscad.csg.CsgEngine
import com.example.openscad.csg.MeshBuilder
import com.example.openscad.model.Color4
import com.example.openscad.model.CsgMesh
import com.example.openscad.model.RenderResult
import com.example.openscad.model.Vector3
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

class ScadScope(
    val parent: ScadScope? = null
) {
    private val vars = mutableMapOf<String, Any>()
    val modules = mutableMapOf<String, AstNode.ModuleDef>()

    fun getVar(name: String): Any? {
        if (vars.containsKey(name)) return vars[name]
        return parent?.getVar(name)
    }

    fun setVar(name: String, value: Any) {
        vars[name] = value
    }

    fun getModule(name: String): AstNode.ModuleDef? {
        return modules[name.lowercase(Locale.ROOT)] ?: parent?.getModule(name)
    }
}

class ScadEvaluator {

    fun evaluateProgram(ast: List<AstNode>, defaultFn: Int = 32): RenderResult {
        val scope = ScadScope()
        scope.setVar("\$fn", defaultFn.toFloat())
        scope.setVar("\$fa", 12f)
        scope.setVar("\$fs", 2f)

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        var finalMesh = CsgMesh.EMPTY

        val timeMs = measureTimeMillis {
            // First pass: collect module definitions and global variable assignments
            for (node in ast) {
                when (node) {
                    is AstNode.ModuleDef -> scope.modules[node.name.lowercase(Locale.ROOT)] = node
                    is AstNode.VarAssign -> {
                        val value = evalExpr(node.expr, scope)
                        scope.setVar(node.name, value)
                    }
                    else -> {}
                }
            }

            val childMeshes = mutableListOf<CsgMesh>()
            for (node in ast) {
                if (node !is AstNode.ModuleDef && node !is AstNode.VarAssign) {
                    val m = evalNode(node, scope, warnings, errors)
                    if (m != null && !m.isEmpty) {
                        childMeshes.add(m)
                    }
                }
            }

            finalMesh = if (childMeshes.isNotEmpty()) {
                CsgEngine.unionAll(childMeshes)
            } else {
                CsgMesh.EMPTY
            }
        }

        val (minBounds, maxBounds) = finalMesh.computeBoundingBox()
        val boundingX = max(0f, maxBounds.x - minBounds.x)
        val boundingY = max(0f, maxBounds.y - minBounds.y)
        val boundingZ = max(0f, maxBounds.z - minBounds.z)

        return RenderResult(
            mesh = finalMesh,
            triangleCount = finalMesh.triangleCount,
            vertexCount = finalMesh.triangleCount * 3,
            boundingBoxX = boundingX,
            boundingBoxY = boundingY,
            boundingBoxZ = boundingZ,
            estimatedVolumeMm3 = finalMesh.computeVolumeMm3(),
            renderTimeMs = timeMs,
            errors = errors,
            warnings = warnings
        )
    }

    private fun evalNode(
        node: AstNode,
        scope: ScadScope,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): CsgMesh? {
        return when (node) {
            is AstNode.VarAssign -> {
                val value = evalExpr(node.expr, scope)
                scope.setVar(node.name, value)
                null
            }
            is AstNode.Block -> {
                val childMeshes = mutableListOf<CsgMesh>()
                val innerScope = ScadScope(scope)
                for (child in node.nodes) {
                    val m = evalNode(child, innerScope, warnings, errors)
                    if (m != null && !m.isEmpty) childMeshes.add(m)
                }
                CsgEngine.unionAll(childMeshes)
            }
            is AstNode.ModuleCall -> {
                evalModuleCall(node, scope, warnings, errors)
            }
            is AstNode.ForLoop -> {
                val rangeVal = evalExpr(node.rangeExpr, scope)
                val items = when (rangeVal) {
                    is List<*> -> rangeVal.mapNotNull { (it as? Number)?.toFloat() }
                    else -> emptyList()
                }

                val childMeshes = mutableListOf<CsgMesh>()
                for (item in items) {
                    val loopScope = ScadScope(scope)
                    loopScope.setVar(node.varName, item)
                    for (child in node.children) {
                        val m = evalNode(child, loopScope, warnings, errors)
                        if (m != null && !m.isEmpty) childMeshes.add(m)
                    }
                }
                CsgEngine.unionAll(childMeshes)
            }
            is AstNode.IfStmt -> {
                val condVal = evalExpr(node.condition, scope)
                val condBool = when (condVal) {
                    is Boolean -> condVal
                    is Number -> condVal.toFloat() != 0f
                    else -> false
                }

                val branch = if (condBool) node.thenBranch else node.elseBranch
                val branchScope = ScadScope(scope)
                val childMeshes = mutableListOf<CsgMesh>()
                for (child in branch) {
                    val m = evalNode(child, branchScope, warnings, errors)
                    if (m != null && !m.isEmpty) childMeshes.add(m)
                }
                CsgEngine.unionAll(childMeshes)
            }
            else -> null
        }
    }

    private fun evalModuleCall(
        node: AstNode.ModuleCall,
        scope: ScadScope,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): CsgMesh? {
        val name = node.name.lowercase(Locale.ROOT)

        // Helper to get evaluated arg map
        fun getArgsMap(): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            node.args.forEachIndexed { idx, arg ->
                val valObj = evalExpr(arg.expr, scope)
                if (arg.name != null) {
                    map[arg.name.lowercase(Locale.ROOT)] = valObj
                } else {
                    map["$idx"] = valObj
                }
            }
            return map
        }

        val argsMap = getArgsMap()
        val fnVal = (scope.getVar("\$fn") as? Number)?.toInt() ?: 32

        when (name) {
            "cube" -> {
                val sizeVal = argsMap["size"] ?: argsMap["0"] ?: 10f
                val centerVal = (argsMap["center"] as? Boolean) ?: false

                val sizeVec = when (sizeVal) {
                    is Number -> Vector3(sizeVal.toFloat(), sizeVal.toFloat(), sizeVal.toFloat())
                    is List<*> -> {
                        val list = sizeVal.mapNotNull { (it as? Number)?.toFloat() }
                        Vector3(list.getOrElse(0) { 10f }, list.getOrElse(1) { 10f }, list.getOrElse(2) { 10f })
                    }
                    else -> Vector3(10f, 10f, 10f)
                }
                return MeshBuilder.createCube(sizeVec, centerVal)
            }
            "sphere" -> {
                val rVal = argsMap["r"] ?: (argsMap["d"] as? Number)?.let { it.toFloat() / 2f } ?: argsMap["0"] ?: 10f
                val rFloat = (rVal as? Number)?.toFloat() ?: 10f
                val localFn = (argsMap["\$fn"] as? Number)?.toInt() ?: fnVal
                return MeshBuilder.createSphere(rFloat, localFn)
            }
            "cylinder" -> {
                val h = (argsMap["h"] ?: argsMap["0"] ?: 10f) as? Number ?: 10f
                val rVal = argsMap["r"] ?: (argsMap["d"] as? Number)?.let { it.toFloat() / 2f } ?: argsMap["1"] ?: 5f
                val r1Val = argsMap["r1"] ?: (argsMap["d1"] as? Number)?.let { it.toFloat() / 2f } ?: rVal
                val r2Val = argsMap["r2"] ?: (argsMap["d2"] as? Number)?.let { it.toFloat() / 2f } ?: rVal

                val hFloat = h.toFloat()
                val r1Float = (r1Val as? Number)?.toFloat() ?: 5f
                val r2Float = (r2Val as? Number)?.toFloat() ?: 5f
                val centerVal = (argsMap["center"] as? Boolean) ?: false
                val localFn = (argsMap["\$fn"] as? Number)?.toInt() ?: fnVal

                return MeshBuilder.createCylinder(hFloat, r1Float, r2Float, centerVal, localFn)
            }
            "polyhedron" -> {
                val pointsVal = argsMap["points"] as? List<*> ?: emptyList<Any>()
                val facesVal = argsMap["faces"] as? List<*> ?: (argsMap["triangles"] as? List<*>) ?: emptyList<Any>()

                val pts = pointsVal.mapNotNull { pt ->
                    val coords = (pt as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }
                    if (coords != null && coords.size >= 3) Vector3(coords[0], coords[1], coords[2]) else null
                }

                val faces = facesVal.mapNotNull { f ->
                    (f as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
                }

                return MeshBuilder.createPolyhedron(pts, faces)
            }
            "translate" -> {
                val vecVal = argsMap["v"] ?: argsMap["0"] ?: listOf(0f, 0f, 0f)
                val vec = when (vecVal) {
                    is List<*> -> {
                        val l = vecVal.mapNotNull { (it as? Number)?.toFloat() }
                        Vector3(l.getOrElse(0) { 0f }, l.getOrElse(1) { 0f }, l.getOrElse(2) { 0f })
                    }
                    else -> Vector3.ZERO
                }
                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                val unionMesh = CsgEngine.unionAll(childMeshes)
                return unionMesh.transform({ v -> v + vec })
            }
            "rotate" -> {
                val aVal = argsMap["a"] ?: argsMap["0"] ?: listOf(0f, 0f, 0f)
                val rotVec = when (aVal) {
                    is Number -> Vector3(0f, 0f, aVal.toFloat())
                    is List<*> -> {
                        val l = aVal.mapNotNull { (it as? Number)?.toFloat() }
                        Vector3(l.getOrElse(0) { 0f }, l.getOrElse(1) { 0f }, l.getOrElse(2) { 0f })
                    }
                    else -> Vector3.ZERO
                }

                val rx = (rotVec.x * PI / 180.0).toFloat()
                val ry = (rotVec.y * PI / 180.0).toFloat()
                val rz = (rotVec.z * PI / 180.0).toFloat()

                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                val unionMesh = CsgEngine.unionAll(childMeshes)

                return unionMesh.transform({ v ->
                    // Rotate X
                    var y1 = v.y * cos(rx) - v.z * sin(rx)
                    var z1 = v.y * sin(rx) + v.z * cos(rx)
                    var x1 = v.x
                    // Rotate Y
                    var x2 = x1 * cos(ry) + z1 * sin(ry)
                    var z2 = -x1 * sin(ry) + z1 * cos(ry)
                    var y2 = y1
                    // Rotate Z
                    var x3 = x2 * cos(rz) - y2 * sin(rz)
                    var y3 = x2 * sin(rz) + y2 * cos(rz)
                    var z3 = z2
                    Vector3(x3, y3, z3)
                })
            }
            "scale" -> {
                val vVal = argsMap["v"] ?: argsMap["0"] ?: listOf(1f, 1f, 1f)
                val sVec = when (vVal) {
                    is Number -> Vector3(vVal.toFloat(), vVal.toFloat(), vVal.toFloat())
                    is List<*> -> {
                        val l = vVal.mapNotNull { (it as? Number)?.toFloat() }
                        Vector3(l.getOrElse(0) { 1f }, l.getOrElse(1) { 1f }, l.getOrElse(2) { 1f })
                    }
                    else -> Vector3(1f, 1f, 1f)
                }

                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                val unionMesh = CsgEngine.unionAll(childMeshes)
                return unionMesh.transform({ v -> Vector3(v.x * sVec.x, v.y * sVec.y, v.z * sVec.z) })
            }
            "color" -> {
                val cVal = argsMap["c"] ?: argsMap["0"] ?: listOf(0.2f, 0.5f, 0.9f)
                val colorObj = when (cVal) {
                    is String -> parseNamedColor(cVal)
                    is List<*> -> {
                        val l = cVal.mapNotNull { (it as? Number)?.toFloat() }
                        Color4(
                            l.getOrElse(0) { 0.2f },
                            l.getOrElse(1) { 0.5f },
                            l.getOrElse(2) { 0.9f },
                            l.getOrElse(3) { 1.0f }
                        )
                    }
                    else -> Color4.DEFAULT
                }

                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                val unionMesh = CsgEngine.unionAll(childMeshes)
                return unionMesh.transform({ v -> v }, colorOverride = colorObj)
            }
            "union" -> {
                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                return CsgEngine.unionAll(childMeshes)
            }
            "difference" -> {
                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                if (childMeshes.isEmpty()) return CsgMesh.EMPTY
                val base = childMeshes[0]
                val sub = childMeshes.drop(1)
                return CsgEngine.subtractAll(base, sub)
            }
            "intersection" -> {
                val childMeshes = evalChildren(node.children, scope, warnings, errors)
                return CsgEngine.intersectAll(childMeshes)
            }
            "linear_extrude" -> {
                val h = (argsMap["height"] ?: argsMap["0"] ?: 10f) as? Number ?: 10f
                val centerVal = (argsMap["center"] as? Boolean) ?: false
                val twistVal = ((argsMap["twist"] as? Number)?.toFloat()) ?: 0f
                val scaleVal = ((argsMap["scale"] as? Number)?.toFloat()) ?: 1.0f

                val circleOrSquare = node.children.firstOrNull() as? AstNode.ModuleCall
                val polygonPoints = if (circleOrSquare != null) {
                    val childName = circleOrSquare.name.lowercase(Locale.ROOT)
                    val cArgs = mutableMapOf<String, Any>()
                    circleOrSquare.args.forEachIndexed { idx, a ->
                        val v = evalExpr(a.expr, scope)
                        if (a.name != null) cArgs[a.name.lowercase(Locale.ROOT)] = v else cArgs["$idx"] = v
                    }

                    when (childName) {
                        "circle" -> {
                            val r = (cArgs["r"] ?: (cArgs["d"] as? Number)?.let { it.toFloat() / 2f } ?: cArgs["0"] ?: 5f) as? Number ?: 5f
                            val numPts = fnVal.coerceAtLeast(8)
                            val pts = mutableListOf<Vector3>()
                            for (i in 0 until numPts) {
                                val ang = (i.toDouble() / numPts) * 2.0 * PI
                                pts.add(Vector3((r.toFloat() * cos(ang)).toFloat(), (r.toFloat() * sin(ang)).toFloat(), 0f))
                            }
                            pts
                        }
                        "square" -> {
                            val size = cArgs["size"] ?: cArgs["0"] ?: 10f
                            val centerSq = cArgs["center"] as? Boolean ?: false
                            val (w, d) = when (size) {
                                is Number -> Pair(size.toFloat(), size.toFloat())
                                is List<*> -> {
                                    val l = size.mapNotNull { (it as? Number)?.toFloat() }
                                    Pair(l.getOrElse(0) { 10f }, l.getOrElse(1) { 10f })
                                }
                                else -> Pair(10f, 10f)
                            }
                            val x0 = if (centerSq) -w / 2f else 0f
                            val x1 = x0 + w
                            val y0 = if (centerSq) -d / 2f else 0f
                            val y1 = y0 + d
                            listOf(
                                Vector3(x0, y0, 0f),
                                Vector3(x1, y0, 0f),
                                Vector3(x1, y1, 0f),
                                Vector3(x0, y1, 0f)
                            )
                        }
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }

                if (polygonPoints.size >= 3) {
                    return MeshBuilder.linearExtrude(polygonPoints, h.toFloat(), centerVal, twistVal, scaleVal)
                }
                return CsgMesh.EMPTY
            }
            else -> {
                // Custom user defined module call
                val modDef = scope.getModule(node.name)
                if (modDef != null) {
                    val modScope = ScadScope(scope)
                    // Bind parameters
                    modDef.params.forEachIndexed { idx, param ->
                        val pName = param.first.lowercase(Locale.ROOT)
                        val argVal = argsMap[pName] ?: argsMap["$idx"]
                        val finalVal = argVal ?: param.second?.let { evalExpr(it, scope) }
                        if (finalVal != null) {
                            modScope.setVar(param.first, finalVal)
                        }
                    }
                    val childMeshes = evalChildren(modDef.children, modScope, warnings, errors)
                    return CsgEngine.unionAll(childMeshes)
                } else {
                    warnings.add("Unknown module: ${node.name}")
                    return null
                }
            }
        }
    }

    private fun evalChildren(
        children: List<AstNode>,
        scope: ScadScope,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): List<CsgMesh> {
        val list = mutableListOf<CsgMesh>()
        for (child in children) {
            val m = evalNode(child, scope, warnings, errors)
            if (m != null && !m.isEmpty) list.add(m)
        }
        return list
    }

    private fun evalExpr(expr: Expr, scope: ScadScope): Any {
        return when (expr) {
            is Expr.Number -> expr.value
            is Expr.StringLit -> expr.value
            is Expr.BoolLit -> expr.value
            is Expr.Variable -> scope.getVar(expr.name) ?: 0f
            is Expr.ArrayLit -> expr.elements.map { evalExpr(it, scope) }
            is Expr.RangeExpr -> {
                val start = (evalExpr(expr.start, scope) as? Number)?.toFloat() ?: 0f
                val step = expr.step?.let { (evalExpr(it, scope) as? Number)?.toFloat() } ?: 1f
                val end = (evalExpr(expr.end, scope) as? Number)?.toFloat() ?: start

                val list = mutableListOf<Float>()
                var curr = start
                if (step > 0) {
                    while (curr <= end + 0.0001f) {
                        list.add(curr)
                        curr += step
                    }
                } else if (step < 0) {
                    while (curr >= end - 0.0001f) {
                        list.add(curr)
                        curr += step
                    }
                }
                list
            }
            is Expr.Unary -> {
                val operand = evalExpr(expr.operand, scope)
                when (expr.op) {
                    "-" -> (operand as? Number)?.let { -it.toFloat() } ?: 0f
                    "!" -> (operand as? Boolean)?.not() ?: false
                    else -> 0f
                }
            }
            is Expr.Binary -> {
                val left = evalExpr(expr.left, scope)
                val right = evalExpr(expr.right, scope)

                if (left is Number && right is Number) {
                    val l = left.toFloat()
                    val r = right.toFloat()
                    when (expr.op) {
                        "+" -> l + r
                        "-" -> l - r
                        "*" -> l * r
                        "/" -> if (r != 0f) l / r else 0f
                        "%" -> if (r != 0f) l % r else 0f
                        "==" -> l == r
                        "!=" -> l != r
                        "<" -> l < r
                        ">" -> l > r
                        "<=" -> l <= r
                        ">=" -> l >= r
                        else -> 0f
                    }
                } else if (left is Boolean && right is Boolean) {
                    when (expr.op) {
                        "&&" -> left && right
                        "||" -> left || right
                        "==" -> left == right
                        "!=" -> left != right
                        else -> false
                    }
                } else {
                    0f
                }
            }
            is Expr.Call -> {
                val fnName = expr.name.lowercase(Locale.ROOT)
                val args = expr.args.map { evalExpr(it.expr, scope) }
                val arg0 = (args.getOrNull(0) as? Number)?.toFloat() ?: 0f
                val arg1 = (args.getOrNull(1) as? Number)?.toFloat() ?: 0f

                when (fnName) {
                    "sin" -> sin(arg0 * PI / 180.0).toFloat()
                    "cos" -> cos(arg0 * PI / 180.0).toFloat()
                    "sqrt" -> sqrt(max(0f, arg0))
                    "abs" -> abs(arg0)
                    "pow" -> arg0.pow(arg1)
                    "min" -> min(arg0, arg1)
                    "max" -> max(arg0, arg1)
                    else -> 0f
                }
            }
        }
    }

    private fun parseNamedColor(name: String): Color4 {
        return when (name.lowercase(Locale.ROOT)) {
            "red" -> Color4.RED
            "green" -> Color4.GREEN
            "blue" -> Color4.BLUE
            "yellow" -> Color4.YELLOW
            "orange" -> Color4.ORANGE
            "gray", "grey" -> Color4.GRAY
            else -> Color4.DEFAULT
        }
    }
}
