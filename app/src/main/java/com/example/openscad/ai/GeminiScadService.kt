package com.example.openscad.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiScadService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateOpenScadCode(userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API Key is missing. Configure it in the Secrets panel."))
            }

            val systemInstruction = """
                You are an expert OpenSCAD 3D CAD programmer.
                Generate ONLY valid OpenSCAD code based on the user's request.
                Do NOT include markdown formatting or explanations outside the code block.
                Return clean, well-commented OpenSCAD code using standard primitives (cube, sphere, cylinder, polyhedron, translate, rotate, scale, difference, union, intersection, linear_extrude).
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "User request: $userPrompt"))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API Error: ${response.code} ${response.message}"))
            }

            val responseStr = response.body?.string() ?: ""
            val responseJson = JSONObject(responseStr)
            val text = responseJson.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanCode = text
                .replace("```openscad", "")
                .replace("```scad", "")
                .replace("```", "")
                .trim()

            Result.success(cleanCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
