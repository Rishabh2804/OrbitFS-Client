# OrbitFS Client — Installation & Build Guide

## 🛠️ Environment Prerequisites

Before building OrbitFS Client, ensure your system meets the following requirements:

- **JDK 21** or higher (`java -version`)
- **Android SDK** (API Levels 26 to 35)
- **Gradle 8.x** (Gradle wrapper included)
- **Android Studio 2026.1+** (or IntelliJ IDEA Ultimate for KMP)

---

## 📱 Building for Android

### 1. Assemble Debug APK
To build the standalone debug APK:
```bash
./gradlew :composeApp:assembleDebug
```
- **Output APK location:** `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### 2. Install on Device / Emulator
Connect an Android device via USB (or start an Android Emulator) and run:
```bash
./gradlew :composeApp:installDebug
```

---

## 💻 Building for Desktop (macOS, Windows, Linux)

### 1. Run Desktop App directly
To start the Compose Multiplatform Desktop app:
```bash
./gradlew :composeApp:run
```

### 2. Package Executable / JAR
To generate the standalone executable package:
```bash
./gradlew :composeApp:desktopJar
```
- **Output location:** `composeApp/build/libs/composeApp-desktop.jar`

### 3. Native Installers (DMG / DEB / MSI)
To generate native installer bundles for your host operating system:
```bash
./gradlew :composeApp:package
```
- **macOS:** `.dmg`
- **Linux:** `.deb`
- **Windows:** `.msi`
