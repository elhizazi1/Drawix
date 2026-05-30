# 🎨 Drawix Pro - Screen Annotation & Drawing Tool

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Open Source](https://img.shields.io/badge/Open_Source-10B981?style=for-the-badge)
![LSPosed Downloades](https://github.com/Xposed-Modules-Repo/io.github.soclear.oneuix/releases)


**Drawix Pro** is a powerful, floating screen drawing and annotation platform for Android. Designed for professionals, educators, and content creators, it allows seamless on-screen interactions without interrupting your workflow.

**Offline First & Privacy Focused:** Works entirely locally. No data is collected, stored, or transmitted.

---

## ✨ Core Features

*   **Floating Service:** Unobtrusive drawing tools that float over any application, built with a clean, dark-mode-optimized glassmorphism interface.
*   **Advanced Annotation:** Includes a smart comet laser pointer and mosaic blur for hiding sensitive data.
*   **Rich Toolset:** Freehand Brush, Rectangle, Circle, Smart Arrow, Eraser, and Text input.
*   **Object Manipulation:** Select, move, scale, rotate, duplicate, and delete previously drawn shapes in real-time.
*   **High-Quality Export:** Ultra HD PNG/JPEG export directly to your device's gallery.

### ⚙️ Multi-Engine Core Architecture
Drawix Pro is built to survive aggressive background app killers and bypass system restrictions through a dynamic engine selection system:
1.  **Standard Engine:** Uses Android's `AccessibilityService` for universal compatibility.
2.  **Shizuku Engine:** Leverages `rikka.shizuku` API for silent, elevated ADB privileges to prevent background kills and execute fast screen captures.
3.  **Root Engine:** Utilizes `libsu` to execute raw system commands (`su -c`) for absolute control and instantaneous screenshots.
4.  **LSPosed Engine (Xposed):** A custom injected module that hooks `WindowManagerService` to bypass `FLAG_SECURE` (black screens in banking/protected apps) globally, and hooks `ActivityManagerService` for deep-system screen captures.

### 🛠️ Smart UI & Customization
*   **Community Translations (JSON):** A built-in `ContextWrapper` engine that allows exporting language templates and importing community-driven translations directly via JSON without rebuilding the app.
*   **Stealth Mode:** Ability to programmatically hide the app icon from the launcher for a cleaner device.
*   **Haptic Feedback:** Integrated native vibrator support for physical feedback during tool selection and captures.

---

## 🏗️ Technical Architecture

The project is structured with clean, modular Kotlin code:
*   **`DrawView.kt`**: The core rendering engine. Manages `DrawShape` objects, matrix transformations (scale/rotate), and canvas caching.
*   **`FloatingDrawService.kt`**: A Foreground Service managing `WindowManager` overlays, handling multi-touch gestures, and managing UI state transitions.
*   **`LSPosedModule.kt`**: Contains `IXposedHookLoadPackage` implementations to manipulate Android system frameworks.
*   **`TranslationEngine`**: A dynamic localization system intercepting string resources at runtime.

---

## 🛡️ Permissions Explained

Drawix Pro requests the following permissions contextually:
*   `SYSTEM_ALERT_WINDOW`: Required to display the floating drawing tools over other applications.
*   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Essential to prevent Android from aggressively killing the floating service during long presentations.
*   `BIND_ACCESSIBILITY_SERVICE`: Used by the Standard engine to capture the screen without requiring Root.
*   `READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE`: Required to save high-quality screenshots directly to your local gallery.

---

## Acknowledgments & Design Assets

The user interface of Drawix Pro relies on clean and professional iconography. The icons used throughout the application are sourced from the **[Iconsax](https://github.com/glenthemes/iconsax/tree/gh-pages)** library. To ensure optimal performance, crisp scaling across all screen densities, and memory efficiency on Android, all utilized icons were converted from their original formats into native Android Vector Drawable (XML) formats.

---

## 🚀 Getting Started & Building

This project uses Gradle and is fully compatible with **Android Studio** as well as on-device IDEs like **AndroidIDE**.

1. Clone the repository:
```bash
   git clone [https://github.com/elhizazi1/Drawix.git](https://github.com/elhizazi1/Drawix.git)
