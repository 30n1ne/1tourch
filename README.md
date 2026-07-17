# 1 torch — Flashlight Controller ⚡

An Android mobile application that provides advanced flashlight control, including the ability to adjust the brightness level of the device's camera flash unit, mirroring the variable brightness sliders found on Samsung (One UI) and Apple (iOS) devices. 

Designed with an **Apple Vision Pro / iOS 18 fluid glassmorphism theme**.

---

## 🌟 Features

1. **Multi-Level Brightness Control (Android 13+ / API 33+)**
   - Features a 1 to 5 level discrete snapping slider.
   - Linearly maps UI levels to device hardware strength levels.
   - Uses native Camera2 API's `turnOnTorchWithStrengthLevel()`.

2. **Home Screen Widget**
   - Renders a custom horizontal glassmorphic App Widget.
   - Allows toggling the flashlight and adjusting brightness directly from the home screen.

3. **Quick Settings Tile**
   - Adds a custom tile to the Android pull-down notification panel.
   - Quick status sync (ON/OFF) and rapid toggle.

4. **Fluid Glassmorphism Theme**
   - Premium dark-mode interface with subtle ambient glows and spring animations.
   - Interactive haptic feedbacks during state changes and level snapping.
   - Automated fallbacks for devices/versions that do not support variable brightness.

---

## 📱 Requirements

- **Minimum Android SDK**: API 26 (Android 8.0)
- **Target / Compile SDK**: API 34 (Android 14)
- **Variable Brightness Requirements**: Android 13+ (API 33+) and a compatible camera sensor containing multi-level flash hardware (e.g. Google Pixel, Samsung Galaxy S-series, etc. running Android 13+).
- *Standard binary fallback (ON/OFF) will be used on older Android versions or unsupported devices.*

---

## 🛠️ How to Build the App APK

1. Open the project in **Android Studio**.
2. **Build a Debug APK**:
   - Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**
   - Or run the Gradle wrapper command:
     ```bash
     # Windows
     .\gradlew.bat assembleDebug
     
     # macOS/Linux
     ./gradlew assembleDebug
     ```
   - The generated debug APK will be located at:
     `app/build/outputs/apk/debug/app-debug.apk`

3. **Build a signed Release APK**:
   - Select **Build > Generate Signed Bundle / APK...**
   - Choose **APK**, create or select a keystore, set passwords, and choose `release` build variant.
   - The signed release APK will be generated in `app/release/`.

---

## 📦 How to Download & Run the APK

You can download the pre-compiled installable APK directly from the repository's release section or folder:
- **Direct Repository File**: [1torch-debug.apk](file:///d:/andstudio/1tourch/release/1torch-debug.apk)
- **GitHub Release (Recommended)**: Go to the "Releases" tab on your GitHub repository page and download the asset associated with the latest tag.

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
