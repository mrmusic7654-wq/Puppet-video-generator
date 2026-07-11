package com.example

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.PuppetScript
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppState(
    val prompt: String = "",
    val isGeneratingScript: Boolean = false,
    val script: PuppetScript? = null,
    val sceneImages: Map<Int, Uri> = emptyMap(),
    val error: String? = null
)

class MainViewModel : ViewModel() {
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
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun generateScript() {
        val currentPrompt = _state.value.prompt
        if (currentPrompt.isBlank()) return

        _state.update { it.copy(isGeneratingScript = true, error = null, script = null, sceneImages = emptyMap()) }

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val systemInstruction = "You are a creative writer for a puppet show video. Create a short script based on the user's topic. Break the script into exactly 3 to 5 scenes. Each scene should have a narrator or puppet talking. Return the output STRICTLY as a JSON object matching this schema: { \"scenes\": [ { \"id\": 1, \"text\": \"Dialog here...\" } ] } No markdown formatting or extra text."
                
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
                        _state.update { it.copy(isGeneratingScript = false, script = script) }
                    } else {
                        _state.update { it.copy(isGeneratingScript = false, error = "Failed to parse script.") }
                    }
                } else {
                    _state.update { it.copy(isGeneratingScript = false, error = "No response from Gemini.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingScript = false, error = e.message ?: "Unknown error") }
            }
        }
    }
}
