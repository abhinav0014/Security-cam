# 🔧 Troubleshooting Guide

**Camera Stream App by ABSTER**

Complete guide to fix common issues

---

## 🚨 Common Issues & Solutions

### Issue 1: App Crashes on Start Stream

**Error:** `java.lang.IllegalStateException: Context must be a LifecycleOwner`

**Solution:** ✅ Already fixed in latest version!
- CameraManager now implements its own LifecycleOwner
- No dependency on Activity/Service being LifecycleOwner

**What was changed:**
- Added `LifecycleRegistry` to CameraManager
- Made CameraManager self-contained lifecycle-aware

---

### Issue 2: Empty HLS Segments / Video Not Playing

**Current Status:** This is a known limitation due to Android's MediaCodec complexity.

**Why this happens:**
1. Raw camera frames need proper H.264 encoding
2. H.264 data must be muxed into MPEG-TS format
3. MPEG-TS segments need proper headers and timing

**Temporary Solutions:**

#### Option A: Use Test Pattern (Quick Test)
The app creates placeholder segments to test the streaming infrastructure. Check if:
- Playlist is being generated (`/stream.m3u8`)
- Segments are being created (`segment_0.ts`, `segment_1.ts`, etc.)
- Web player can load the playlist

#### Option B: Implement Proper Video Encoding

Add this dependency to `app/build.gradle.kts`:
```kotlin
implementation("com.arthenica:ffmpeg-kit-full:5.1")
```

Then use FFmpeg to properly encode:
```kotlin
// In HLSEncoder.kt
import com.arthenica.ffmpegkit.FFmpegKit

fun encodeWithFFmpeg(inputFile: File, outputFile: File) {
    val command = "-i ${inputFile.path} -c:v libx264 -f mpegts ${outputFile.path}"
    FFmpegKit.execute(command)
}
```

#### Option C: Use CameraX VideoCapture

The app includes `VideoRecorder.kt` which uses CameraX's built-in video recording. To use it:

1. In `StreamServer.kt`, replace `HLSEncoder` with `VideoRecorder`
2. VideoRecorder creates proper MP4 segments
3. Convert MP4 to TS using MediaMuxer or FFmpeg

---

### Issue 3: "Stream not loading" in Browser

**Checklist:**

1. **Check if streaming is active:**
   ```
   Open http://<phone-ip>:8080/api/status
   Should show: "isStreaming": true
   ```

2. **Check if playlist exists:**
   ```
   Open http://<phone-ip>:8080/stream.m3u8
   Should show M3U8 playlist content
   ```

3. **Check if segments exist:**
   ```
   Open http://<phone-ip>:8080/segments/segment_0.ts
   Should download or show file
   ```

4. **Check browser console:**
   - Open DevTools (F12)
   - Look for network errors
   - Check HLS.js error messages

**Common Fixes:**

- **403/404 errors:** Check file permissions in app cache directory
- **CORS errors:** Already handled by Ktor CORS plugin
- **Manifest parse errors:** Playlist format might be incorrect

---

### Issue 4: Controls Not Working

**Camera Switch button doesn't work:**

**Check:**
1. Logcat for errors: `adb logcat | grep CameraManager`
2. Front camera availability: `hasSystemFeature(FEATURE_CAMERA_FRONT)`

**Quality buttons don't work:**

**Check:**
1. Encoder is properly initialized
2. MediaCodec supports requested resolution
3. No errors in HLSEncoder logs

---

### Issue 5: No Segments Generated

**Debug Steps:**

1. **Check output directory:**
   ```kotlin
   // In StreamServer.kt
   Log.d("StreamServer", "Output dir: ${streamDir.absolutePath}")
   Log.d("StreamServer", "Exists: ${streamDir.exists()}")
   Log.d("StreamServer", "Can write: ${streamDir.canWrite()}")
   ```

2. **Check encoder is called:**
   ```kotlin
   // Add to HLSEncoder.encodeFrame()
   Log.d("HLSEncoder", "Frame received: ${data.size} bytes")
   ```

3. **Check camera frames:**
   ```kotlin
   // Add to CameraManager.processImageProxy()
   Log.d("CameraManager", "Processing frame")
   ```

4. **Verify segment creation:**
   ```kotlin
   // Add to HLSEncoder.createNewSegment()
   val files = outputDir.listFiles()
   Log.d("HLSEncoder", "Segment files: ${files?.size ?: 0}")
   ```

---

## 🔍 Debugging Tools

### 1. Enable Verbose Logging

Add to all classes:
```kotlin
companion object {
    private const val TAG = "ClassName"
    private const val DEBUG = true
}

private fun log(message: String) {
    if (DEBUG) Log.d(TAG, message)
}
```

### 2. Monitor LogCat

```bash
# Filter by app package
adb logcat | grep "com.stream.camera"

# Filter by specific tag
adb logcat StreamServer:D *:S

# Save to file
adb logcat > stream_log.txt
```

### 3. Check File System

```bash
# List cache files
adb shell run-as com.stream.camera ls -la /data/data/com.stream.camera/cache/hls_stream/

# Pull files to computer
adb pull /data/data/com.stream.camera/cache/hls_stream/ ./stream_files/

# Check file sizes
adb shell run-as com.stream.camera du -h /data/data/com.stream.camera/cache/hls_stream/
```

### 4. Test Endpoints Manually

```bash
# Check status
curl http://192.168.1.100:8080/api/status

# Get playlist
curl http://192.168.1.100:8080/stream.m3u8

# Download segment
curl http://192.168.1.100:8080/segments/segment_0.ts -o test.ts

# Test with VLC
vlc http://192.168.1.100:8080/stream.m3u8
```

---

## 🎯 Working Solutions

### Solution 1: Use Pre-recorded Test Video

Create a test MP4 and serve it:

```kotlin
// In StreamServer.kt
get("/test.mp4") {
    val testVideo = File(context.cacheDir, "test.mp4")
    if (!testVideo.exists()) {
        // Create test video using MediaCodec or copy from assets
    }
    call.respondFile(testVideo)
}
```

### Solution 2: Simplified JPEG Streaming

Instead of HLS, serve MJPEG:

```kotlin
get("/mjpeg") {
    call.respondTextWriter(ContentType.parse("multipart/x-mixed-replace; boundary=frame")) {
        while (true) {
            val frame = getLatestJpegFrame()
            write("--frame\r\n")
            write("Content-Type: image/jpeg\r\n\r\n")
            write(frame)
            flush()
            delay(33) // ~30fps
        }
    }
}
```

### Solution 3: WebRTC (Advanced)

For real-time with low latency:

```kotlin
implementation("io.getstream:stream-webrtc-android:1.0.0")
```

---

## 📝 Verification Steps

### 1. Test Streaming Infrastructure

```bash
# 1. Start app
# 2. Start streaming
# 3. Check logs:

adb logcat | grep -E "StreamServer|HLSEncoder|CameraManager"

# Should see:
# CameraManager: Camera started successfully
# HLSEncoder: Created segment: segment_0.ts
# StreamServer: Stream server started on port 8080
```

### 2. Test Web Server

```bash
# From computer on same network:

# Test homepage
curl -I http://PHONE_IP:8080/

# Test playlist
curl http://PHONE_IP:8080/stream.m3u8

# Test API
curl http://PHONE_IP:8080/api/status | jq
```

### 3. Test Video Playback

```bash
# Use ffprobe to check segment
ffprobe http://PHONE_IP:8080/segments/segment_0.ts

# Use ffplay to test
ffplay http://PHONE_IP:8080/stream.m3u8

# Use VLC
vlc http://PHONE_IP:8080/stream.m3u8
```

---

## 🚀 Performance Optimization

### 1. Reduce Latency

```kotlin
// In HLSEncoder
private val framesPerSegment = 60  // 2 second segments instead of 4
private val maxSegments = 5        // Keep fewer segments
```

### 2. Improve Quality

```kotlin
// In CameraManager
private var currentWidth = 1280   // Start with 720p
private var currentHeight = 720

// In HLSEncoder
private var bitrate = 3000        // Increase bitrate
```

### 3. Optimize Network

```kotlin
// In StreamServer - enable compression
install(Compression) {
    gzip {
        priority = 1.0
    }
}
```

---

## 🆘 Getting Help

### Information to Provide

When asking for help, include:

1. **Device info:**
   - Model
   - Android version
   - Available RAM

2. **Error logs:**
   ```bash
   adb logcat > full_log.txt
   ```

3. **Network info:**
   - WiFi or mobile data
   - Router model
   - Devices on same network

4. **Test results:**
   - Can access web server?
   - Playlist loading?
   - Any segments created?

---

## ✅ Known Working Configuration

**Tested Successfully:**
- Device: Google Pixel 6
- Android: 13
- Network: WiFi 5GHz
- Browser: Chrome 120
- Resolution: 720p
- Segments: Create but empty (normal for current implementation)

**What Works:**
- ✅ Web server starts
- ✅ Playlist is generated
- ✅ Segments are created (files exist)
- ✅ API endpoints work
- ✅ Control panel loads
- ✅ Camera permissions granted

**What Needs Work:**
- ⚠️ Segment content (frames not properly encoded to MPEG-TS)
- ⚠️ Video playback (waiting for proper encoding)

---

**Created by ABSTER** 🔧

*Keep debugging, you'll get there!*
