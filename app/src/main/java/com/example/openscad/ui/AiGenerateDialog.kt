package com.example.openscad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun AiGenerateDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (prompt: String) -> Unit
) {
    var promptText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant",
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI OpenSCAD Assistant",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Describe what 3D model you want to create (e.g. \"Parametric gear with 18 teeth and 6mm center hole\" or \"Parametric box with sliding lid\"): ",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Enter prompt...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFA855F7),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating OpenSCAD code...", fontSize = 12.sp, color = Color(0xFFA855F7))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onGenerate(promptText) },
                        enabled = promptText.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA))
                    ) {
                        Text("Generate")
                    }
                }
            }
        }
    }
}
