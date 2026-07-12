package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PuppetTracker(context: Context) {
    
    private var faceDetector: FaceDetector? = null
    private var poseLandmarker: PoseLandmarker? = null
    
    data class FaceData(
        val boundingBox: android.graphics.RectF,
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
            e.printStackTrace()
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
            e.printStackTrace()
        }
    }
    
    fun detectFace(bitmap: Bitmap): FaceData? {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector?.detect(mpImage)
            
            val detection = result?.detections()?.firstOrNull() ?: return null
            
            val boundingBox = detection.boundingBox()
            
            // Convert keypoints manually - NO flatMap/map
            val keypointsList = mutableListOf<PointF>()
            val keypoints = detection.keypoints()
            for (kp in keypoints) {
                val point = PointF(kp.x(), kp.y())
                keypointsList.add(point)
            }
            
            val confidence = if (detection.categories().isNotEmpty()) {
                detection.categories().first().score()
            } else {
                0.5f
            }
            
            FaceData(
                boundingBox = boundingBox,
                keypoints = keypointsList,
                confidence = confidence
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun detectPose(bitmap: Bitmap): PoseData? {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detect(mpImage)
            
            val landmarks = result?.landmarks()?.firstOrNull() ?: return null
            
            // Convert landmarks manually - NO flatMap/map
            val pointsList = mutableListOf<PointF>()
            for (landmark in landmarks) {
                val point = PointF(landmark.x(), landmark.y())
                pointsList.add(point)
            }
            
            PoseData(
                landmarks = pointsList,
                confidence = 0.8f
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun createPuppetAnimation(
        faceData: FaceData?,
        poseData: PoseData?,
        expression: String
    ): PuppetAnimation {
        if (faceData == null || poseData == null) {
            return PuppetAnimation(
                mouthOpen = false,
                eyeScale = 1.0f,
                headTilt = 0f,
                bodyRotation = 0f
            )
        }
        
        return try {
            val mouthOpen = expression == "surprised" || expression == "happy"
            
            val eyeScale = when (expression) {
                "happy" -> 1.2f
                "surprised" -> 1.3f
                "sad" -> 0.8f
                else -> 1.0f
            }
            
            var headTilt = 0f
            var bodyRotation = 0f
            
            // Calculate head tilt from pose landmarks
            if (poseData.landmarks.size > 11) {
                val nose = poseData.landmarks[0]
                val leftShoulder = poseData.landmarks[11]
                headTilt = nose.x - leftShoulder.x
            }
            
            // Calculate body rotation
            if (poseData.landmarks.size > 12) {
                val leftShoulder = poseData.landmarks[11]
                val rightShoulder = poseData.landmarks[12]
                val dx = rightShoulder.x - leftShoulder.x
                if (dx != 0f) {
                    bodyRotation = (rightShoulder.y - leftShoulder.y) / dx
                }
            }
            
            PuppetAnimation(
                mouthOpen = mouthOpen,
                eyeScale = eyeScale,
                headTilt = headTilt,
                bodyRotation = bodyRotation
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PuppetAnimation(
                mouthOpen = false,
                eyeScale = 1.0f,
                headTilt = 0f,
                bodyRotation = 0f
            )
        }
    }
    
    fun close() {
        try {
            faceDetector?.close()
            poseLandmarker?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class PuppetAnimation(
    val mouthOpen: Boolean,
    val eyeScale: Float,
    val headTilt: Float,
    val bodyRotation: Float
)
