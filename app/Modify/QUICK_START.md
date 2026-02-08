# 🚀 Quick Start Guide

**Camera Stream App by ABSTER**

Get up and running in 5 minutes!

---

## ⚡ Fast Track Setup

### Prerequisites

- ✅ **Android Studio** (Latest version)
- ✅ **Android device** (API 26+) or emulator
- ✅ **WiFi connection** for streaming

### 1️⃣ Import Project

```bash
# Open Android Studio
File → Open → Select camera-stream-app folder
```

### 2️⃣ Add Launcher Icons (1 minute)

**Easy Method:**

1. Right-click `app/res` folder
2. Select `New → Image Asset`
3. Choose `Launcher Icons (Adaptive and Legacy)`
4. Configure:
   - **Icon Type:** Launcher Icons
   - **Asset Type:** Clip Art
   - **Clip Art:** Select a camera icon
   - **Background Color:** `#667EEA`
5. Click `Next → Finish`

**Done!** Android Studio generates all icon sizes automatically.

### 3️⃣ Sync & Build

```bash
# In Android Studio:
1. Click "Sync Project with Gradle Files" (🐘 icon)
2. Wait for sync to complete
3. Build → Make Project (Ctrl+F9)
```

### 4️⃣ Run the App

```bash
1. Connect Android device (or start emulator)
2. Click Run (▶️) or press Shift+F10
3. Grant permissions when prompted
4. Tap "Start Streaming"
```

### 5️⃣ View the Stream

**On your phone:**
- Note the stream URL shown (e.g., `http://192.168.1.100:8080`)

**On any device (same WiFi):**
- Open browser
- Navigate to the URL
- Enjoy the live stream! 🎥

---

## 📱 First Use Checklist

- [ ] Import project in Android Studio
- [ ] Generate launcher icons
- [ ] Sync Gradle
- [ ] Run on device
- [ ] Grant camera permission
- [ ] Grant audio permission
- [ ] Grant notification permission
- [ ] Connect to WiFi
- [ ] Start streaming
- [ ] Open stream URL in browser

---

## 🎯 Testing the Stream

### Option 1: Browser (Easiest)

```
1. Open Chrome/Safari on any device
2. Go to http://<your-ip>:8080
3. Video plays automatically
```

### Option 2: VLC Player

```
1. Open VLC
2. Media → Open Network Stream
3. Enter: http://<your-ip>:8080/stream.m3u8
4. Play
```

### Option 3: Control Panel

```
1. Go to http://<your-ip>:8080/control
2. View stats and controls
3. Switch camera
4. Change quality
```

---

## ⚙️ Configuration (Optional)

### Change Server Port

Edit `StreamServer.kt`:
```kotlin
companion object {
    private const val PORT = 8080  // Change to your port
}
```

### Change Video Quality

Edit `HLSEncoder.kt`:
```kotlin
private var bitrate = 2000000      // 2 Mbps
private var frameRate = 30         // 30 FPS
```

### Change Segment Duration

Edit `HLSEncoder.kt`:
```kotlin
private val segmentDuration = 4    // 4 seconds
private val maxSegments = 10       // Keep 10 segments
```

---

## 🐛 Common Issues & Fixes

### Issue: "Can't find IP address"

**Fix:**
```
1. Connect to WiFi (not mobile data)
2. Check WiFi is enabled
3. Restart app
```

### Issue: "Camera permission denied"

**Fix:**
```
1. Go to Settings → Apps → Camera Stream
2. Permissions → Enable Camera & Microphone
3. Restart app
```

### Issue: "Stream not loading in browser"

**Fix:**
```
1. Wait 5-10 seconds for segments to generate
2. Refresh browser
3. Check both devices on same WiFi
4. Try http (not https)
```

### Issue: "Black screen in stream"

**Fix:**
```
1. Wait for first segment to generate
2. Refresh browser page
3. Check camera is not used by another app
4. Restart streaming
```

---

## 📊 Recommended Settings

### For Best Quality

- **Resolution:** High (1080p)
- **Device:** Flagship phone (4GB+ RAM)
- **Network:** Strong WiFi signal
- **Lighting:** Good room lighting

### For Best Performance

- **Resolution:** Medium (720p)
- **Device:** Any modern Android
- **Network:** Any WiFi connection
- **CPU:** Will work on any device

### For Low-End Devices

- **Resolution:** Low (480p)
- **Bitrate:** 1 Mbps
- **Segments:** Reduce to 5
- **FPS:** Lower to 24

---

## 🎨 Customization Ideas

### Easy Customizations

1. **Change app colors:**
   - Edit `res/values/colors.xml`

2. **Change app name:**
   - Edit `res/values/strings.xml`

3. **Add your branding:**
   - Replace launcher icons
   - Update theme colors
   - Modify web UI HTML

### Advanced Customizations

1. **Add recording feature**
2. **Implement authentication**
3. **Add overlay graphics**
4. **Support multiple cameras**
5. **Add cloud storage**

---

## 📱 Recommended Devices

### ✅ Tested On

- Samsung Galaxy S21+
- Google Pixel 6
- OnePlus 9 Pro
- Xiaomi Mi 11

### 💪 Best Performance

- Snapdragon 888 or better
- 6GB+ RAM
- Android 11+

### ⚡ Minimum Requirements

- Snapdragon 660 or equivalent
- 3GB RAM
- Android 8.0+

---

## 🌐 Network Requirements

### WiFi

- **2.4GHz or 5GHz** (5GHz preferred)
- **Router with good coverage**
- **No MAC filtering** on streamer device

### Ports

- **Port 8080** must be accessible
- **No firewall blocking** local traffic
- **UPnP enabled** (optional, for external access)

---

## 🎓 Learning Resources

### Concepts Used

- **HLS Streaming:** HTTP Live Streaming protocol
- **CameraX:** Modern camera API
- **MediaCodec:** Hardware video encoding
- **Ktor:** Kotlin HTTP server
- **Jetpack Compose:** Modern Android UI

### Official Docs

- [CameraX Guide](https://developer.android.com/training/camerax)
- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
- [Ktor Server](https://ktor.io/docs/server.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [HLS Specification](https://tools.ietf.org/html/rfc8216)

---

## 💡 Pro Tips

1. **Test on real device first** - Emulator camera is limited
2. **Use 5GHz WiFi** for better bandwidth
3. **Keep devices close to router** for stable stream
4. **Close other apps** to free up camera
5. **Check battery settings** - Don't restrict app
6. **Enable Developer Options** for better debugging

---

## 🎯 Next Steps

After getting it running:

1. ✅ Test all quality settings
2. ✅ Try camera switching
3. ✅ Test on multiple browsers
4. ✅ Measure network performance
5. ✅ Customize the UI
6. ✅ Add your own features

---

## 📞 Need Help?

### Debugging Steps

1. Check Android Studio Logcat
2. Look for error messages
3. Verify permissions granted
4. Check network connectivity
5. Review troubleshooting section in README

### Log Tags

Monitor these in Logcat:
- `StreamServer`
- `CameraManager`
- `HLSEncoder`
- `StreamingService`

---

## ✨ You're Ready!

Your camera streaming app is now set up and ready to go!

**Happy Streaming! 📹**

---

**Created with ❤️ by ABSTER**

*Need more details? Check out the full README.md*
