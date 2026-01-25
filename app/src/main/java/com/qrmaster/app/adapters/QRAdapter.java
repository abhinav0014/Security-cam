// QRAdapter.java
package com.qrmaster.app.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QRAdapter extends RecyclerView.Adapter<QRAdapter.QRViewHolder> {
    private List<QRItem> items = new ArrayList<>();
    private Context context;
    private QRViewModel viewModel;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public QRAdapter(Context context, QRViewModel viewModel) {
        this.context = context;
        this.viewModel = viewModel;
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
        
        holder.itemView.setOnClickListener(v -> {
            // Show detail dialog
            showDetailDialog(item);
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

    private void showDetailDialog(QRItem item) {
        // This would show a detail dialog with full QR and actions
        // Implementation would use MaterialAlertDialogBuilder
    }

    static class QRViewHolder extends RecyclerView.ViewHolder {
        ImageView qrPreview, typeIcon, favoriteIcon;
        TextView contentText, dateText;

        public QRViewHolder(@NonNull View itemView) {
            super(itemView);
            qrPreview = itemView.findViewById(R.id.qr_preview);
            typeIcon = itemView.findViewById(R.id.type_icon);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
            contentText = itemView.findViewById(R.id.content_text);
            dateText = itemView.findViewById(R.id.date_text);
        }
    }
}