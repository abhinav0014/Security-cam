// QRCodeUtils.java
package com.qrmaster.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Environment;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class QRCodeUtils {
    
    public static Bitmap generateQRCode(String content, int size) throws WriterException {
        return generateQRCode(content, size, "#000000", "#FFFFFF");
    }
    
    public static Bitmap generateQRCode(String content, int size, String fgColor, String bgColor) 
            throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        
        int fg = Color.parseColor(fgColor);
        int bg = Color.parseColor(bgColor);
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? fg : bg);
            }
        }
        
        return bitmap;
    }
    
    public static boolean saveQRToGallery(Context context, Bitmap bitmap, String fileName) {
        File picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES);
        File qrDir = new File(picturesDir, "QR Master");
        
        if (!qrDir.exists()) {
            qrDir.mkdirs();
        }
        
        File imageFile = new File(qrDir, fileName + ".png");
        
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            
            // Notify media scanner
            MediaScannerConnection.scanFile(context, 
                new String[]{imageFile.getAbsolutePath()}, 
                new String[]{"image/png"}, null);
            
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    public static String formatWiFiQR(String ssid, String password, String security) {
        return String.format("WIFI:T:%s;S:%s;P:%s;;", security, ssid, password);
    }
    
    public static String formatContactQR(String name, String phone, String email) {
        return String.format("BEGIN:VCARD\nVERSION:3.0\nFN:%s\nTEL:%s\nEMAIL:%s\nEND:VCARD", 
            name, phone, email);
    }
    
    public static String formatEmailQR(String email, String subject, String body) {
        return String.format("mailto:%s?subject=%s&body=%s", email, subject, body);
    }
    
    public static String formatSMSQR(String phone, String message) {
        return String.format("smsto:%s:%s", phone, message);
    }
    
    public static String getQRTypeFromContent(String content) {
        if (content.startsWith("http://") || content.startsWith("https://")) {
            return "URL";
        } else if (content.startsWith("WIFI:")) {
            return "WiFi";
        } else if (content.startsWith("BEGIN:VCARD")) {
            return "Contact";
        } else if (content.startsWith("mailto:")) {
            return "Email";
        } else if (content.startsWith("tel:")) {
            return "Phone";
        } else if (content.startsWith("smsto:")) {
            return "SMS";
        } else if (content.startsWith("geo:")) {
            return "Location";
        } else {
            return "Text";
        }
    }
}