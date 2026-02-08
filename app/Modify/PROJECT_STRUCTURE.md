# 📁 Project Structure

**Camera Stream App by ABSTER**

Complete file organization and structure overview.

---

## 🌳 Directory Tree

```
camera-stream-app/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/stream/camera/
│   │   │   │   ├── MainActivity.kt                    # Main UI Activity
│   │   │   │   │
│   │   │   │   ├── service/
│   │   │   │   │   └── StreamingService.kt           # Foreground Service
│   │   │   │   │
│   │   │   │   ├── server/
│   │   │   │   │   └── StreamServer.kt               # Ktor HTTP Server
│   │   │   │   │
│   │   │   │   ├── camera/
│   │   │   │   │   └── CameraManager.kt              # CameraX Manager
│   │   │   │   │
│   │   │   │   ├── encoder/
│   │   │   │   │   └── HLSEncoder.kt                 # HLS Video Encoder
│   │   │   │   │
│   │   │   │   ├── utils/
│   │   │   │   │   └── NetworkUtils.kt               # Network Utilities
│   │   │   │   │
│   │   │   │   └── ui/theme/
│   │   │   │       ├── Theme.kt                      # Material Theme
│   │   │   │       └── Type.kt                       # Typography
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   │   └── ic_notification.xml           # Notification Icon
│   │   │   │   │
│   │   │   │   ├── mipmap-*/                         # Launcher Icons
│   │   │   │   │   ├── ic_launcher.png
│   │   │   │   │   └── ic_launcher_round.png
│   │   │   │   │
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml                    # Color Resources
│   │   │   │   │   ├── strings.xml                   # String Resources
│   │   │   │   │   └── themes.xml                    # Theme Definitions
│   │   │   │   │
│   │   │   │   └── xml/
│   │   │   │       ├── backup_rules.xml              # Backup Rules
│   │   │   │       └── data_extraction_rules.xml     # Data Extraction
│   │   │   │
│   │   │   └── AndroidManifest.xml                   # App Manifest
│   │   │
│   │   └── test/                                      # Unit Tests
│   │
│   ├── build.gradle.kts                              # App Build Config
│   └── proguard-rules.pro                            # ProGuard Rules
│
├── build.gradle.kts                                  # Project Build Config
├── settings.gradle.kts                               # Project Settings
├── gradle.properties                                 # Gradle Properties
│
├── README.md                                         # Main Documentation
├── VECTOR_ICONS_GUIDE.md                            # Icon Guide
└── PROJECT_STRUCTURE.md                             # This File
```

---

## 📄 File Descriptions

### Root Level

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Project-level Gradle build configuration |
| `settings.gradle.kts` | Project settings and module configuration |
| `gradle.properties` | Gradle properties and JVM settings |
| `README.md` | Complete project documentation |
| `VECTOR_ICONS_GUIDE.md` | Icon reference and creation guide |
| `PROJECT_STRUCTURE.md` | This file - project organization |

### App Module

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | App-level build configuration with dependencies |
| `app/proguard-rules.pro` | Code obfuscation rules for release builds |

### Source Code (Kotlin)

#### Main Package (`com.stream.camera`)

**MainActivity.kt**
- Entry point of the application
- Jetpack Compose UI implementation
- Permission handling
- Stream control logic
- **Lines:** ~300
- **Key Features:** Material Design 3, state management, error handling

#### Service Package (`service/`)

**StreamingService.kt**
- Foreground service for continuous streaming
- Notification management
- Service lifecycle handling
- **Lines:** ~100
- **Key Features:** Background operation, notification channel

#### Server Package (`server/`)

**StreamServer.kt**
- Ktor HTTP server implementation
- HLS streaming endpoints
- Web UI hosting
- REST API for stream control
- **Lines:** ~450
- **Key Features:** 
  - HLS playlist serving
  - Segment delivery
  - Camera switching API
  - Quality control API
  - Embedded HTML control panel

#### Camera Package (`camera/`)

**CameraManager.kt**
- CameraX integration
- Camera lifecycle management
- Front/back camera switching
- Resolution control
- Frame capture and delivery
- **Lines:** ~200
- **Key Features:** Real-time frame processing, camera state management

#### Encoder Package (`encoder/`)

**HLSEncoder.kt**
- MediaCodec video encoding
- HLS segment creation
- M3U8 playlist generation
- Bitrate and quality management
- **Lines:** ~250
- **Key Features:** 
  - H.264 encoding
  - Segment management
  - Adaptive bitrate support
  - Metadata handling

#### Utils Package (`utils/`)

**NetworkUtils.kt**
- IP address detection
- Network state checking
- WiFi connectivity
- **Lines:** ~100
- **Key Features:** Multi-method IP detection, connectivity checks

#### UI Theme Package (`ui/theme/`)

**Theme.kt**
- Material Design 3 theming
- Color schemes (light/dark)
- Status bar styling
- **Lines:** ~60

**Type.kt**
- Typography definitions
- Text styles
- Font configurations
- **Lines:** ~30

### Resources

#### Drawable (`res/drawable/`)

| File | Type | Purpose |
|------|------|---------|
| `ic_notification.xml` | Vector | Foreground service notification icon |

#### Mipmap (`res/mipmap-*/`)

Launcher icons at various densities:
- **mdpi** - 48x48px
- **hdpi** - 72x72px
- **xhdpi** - 96x96px
- **xxhdpi** - 144x144px
- **xxxhdpi** - 192x192px

#### Values (`res/values/`)

**colors.xml**
- Color palette definitions
- Brand colors
- State colors (success, error)

**strings.xml**
- All text strings
- Localization ready
- Error messages

**themes.xml**
- App theme definition
- Status bar configuration

#### XML (`res/xml/`)

**backup_rules.xml**
- Backup and restore configuration
- Privacy settings

**data_extraction_rules.xml**
- Cloud backup rules
- Device transfer settings

### Manifest

**AndroidManifest.xml**
- App configuration
- Permissions declarations
- Service registration
- Activity definitions
- Features and hardware requirements

---

## 📊 Code Statistics

### Total Lines of Code

| Component | Kotlin Lines | XML Lines | Total |
|-----------|-------------|-----------|-------|
| Main Activity | ~300 | - | ~300 |
| Streaming Service | ~100 | - | ~100 |
| Stream Server | ~450 | - | ~450 |
| Camera Manager | ~200 | - | ~200 |
| HLS Encoder | ~250 | - | ~250 |
| Network Utils | ~100 | - | ~100 |
| UI Theme | ~90 | - | ~90 |
| Resources | - | ~150 | ~150 |
| Build Files | ~150 | - | ~150 |
| **Total** | **~1,640** | **~150** | **~1,790** |

### File Count

- **Kotlin files:** 8
- **XML files:** 9
- **Gradle files:** 4
- **Documentation:** 3
- **Total:** 24 files

---

## 🔗 Dependencies

### Core Android

```kotlin
androidx.core:core-ktx:1.12.0
androidx.lifecycle:lifecycle-runtime-ktx:2.6.2
androidx.activity:activity-compose:1.8.1
```

### Jetpack Compose

```kotlin
androidx.compose:compose-bom:2023.10.01
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended
```

### CameraX

```kotlin
androidx.camera:camera-core:1.3.1
androidx.camera:camera-camera2:1.3.1
androidx.camera:camera-lifecycle:1.3.1
androidx.camera:camera-video:1.3.1
androidx.camera:camera-view:1.3.1
```

### Networking (Ktor)

```kotlin
io.ktor:ktor-server-core:2.3.7
io.ktor:ktor-server-netty:2.3.7
io.ktor:ktor-server-cors:2.3.7
io.ktor:ktor-server-websockets:2.3.7
io.ktor:ktor-serialization-kotlinx-json:2.3.7
```

### Coroutines

```kotlin
kotlinx-coroutines-android:1.7.3
kotlinx-coroutines-core:1.7.3
```

### Utilities

```kotlin
kotlinx-serialization-json:1.6.2
com.google.accompanist:accompanist-permissions:0.32.0
```

---

## 🏗️ Build Configuration

### Compile SDK

- **Target:** API 34 (Android 14)
- **Minimum:** API 26 (Android 8.0)
- **Compile SDK:** 34

### Build Features

- Jetpack Compose ✅
- View Binding ✅
- Vector Drawables ✅

### Kotlin

- **Version:** 1.9.20
- **JVM Target:** Java 17
- **Compose Compiler:** 1.5.4

---

## 🎯 Architecture Patterns

### MVVM-ish Architecture

- **View:** Jetpack Compose UI (MainActivity.kt)
- **Logic:** Service layer (StreamingService.kt)
- **Data:** Camera and Network managers

### Key Patterns Used

1. **Service Pattern** - Background streaming
2. **Repository Pattern** - Camera and network abstraction
3. **Observer Pattern** - State management with Compose
4. **Singleton Pattern** - NetworkUtils
5. **Factory Pattern** - MediaCodec initialization

---

## 🔐 Permissions

### Required Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Runtime Permissions

Requested in MainActivity:
- Camera
- Record Audio
- Post Notifications (Android 13+)

---

## 📦 Output Files

### Debug APK

```
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```
app/build/outputs/apk/release/app-release.apk
```

### AAB (App Bundle)

```
app/build/outputs/bundle/release/app-release.aab
```

---

## 🧪 Testing Structure

### Unit Tests

```
app/src/test/java/com/stream/camera/
```

### Instrumented Tests

```
app/src/androidTest/java/com/stream/camera/
```

---

## 📝 Documentation

| Document | Purpose |
|----------|---------|
| `README.md` | Complete usage and setup guide |
| `VECTOR_ICONS_GUIDE.md` | Icon reference and creation |
| `PROJECT_STRUCTURE.md` | This file - project organization |

---

## 🚀 Quick Navigation

### To Find...

| What | Where |
|------|-------|
| UI Code | `MainActivity.kt` |
| Server Logic | `server/StreamServer.kt` |
| Camera Code | `camera/CameraManager.kt` |
| Encoding Logic | `encoder/HLSEncoder.kt` |
| Network Utils | `utils/NetworkUtils.kt` |
| Theme Colors | `res/values/colors.xml` |
| String Resources | `res/values/strings.xml` |
| Dependencies | `app/build.gradle.kts` |
| Permissions | `AndroidManifest.xml` |

---

## 📈 Scalability Considerations

### Easy to Extend

- Add new quality presets in `HLSEncoder.kt`
- Add new API endpoints in `StreamServer.kt`
- Customize UI theme in `ui/theme/`
- Add new utilities in `utils/`

### Potential Enhancements

- [ ] Recording functionality
- [ ] RTMP streaming support
- [ ] Multiple camera support
- [ ] Audio mixing
- [ ] Overlay graphics
- [ ] Cloud storage integration
- [ ] Analytics integration
- [ ] User authentication

---

**Created by ABSTER** 📁✨

*Well-organized code is maintainable code!*
