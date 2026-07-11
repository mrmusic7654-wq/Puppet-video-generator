package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PuppetScript(
    val scenes: List<Scene>
)

@JsonClass(generateAdapter = true)
data class Scene(
    val id: Int,
    val text: String
)
