package com.example.openscad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ExportStlDialog(
    modelName: String,
    triangleCount: Int,
    onDismiss: () -> Unit,
    onConfirmExport: (isBinary: Boolean) -> Unit
) {
    var isBinary by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Export STL File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Model: $modelName ($triangleCount triangles)",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Binary STL Option
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isBinary) Color(0xFF0F172A) else Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBinary = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isBinary,
                            onClick = { isBinary = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Binary STL (.stl)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Compact, fast loading in 3D slicers (Cura, Prusa, Bambu)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ASCII STL Option
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (!isBinary) Color(0xFF0F172A) else Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBinary = false }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isBinary,
                            onClick = { isBinary = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("ASCII STL (.stl)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Text format with facet coordinates, readable in text editor", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmExport(isBinary)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Export & Share")
                    }
                }
            }
        }
    }
}
