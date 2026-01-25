# QR Scanner & Generator App - Complete Documentation

## 📱 Project Overview

A professional Material 3 QR code scanner and generator application for Android, compatible with API 26 (Android 8.0) through the latest Android versions.

---

## 🎨 Required Vector Drawables

Create these vector drawable files in `res/drawable/`:

### Navigation Icons

**ic_qr_code_scanner.xml**
```xml
<vector android:height="24dp" android:tint="?attr/colorControlNormal"
    android:viewportHeight="24" android:viewportWidth="24"
    android:width="24dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="@android:color/white" 
        android:pathData="M9.5,6.5v3h-3v-3H9.5M11,5H5v6h6V5L11,5zM9.5,14.5v3h-3v-3H9.5M11,13H5v6h6V13L11,13zM17.5,6.5v3h-3v-3H17.5M19,5h-6v6h6V5L19,5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5zM16,13h1.5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5zM16,16h1.5v1.5H16V16zM17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22v-5h-2v3h-3v2H22zM2,22h5v-2H4v-3H2V22zM2,2v5h2V4h3V2H2z"/>
</vector>
```

**ic_create.xml**
```xml
<vector android:height="24dp" android:tint="?attr/colorControlNormal"
    android:viewportHeight="24" android:viewportWidth="24"
    android:width="24dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="@android:color/white" 
        android:pathData="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"/>
</vector>
```

**ic_history.xml**
```xml
<vector android:height="24dp" android:tint="?attr/colorControlNormal"
    android:viewportHeight="24" android:viewportWidth="24"
    android:width="24dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="@android:color/white" 
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z"/>
</vector>
```

**ic_bookmark.xml**
```xml
<vector android:height="24dp" android:tint="?attr/colorControlNormal"
    android:viewportHeight="24" android:viewportWidth="24"
    android:width="24dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="@android:color/white" 
        android:pathData="M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"/>
</vector>
```

### Type Icons

**ic_link.xml, ic_wifi.xml, ic_email.xml, ic_phone.xml, ic_sms.xml, ic_contact.xml, ic_location.xml, ic_payment.xml, ic_text.xml**

Use Material Icons from: https://fonts.google.com/icons

### Action Icons

**ic_flash_on.xml, ic_flash_off.xml, ic_image.xml, ic_save.xml, ic_share.xml, ic_delete.xml, ic_search.xml, ic_filter.xml, ic_favorite.xml, ic_favorite_border.xml, ic_qr_code.xml**

---

## 📂 Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/qrmaster/app/
│   │   │   ├── MainActivity.java
│   │   │   ├── models/
│   │   │   │   └── QRItem.java
│   │   │   ├── data/
│   │   │   │   ├── QRDao.java
│   │   │   │   ├── QRDatabase.java
│   │   │   │   └── QRRepository.java
│   │   │   ├── viewmodels/
│   │   │   │   └── QRViewModel.java
│   │   │   ├── fragments/
│   │   │   │   ├── ScanFragment.java
│   │   │   │   ├── CreateFragment.java
│   │   │   │   ├── HistoryFragment.java
│   │   │   │   └── SavedFragment.java
│   │   │   ├── adapters/
│   │   │   │   └── QRAdapter.java
│   │   │   └── utils/
│   │   │       ├── QRCodeUtils.java
│   │   │       ├── PermissionHelper.java
│   │   │       └── ShareHelper.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── fragment_scan.xml
│   │   │   │   ├── fragment_create.xml
│   │   │   │   ├── fragment_history.xml
│   │   │   │   └── item_qr.xml
│   │   │   ├── drawable/
│   │   │   ├── menu/
│   │   │   ├── values/
│   │   │   │   ├── colors.xml
│   │   │   │   ├── themes.xml
│   │   │   │   └── strings.xml
│   │   │   ├── values-night/
│   │   │   │   └── themes.xml
│   │   │   └── xml/
│   │   │       └── file_paths.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
└── build.gradle
```

---

## 🚀 Getting Started

### 1. **Prerequisites**
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 8 or higher
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 34 (Android 14)

### 2. **Setup Steps**

1. Create new Android Studio project
2. Replace `build.gradle` files with provided configurations
3. Copy all Java classes to appropriate packages
4. Add all XML layout files
5. Add drawable resources (icons)
6. Sync Gradle
7. Build and run

### 3. **Permissions Setup**

The app automatically handles:
- Camera permission (runtime)
- Storage permission (for Android 9 and below)

---

## 🎯 Key Features Implemented

### ✅ QR Scanner
- Real-time camera scanning
- ML Kit barcode detection
- Flash toggle
- Auto-focus support
- Detects: URL, WiFi, Email, Phone, SMS, Contact, Location, Text

### ✅ QR Generator
- Live preview
- Custom colors (foreground/background)
- Multiple QR types
- Save to history automatically

### ✅ History Management
- Room database persistence
- Search functionality
- Type filtering
- Multi-select delete
- Date/time stamps

### ✅ Saved QR Codes
- Favorite/bookmark system
- Quick access
- Fullscreen view
- Share functionality

---

## 🎨 Material 3 Design

### Color System
- Dynamic color support (Android 12+)
- Full light/dark theme support
- Material You adaptive colors
- High contrast ratios for accessibility

### Components Used
- Material Cards
- Bottom Navigation
- FAB (Floating Action Button)
- Text Fields (outlined)
- Buttons (filled, tonal, text)
- Toolbars
- RecyclerView with Material styling

---

## 📊 Architecture

### MVVM Pattern
```
View (Fragment) ↔ ViewModel ↔ Repository ↔ DAO ↔ Database
```

### Data Flow
1. User interaction in Fragment
2. ViewModel processes request
3. Repository handles data operations
4. Room Database persists data
5. LiveData updates UI automatically

---

## 🔧 Customization Options

### Change Primary Color
Edit `colors.xml`:
```xml
<color name="md_theme_light_primary">#YourColor</color>
```

### Add New QR Type
1. Add type to `QRCodeUtils.getQRTypeFromContent()`
2. Add icon resource
3. Update `QRAdapter.getTypeIcon()`
4. Add to spinner in `CreateFragment`

### Modify Database Schema
1. Update `QRItem` model
2. Increment database version in `QRDatabase`
3. Add migration strategy

---

## 🧪 Testing

### Manual Testing Checklist
- [ ] Scan various QR types
- [ ] Generate QR codes
- [ ] Save to favorites
- [ ] Delete from history
- [ ] Search functionality
- [ ] Dark mode switching
- [ ] Permission handling
- [ ] Camera flash
- [ ] Share QR codes

---

## 📈 Performance Optimizations

1. **Camera**: Background-safe lifecycle handling
2. **Database**: Async operations with ExecutorService
3. **UI**: ViewBinding for efficient view access
4. **Memory**: Bitmap recycling for QR generation
5. **Threading**: Main thread for UI, background for DB

---

## 🐛 Known Limitations

1. Color picker not implemented (shows toast)
2. Gallery import not implemented (shows toast)
3. WiFi auto-connect requires additional permissions
4. Batch export feature not included

---

## 📝 Future Enhancements

- [ ] Color picker dialog
- [ ] Import QR from gallery
- [ ] Batch export (CSV/PDF)
- [ ] QR code analytics
- [ ] Cloud backup
- [ ] Widget support
- [ ] Wear OS companion
- [ ] Shortcuts API integration

---

## 📄 License

This code is provided as-is for educational and commercial use.

---

## 💡 Tips for Developers

### Adding Vector Icons
Use Android Studio's Vector Asset tool:
1. Right-click `res/drawable` → New → Vector Asset
2. Choose Material Icon
3. Search for icon name
4. Customize color/size
5. Import

### Debugging Camera Issues
- Check physical device (emulator cameras are limited)
- Verify manifest permissions
- Use Logcat to monitor CameraX lifecycle

### Improving Scan Speed
- Reduce `ImageAnalysis` resolution
- Implement scan cooldown
- Use `STRATEGY_KEEP_ONLY_LATEST`

---

## 🤝 Contributing

Feel free to:
- Report bugs
- Suggest features
- Submit pull requests
- Improve documentation

---

## 📞 Support

For issues with:
- **CameraX**: Check official docs at developer.android.com
- **ML Kit**: Visit firebase.google.com/docs/ml-kit
- **Room**: See developer.android.com/training/data-storage/room

---

**Built with ❤️ using Material Design 3 and modern Android development practices**