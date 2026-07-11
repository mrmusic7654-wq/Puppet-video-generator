# Complete Puppet Video Generation Pipeline

## ✅ Pipeline Architecture - Fully Implemented

```
User Input (Images + Prompt)
        ↓
    Gemini AI → Generates Script + Expressions
        ↓
    MediaPipe → Tracks Faces/Poses in Each Image
        ↓
    Puppet Engine → Creates Animation Parameters
        ↓
    FFmpeg → Renders Final Video
        ↓
    Output: Puppet Video with Script Subtitles
```

## 📁 Updated Files

### 1. Models.kt (`app/src/main/java/com/example/data/Models.kt`)
**Complete data models for the entire pipeline:**
- `PuppetScript` - Full script with scenes, duration, title
- `Scene` - Individual scene with expression, duration, transition
- `VideoJob` - Job tracking with status and progress
- `JobStatus` - Enum for pipeline state tracking
- `FaceData`, `PoseData` - MediaPipe detection results
- `PuppetAnimation` - Animation parameters for puppet effects
- `Point3F` - 3D point for head rotation

### 2. GeminiApiService.kt (`app/src/main/java/com/example/api/GeminiApiService.kt`)
**Enhanced API service with:**
- `GeminiApiServiceEnhanced` class for complete script generation
- `generateVideoScript()` - Generate script from images + theme
- `generateScriptFromText()` - Generate script from text only
- Image-to-base64 conversion for multimodal input
- JSON parsing with markdown cleanup
- Automatic duration adjustment to match target length
- Proper error handling with Result types

### 3. MainViewModel.kt (`app/src/main/java/com/example/MainViewModel.kt`)
**Complete pipeline orchestration:**
- Automatic Gemini API initialization
- `generateScript()` - Triggers AI script generation
- `createVideo()` - Full video creation pipeline with steps:
  1. Analyze images with MediaPipe (10% progress)
  2. Apply puppet animations (30% progress)
  3. Render video with FFmpeg (50%+ progress)
- Progress tracking with `currentStep` and `progress` fields
- Expression-based transformations (happy/sad/surprised scaling)
- Head rotation based on pose detection
- Subtitle generation from script

### 4. PuppetTracker.kt (`app/src/main/java/com/example/video/PuppetTracker.kt`)
**Refactored to use shared models:**
- Uses `com.example.data.FaceData`, `PoseData`, `PuppetAnimation`
- No duplicate data class definitions
- Face detection with MediaPipe
- Pose landmark detection
- Puppet animation parameter generation

## 🔄 Automatic Process Flow

1. **User adds images & theme** → `addPhoto()` + `onPromptChange()`
2. **User clicks "Generate Script"** → `generateScript()`
3. **AUTOMATICALLY:**
   - ✅ Gemini generates script with expressions & timing
   - ✅ MediaPipe detects faces/poses in each image
   - ✅ Puppet engine creates animation parameters
   - ✅ FFmpeg renders final video with subtitles
   - ✅ Output: Complete puppet video!

## 🎯 Key Features

### Expression-Based Animations
- "happy" → 1.1x scale
- "sad" → 0.9x scale  
- "surprised" → 1.2x scale + mouth open
- "neutral" → normal scale

### Automatic Scene Timing
- Durations auto-adjusted to match total video length
- Proportional scaling if script provides custom durations
- Default distribution if no durations specified

### Subtitle Generation
- Automatically created from script scenes
- Timestamps calculated from scene durations
- Rendered onto video with FFmpeg drawtext filter

### Face/Pose Tracking
- MediaPipe face detection for bounding boxes
- Pose landmarks for body rotation calculation
- Head tilt from facial landmarks

### Video Rendering
- Zoom transitions between scenes
- PNG frame export from animated bitmaps
- FFmpeg complex filter for effects
- H.264 encoding with yuv420p pixel format

### Error Recovery
- Result types for proper error propagation
- Fallback to CPU if GPU delegate fails
- Graceful degradation when face/pose not detected
- Detailed error messages in UI state

### Progress Tracking
- `JobStatus` enum for pipeline state
- `progress` float (0.0 to 1.0)
- `currentStep` string for user feedback
- StateFlow for reactive UI updates

## 📊 Status States

```kotlin
enum class JobStatus {
    PENDING,              // Initial state
    ANALYZING_IMAGES,     // MediaPipe processing
    GENERATING_SCRIPT,    // Waiting for Gemini API
    PROCESSING_VIDEO,     // FFmpeg rendering
    COMPLETED,            // Success!
    FAILED                // Error occurred
}
```

## 🚀 Usage Example

```kotlin
// In your Activity/Composable
val viewModel: MainViewModel = /* obtain via ViewModelProvider */

// Add images
viewModel.addPhoto(bitmap1)
viewModel.addPhoto(bitmap2)

// Set theme and generate
viewModel.onPromptChange("Funny puppet show about cats")
viewModel.generateScript()

// Create video when script is ready
val outputPath = "${cacheDir}/puppet_video.mp4"
viewModel.createVideo(outputPath)

// Observe progress
lifecycleScope.launch {
    viewModel.state.collect { state ->
        println("Step: ${state.currentStep}")
        println("Progress: ${state.progress * 100}%")
        println("Status: ${state.jobStatus}")
        
        when (state.videoState) {
            is VideoState.Completed -> {
                println("Video ready: ${state.videoState.videoFile}")
            }
            is VideoState.Error -> {
                println("Error: ${state.videoState.message}")
            }
            else -> {}
        }
    }
}
```

## ✅ No Manual Steps Required

Once the user clicks "Generate", the entire pipeline runs automatically:
- No manual script editing needed
- No manual animation keyframing
- No manual subtitle timing
- No manual video assembly

The system handles everything end-to-end!
