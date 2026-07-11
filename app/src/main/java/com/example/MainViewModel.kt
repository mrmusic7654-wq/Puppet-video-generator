package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.PuppetScript
import com.example.video.PuppetTracker
import com.example.video.Subtitle
import com.example.video.VideoProcessingService
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AppState(
    val prompt: String = "",
    val isGeneratingScript: Boolean = false,
    val script: PuppetScript? = null,
    val sceneImages: Map<Int, Uri> = emptyMap(),
    val error: String? = null,
    val videoState: VideoState = VideoState.Idle,
    val selectedPhotos: List<Bitmap> = emptyList(),
    val generatedScript: String = ""
)

sealed class VideoState {
    object Idle : VideoState()
    object GeneratingScript : VideoState()
    object ScriptReady : VideoState()
    data class Processing(val progress: Float) : VideoState()
    data class Completed(val videoFile: File) : VideoState()
    data class Error(val message: String) : VideoState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val videoService = VideoProcessingService(application)
    private val puppetTracker = PuppetTracker(application)
    
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    fun onPromptChange(prompt: String) {
        _state.update { it.copy(prompt = prompt) }
    }

    fun onImageSelected(sceneId: Int, uri: Uri?) {
        if (uri != null) {
            _state.update {
                it.copy(sceneImages = it.sceneImages + (sceneId to uri))
            }
        }
    }
    
    fun addPhoto(bitmap: Bitmap) {
        _state.update { it.copy(selectedPhotos = it.selectedPhotos + bitmap) }
        
        // Track face/pose in background
        viewModelScope.launch {
            val faceData = puppetTracker.detectFace(bitmap)
            val poseData = puppetTracker.detectPose(bitmap)
            
            if (faceData != null && poseData != null) {
                // Analyze for puppet animation
                val animation = puppetTracker.createPuppetAnimation(
                    faceData, 
                    poseData, 
                    "neutral"
                )
                // Store animation data for video generation (could be added to state if needed)
            }
        }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun generateScript() {
        val currentPrompt = _state.value.prompt
        if (currentPrompt.isBlank()) return

        _state.update { 
            it.copy(
                isGeneratingScript = true, 
                error = null, 
                script = null, 
                sceneImages = emptyMap(),
                videoState = VideoState.GeneratingScript
            ) 
        }

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val systemInstruction = "You are a creative writer for a puppet show video. Create a short script based on the user's topic. Break the script into exactly 3 to 5 scenes. Each scene should have a narrator or puppet talking. Return the output STRICTLY as a JSON object matching this schema: { \\\"scenes\\\": [ { \\\"id\\\": 1, \\\"text\\\": \\\"Dialog here...\\\" } ] } No markdown formatting or extra text."
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Topic: $currentPrompt")))),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    val moshi = Moshi.Builder().build()
                    val adapter = moshi.adapter(PuppetScript::class.java)
                    val script = adapter.fromJson(responseText)
                    if (script != null) {
                        _state.update { 
                            it.copy(
                                isGeneratingScript = false, 
                                script = script,
                                generatedScript = script.scenes.joinToString("\n") { scene -> scene.text },
                                videoState = VideoState.ScriptReady
                            ) 
                        }
                    } else {
                        _state.update { 
                            it.copy(
                                isGeneratingScript = false, 
                                error = "Failed to parse script.",
                                videoState = VideoState.Error("Failed to parse script")
                            ) 
                        }
                    }
                } else {
                    _state.update { 
                        it.copy(
                            isGeneratingScript = false, 
                            error = "No response from Gemini.",
                            videoState = VideoState.Error("No response from Gemini")
                        ) 
                    }
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isGeneratingScript = false, 
                        error = e.message ?: "Unknown error",
                        videoState = VideoState.Error(e.message ?: "Unknown error")
                    ) 
                }
            }
        }
    }
    
    fun createVideo(outputPath: String) {
        viewModelScope.launch {
            _state.update { it.copy(videoState = VideoState.Processing(0f)) }
            
            try {
                val config = VideoProcessingService.VideoConfig(
                    outputPath = outputPath,
                    transitionEffect = VideoProcessingService.TransitionEffect.ZOOM
                )
                
                val subtitles = _state.value.generatedScript
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, text ->
                        Subtitle(
                            text = text,
                            startTime = index * 3.0f,
                            endTime = (index + 1) * 3.0f
                        )
                    }
                
                val result = videoService.createVideoFromImages(
                    images = _state.value.selectedPhotos,
                    subtitles = if (subtitles.isNotEmpty()) subtitles else null,
                    config = config
                )
                
                result.onSuccess { videoFile ->
                    _state.update { it.copy(videoState = VideoState.Completed(videoFile)) }
                }.onFailure { error ->
                    _state.update { it.copy(videoState = VideoState.Error(error.message ?: "Video creation failed")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(videoState = VideoState.Error(e.message ?: "Unknown error")) }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        puppetTracker.close()
    }
}
