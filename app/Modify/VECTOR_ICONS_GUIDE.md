# 📐 Vector Icons Guide

## Required Icons for Camera Stream App

This document lists all vector icons needed for the Camera Stream application created by **ABSTER**.

---

## ✅ Icons Already Included

The following icons are automatically included through **Material Icons Extended** dependency in the app's `build.gradle.kts`:

```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

### Main UI Icons (from Material Icons)

| Icon | Usage | Import |
|------|-------|--------|
| `Icons.Default.Videocam` | Main app icon, camera representation | `androidx.compose.material.icons.filled.Videocam` |
| `Icons.Default.PlayArrow` | Start streaming button | `androidx.compose.material.icons.filled.PlayArrow` |
| `Icons.Default.Stop` | Stop streaming button | `androidx.compose.material.icons.filled.Stop` |
| `Icons.Default.Link` | Stream URL indicator | `androidx.compose.material.icons.filled.Link` |
| `Icons.Default.VideoLibrary` | HLS playlist indicator | `androidx.compose.material.icons.filled.VideoLibrary` |
| `Icons.Default.Settings` | Settings/configuration | `androidx.compose.material.icons.filled.Settings` |
| `Icons.Default.OpenInBrowser` | Open web control panel | `androidx.compose.material.icons.filled.OpenInBrowser` |

### Additional UI Icons

These are also available but used less frequently:

- `Icons.Default.CameraAlt` - Alternative camera icon
- `Icons.Default.Refresh` - Refresh/reload
- `Icons.Default.Info` - Information
- `Icons.Default.Warning` - Warning/error states
- `Icons.Default.CheckCircle` - Success states
- `Icons.Default.Error` - Error states
- `Icons.Default.Close` - Close/dismiss actions

---

## 🎨 Custom Drawable Resources Needed

### 1. Notification Icon (REQUIRED)

**File:** `app/src/main/res/drawable/ic_notification.xml`

**Status:** ✅ Already created in the project

```xml
<?xml version="1.0" encoding="utf-8"?>
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

**Purpose:** Used in the foreground service notification when streaming is active.

**Design:** Simple camera/video recorder icon in white, suitable for notification tray.

---

## 🖼️ App Launcher Icons

### Mipmap Resources (Required for App Icon)

You'll need to create launcher icons for different screen densities. Use **Android Studio's Image Asset Studio** to generate these:

**Steps:**
1. Right-click on `res` folder
2. Select **New → Image Asset**
3. Choose **Launcher Icons (Adaptive and Legacy)**
4. Configure:
   - **Foreground Layer:** Camera/video icon
   - **Background Layer:** Gradient (purple to blue)
   - **Icon Type:** Adaptive and Legacy
5. Click **Next** → **Finish**

**Expected output files:**
```
res/
├── mipmap-mdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-hdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-xhdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
├── mipmap-xxhdpi/
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
└── mipmap-xxxhdpi/
    ├── ic_launcher.png
    └── ic_launcher_round.png
```

---

## 🎨 Recommended Icon Design

### Color Scheme

Based on the app's theme:
- **Primary:** `#667EEA` (Purple-Blue)
- **Secondary:** `#764BA2` (Deep Purple)
- **Accent:** `#4CAF50` (Green for "Live" indicators)

### Design Guidelines

1. **Notification Icon:**
   - Must be white/transparent
   - Simple silhouette
   - 24x24dp recommended
   - No gradients (Android notification style)

2. **Launcher Icon:**
   - Use app's gradient colors
   - Include camera/video symbol
   - Follow Material Design guidelines
   - Ensure good visibility at small sizes

---

## 📦 Complete Icon Checklist

### ✅ Already Included

- [x] All Material Icons (via Compose dependency)
- [x] Notification icon (`ic_notification.xml`)

### 📝 To Be Added (Optional)

- [ ] App launcher icons (mipmap resources)
- [ ] Adaptive icon background
- [ ] Adaptive icon foreground
- [ ] Round launcher icons

---

## 🛠️ Creating Custom Icons

If you want to create additional custom icons:

### Method 1: Vector Asset Studio

1. Right-click `res/drawable`
2. Select **New → Vector Asset**
3. Choose from:
   - **Material Icon** - Browse Material icons
   - **Local File** - Import SVG
   - **Clip Art** - Use built-in clip art

### Method 2: Manual XML

Create a new XML file in `res/drawable/`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,2L2,7v10c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V7l-10,-5z"/>
</vector>
```

### Method 3: SVG to Vector Drawable

1. Create/download SVG icon
2. Use Android Studio's **Vector Asset** tool
3. Select **Local file (SVG, PSD)**
4. Import and adjust size/color

---

## 🎯 Icon Usage in Code

### Compose Icons

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// In your Composable:
Icon(
    imageVector = Icons.Default.Videocam,
    contentDescription = "Camera",
    tint = Color.White,
    modifier = Modifier.size(24.dp)
)
```

### Drawable Resources

```kotlin
// In Compose:
Icon(
    painter = painterResource(id = R.drawable.ic_notification),
    contentDescription = "Notification"
)

// In XML:
<ImageView
    android:src="@drawable/ic_notification"
    android:layout_width="24dp"
    android:layout_height="24dp" />
```

---

## 📚 Resources

### Icon Libraries

- **Material Icons:** https://fonts.google.com/icons
- **Material Design Guidelines:** https://m3.material.io/styles/icons
- **Vector Asset Studio:** Built into Android Studio

### Online Tools

- **SVG to Vector Drawable:** https://svg2vector.com/
- **Icon Generator:** https://romannurik.github.io/AndroidAssetStudio/
- **Material Palette:** https://materialpalette.com/

---

## 💡 Pro Tips

1. **Always use vector drawables** for icons instead of PNGs (better scaling)
2. **Keep notification icons simple** - Android uses them as silhouettes
3. **Test icons at different sizes** - Ensure clarity at 18dp, 24dp, 48dp
4. **Use tint colors** instead of hard-coded colors when possible
5. **Follow Material Design** guidelines for consistency

---

## ✨ Summary

**Minimum Required Icons:**
- ✅ Material Icons (included via dependency)
- ✅ `ic_notification.xml` (already created)
- ⚠️ App launcher icons (generate via Image Asset Studio)

**Everything else is optional** and depends on your customization needs.

---

**Created by ABSTER** 🎨
