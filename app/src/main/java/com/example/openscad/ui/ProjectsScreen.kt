package com.example.openscad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.example.openscad.model.ScadProject

@Composable
fun ProjectsScreen(
    projects: List<ScadProject>,
    onSelectProject: (ScadProject) -> Unit,
    onNewProject: () -> Unit,
    onDeleteProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Presets", "My Models", "Hardware", "Mechanical", "Vases")

    val filteredProjects = projects.filter { project ->
        val matchesSearch = project.title.contains(searchQuery, ignoreCase = true) ||
                project.code.contains(searchQuery, ignoreCase = true)

        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Presets" -> project.isPreset
            "My Models" -> !project.isPreset
            else -> project.category.equals(selectedCategory, ignoreCase = true)
        }

        matchesSearch && matchesCategory
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search OpenSCAD models...", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Category Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = Color(0xFF38BDF8),
                edgePadding = 16.dp,
                divider = {}
            ) {
                categories.forEach { cat ->
                    Tab(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        text = {
                            Text(
                                text = cat,
                                color = if (selectedCategory == cat) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Projects Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredProjects, key = { it.id }) { proj ->
                    ProjectCard(
                        project = proj,
                        onClick = { onSelectProject(proj) },
                        onDelete = { onDeleteProject(proj.id) }
                    )
                }
            }
        }

        // Floating New Model Action Button
        ExtendedFloatingActionButton(
            onClick = onNewProject,
            icon = { Icon(Icons.Default.Add, contentDescription = "New Model") },
            text = { Text("New Model") },
            containerColor = Color(0xFF0284C7),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}

@Composable
fun ProjectCard(
    project: ScadProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (project.isPreset) Icons.Default.Folder else Icons.Default.Code,
                    contentDescription = null,
                    tint = if (project.isPreset) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                )

                if (!project.isPreset) {
                    IconButton(onClick = onDelete, modifier = Modifier.height(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = project.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (project.description.isNotBlank()) project.description else project.code.take(60),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 2
            )
        }
    }
}
