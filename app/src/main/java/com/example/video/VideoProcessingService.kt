package com.example.video

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        val bitrate: String = "2M",
        val duration: Float = 3.0f, // seconds per image
        val transitionEffect: TransitionEffect = TransitionEffect.FADE
    )
    
    enum class TransitionEffect {
        FADE, SLIDE, ZOOM, NONE
    }
    
    suspend fun createVideoFromImages(
        images: List<Bitmap>,
        audioPath: String? = null,
        subtitles: List<Subtitle>? = null,
        config: VideoConfig
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Save images to temp directory
            val tempDir = File(context.cacheDir, "puppet_temp")
            tempDir.mkdirs()
            
            // Clean up old temp files
            tempDir.listFiles()?.forEach { it.delete() }
            
            val imageFiles = images.mapIndexed { index, bitmap ->
                val file = File(tempDir, "frame_$index.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            }
            
            // Build FFmpeg command
            val command = buildFFmpegCommand(imageFiles, audioPath, subtitles, config)
            
            // Execute FFmpeg using the session-based approach for better error handling
            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            val returnCode = session.returnCode
            
            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                val outputFile = File(config.outputPath)
                if (outputFile.exists()) {
                    Result.success(outputFile)
                } else {
                    Result.failure(Exception("Output file was not created"))
                }
            } else {
                val errorMessage = session.failStackTrace ?: "FFmpeg failed with return code: $returnCode"
                Result.failure(Exception("FFmpeg failed: $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildFFmpegCommand(
        imageFiles: List<String>,
        audioPath: String?,
        subtitles: List<Subtitle>?,
        config: VideoConfig
    ): String {
        val cmd = StringBuilder("-y ")
        
        // Input images with duration
        imageFiles.forEach { image ->
            cmd.append("-loop 1 -t ${config.duration} -i \"$image\" ")
        }
        
        // Complex filter for transitions
        cmd.append("-filter_complex \"")
        
        val numImages = imageFiles.size
        
        when (config.transitionEffect) {
            TransitionEffect.FADE -> {
                // Simple fade transition between images
                if (numImages > 1) {
                    // Concatenate with crossfade
                    val concatInputs = buildString {
                        imageFiles.indices.forEach { i ->
                            append("[${i}:v]fade=t=out:st=${config.duration - 0.5}:d=0.5[fade$i]; ")
                        }
                    }
                    cmd.append(concatInputs)
                    
                    // Concatenate all faded clips
                    val concatChain = imageFiles.indices.joinToString("") { "[fade$it]" }
                    cmd.append("${concatChain}concat=n=$numImages:v=1:a=0[v]; ")
                } else {
                    cmd.append("[0:v]format=yuv420p[v]; ")
                }
            }
            TransitionEffect.ZOOM -> {
                // Zoom effect (puppet-style)
                if (numImages > 1) {
                    imageFiles.indices.forEach { i ->
                        cmd.append("[${i}:v]scale=8000:-1,zoompan=z='min(zoom+0.0015,1.5)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=${config.width}x${config.height}[zoom$i]; ")
                    }
                    val concatChain = imageFiles.indices.joinToString("") { "[zoom$it]" }
                    cmd.append("${concatChain}concat=n=$numImages:v=1:a=0[v]; ")
                } else {
                    cmd.append("[0:v]scale=8000:-1,zoompan=z='min(zoom+0.0015,1.5)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=${config.width}x${config.height}[v]; ")
                }
            }
            TransitionEffect.SLIDE -> {
                // Slide transition
                if (numImages > 1) {
                    imageFiles.indices.forEach { i ->
                        if (i < numImages - 1) {
                            cmd.append("[${i}:v]format=rgba,fade=t=out:st=${config.duration - 0.5}:d=0.5:alpha=1,setpts=PTS-STARTPTS[v$i]; ")
                        } else {
                            cmd.append("[${i}:v]setpts=PTS-STARTPTS[v$i]; ")
                        }
                    }
                    val concatChain = imageFiles.indices.joinToString("") { "[v$it]" }
                    cmd.append("${concatChain}concat=n=$numImages:v=1:a=0[v]; ")
                } else {
                    cmd.append("[0:v]format=yuv420p[v]; ")
                }
            }
            TransitionEffect.NONE -> {
                // Simple concatenation
                if (numImages > 1) {
                    val concatChain = imageFiles.indices.joinToString(" ") { "[${it}:v]" }
                    cmd.append("$concatChain concat=n=$numImages:v=1:a=0[v]; ")
                } else {
                    cmd.append("[0:v]format=yuv420p[v]; ")
                }
            }
        }
        
        // Add subtitles if provided
        if (!subtitles.isNullOrEmpty()) {
            val subtitleText = subtitles.joinToString("\\n") { it.text.replace("'", "'\\''") }
            cmd.append("[v]drawtext=text='$subtitleText':fontcolor=white:fontsize=24:box=1:boxcolor=black@0.5:boxborderw=5:x=(w-text_w)/2:y=h-th-10[outv]")
        } else {
            cmd.append("[v]format=yuv420p[outv]")
        }
        
        cmd.append("\" ")
        
        // Map video
        cmd.append("-map \"[outv]\" ")
        
        // Add audio if provided
        if (audioPath != null) {
            cmd.append("-i \"$audioPath\" ")
            cmd.append("-shortest ") // Match video length to audio
            cmd.append("-c:a aac ")
            cmd.append("-b:a 128k ")
        }
        
        // Output settings
        cmd.append("-c:v libx264 ")
        cmd.append("-preset ultrafast ")
        cmd.append("-b:v ${config.bitrate} ")
        cmd.append("-r ${config.frameRate} ")
        cmd.append("-s ${config.width}x${config.height} ")
        cmd.append("-pix_fmt yuv420p ")
        cmd.append("\"${config.outputPath}\"")
        
        return cmd.toString()
    }
}
