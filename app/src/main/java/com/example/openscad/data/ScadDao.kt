package com.example.openscad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.openscad.model.ScadProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ScadDao {

    @Query("SELECT * FROM scad_projects ORDER BY isPreset DESC, updatedAt DESC")
    fun getAllProjects(): Flow<List<ScadProject>>

    @Query("SELECT * FROM scad_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ScadProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ScadProject): Long

    @Update
    suspend fun updateProject(project: ScadProject)

    @Query("DELETE FROM scad_projects WHERE id = :id AND isPreset = 0")
    suspend fun deleteProjectById(id: Long)

    @Query("SELECT COUNT(*) FROM scad_projects")
    suspend fun getProjectCount(): Int
}
