package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PuppetTracker(context: Context) {
    
    private var faceDetector: FaceDetector? = null
    private var poseLandmarker: PoseLandmarker? = null
    
    data class FaceData(
        val boundingBox: RectF,
        val keypoints: List<PointF>,
        val confidence: Float
    )
    
    data class PoseData(
        val landmarks: List<PointF>,
        val confidence: Float
    )
    
    init {
        setupFaceDetector(context)
        setupPoseLandmarker(context)
    }
    
    private fun setupFaceDetector(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_detection_short_range.tflite")
                .setDelegate(Delegate.GPU)
                .build()
            
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f)
                .build()
            
            faceDetector = FaceDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            // If GPU delegate fails, try CPU
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_detection_short_range.tflite")
                    .setDelegate(Delegate.CPU)
                    .build()
                
                val options = FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinDetectionConfidence(0.5f)
                    .build()
                
                faceDetector = FaceDetector.createFromOptions(context, options)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    private fun setupPoseLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .setDelegate(Delegate.GPU)
                .build()
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(0.5f)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            // If GPU delegate fails, try CPU
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_lite.task")
                    .setDelegate(Delegate.CPU)
                    .build()
                
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(0.5f)
                    .build()
                
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    fun detectFace(bitmap: Bitmap): FaceData? {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector?.detect(mpImage)
            return result?.detections()?.firstOrNull()?.let { detection ->
                FaceData(
                    boundingBox = detection.boundingBox(),
                    keypoints = detection.keypoints().map { 
                        PointF(it.x(), it.y()) 
                    },
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun detectPose(bitmap: Bitmap): PoseData? {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detect(mpImage)
            return result?.landmarks()?.firstOrNull()?.let { landmarks ->
                PoseData(
                    landmarks = landmarks.map { 
                        PointF(it.x(), it.y()) 
                    },
                    confidence = 0.8f
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun createPuppetAnimation(
        faceData: FaceData,
        poseData: PoseData,
        expression: String // From Gemini AI: "happy", "sad", "surprised"
    ): PuppetAnimation {
        // Generate puppet animation parameters based on face/pose tracking
        return PuppetAnimation(
            mouthOpen = expression == "surprised",
            eyeScale = if (expression == "happy") 1.2f else 1.0f,
            headTilt = poseData.landmarks.getOrNull(0)?.x?.minus(poseData.landmarks.getOrNull(11)?.x ?: 0f) ?: 0f,
            bodyRotation = calculateBodyRotation(poseData)
        )
    }
    
    private fun calculateBodyRotation(poseData: PoseData): Float {
        val leftShoulder = poseData.landmarks.getOrNull(11) ?: return 0f
        val rightShoulder = poseData.landmarks.getOrNull(12) ?: return 0f
        val dx = rightShoulder.x - leftShoulder.x
        val dy = rightShoulder.y - leftShoulder.y
        return if (dx != 0f) dy / dx else 0f
    }
    
    fun close() {
        faceDetector?.close()
        poseLandmarker?.close()
    }
}

data class PuppetAnimation(
    val mouthOpen: Boolean,
    val eyeScale: Float,
    val headTilt: Float,
    val bodyRotation: Float
)
