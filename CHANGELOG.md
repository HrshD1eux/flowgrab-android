# Changelog

All notable changes to FlowGrab stable and preview releases are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v2.0.0] - 2026-10-13

### 🚀 Highlights & Features
- **Instant App Launch**: Eliminated main-thread startup blocking and system splash lag (windowSplashScreenAnimationDuration = 0).
- **Complete Edge-to-Edge Redesign**: Cleaned up top whitespace across navigation drawer, downloads page, and settings subpages.
- **Persistent View & Configuration**: View mode toggle (Grid vs. List) and app preferences persist reliably across app restarts via MMKV write-through state delegates.
- **Queue Quick Action Dropdown**: Added 3-dot dropdown menu in the downloads screen (Resume All, Pause All, Clear Completed, Clear Queue).
- **Download Timestamps**: Exact download date and time displayed alongside media file sizes in lists and details drawer.
- **YouTube Anti-Bot & SABR Bypass**: Integrated YouTube client spoofing (player_client=android_creator,web,tv) to bypass botguard challenges and 40-70 KB/s throttles.
- **High-Performance Multi-URL Batching**: Simultaneous multi-URL and playlist downloads with background concurrency.
- **In-App Auto-Update System**: Fully integrated GitHub release checking with device-architecture APK matching.

### 🐛 Bug Fixes & Refactoring
- Fixed auto-update toggle state syncing when navigating between settings pages.
- Resolved false-positive "Failed to update the latest version" toast on update checks.
- Eliminated all compiler deprecation warnings and modernized to Jetpack Compose Material 3 standards.
- Streamlined GitHub Actions CI/CD release workflow for automated multi-architecture APK releases.