package com.example.openscad.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OpenScadCodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    onRenderClick: () -> Unit,
    onAiAssistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snippets = listOf(
        "cube([10, 10, 10]);" to "cube",
        "sphere(r=10, \$fn=32);" to "sphere",
        "cylinder(h=15, r=5, \$fn=32);" to "cylinder",
        "translate([10, 0, 0])" to "translate",
        "rotate([0, 0, 45])" to "rotate",
        "difference() {\n    \n}" to "difference",
        "union() {\n    \n}" to "union",
        "color(\"red\")" to "color",
        "linear_extrude(height=20) {\n    circle(r=10);\n}" to "extrude"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Slate 900 editor bg
    ) {
        // Snippets Horizontal Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            snippets.forEach { (snippetText, label) ->
                AssistChip(
                    onClick = {
                        onCodeChange(if (code.isBlank()) snippetText else "$code\n$snippetText")
                    },
                    label = { Text("+ $label", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF38BDF8)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Code Area with Line Numbers
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val lines = code.split("\n")
            val lineCount = lines.size.coerceAtLeast(1)
            val lineNumbersText = (1..lineCount).joinToString("\n")

            val scrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Line Numbers Sidebar
                Text(
                    text = lineNumbersText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier
                        .background(Color(0xFF020617))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .fillMaxHeight()
                )

                // Code Input Field
                BasicTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFF1F5F9),
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    visualTransformation = OpenScadSyntaxHighlighter(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                )
            }
        }

        // Bottom Quick Controls Bar
        Surface(
            color = Color(0xFF1E293B),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onCodeChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                    IconButton(onClick = onAiAssistClick) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant", tint = Color(0xFFA855F7))
                    }
                }

                OutlinedButton(
                    onClick = onRenderClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Render", tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Render 3D", color = Color(0xFF38BDF8))
                }
            }
        }
    }
}

class OpenScadSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)

            val keywords = listOf(
                "cube", "sphere", "cylinder", "polyhedron", "square", "circle",
                "translate", "rotate", "scale", "color", "mirror",
                "union", "difference", "intersection", "linear_extrude", "rotate_extrude",
                "module", "for", "if", "else", "true", "false"
            )

            val keywordRegex = Regex("\\b(${keywords.joinToString("|")})\\b")
            val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
            val commentRegex = Regex("//.*|/\\*.*?\\*/")
            val varRegex = Regex("\\$[a-zA-Z0-9_]+")

            // Highlight keywords (Cyan/Purple)
            for (match in keywordRegex.findAll(raw)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFC084FC)), // Light Purple
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // Highlight numbers (Orange)
            for (match in numberRegex.findAll(raw)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFFB923C)), // Orange
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // Highlight $fn variables (Green)
            for (match in varRegex.findAll(raw)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFF4ADE80)), // Green
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // Highlight comments (Gray)
            for (match in commentRegex.findAll(raw)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFF64748B)),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
