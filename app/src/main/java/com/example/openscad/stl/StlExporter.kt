package com.example.openscad.stl

import com.example.openscad.model.CsgMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object StlExporter {

    fun exportBinaryStl(mesh: CsgMesh, modelName: String = "OpenSCAD_Model"): ByteArray {
        val numTriangles = mesh.triangles.size
        val bufferSize = 80 + 4 + (numTriangles * 50)
        val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        // 80-byte header
        val headerBytes = "Exported from OpenSCAD 3D Android App ($modelName)".padEnd(80, ' ').take(80).toByteArray(Charsets.US_ASCII)
        buffer.put(headerBytes)

        // Number of triangles (4 bytes uint)
        buffer.putInt(numTriangles)

        // Triangles
        for (t in mesh.triangles) {
            val norm = t.normal
            buffer.putFloat(norm.x)
            buffer.putFloat(norm.y)
            buffer.putFloat(norm.z)

            buffer.putFloat(t.v1.x)
            buffer.putFloat(t.v1.y)
            buffer.putFloat(t.v1.z)

            buffer.putFloat(t.v2.x)
            buffer.putFloat(t.v2.y)
            buffer.putFloat(t.v2.z)

            buffer.putFloat(t.v3.x)
            buffer.putFloat(t.v3.y)
            buffer.putFloat(t.v3.z)

            // Attribute byte count (0)
            buffer.putShort(0)
        }

        return buffer.array()
    }

    fun exportAsciiStl(mesh: CsgMesh, modelName: String = "OpenSCAD_Model"): String {
        val cleanName = modelName.replace(" ", "_")
        val sb = StringBuilder()
        sb.append("solid ").append(cleanName).append("\n")

        for (t in mesh.triangles) {
            val n = t.normal
            sb.append(String.format(Locale.US, "  facet normal %e %e %e\n", n.x, n.y, n.z))
            sb.append("    outer loop\n")
            sb.append(String.format(Locale.US, "      vertex %e %e %e\n", t.v1.x, t.v1.y, t.v1.z))
            sb.append(String.format(Locale.US, "      vertex %e %e %e\n", t.v2.x, t.v2.y, t.v2.z))
            sb.append(String.format(Locale.US, "      vertex %e %e %e\n", t.v3.x, t.v3.y, t.v3.z))
            sb.append("    endloop\n")
            sb.append("  endfacet\n")
        }

        sb.append("endsolid ").append(cleanName).append("\n")
        return sb.toString()
    }
}
