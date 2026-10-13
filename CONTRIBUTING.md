# Contributing to FlowGrab

Thank you for your interest in contributing to FlowGrab!

FlowGrab is a high-performance, resilient video and audio downloader for Android powered by [yt-dlp](https://github.com/yt-dlp/yt-dlp) and Jetpack Compose.

## 🐛 Bug Reports

When submitting a bug report:
1. Ensure your issue has not already been reported by searching existing issues.
2. Provide complete steps to reproduce the issue, including:
   - Target URL (or sample video)
   - Download mode (Video, Audio, Custom Command)
   - Error stack trace from the notification or crash report dialog
   - Device model, Android OS version, and FlowGrab version

## 💡 Feature Requests

Feature requests are welcome! Please ensure requested features are supported by upstream [yt-dlp](https://github.com/yt-dlp/yt-dlp).

FlowGrab offers two primary download modes:
- **General Mode:** Quick presets for video/audio downloads, automated stream merging, chapter splitting, and system MediaStore synchronization.
- **Custom Command Mode:** Parameterized templates to execute raw yt-dlp arguments on Android.

## 🔨 Building From Source

FlowGrab builds with standard Android Gradle Plugin (AGP 8.9+):

```bash
# Run unit tests
./gradlew testGenericDebugUnitTest

# Assemble debug APK
./gradlew assembleGenericDebug

# Assemble signed release APK
./gradlew assembleGenericRelease
```
