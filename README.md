# ⚡ AutoClick Pro

An Android automation app that performs auto-clicks based on signals from REST API, WebSocket, Telegram Bot, or Firebase.

## 📱 Features
- Floating overlay panel (always on top)
- Signal-based auto clicking (BIG / SMALL / NUMBER)
- Multiple data sources: REST API, WebSocket, Telegram Bot, Firebase, Local Intent
- Click profiles with saved positions
- Boot auto-start
- Foreground service (stays alive in background)

## 🏗️ Project Structure
```
AutoClickPro/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/autoclicker/pro/
│       │   ├── AutoClickApplication.kt
│       │   ├── model/Models.kt
│       │   ├── service/
│       │   │   ├── AutoClickAccessibilityService.kt
│       │   │   ├── AutomationForegroundService.kt
│       │   │   └── DataReceiverService.kt
│       │   ├── overlay/FloatingOverlayService.kt
│       │   ├── receiver/Receivers.kt
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ProfileActivity.kt
│       │   │   ├── SettingsActivity.kt
│       │   │   └── CoordinatePickerActivity.kt
│       │   └── utils/
│       │       ├── EventBus.kt
│       │       └── AppPreferences.kt
│       └── res/
│           ├── layout/ (5 XML files)
│           ├── values/ (colors, strings, themes)
│           ├── xml/accessibility_service_config.xml
│           └── drawable/ (launcher icons)
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## ⚙️ Requirements
- Android Studio (recommended) or AndroidIDE
- Android SDK 34
- Kotlin 1.9.22
- Gradle 8.2
- Device: Android 7.0+ (API 24+)

## 🚀 Build করার নিয়ম

### Android Studio দিয়ে (সবচেয়ে সহজ):
1. Android Studio খুলুন
2. `File → Open` → এই folder select করুন
3. Gradle sync হতে দিন
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`
5. APK: `app/build/outputs/apk/debug/app-debug.apk`

### AndroidIDE (Mobile এ):
1. [AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE/releases) install করুন
2. Setup wizard থেকে JDK 17 + SDK install করুন
3. Project folder open করুন
4. Build → Assemble Debug

### Termux (Advanced):
```bash
pkg install openjdk-17
./gradlew assembleDebug
```

## 📡 Signal Format
```json
{"signal": "BIG", "count": 10, "delay": 1000}
{"signal": "SMALL", "count": 5, "delay": 500}
```
Plain text: `BIG 10 1000` or `SMALL:5:500`

## 🔧 First Time Setup
1. **Accessibility Service:** Settings → Accessibility → Auto Click Pro → Enable
2. **Overlay:** Settings → Apps → Auto Click Pro → Display over other apps → Allow
3. **Battery:** Settings → Battery → Auto Click Pro → Don't optimize
4. **Configure Profile:** App → Positions → Pick each click position

## ❓ Troubleshooting
| সমস্যা | সমাধান |
|--------|---------|
| Click কাজ করছে না | Accessibility Service enable করুন |
| Overlay দেখাচ্ছে না | Overlay permission দিন |
| Background এ বন্ধ হয় | Battery optimization off করুন |
| Signal আসছে না | API URL চেক করুন |

---
**minSdk:** 24 (Android 7.0) | **targetSdk:** 34 | **Kotlin:** 1.9.22
