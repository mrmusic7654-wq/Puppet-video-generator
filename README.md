<div align="center">
<img width="1200" height="475" alt="Puppet Studio Banner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Puppet Studio

An AI-powered video editor that generates scripts and creates puppet-style videos from your photos using Google's Gemini AI.

## Overview

Puppet Studio is an Android application that leverages AI to transform your photos into engaging puppet-style videos. The app uses Gemini AI to automatically generate scripts and animate your images, creating professional-looking content with minimal effort.

## Features

- 🎭 **AI-Powered Script Generation**: Automatically create engaging scripts from your photos
- 🎬 **Puppet-Style Video Creation**: Transform static images into animated puppet videos
- 🤖 **Gemini AI Integration**: Powered by Google's advanced Gemini AI models
- 🎥 **FFmpeg Video Processing**: Professional video encoding with transitions and effects
- 👤 **MediaPipe Face & Pose Tracking**: Detect faces and body poses for puppet animation
- 📱 **Modern Android UI**: Built with Jetpack Compose for a smooth user experience
- 🔒 **Secure API Key Management**: Environment-based configuration for API keys

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **AI Backend**: Firebase AI (Gemini)
- **Video Processing**: FFmpeg-Kit
- **Face/Pose Detection**: MediaPipe
- **Architecture**: MVVM with ViewModel
- **Dependency Injection**: Manual DI
- **Build System**: Gradle (Kotlin DSL)
- **Minimum SDK**: 24
- **Target SDK**: 36

## Project Structure

```
puppetmaster-ai/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── api/          # API services (GeminiApiService)
│   │   │   │   ├── data/         # Data models
│   │   │   │   ├── ui/theme/     # App theme (Color, Type, Theme)
│   │   │   │   ├── video/        # Video processing & puppet tracking
│   │   │   │   │   ├── VideoProcessingService.kt  # FFmpeg video creation
│   │   │   │   │   └── PuppetTracker.kt           # MediaPipe face/pose detection
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── MainViewModel.kt
│   │   │   ├── assets/           # MediaPipe model files
│   │   │   ├── res/              # Android resources
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                 # Unit tests
│   │   └── androidTest/          # Instrumentation tests
│   ├── build.gradle.kts          # App-level build configuration
│   └── proguard-rules.pro        # ProGuard rules
├── assets/                       # Application assets
├── gradle/                       # Gradle wrapper and version catalog
├── .env.example                  # Environment variables template
├── .gitignore                    # Git ignore rules
├── build.gradle.kts              # Project-level build configuration
├── gradle.properties             # Gradle properties
├── settings.gradle.kts           # Gradle settings
└── metadata.json                 # App metadata
```

## Prerequisites

Before running this project, ensure you have:

- **Android Studio** (Latest version recommended)
- **JDK 11** or higher
- **Android SDK** with API level 24 or higher
- **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/)

## Getting Started

### 1. Clone and Open the Project

```bash
# Navigate to the project directory
cd /workspace
```

1. Open Android Studio
2. Select **File** → **Open** and choose the project directory
3. Allow Android Studio to sync and download dependencies

### 2. Configure API Keys

Create a `.env` file in the project root directory:

```bash
cp .env.example .env
```

Edit the `.env` file and add your Gemini API key:

```env
GEMINI_API_KEY=your_actual_gemini_api_key_here
```

> **Note**: Never commit your `.env` file to version control. The `.env.example` file is provided as a template.

### 3. Download MediaPipe Models

The PuppetTracker requires two model files to be placed in `app/src/main/assets/`:

1. **Face Detection Model** (`face_detection_short_range.tflite`)
   ```bash
   wget https://storage.googleapis.com/mediapipe-models/face_detector/face_detection_short_range/float16/1/face_detection_short_range.tflite \
     -O app/src/main/assets/face_detection_short_range.tflite
   ```

2. **Pose Landmarker Model** (`pose_landmarker_lite.task`)
   ```bash
   wget https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task \
     -O app/src/main/assets/pose_landmarker_lite.task
   ```

Alternatively, you can download these files manually from the MediaPipe website and place them in the assets directory.

### 4. Configure Signing (Optional)

For debug builds, no additional configuration is needed. For release builds:

1. Create a keystore file at the root directory or set environment variables:
   - `KEYSTORE_PATH`: Path to your keystore
   - `STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password

2. Or modify `app/build.gradle.kts` to use your preferred signing configuration


### 5. Build and Run

1. Connect an Android device or start an emulator
2. Click **Run** in Android Studio or execute:

```bash
./gradlew installDebug
```

## Configuration

### Build Variants

- **Debug**: Includes debugging tools and uses debug signing config
- **Release**: Optimized for production with ProGuard rules

### Dependencies

Key dependencies include:

- `androidx.compose.*`: Jetpack Compose UI components
- `firebase.ai`: Firebase AI SDK for Gemini integration
- `retrofit` & `okhttp`: Network communication
- `coil`: Image loading
- `moshi`: JSON serialization
- `room`: Local database
- `ffmpeg-kit-full`: FFmpeg video processing
- `mediapipe-tasks-vision`: MediaPipe face and pose detection
- `work-runtime-ktx`: WorkManager for background tasks

## Testing

Run unit tests:

```bash
./gradlew test
```

Run instrumented tests:

```bash
./gradlew connectedAndroidTest
```

Run screenshot tests with Roborazzi:

```bash
./gradlew verifyRoborazziDemoDebug
```

## Troubleshooting

### Common Issues

1. **Missing API Key Error**
   - Ensure `.env` file exists in the project root
   - Verify `GEMINI_API_KEY` is set correctly
   - Check that the key has proper permissions

2. **Build Failures**
   - Sync Gradle files: **File** → **Sync Project with Gradle Files**
   - Clean and rebuild: **Build** → **Clean Project**, then **Rebuild Project**
   - Invalidate caches: **File** → **Invalidate Caches / Restart**

3. **Runtime Crashes**
   - Check Logcat for error messages
   - Verify device/emulator meets minimum SDK requirements
   - Ensure all permissions are granted

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is part of Google AI Studio. Refer to the original project license for usage terms.

## Resources

- [AI Studio App](https://ai.studio/apps/47ef92c7-6be4-4bb3-8a05-d7c2878bf340)
- [Gemini AI Documentation](https://ai.google.dev/)
- [Jetpack Compose Guide](https://developer.android.com/jetpack/compose)
- [Firebase AI SDK](https://firebase.google.com/docs/vertex-ai/get-started?platform=android)

## Support

For issues and questions:
- Check the [AI Studio app page](https://ai.studio/apps/47ef92c7-6be4-4bb3-8a05-d7c2878bf340)
- Review [Gemini AI documentation](https://ai.google.dev/)
- File issues on the project repository

---

<div align="center">
  <p>Built with ❤️ using Google Gemini AI</p>
</div>
