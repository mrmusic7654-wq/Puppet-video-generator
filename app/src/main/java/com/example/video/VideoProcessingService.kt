package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMuxer.OutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class Subtitle(
    val text: String,
    val startTime: Float,
    val endTime: Float
)

class VideoProcessingService(private val context: Context) {
    
    data class VideoConfig(
        val outputPath: String,
        val width: Int = 1080,
        val height: Int = 1920,
        val frameRate: Int = 30,
        val durationPerImage: Float = 3.0f, // seconds per image
        val transitionEffect: TransitionEffect = TransitionEffect.FADE
    )
    
    enum class TransitionEffect {
        FADE, ZOOM, SLIDE, NONE
    }
    
    suspend fun createVideoFromImages(
        images: List<Bitmap>,
        subtitles: List<Subtitle>? = null,
        config: VideoConfig
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(config.outputPath)
            outputFile.parentFile?.mkdirs()
            
            // Prepare MediaMuxer for output
            val muxer = MediaMuxer(
                config.outputPath,
                OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            
            // Configure video encoder
            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                config.width,
                config.height
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
            }
            
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            val inputSurface = encoder.createInputSurface()
            encoder.start()
            
            var trackIndex = -1
            var muxerStarted = false
            
            // Process each image
            images.forEachIndexed { index, bitmap ->
                // Scale bitmap to video dimensions
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    config.width,
                    config.height,
                    true
                )
                
                // Calculate frames for this image
                val totalFrames = (config.durationPerImage * config.frameRate).toInt()
                
                for (frame in 0 until totalFrames) {
                    val progress = frame.toFloat() / totalFrames
                    
                    // Apply transition effects
                    val processedBitmap = applyTransitionEffect(
                        scaledBitmap,
                        config.transitionEffect,
                        progress,
                        index > 0
                    )
                    
                    // Add subtitles if available
                    val finalBitmap = if (subtitles != null) {
                        addSubtitles(processedBitmap, subtitles, frame, totalFrames, index)
                    } else {
                        processedBitmap
                    }
                    
                    // Draw frame to encoder surface
                    val canvas = inputSurface.lockCanvas(null)
                    canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                    inputSurface.unlockCanvasAndPost(canvas)
                    
                    // Get encoded data
                    val bufferInfo = MediaCodec.BufferInfo()
                    var outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    
                    while (outputBufferId >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferId)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            encoder.releaseOutputBuffer(outputBufferId, false)
                            outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                            continue
                        }
                        
                        if (bufferInfo.size != 0) {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            
                            outputBuffer?.position(bufferInfo.offset)
                            outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                        }
                        
                        encoder.releaseOutputBuffer(outputBufferId, false)
                        outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    }
                }
            }
            
            // Signal end of stream
            encoder.signalEndOfInputStream()
            
            // Get remaining encoded data
            var bufferInfo = MediaCodec.BufferInfo()
            var outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            
            while (outputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (outputBufferId >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferId)
                    if (bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferId, false)
                }
                outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            }
            
            // Clean up
            encoder.stop()
            encoder.release()
            inputSurface.release()
            
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            
            Result.success(outputFile)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun applyTransitionEffect(
        bitmap: Bitmap,
        effect: TransitionEffect,
        progress: Float,
        hasPrevious: Boolean
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply { isAntiAlias = true }
        
        when (effect) {
            TransitionEffect.FADE -> {
                val alpha = if (hasPrevious) {
                    (255 * progress).toInt()
                } else {
                    255
                }
                paint.alpha = alpha
            }
            
            TransitionEffect.ZOOM -> {
                val scale = 1.0f + (0.2f * progress) // Zoom in
                canvas.scale(
                    scale, scale,
                    bitmap.width / 2f,
                    bitmap.height / 2f
                )
            }
            
            TransitionEffect.SLIDE -> {
                val offsetX = bitmap.width * progress
                canvas.translate(offsetX, 0f)
            }
            
            TransitionEffect.NONE -> {
                // No transition effect
            }
        }
        
        return result
    }
    
    private fun addSubtitles(
        bitmap: Bitmap,
        subtitles: List<Subtitle>,
        currentFrame: Int,
        totalFrames: Int,
        imageIndex: Int
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val currentTime = (imageIndex * totalFrames + currentFrame) / 30f // Assuming 30fps
        
        subtitles.forEach { subtitle ->
            if (currentTime in subtitle.startTime..subtitle.endTime) {
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 72f
                    isAntiAlias = true
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
                
                // Draw text at bottom of frame
                val textWidth = paint.measureText(subtitle.text)
                canvas.drawText(
                    subtitle.text,
                    (bitmap.width - textWidth) / 2f,
                    bitmap.height - 100f,
                    paint
                )
            }
        }
        
        return result
    }
}
