========================================================================
                      1 torch - Flashlight Controller
========================================================================

An Android mobile application that provides advanced flashlight control, including the ability to adjust the brightness level of the device's camera flash unit, mirroring the variable brightness sliders found on Samsung (One UI) and Apple (iOS) devices.

Designed with an Apple Vision Pro / iOS 18 fluid glassmorphism aesthetic.

------------------------------------------------------------------------
Features
------------------------------------------------------------------------
1. Multi-Level Brightness Control (Android 13+ / API 33+)
   - Features a 1 to 5 level discrete snapping slider.
   - Linearly maps UI levels to device hardware strength levels.
   - Uses native Camera2 API's `turnOnTorchWithStrengthLevel()`.

2. Home Screen Widget
   - Renders a custom horizontal glassmorphic App Widget.
   - Allows toggling the flashlight and adjusting brightness directly from the home screen.

3. Quick Settings Tile
   - Adds a custom tile to the Android pull-down notification panel.
   - Quick status sync (ON/OFF) and rapid toggle.

4. Fluid Glassmorphism Theme
   - Premium dark-mode interface with subtle ambient glows and spring animations.
   - Interactive haptic feedbacks during state changes and level snapping.
   - Automated fallbacks for devices/versions that do not support variable brightness.

------------------------------------------------------------------------
Requirements
------------------------------------------------------------------------
- Minimum Android SDK: API 26 (Android 8.0)
- Target / Compile SDK: API 34 (Android 14)
- Variable Brightness Requirements: Android 13+ (API 33+) and a compatible camera sensor containing multi-level flash hardware. (e.g. Google Pixel, Samsung Galaxy S-series, etc. running Android 13+).
- Standard binary fallback will be used on older Android versions or unsupported devices.

------------------------------------------------------------------------
How to Build the App APK
------------------------------------------------------------------------
1. Open the project in Android Studio.
2. Build a Debug APK:
   - Select Build > Build Bundle(s) / APK(s) > Build APK(s)
   - Or run the Gradle wrapper command:
     .\gradlew.bat assembleDebug (Windows)
     ./gradlew assembleDebug (macOS/Linux)
   - The generated debug APK will be located at:
     app/build/outputs/apk/debug/app-debug.apk

3. Build a signed Release APK:
   - Select Build > Generate Signed Bundle / APK...
   - Choose APK, create or select a keystore, set passwords, and choose "release" build variant.
   - The signed release APK will be generated in:
     app/release/

------------------------------------------------------------------------
How to Host and Add the APK to GitHub (Recommended)
------------------------------------------------------------------------
Do not upload the APK file directly into the Git repository history. Big binary files slow down cloning and blow up repository size. Instead, use GitHub Releases:

1. Create a public repository on GitHub and push this project:
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin <your-github-repo-url>
   git branch -M main
   git push -u origin main

2. Create a GitHub Release:
   - Go to your repository page on GitHub.com.
   - On the right sidebar, click on "Releases" (or "Create a new release").
   - Click "Draft a new release".
   - Create a version tag (e.g., v1.0.0).
   - Write a title (e.g., "1 torch v1.0.0 - Initial Release").
   - Write a description of what the version includes.

3. Upload the APK file:
   - In the release editor, locate the box labeled "Attach binaries by dropping them here or selecting them".
   - Drag and drop your built APK file (e.g., `app-debug.apk` or your signed release APK).
   - Click "Publish release".

Now, users can download the installable APK directly from the "Releases" section of your GitHub repository.
