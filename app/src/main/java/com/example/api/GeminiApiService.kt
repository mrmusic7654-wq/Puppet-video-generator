package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.PuppetScript
import com.example.data.Scene
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: PartImageData? = null
)

@JsonClass(generateAdapter = true)
data class PartImageData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}

/**
 * Enhanced Gemini API service with script generation and image support
 */
class GeminiApiServiceEnhanced(private val apiKey: String) {
    
    private val moshi = Moshi.Builder().build()
    private val scriptAdapter = moshi.adapter(PuppetScript::class.java)
    
    /**
     * Generate a video script based on images and a theme
     */
    suspend fun generateVideoScript(
        images: List<Bitmap>,
        theme: String,
        duration: Float = 30f
    ): Result<PuppetScript> {
        return try {
            // Build prompt with image analysis request
            val imageCount = images.size
            val prompt = buildPrompt(theme, imageCount, duration)
            
            // Create request with images (if any)
            val parts = mutableListOf<Part>()
            
            // Add images as inline data
            images.forEach { bitmap ->
                val base64Image = bitmapToBase64(bitmap)
                parts.add(
                    Part(
                        inlineData = PartImageData(
                            mimeType = "image/png",
                            data = base64Image
                        )
                    )
                )
            }
            
            // Add text prompt
            parts.add(Part(text = prompt))
            
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = parts)),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    maxOutputTokens = 2048
                ),
                systemInstruction = Content(
                    parts = listOf(
                        Part(
                            text = """
                            You are a creative puppet show director. Analyze the provided images and create an engaging video script.
                            
                            For each image/scene, provide:
                            1. What the puppet says (narration/dialogue)
                            2. Expression emotion (happy, sad, surprised, neutral)
                            3. Scene duration in seconds
                            4. Transition effect (fade, zoom, slide)
                            
                            Return ONLY valid JSON matching this schema:
                            {
                              "scenes": [
                                {
                                  "id": 1,
                                  "text": "dialogue text here",
                                  "imageIndex": 0,
                                  "expression": "happy",
                                  "duration": 3.0,
                                  "transition": "fade",
                                  "audioCue": null
                                }
                              ],
                              "totalDuration": 15.0,
                              "title": "My Puppet Show"
                            }
                            
                            Make it entertaining and puppet-show style! No markdown formatting.
                            """.trimIndent()
                        )
                    )
                )
            )
            
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (responseText != null) {
                val script = parseScript(responseText)
                if (script != null) {
                    // Adjust durations to match total
                    val adjustedScript = adjustDurations(script, duration)
                    Result.success(adjustedScript)
                } else {
                    Result.failure(Exception("Failed to parse script from response"))
                }
            } else {
                Result.failure(Exception("No response from Gemini API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generate script from text prompt only (no images)
     */
    suspend fun generateScriptFromText(
        photoCount: Int,
        theme: String,
        duration: Float = 30f
    ): Result<PuppetScript> {
        return try {
            val prompt = buildPrompt(theme, photoCount, duration)
            
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    maxOutputTokens = 2048
                ),
                systemInstruction = Content(
                    parts = listOf(
                        Part(
                            text = """
                            You are a creative puppet show director. Create an engaging video script for $photoCount photos.
                            Theme: $theme
                            Total duration: $duration seconds
                            
                            For each scene, provide:
                            1. What the puppet says (narration/dialogue)
                            2. Expression emotion (happy, sad, surprised, neutral)
                            3. Scene duration in seconds
                            4. Transition effect (fade, zoom, slide)
                            
                            Return ONLY valid JSON matching this schema:
                            {
                              "scenes": [
                                {
                                  "id": 1,
                                  "text": "dialogue text here",
                                  "imageIndex": 0,
                                  "expression": "happy",
                                  "duration": 3.0,
                                  "transition": "fade",
                                  "audioCue": null
                                }
                              ],
                              "totalDuration": $duration,
                              "title": "Puppet Show"
                            }
                            
                            Make it entertaining and puppet-show style! No markdown formatting.
                            """.trimIndent()
                        )
                    )
                )
            )
            
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (responseText != null) {
                val script = parseScript(responseText)
                if (script != null) {
                    val adjustedScript = adjustDurations(script, duration)
                    Result.success(adjustedScript)
                } else {
                    Result.failure(Exception("Failed to parse script from response"))
                }
            } else {
                Result.failure(Exception("No response from Gemini API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildPrompt(theme: String, imageCount: Int, duration: Float): String {
        return """
        Create a puppet show video script with $imageCount scenes.
        Theme: $theme
        Total duration: $duration seconds
        
        Make it engaging, funny, and suitable for all ages.
        Each scene should have dialogue that matches a puppet performance style.
        """.trimIndent()
    }
    
    private fun parseScript(responseText: String): PuppetScript? {
        return try {
            // Clean up response (remove markdown code blocks if present)
            val cleanJson = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            scriptAdapter.fromJson(cleanJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun adjustDurations(script: PuppetScript, totalDuration: Float): PuppetScript {
        if (script.scenes.isEmpty()) return script
        
        // Calculate current total - use fold to avoid sumOf type ambiguity with Float
        val currentTotal = script.scenes.fold(0f) { acc, scene -> acc + scene.duration }
        
        if (currentTotal == 0f) {
            // If no durations set, distribute evenly
            val sceneDuration = totalDuration / script.scenes.size
            return script.copy(
                scenes = script.scenes.map { it.copy(duration = sceneDuration) },
                totalDuration = totalDuration
            )
        }
        
        // Scale durations proportionally
        val scale = totalDuration / currentTotal
        return script.copy(
            scenes = script.scenes.map { it.copy(duration = it.duration * scale) },
            totalDuration = totalDuration
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArray = ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        }
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
