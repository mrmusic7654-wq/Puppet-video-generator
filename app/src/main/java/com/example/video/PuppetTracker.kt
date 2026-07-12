package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.ArrayList

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
        try {
            val baseOptionsFace = BaseOptions.builder()
                .setModelAssetPath("face_detection_short_range.tflite")
                .setDelegate(Delegate.GPU)
                .build()
            
            val faceOptions = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptionsFace)
                .setMinDetectionConfidence(0.5f)
                .build()
            
            faceDetector = FaceDetector.createFromOptions(context, faceOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val baseOptionsPose = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .setDelegate(Delegate.GPU)
                .build()
            
            val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsPose)
                .setMinPoseDetectionConfidence(0.5f)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun detectFace(bitmap: Bitmap): FaceData? {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector?.detect(mpImage) ?: return null
            val detections = result.detections()
            
            if (detections.isEmpty()) return null
            
            val detection = detections[0]
            val boundingBox = detection.boundingBox()
            
            val keypointsList = ArrayList<PointF>()
            val keypoints = detection.keypoints()
            for (i in 0 until keypoints.size) {
                val kp = keypoints[i]
                keypointsList.add(PointF(kp.x(), kp.y()))
            }
            
            val confidence = if (detection.categories().size > 0) {
                detection.categories()[0].score()
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
            val result = poseLandmarker?.detect(mpImage) ?: return null
            val allLandmarks = result.landmarks()
            
            if (allLandmarks.isEmpty()) return null
            
            val landmarks = allLandmarks[0]
            val pointsList = ArrayList<PointF>()
            
            for (i in 0 until landmarks.size) {
                val lm = landmarks[i]
                pointsList.add(PointF(lm.x(), lm.y()))
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
            return PuppetAnimation(false, 1.0f, 0f, 0f)
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
            
            if (poseData.landmarks.size > 11) {
                val nose = poseData.landmarks[0]
                val leftShoulder = poseData.landmarks[11]
                headTilt = nose.x - leftShoulder.x
            }
            
            if (poseData.landmarks.size > 12) {
                val leftShoulder = poseData.landmarks[11]
                val rightShoulder = poseData.landmarks[12]
                val dx = rightShoulder.x - leftShoulder.x
                if (dx != 0f) {
                    bodyRotation = (rightShoulder.y - leftShoulder.y) / dx
                }
            }
            
            PuppetAnimation(mouthOpen, eyeScale, headTilt, bodyRotation)
        } catch (e: Exception) {
            e.printStackTrace()
            PuppetAnimation(false, 1.0f, 0f, 0f)
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
