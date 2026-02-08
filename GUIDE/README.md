# 📹 Camera Stream App - HLS Live Streaming

**Created by ABSTER**

A professional Android application for live camera streaming with HLS encoding, real-time quality control, and a beautiful Material Design 3 UI.

## ✨ Features

- 🎥 **Live HLS Streaming** - Broadcast camera feed over local network
- 🌐 **Web Interface** - Beautiful control panel accessible from any browser
- 📱 **Camera Switching** - Toggle between front and back cameras
- 🎚️ **Quality Control** - Adjust resolution and bitrate on-the-fly (480p, 720p, 1080p)
- 📊 **Real-time Stats** - Monitor FPS, bitrate, and segment count
- 🔄 **Auto Segmentation** - HLS segments with proper metadata
- 🎨 **Material Design 3** - Modern, polished UI with Jetpack Compose
- ⚡ **Foreground Service** - Reliable streaming in background
- 🛡️ **Error Handling** - Comprehensive error management throughout

## 🏗️ Architecture

```
com.stream.camera/
├── MainActivity.kt           # Main UI with Jetpack Compose
├── service/
│   └── StreamingService.kt  # Foreground service for streaming
├── server/
│   └── StreamServer.kt      # Ktor HTTP server with HLS endpoints
├── camera/
│   └── CameraManager.kt     # CameraX integration
├── encoder/
│   └── HLSEncoder.kt        # MediaCodec HLS encoding
├── utils/
│   └── NetworkUtils.kt      # Network utilities
└── ui/theme/
    ├── Theme.kt             # Material Design 3 theme
    └── Type.kt              # Typography definitions
```

## 📋 Requirements

- **Android 8.0 (API 26)** or higher
- **Camera** permission
- **WiFi** connection (for local streaming)
- **4GB RAM** recommended for smooth 1080p streaming

## 🚀 Setup & Installation

### 1. Clone the Project

```bash
git clone <repository-url>
cd camera-stream-app
```

### 2. Add Required Vector Icons

Create these drawable resources in `app/src/main/res/drawable/`:

#### ic_notification.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11L17,10.5z"/>
</vector>
```

This icon is used for the foreground service notification.

### 3. Configure Gradle

The project uses:
- **Kotlin 1.9.20**
- **Android Gradle Plugin 8.2.0**
- **Jetpack Compose BOM 2023.10.01**
- **CameraX 1.3.1**
- **Ktor 2.3.7**

### 4. Sync and Build

```bash
./gradlew build
```

### 5. Run the App

```bash
./gradlew installDebug
```

Or use Android Studio:
1. Open the project in Android Studio
2. Connect an Android device or start an emulator
3. Click Run (Shift + F10)

## 📱 Usage

### Starting a Stream

1. **Launch the app** on your Android device
2. **Grant permissions** when prompted (Camera, Audio, Notifications)
3. **Connect to WiFi** - The app will display your local IP
4. **Tap "Start Streaming"** - The stream begins immediately
5. **Note the Stream URL** - Displayed on the screen (e.g., `http://192.168.1.100:8080`)

### Viewing the Stream

**On the Same Network:**

1. Open any browser on a device connected to the same WiFi
2. Navigate to the stream URL (e.g., `http://192.168.1.100:8080`)
3. The video player will load automatically with HLS.js

**Available Endpoints:**

- `http://<IP>:8080/` - Homepage with video player
- `http://<IP>:8080/control` - Control panel
- `http://<IP>:8080/stream.m3u8` - HLS playlist (for VLC, etc.)
- `http://<IP>:8080/api/status` - JSON status endpoint

### Control Panel Features

Access the control panel at `http://<IP>:8080/control`:

- **📊 Real-time Stats** - View current streaming status
- **📷 Switch Camera** - Toggle front/back camera
- **⚙️ Quality Control** - Change resolution:
  - Low: 640x480 @ 1Mbps
  - Medium: 1280x720 @ 2Mbps
  - High: 1920x1080 @ 4Mbps

### API Endpoints

#### GET `/api/status`
Returns current stream status:
```json
{
  "isStreaming": true,
  "currentCamera": "back",
  "resolution": "1920x1080",
  "bitrate": 2000,
  "fps": 30,
  "segmentCount": 8
}
```

#### POST `/api/camera/switch`
Switches between front and back cameras.

#### POST `/api/quality/{level}`
Changes stream quality. Levels: `low`, `medium`, `high`

## 🎨 UI Components

### Material Design 3 Icons Used

The app uses Material Icons Extended from Jetpack Compose:

```kotlin
// Primary icons used in the app:
Icons.Default.Videocam        // Main camera icon
Icons.Default.PlayArrow       // Start streaming
Icons.Default.Stop            // Stop streaming
Icons.Default.Link            // Stream URL
Icons.Default.VideoLibrary    // HLS playlist
Icons.Default.Settings        // Settings/quality
Icons.Default.OpenInBrowser   // Open control panel
```

**No additional vector assets needed** - All icons come from the Material Icons library included in Jetpack Compose.

## 🔧 Configuration

### Changing Stream Settings

Edit `StreamServer.kt` to modify:

```kotlin
companion object {
    private const val PORT = 8080        // Server port
}
```

Edit `HLSEncoder.kt` to modify:

```kotlin
private var bitrate = 2000000           // 2 Mbps default
private var frameRate = 30              // 30 FPS
private val segmentDuration = 4         // 4 second segments
private val maxSegments = 10            // Keep 10 segments
```

### Network Configuration

The app automatically detects your local IP address. Ensure:
- Device is connected to WiFi
- Firewall allows connections on port 8080
- Router doesn't block local network traffic

## 🐛 Troubleshooting

### Stream Not Starting
- Check camera permissions are granted
- Ensure WiFi is connected
- Verify no other app is using the camera
- Check Android version is 8.0+

### Can't Access Stream URL
- Verify device and client are on same WiFi network
- Check firewall settings on both devices
- Try pinging the IP address
- Ensure port 8080 is not blocked

### Poor Stream Quality
- Reduce quality setting to Medium or Low
- Check WiFi signal strength
- Close other network-intensive apps
- Verify device has sufficient RAM

### Black Screen on Web Player
- Wait a few seconds for segments to generate
- Refresh the browser page
- Check browser console for HLS errors
- Try a different browser (Chrome recommended)

## 📚 Technologies Used

- **Jetpack Compose** - Modern UI toolkit
- **CameraX** - Camera integration
- **MediaCodec** - Video encoding
- **Ktor** - HTTP server framework
- **Kotlin Coroutines** - Asynchronous programming
- **Material Design 3** - UI design system
- **HLS (HTTP Live Streaming)** - Streaming protocol

## 🎯 Performance

**Recommended Specs:**
- 4GB+ RAM for 1080p streaming
- Dual-core 1.5GHz+ processor
- Android 10+ for best results

**Expected Performance:**
- **Low (480p)**: 1 Mbps, smooth on any device
- **Medium (720p)**: 2 Mbps, recommended for most devices
- **High (1080p)**: 4 Mbps, flagship devices only

## 📄 License

This project is created by **ABSTER** for educational purposes.

## 🤝 Contributing

Feel free to:
- Report bugs
- Suggest features
- Submit pull requests
- Improve documentation

## 📞 Support

For issues or questions:
1. Check the troubleshooting section
2. Review the code comments
3. Open an issue on GitHub

---

**Built with ❤️ by ABSTER**

*Happy Streaming! 📹✨*
