// ShareHelper.java
package com.qrmaster.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ShareHelper {
    
    public static void shareQRCode(Context context, Bitmap qrBitmap, String content) {
        try {
            File cachePath = new File(context.getCacheDir(), "images");
            cachePath.mkdirs();
            
            File imageFile = new File(cachePath, "qr_share.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            
            Uri imageUri = FileProvider.getUriForFile(context, 
                context.getPackageName() + ".fileprovider", imageFile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, content);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"));
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void shareText(Context context, String text) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(shareIntent, "Share"));
    }
    
    public static void openUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        context.startActivity(intent);
    }
    
    public static void dialPhone(Context context, String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phone));
        context.startActivity(intent);
    }
    
    public static void sendEmail(Context context, String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        context.startActivity(intent);
    }
}