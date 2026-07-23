package com.example.openscad.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.openscad.ai.GeminiScadService
import com.example.openscad.data.ScadDatabase
import com.example.openscad.data.ScadRepository
import com.example.openscad.model.RenderResult
import com.example.openscad.model.RenderSettings
import com.example.openscad.model.ScadProject
import com.example.openscad.parser.ScadEvaluator
import com.example.openscad.parser.ScadParser
import com.example.openscad.stl.StlExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScadRepository
    val projectsList: StateFlow<List<ScadProject>>

    private val _code = MutableStateFlow<String>("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _projectTitle = MutableStateFlow<String>("New Model")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _currentProjectId = MutableStateFlow<Long>(0L)
    val currentProjectId: StateFlow<Long> = _currentProjectId.asStateFlow()

    private val _renderResult = MutableStateFlow<RenderResult?>(null)
    val renderResult: StateFlow<RenderResult?> = _renderResult.asStateFlow()

    private val _renderSettings = MutableStateFlow(RenderSettings())
    val renderSettings: StateFlow<RenderSettings> = _renderSettings.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Editor, 1: 3D Preview, 2: Presets & Projects
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    init {
        val dao = ScadDatabase.getInstance(application).scadDao()
        repository = ScadRepository(dao)

        projectsList = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load default code on launch
        val defaultCode = """
// 20mm Calibration Cube
difference() {
    cube([20, 20, 20], center=true);
    
    // Hollow inner sphere
    sphere(r=8, ${"$"}fn=32);
    
    // Axis cross holes
    cylinder(h=25, r=4, center=true, ${"$"}fn=32);
    rotate([90, 0, 0]) cylinder(h=25, r=4, center=true, ${"$"}fn=32);
    rotate([0, 90, 0]) cylinder(h=25, r=4, center=true, ${"$"}fn=32);
}
        """.trimIndent()

        _code.value = defaultCode
        renderCode(defaultCode)
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setCode(newCode: String) {
        _code.value = newCode
    }

    fun setProjectTitle(title: String) {
        _projectTitle.value = title
    }

    fun updateRenderSettings(update: (RenderSettings) -> RenderSettings) {
        _renderSettings.value = update(_renderSettings.value)
    }

    fun renderCode(scadCode: String = _code.value) {
        viewModelScope.launch(Dispatchers.Default) {
            _isRendering.value = true
            try {
                val parser = ScadParser(scadCode)
                val ast = parser.parseProgram()
                val evaluator = ScadEvaluator()
                val result = evaluator.evaluateProgram(ast, _renderSettings.value.fnDefault)
                _renderResult.value = result
            } catch (e: Exception) {
                // Keep previous result with error tag
            } finally {
                _isRendering.value = false
            }
        }
    }

    fun loadProject(project: ScadProject) {
        _currentProjectId.value = project.id
        _projectTitle.value = project.title
        _code.value = project.code
        renderCode(project.code)
    }

    fun createNewProject() {
        _currentProjectId.value = 0L
        _projectTitle.value = "New Model"
        val emptyCode = "// New OpenSCAD Model\ncube([10, 10, 10], center=true);\n"
        _code.value = emptyCode
        renderCode(emptyCode)
    }

    fun saveCurrentProject(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val project = ScadProject(
                id = _currentProjectId.value,
                title = _projectTitle.value.ifBlank { "Untitled Model" },
                code = _code.value,
                isPreset = false
            )
            val savedId = repository.saveProject(project)
            _currentProjectId.value = savedId
            Toast.makeText(getApplication(), "Project saved!", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            repository.deleteProject(id)
            if (_currentProjectId.value == id) {
                createNewProject()
            }
        }
    }

    fun generateWithAi(prompt: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isAiLoading.value = true
            val result = GeminiScadService.generateOpenScadCode(prompt)
            _isAiLoading.value = false

            result.fold(
                onSuccess = { generatedCode ->
                    _code.value = generatedCode
                    renderCode(generatedCode)
                    onSuccess()
                },
                onFailure = { error ->
                    Toast.makeText(getApplication(), "AI Generation failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun shareStlFile(isBinary: Boolean = true) {
        val result = _renderResult.value ?: return
        if (result.mesh.isEmpty) {
            Toast.makeText(getApplication(), "Cannot export empty 3D model", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = "${_projectTitle.value.replace(" ", "_")}.stl"
                val cacheDir = getApplication<Application>().cacheDir
                val file = File(cacheDir, filename)

                if (isBinary) {
                    val bytes = StlExporter.exportBinaryStl(result.mesh, _projectTitle.value)
                    FileOutputStream(file).use { it.write(bytes) }
                } else {
                    val asciiStr = StlExporter.exportAsciiStl(result.mesh, _projectTitle.value)
                    file.writeText(asciiStr)
                }

                val uri: Uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "model/stl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, _projectTitle.value)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Export STL via")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    getApplication<Application>().startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
