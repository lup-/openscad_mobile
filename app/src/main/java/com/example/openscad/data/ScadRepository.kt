package com.example.openscad.data

import com.example.openscad.model.ScadProject
import kotlinx.coroutines.flow.Flow

class ScadRepository(private val dao: ScadDao) {

    val allProjects: Flow<List<ScadProject>> = dao.getAllProjects()

    suspend fun getProjectById(id: Long): ScadProject? = dao.getProjectById(id)

    suspend fun saveProject(project: ScadProject): Long {
        return if (project.id == 0L) {
            dao.insertProject(project.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            project.id
        }
    }

    suspend fun deleteProject(id: Long) = dao.deleteProjectById(id)
}
