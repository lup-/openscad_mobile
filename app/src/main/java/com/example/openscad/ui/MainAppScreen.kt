package com.example.openscad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openscad.editor.OpenScadCodeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val code by viewModel.code.collectAsState()
    val projectTitle by viewModel.projectTitle.collectAsState()
    val renderResult by viewModel.renderResult.collectAsState()
    val renderSettings by viewModel.renderSettings.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val projectsList by viewModel.projectsList.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { viewModel.setProjectTitle(it) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.createNewProject() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Model", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.saveCurrentProject() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Export STL", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF020617))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF020617),
                contentColor = Color(0xFF38BDF8)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Code Editor") },
                    label = { Text("Code Editor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.setSelectedTab(1)
                        viewModel.renderCode()
                    },
                    icon = {
                        if (isRendering) {
                            BadgedBox(badge = { Badge { Text("...") } }) {
                                Icon(Icons.Default.ViewInAr, contentDescription = "3D Preview")
                            }
                        } else {
                            Icon(Icons.Default.ViewInAr, contentDescription = "3D Preview")
                        }
                    },
                    label = { Text("3D Preview") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Models") },
                    label = { Text("Presets & Saved") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    OpenScadCodeEditor(
                        code = code,
                        onCodeChange = { viewModel.setCode(it) },
                        onRenderClick = {
                            viewModel.renderCode()
                            viewModel.setSelectedTab(1)
                        },
                        onAiAssistClick = { showAiDialog = true }
                    )
                }
                1 -> {
                    ViewerScreen(
                        renderResult = renderResult,
                        renderSettings = renderSettings,
                        isRendering = isRendering,
                        onSettingsChange = { update -> viewModel.updateRenderSettings(update) },
                        onExportStlClick = { showExportDialog = true }
                    )
                }
                2 -> {
                    ProjectsScreen(
                        projects = projectsList,
                        onSelectProject = { proj ->
                            viewModel.loadProject(proj)
                            viewModel.setSelectedTab(0)
                        },
                        onNewProject = {
                            viewModel.createNewProject()
                            viewModel.setSelectedTab(0)
                        },
                        onDeleteProject = { id -> viewModel.deleteProject(id) }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportStlDialog(
            modelName = projectTitle,
            triangleCount = renderResult?.triangleCount ?: 0,
            onDismiss = { showExportDialog = false },
            onConfirmExport = { isBinary ->
                viewModel.shareStlFile(isBinary)
            }
        )
    }

    if (showAiDialog) {
        AiGenerateDialog(
            isLoading = isAiLoading,
            onDismiss = { showAiDialog = false },
            onGenerate = { prompt ->
                viewModel.generateWithAi(prompt) {
                    showAiDialog = false
                    viewModel.setSelectedTab(1)
                }
            }
        )
    }
}
