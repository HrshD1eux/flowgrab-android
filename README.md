<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" height="128" alt="FlowGrab App Logo" style="border-radius: 26px; box-shadow: 0 8px 24px rgba(0,0,0,0.12);" />
</p>

<h1 align="center">FlowGrab</h1>

<p align="center">
  <strong>Next-Generation, Resilient Video & Audio Downloader for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/HrshD1eux/flowgrab-android/releases/latest"><img src="https://img.shields.io/github/v/release/HrshD1eux/flowgrab-android?style=flat&color=3B82F6&logo=github&label=Release" alt="Latest Release" /></a>
  <a href="https://hrshd1eux.github.io/flowgrab-android/"><img src="https://img.shields.io/badge/Website-GitHub_Pages-2563EB?logo=google-chrome&logoColor=white" alt="GitHub Pages Website" /></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?logo=android&logoColor=white" alt="Platform" /></a>
  <a href="https://github.com/yt-dlp/yt-dlp"><img src="https://img.shields.io/badge/Powered_by-yt--dlp-FF0000?logo=youtube&logoColor=white" alt="yt-dlp" /></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3" /></a>
</p>

<p align="center">
  FlowGrab is a high-performance, battery-conscious Android media downloader powered by <strong>yt-dlp</strong>, crafted with Jetpack Compose and modern Material Design 3.
</p>

---

## ⚡ What's New in FlowGrab v2.0.0

* **Instant App Cold Start:** Eliminated main-thread startup blocking and system splash lag (windowSplashScreenAnimationDuration = 0) for instantaneous launch.
* **Modern Edge-to-Edge UI:** Eliminated top dead-space across side navigation drawer, settings subpages, and downloads page.
* **Persistent View & Settings:** Grid / List view mode and app preferences persist reliably across app restarts via write-through MMKV state delegates.
* **Queue Quick Actions Menu:** 3-dot dropdown menu in the download page offering Resume All, Pause All, Clear Completed, and Clear Queue.
* **Download Timestamps:** Displays exact download date & time alongside file size on media cards and details drawer.
* **YouTube Anti-Bot & SABR Bypass:** Built-in client profile spoofing (player_client=android_creator,web,tv) eliminates web botguard challenges and bypasses 40-70 KB/s SABR speed throttles without requiring custom browser cookies.
* **High-Speed Batch Downloading:** Concurrent segment downloading tuned specifically for mobile flash memory via aria2c (--file-allocation=none, -j4 -x4 -s4 -k1M).
* **Zero Software Transcoding:** Stream-copy remuxing (-c:v copy -c:a copy) ensures files are cleanly packaged in seconds without pegging CPU cores or draining battery.
* **Self-Healing OTA Core:** In-place OTA yt-dlp extractor updater and in-app automated update checking.

---

## 📱 Architecture / Device Selection

Download the build tailored to your device from [Releases](https://github.com/HrshD1eux/flowgrab-android/releases/latest) or our [Official Website](https://hrshd1eux.github.io/flowgrab-android/):

| Build Variant | Target Architecture | Recommended Devices |
| :--- | :--- | :--- |
| **FlowGrab-universal** | All architectures | Any device (Safest choice if unsure) |
| **FlowGrab-arm64-v8a** | 64-bit ARM | Modern smartphones & tablets (Pixel, Galaxy S/A, OnePlus, Xiaomi) |
| **FlowGrab-armeabi-v7a** | 32-bit ARM | Older smartphones, budget Android Go devices |
| **FlowGrab-x86_64** | 64-bit x86 | Android emulators, ChromeOS Chromebooks, Android x86 PCs |

---

## 🚀 Core Features

- **1000+ Platforms Supported:** Direct video and audio extraction powered by [yt-dlp](https://github.com/yt-dlp/yt-dlp).
- **Format & Resolution Control:** Choose video resolutions (4K, 1440p, 1080p, 720p, etc.) or lossless/compressed audio formats (Opus, M4A, MP3 320kbps CBR, FLAC, WAV) with real-time size computation.
- **Batch & Playlist Queuing:** Download multiple URLs or entire playlists simultaneously with customizable concurrency.
- **Metadata & Chapter Embedding:** Automatically embed video thumbnails, chapter markers, and artist/track tags into media files.
- **Subtitle & SponsorBlock Integration:** Download embedded subtitles in multiple languages and automatically strip sponsored segments.
- **Custom Parameterized Templates:** Create and execute raw yt-dlp command lines with templated flags directly inside Android.

---

## 📄 Upstream & Credits

FlowGrab stands on the shoulders of giants. We express our deepest gratitude and credit to the upstream open-source projects that made this application possible:

* **[Seal](https://github.com/JunkFood02/Seal)**: FlowGrab originated as a modern fork and evolution of **Seal**, conceived and created by [JunkFood02](https://github.com/JunkFood02). We pay the utmost respect and credit to JunkFood02 and the contributors of Seal for pioneering the Material You interface and yt-dlp Android integration under GPLv3.
* **[yt-dlp](https://github.com/yt-dlp/yt-dlp)**: The industry-leading core media extractor powering video and audio scraping across 1,800+ websites.
* **[youtubedl-android](https://github.com/yausername/youtubedl-android)**: Android wrapper porting yt-dlp, FFmpeg, and aria2c native binaries to Android NDK.
* **[Read You](https://github.com/Ashinch/ReadYou)** & **[Music You](https://github.com/Kyant0/MusicYou)**: Material 3 UI design patterns and Monet palette inspirations.

---

## 📜 License & Usage Terms

FlowGrab is distributed under the **GNU General Public License v3.0 (GPLv3)** with the following community terms:

* ✅ **Do Whatever You Want (Personal & Educational):** You are completely free to study, fork, run, and modify the code.
* 🏷️ **Proper Credits Required:** Any redistribution, public display, or fork **must give full, visible credit** to both [FlowGrab](https://github.com/HrshD1eux/flowgrab-android) and [Seal](https://github.com/JunkFood02/Seal).
* 🚫 **Strictly Non-Commercial / No-Sale:** You **CANNOT sell the app**, distribute paid versions, gate features behind subscriptions or paywalls, or inject commercial ad networks. It is 100% free software for the community.

See the full [LICENSE](LICENSE) file for legal details.
