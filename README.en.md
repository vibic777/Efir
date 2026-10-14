# Efir

[Русский](README.md) · **English**

**License:** [PolyForm Noncommercial 1.0.0](LICENSE). Noncommercial use is allowed. Commercial use requires a separate agreement with vibic777.


**Efir** is an applied project at the MVP stage. It links a phone and a computer directly, so each side can exchange data, stay in sync, and control the other. The aim is a calm, everyday tool. This version is the working sketch used to find how that link should feel.

You can already try an **Android ↔ Windows** pair on a local network, with no internet account and no cloud: files, folder sync, clipboard, notifications, camera and audio, SMS, and calls. Either device can start or accept the connection.

The shared core is built with Kotlin Multiplatform and Compose Multiplatform. The Android app requires Android 8.0 or later; the Windows client supports Windows 10/11. Building requires JDK 17 and the Android SDK.

Run desktop tests with gradlew.bat :core:desktopTest, launch the Windows app with gradlew.bat :desktopApp:run, or build the Android debug app with gradlew.bat :androidApp:assembleDebug.

See the [Russian project guide](README.md) and [security review](docs/SECURITY_RU.md) for details.
