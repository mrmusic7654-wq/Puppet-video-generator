package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.GeminiApiServiceEnhanced
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.JobStatus
import com.example.data.PuppetAnimation
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
    val generatedScript: String = "",
    val jobStatus: JobStatus = JobStatus.PENDING,
    val progress: Float = 0f,
    val currentStep: String = "Add images to begin"
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
    private var geminiApi: GeminiApiServiceEnhanced? = null
    
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    init {
        // Initialize Gemini API if key is available
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isNotEmpty()) {
                geminiApi = GeminiApiServiceEnhanced(apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
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
        
        // Track face/pose in background for puppet animation
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
                videoState = VideoState.GeneratingScript,
                jobStatus = JobStatus.GENERATING_SCRIPT,
                currentStep = "Generating script with AI..."
            ) 
        }

        viewModelScope.launch {
            try {
                val api = geminiApi ?: throw IllegalStateException("Gemini API not initialized")
                val images = _state.value.selectedPhotos
                
                val result = if (images.isNotEmpty()) {
                    // Generate script with images
                    api.generateVideoScript(
                        images = images,
                        theme = currentPrompt,
                        duration = images.size * 3f
                    )
                } else {
                    // Generate script from text only
                    api.generateScriptFromText(
                        photoCount = 5, // Default to 5 scenes
                        theme = currentPrompt,
                        duration = 15f
                    )
                }
                
                result.onSuccess { script ->
                    _state.update { 
                        it.copy(
                            isGeneratingScript = false, 
                            script = script,
                            generatedScript = script.scenes.joinToString("\n") { scene -> scene.text },
                            videoState = VideoState.ScriptReady,
                            jobStatus = JobStatus.COMPLETED,
                            currentStep = "Script ready! Add images or create video."
                        ) 
                    }
                }.onFailure { error ->
                    _state.update { 
                        it.copy(
                            isGeneratingScript = false, 
                            error = error.message ?: "Script generation failed",
                            videoState = VideoState.Error(error.message ?: "Script generation failed"),
                            jobStatus = JobStatus.FAILED,
                            currentStep = "Script generation failed"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isGeneratingScript = false, 
                        error = e.message ?: "Unknown error",
                        videoState = VideoState.Error(e.message ?: "Unknown error"),
                        jobStatus = JobStatus.FAILED,
                        currentStep = "Error generating script"
                    ) 
                }
            }
        }
    }
    
    fun createVideo(outputPath: String) {
        viewModelScope.launch {
            _state.update { 
                it.copy(
                    videoState = VideoState.Processing(0f),
                    jobStatus = JobStatus.PROCESSING_VIDEO,
                    currentStep = "Analyzing images..."
                ) 
            }
            
            try {
                val images = _state.value.selectedPhotos
                val script = _state.value.script
                
                if (images.isEmpty()) {
                    throw IllegalStateException("No images selected")
                }
                
                // Step 1: Analyze all images with MediaPipe
                _state.update { it.copy(currentStep = "Detecting faces and poses...", progress = 0.1f) }
                
                val analyzedImages = images.mapIndexed { index, bitmap ->
                    val faceData = puppetTracker.detectFace(bitmap)
                    val poseData = puppetTracker.detectPose(bitmap)
                    
                    val expression = script?.scenes?.getOrNull(index)?.expression ?: "neutral"
                    val animation = if (faceData != null && poseData != null) {
                        puppetTracker.createPuppetAnimation(faceData, poseData, expression)
                    } else {
                        PuppetAnimation(
                            mouthOpen = false,
                            eyeScale = 1.0f,
                            headTilt = 0f,
                            bodyRotation = 0f
                        )
                    }
                    
                    Pair(bitmap, animation)
                }
                
                // Step 2: Apply puppet animations to images
                _state.update { it.copy(currentStep = "Applying puppet animations...", progress = 0.3f) }
                
                val animatedFrames = analyzedImages.mapIndexed { index, pair ->
                    val (bitmap, animation) = pair
                    val expression = script?.scenes?.getOrNull(index)?.expression ?: "neutral"
                    
                    if (animation != PuppetAnimation(false, 1.0f, 0f, 0f, 1.0f, android.graphics.PointF(0f, 0f))) {
                        applyPuppetTransformations(bitmap, animation, expression)
                    } else {
                        bitmap
                    }
                }
                
                // Step 3: Generate video with FFmpeg
                _state.update { it.copy(currentStep = "Rendering video...", progress = 0.5f) }
                
                val config = VideoProcessingService.VideoConfig(
                    outputPath = outputPath,
                    transitionEffect = VideoProcessingService.TransitionEffect.ZOOM
                )
                
                // Prepare subtitles from script
                val subtitles = script?.scenes?.let { scenes ->
                    var currentTime = 0f
                    scenes.map { scene ->
                        val startTime = currentTime
                        currentTime += scene.duration
                        Subtitle(
                            text = scene.text,
                            startTime = startTime,
                            endTime = currentTime
                        )
                    }
                }
                
                val result = videoService.createVideoFromImages(
                    images = animatedFrames,
                    subtitles = subtitles,
                    config = config
                )
                
                result.onSuccess { videoFile ->
                    _state.update { 
                        it.copy(
                            videoState = VideoState.Completed(videoFile),
                            jobStatus = JobStatus.COMPLETED,
                            progress = 1.0f,
                            currentStep = "Video created successfully!"
                        ) 
                    }
                }.onFailure { error ->
                    _state.update { 
                        it.copy(
                            videoState = VideoState.Error(error.message ?: "Video creation failed"),
                            jobStatus = JobStatus.FAILED,
                            currentStep = "Video creation failed"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        videoState = VideoState.Error(e.message ?: "Unknown error"),
                        jobStatus = JobStatus.FAILED,
                        currentStep = "Error creating video"
                    ) 
                }
            }
        }
    }
    
    private fun applyPuppetTransformations(
        original: Bitmap,
        animation: PuppetAnimation,
        expression: String
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        
        // Apply puppet effects based on expression and animation
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        
        // Scale puppet based on expression
        val scale = when (expression) {
            "surprised" -> 1.2f
            "happy" -> 1.1f
            "sad" -> 0.9f
            else -> animation.scale
        }
        
        canvas.scale(scale, scale, result.width / 2f, result.height / 2f)
        
        // Rotate head based on pose
        canvas.rotate(animation.headTilt, result.width / 2f, result.height / 4f)
        
        return result
    }
    
    override fun onCleared() {
        super.onCleared()
        puppetTracker.close()
    }
}
