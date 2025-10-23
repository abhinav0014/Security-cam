# Android Security Camera App - Comprehensive Development Prompt

## Overview
Create a professional Android security camera application that streams live video over a local network with full browser-based control, customizable settings, and robust error handling.

## Core Requirements

### 1. Camera Streaming System
- **Live Video Streaming**: Real-time MJPEG/H.264 streaming over HTTP
- **Multi-Camera Support**: Toggle between front/back cameras
- **Quality Presets**: Low (640x480@15fps), Medium (1280x720@30fps), High (1920x1080@30fps)
- **Custom Quality**: Allow manual width/height/fps/bitrate configuration
- **Auto-Focus & Exposure**: Automatic camera optimization
- **Flash Control**: Toggle flashlight for back camera

### 2. Network Server
- **Embedded HTTP Server**: Use NanoHTTPD with WebSocket support
- **Configurable Port**: Default 8080, allow range 1024-65535
- **Auto IP Detection**: Display WiFi IP address prominently
- **CORS Support**: Enable cross-origin requests for API access
- **Connection Status**: Show active client count
- **Network Error Recovery**: Auto-restart on network changes

### 3. Browser Interface (Web UI)

#### Main Dashboard (`/`)
```
- Live video stream with auto-refresh
- Connection quality indicator (FPS, latency)
- Quick controls: Camera flip, flash, record
- Server status and uptime
- Device information panel
```

#### Settings Page (`/settings`)
```
Video Quality:
  - Preset selector (Low/Medium/High/Custom)
  - Custom resolution (width x height)
  - Frame rate slider (1-60 fps)
  - Bitrate control (500Kbps - 10Mbps)

Recording:
  - Enable/disable recording
  - Storage location display
  - Recording interval (1-60 minutes)
  - Max storage size (1-100 GB)
  - Retention period (1-90 days)
  - Motion-triggered recording toggle

Enhancement:
  - Night mode with gain adjustment
  - Motion detection sensitivity
  - Noise reduction toggle
  - Auto-focus mode

Server:
  - Port configuration
  - Password protection (optional)
  - Auto-start on boot
  - Keep screen on option
```

#### Recordings Page (`/recordings`)
```
- List of recorded videos with thumbnails
- Sort by date/size/duration
- Play in-browser video player
- Download recordings
- Delete individual/bulk recordings
- Storage usage visualization
```

#### API Endpoints
```
GET  /api/status       - Server and camera status
GET  /api/settings     - Current settings JSON
POST /api/settings     - Update settings
GET  /api/stream       - Video stream (MJPEG)
GET  /api/snapshot     - Single frame JPEG
POST /api/camera/flip  - Switch camera
POST /api/camera/flash - Toggle flash
POST /api/recording/start - Start recording
POST /api/recording/stop  - Stop recording
GET  /api/recordings   - List recordings
GET  /api/recordings/:id - Download recording
DELETE /api/recordings/:id - Delete recording
WS   /ws              - WebSocket for real-time updates
```

### 4. Error Handling

#### Camera Errors
- **Permission Denied**: Show permission request with rationale
- **Camera In Use**: Detect conflicts, offer to close other apps
- **Camera Disconnected**: Auto-reconnect with exponential backoff
- **Initialization Failed**: Log error, show user-friendly message
- **Frame Capture Timeout**: Skip frame, log warning, continue streaming

#### Network Errors
- **Port Already in Use**: Auto-increment port or show error
- **WiFi Disconnected**: Show offline indicator, pause streaming
- **Network Changed**: Detect IP change, update UI
- **Server Start Failed**: Show detailed error with troubleshooting steps

#### Storage Errors
- **Insufficient Space**: Alert user, stop recording, clean old files
- **Write Permission Denied**: Request permissions, suggest alternatives
- **SD Card Removed**: Detect, pause recording, show notification
- **Corrupted Recording**: Mark as invalid, offer recovery/deletion

#### Resource Management
- **Memory Pressure**: Reduce quality automatically, log event
- **Thermal Throttling**: Monitor temperature, warn user, reduce load
- **Battery Low**: Suggest battery optimization exemption
- **Background Restrictions**: Guide user to disable battery optimization

### 5. Android App Features

#### MainActivity
- **Server Status Display**: Running/stopped with colored indicators
- **IP Address & Port**: Large, copyable text
- **QR Code**: Generate QR with server URL for easy mobile access
- **Start/Stop Button**: Toggle server with confirmation
- **Permission Checker**: Comprehensive permission flow
- **Battery Optimization**: Prompt to disable for background operation
- **Quick Settings**: Access to key settings without browser

#### CameraService (Foreground Service)
- **Persistent Notification**: Show streaming status, controls
- **Wake Lock Management**: Prevent sleep during streaming
- **Lifecycle Handling**: Proper cleanup on destroy
- **Crash Recovery**: Restart service on unexpected termination
- **Settings Sync**: React to settings changes immediately

#### Settings Management
- **SharedPreferences**: Store all settings persistently
- **JSON Serialization**: Easy backup/restore
- **Default Values**: Sensible defaults for all options
- **Validation**: Validate all inputs before saving
- **Change Listeners**: Update UI immediately on settings change

#### Recording Management
- **Background Recording**: Continue even when app closed
- **Automatic Segmentation**: Split recordings by time/size
- **Thumbnail Generation**: Create thumbnails for quick preview
- **Metadata**: Store date, duration, resolution, file size
- **Auto-Cleanup**: Delete recordings based on retention policy

### 6. Advanced Features

#### Motion Detection
- **Frame Differencing**: Compare consecutive frames
- **Sensitivity Control**: Adjustable threshold (0-100%)
- **Detection Zones**: Define specific areas to monitor
- **Alerts**: Notification/webhook on motion detected
- **Event Recording**: Save clips only when motion occurs

#### Night Vision
- **Low Light Enhancement**: Increase ISO/exposure automatically
- **IR Support**: Utilize IR camera if available
- **Gain Control**: Manual brightness adjustment

#### Security
- **Password Protection**: Optional authentication for web UI
- **HTTPS Support**: Optional SSL/TLS encryption
- **Access Logging**: Log all connections and actions
- **Rate Limiting**: Prevent abuse/DoS

#### Performance Optimization
- **Hardware Acceleration**: Use device GPU when available
- **Adaptive Bitrate**: Adjust quality based on network conditions
- **Buffer Management**: Prevent memory leaks
- **Thread Pooling**: Efficient resource utilization

### 7. UI/UX Requirements

#### Mobile App
- **Material Design 3**: Follow latest Android design guidelines
- **Dark Mode**: Full dark theme support
- **Responsive Layout**: Support all screen sizes
- **Haptic Feedback**: Tactile responses for actions
- **Toast Messages**: User-friendly success/error notifications
- **Progress Indicators**: Show loading states clearly

#### Web Interface
- **Responsive Design**: Work on desktop, tablet, mobile browsers
- **Modern UI**: Clean, professional appearance
- **Real-time Updates**: Use WebSocket for live status
- **Keyboard Shortcuts**: Quick controls (Space=play/pause, F=flash, etc.)
- **Touch Gestures**: Pinch-to-zoom on video, swipe for controls
- **Loading States**: Show skeletons/spinners during loading
- **Error Messages**: Clear, actionable error notifications

### 8. Testing & Validation

#### Unit Tests
- Settings serialization/deserialization
- URL generation and validation
- Permission state management
- Storage calculation utilities

#### Integration Tests
- Camera initialization flow
- Server start/stop lifecycle
- Recording start/stop/save
- Settings update propagation

#### Manual Testing Checklist
- [ ] App starts without crashes
- [ ] All permissions requested and handled
- [ ] Camera preview displays correctly
- [ ] Server accessible from browser
- [ ] Video stream plays smoothly
- [ ] Settings changes apply immediately
- [ ] Recording creates valid video files
- [ ] App survives background/foreground transitions
- [ ] Service continues after app closed
- [ ] Battery optimization doesn't kill service
- [ ] Network changes handled gracefully
- [ ] Storage full scenario handled
- [ ] Multiple concurrent connections work
- [ ] App uninstalls cleanly

### 9. Code Structure

```
com.onnet.securitycam/
├── MainActivity.kt                 // Main entry point
├── StreamActivity.kt              // Camera preview (optional)
├── PermissionHelper.kt            // Permission management
├── config/
│   ├── CameraSettings.kt         // Data classes
│   └── SettingsManager.kt        // Persistence
├── server/
│   ├── EmbeddedServer.kt         // HTTP server
│   ├── WebSocketHandler.kt       // WebSocket connections
│   └── ApiController.kt          // REST endpoints
├── camera/
│   ├── CameraManager.kt          // Camera lifecycle
│   ├── StreamEncoder.kt          // Video encoding
│   └── FrameProcessor.kt         // Image processing
├── recording/
│   ├── RecordingManager.kt       // Recording logic
│   ├── VideoEncoder.kt           // MP4 encoding
│   └── StorageManager.kt         // File management
├── features/
│   ├── MotionDetector.kt         // Motion detection
│   └── NightVision.kt            // Low-light enhancement
├── services/
│   └── CameraService.kt          // Foreground service
└── utils/
    ├── NetworkUtils.kt            // IP detection, etc.
    ├── ErrorHandler.kt            // Error management
    └── Logger.kt                  // Logging utility
```

### 10. Error Messages & Logging

#### User-Facing Error Messages
```kotlin
// Permission denied
"Camera permission is required to stream video. Please grant permission in Settings."

// Port in use
"Port 8080 is already in use. Try port 8081 or choose a different port in settings."

// No WiFi
"WiFi is not connected. Please connect to WiFi to use network streaming."

// Low storage
"Storage is almost full (95%). Recording will stop automatically. Delete old recordings or increase storage."

// Camera error
"Failed to initialize camera. Please close other apps using the camera and try again."
```

#### Developer Logging
```kotlin
Log.d(TAG, "Starting camera with settings: ${settings}")
Log.i(TAG, "Server started successfully on port $port")
Log.w(TAG, "Frame capture timeout, skipping frame")
Log.e(TAG, "Failed to encode frame", exception)
```

### 11. Documentation Requirements

#### README.md
- App description and features
- Installation instructions
- Usage guide with screenshots
- Network requirements
- Troubleshooting common issues
- FAQ section

#### In-App Help
- First-run tutorial
- Tooltips for each setting
- Link to online documentation
- Contact/support information

## Implementation Priorities

### Phase 1: Core Functionality (MVP)
1. Camera preview and capture
2. Basic HTTP server with MJPEG stream
3. Simple web UI with video display
4. Settings persistence
5. Essential error handling

### Phase 2: Enhanced Features
1. Recording functionality
2. Settings web interface
3. Motion detection
4. Recording playback/management
5. Foreground service

### Phase 3: Polish & Optimization
1. Advanced settings
2. Performance optimization
3. Comprehensive error handling
4. UI/UX improvements
5. Security features

## Quality Standards

- **Code Quality**: Follow Kotlin best practices, use coroutines, avoid memory leaks
- **Performance**: Maintain 30fps at medium quality on mid-range devices
- **Reliability**: Handle 24+ hour continuous streaming
- **Security**: No hardcoded credentials, validate all inputs
- **Maintainability**: Clear comments, modular architecture, unit tests

## Deliverables

1. Complete Android project with all source code
2. APK file for installation
3. README with setup and usage instructions
4. Screenshots of app and web interface
5. API documentation
6. Known issues and future improvements list

---

**Note**: This app should be production-ready with professional error handling, user-friendly interfaces, and robust performance under various network and device conditions.