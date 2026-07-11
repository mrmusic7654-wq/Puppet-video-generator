package com.example.data

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.squareup.moshi.JsonClass

// Gemini API Models (already in GeminiApiService.kt, but keeping for completeness)
@JsonClass(generateAdapter = true)
data class PuppetScript(
    val scenes: List<Scene>,
    val totalDuration: Float = 0f,
    val title: String = "Puppet Show"
)

@JsonClass(generateAdapter = true)
data class Scene(
    val id: Int,
    val text: String,
    val imageIndex: Int = 0,
    val expression: String = "neutral", // happy, sad, surprised, neutral
    val duration: Float = 3.0f,
    val transition: String = "fade", // fade, zoom, slide
    val audioCue: String? = null
)

// Video Generation Models
data class VideoJob(
    val id: String,
    val images: List<Bitmap>,
    val script: PuppetScript,
    val outputPath: String,
    val status: JobStatus = JobStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

enum class JobStatus {
    PENDING,
    ANALYZING_IMAGES,
    GENERATING_SCRIPT,
    PROCESSING_VIDEO,
    COMPLETED,
    FAILED
}

// Puppet Animation Data
data class PuppetAnimationData(
    val faceData: FaceData?,
    val poseData: PoseData?,
    val animation: PuppetAnimation
)

data class FaceData(
    val boundingBox: RectF,
    val keypoints: List<PointF>,
    val confidence: Float,
    val expressions: Map<String, Float> = emptyMap(), // emotion -> confidence
    val headRotation: Point3F = Point3F(0f, 0f, 0f)
)

data class PoseData(
    val landmarks: List<PointF>,
    val confidence: Float,
    val bodyAngles: Map<String, Float> = emptyMap()
)

data class Point3F(
    val x: Float,
    val y: Float,
    val z: Float
)

data class PuppetAnimation(
    val mouthOpen: Boolean = false,
    val eyeScale: Float = 1.0f,
    val headTilt: Float = 0f,
    val bodyRotation: Float = 0f,
    val scale: Float = 1.0f,
    val position: PointF = PointF(0f, 0f)
)
