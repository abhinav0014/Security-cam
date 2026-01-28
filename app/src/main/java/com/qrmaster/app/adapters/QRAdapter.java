// QRAdapter.java - Enhanced with menu options and selection
package com.qrmaster.app.adapters;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QRAdapter extends RecyclerView.Adapter<QRAdapter.QRViewHolder> {
    private List<QRItem> items = new ArrayList<>();
    private List<QRItem> selectedItems = new ArrayList<>();
    private Context context;
    private QRViewModel viewModel;
    private OnItemClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public interface OnItemClickListener {
        void onItemClick(QRItem item);
        void onItemLongClick(QRItem item);
        void onMenuClick(QRItem item);
    }

    public QRAdapter(Context context, QRViewModel viewModel) {
        this.context = context;
        this.viewModel = viewModel;
    }

    public QRAdapter(Context context, QRViewModel viewModel, OnItemClickListener listener) {
        this.context = context;
        this.viewModel = viewModel;
        this.listener = listener;
    }

    @NonNull
    @Override
    public QRViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_qr, parent, false);
        return new QRViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QRViewHolder holder, int position) {
        QRItem item = items.get(position);
        
        holder.contentText.setText(item.getContent());
        holder.dateText.setText(dateFormat.format(new Date(item.getTimestamp())));
        
        // Set type icon
        int iconRes = getTypeIcon(item.getType());
        holder.typeIcon.setImageResource(iconRes);
        
        // Generate QR preview
        try {
            Bitmap qrBitmap = generateQRBitmap(item.getContent(), 200, 200, 
                item.getColorForeground(), item.getColorBackground());
            holder.qrPreview.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            // Use placeholder
        }
        
        // Set favorite icon
        holder.favoriteIcon.setImageResource(
            item.isSaved() ? R.drawable.ic_favorite : R.drawable.ic_favorite_border
        );
        
        holder.favoriteIcon.setOnClickListener(v -> {
            item.setSaved(!item.isSaved());
            viewModel.update(item);
            notifyItemChanged(position);
        });
        
        // Selection state
        boolean isSelected = selectedItems.contains(item);
        holder.card.setCardBackgroundColor(
            isSelected ? 
                context.getResources().getColor(R.color.md_theme_light_primaryContainer, null) :
                context.getResources().getColor(R.color.md_theme_light_surface, null)
        );
        
        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            } else {
                showDetailDialog(item, (Activity) context);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(item);
            }
            return true;
        });
        
        holder.menuIcon.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenuClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(List<QRItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void setSelectedItems(List<QRItem> selected) {
        this.selectedItems = selected;
        notifyDataSetChanged();
    }

    public void showDetailDialog(QRItem item, Activity activity) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_qr_full, null);
        
        ImageView qrImage = dialogView.findViewById(R.id.qr_full_image);
        TextView typeText = dialogView.findViewById(R.id.qr_full_type);
        TextView contentText = dialogView.findViewById(R.id.qr_full_content);
        TextView dateText = dialogView.findViewById(R.id.qr_full_date);
        
        try {
            Bitmap qrBitmap = generateQRBitmap(item.getContent(), 512, 512, 
                item.getColorForeground(), item.getColorBackground());
            qrImage.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            // Handle error
        }
        
        typeText.setText(item.getType());
        contentText.setText(item.getContent());
        dateText.setText(dateFormat.format(new Date(item.getTimestamp())));
        
        new MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setPositiveButton("Share", (dialog, which) -> shareQRCode(item, context))
            .setNeutralButton("Copy", (dialog, which) -> copyToClipboard(item.getContent()))
            .setNegativeButton("Close", null)
            .show();
    }

    public void shareQRCode(QRItem item, Context context) {
        try {
            Bitmap qrBitmap = generateQRBitmap(item.getContent(), 512, 512, 
                item.getColorForeground(), item.getColorBackground());
            
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
            shareIntent.putExtra(Intent.EXTRA_TEXT, item.getContent());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"));
            
        } catch (WriterException | IOException e) {
            Toast.makeText(context, "Error sharing QR code", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("QR Code", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private Bitmap generateQRBitmap(String content, int width, int height, 
                                    String fgColor, String bgColor) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        
        int fg = Color.parseColor(fgColor);
        int bg = Color.parseColor(bgColor);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? fg : bg);
            }
        }
        
        return bitmap;
    }

    private int getTypeIcon(String type) {
        switch (type) {
            case "URL": return R.drawable.ic_link;
            case "WiFi": return R.drawable.ic_wifi;
            case "Email": return R.drawable.ic_email;
            case "Phone": return R.drawable.ic_phone;
            case "SMS": return R.drawable.ic_sms;
            case "Contact": return R.drawable.ic_contact;
            case "Location": return R.drawable.ic_location;
            case "Payment": return R.drawable.ic_payment;
            default: return R.drawable.ic_text;
        }
    }

    static class QRViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView qrPreview, typeIcon, favoriteIcon, menuIcon;
        TextView contentText, dateText;

        public QRViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            qrPreview = itemView.findViewById(R.id.qr_preview);
            typeIcon = itemView.findViewById(R.id.type_icon);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
            menuIcon = itemView.findViewById(R.id.menu_icon);
            contentText = itemView.findViewById(R.id.content_text);
            dateText = itemView.findViewById(R.id.date_text);
        }
    }
}
