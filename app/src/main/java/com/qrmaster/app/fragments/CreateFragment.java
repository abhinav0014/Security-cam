// CreateFragment.java
package com.qrmaster.app.fragments;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;

public class CreateFragment extends Fragment {
    private ImageView qrPreview;
    private AutoCompleteTextView typeSpinner;
    private TextInputEditText contentInput;
    private View colorForeground, colorBackground;
    private MaterialButton btnGenerate, btnSave;
    private QRViewModel viewModel;
    private Bitmap currentQRBitmap;
    private String currentFgColor = "#000000";
    private String currentBgColor = "#FFFFFF";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create, container, false);
        
        qrPreview = view.findViewById(R.id.qr_preview);
        typeSpinner = view.findViewById(R.id.qr_type_spinner);
        contentInput = view.findViewById(R.id.content_input);
        colorForeground = view.findViewById(R.id.color_foreground);
        colorBackground = view.findViewById(R.id.color_background);
        btnGenerate = view.findViewById(R.id.btn_generate);
        btnSave = view.findViewById(R.id.btn_save);
        
        
        viewModel = new ViewModelProvider(this).get(QRViewModel.class);

        setupTypeSpinner();
        setupClickListeners();
        
        return view;
    }

    private void setupTypeSpinner() {
        String[] types = {"Text", "URL", "WiFi", "Contact", "Email", "Phone", "Payment"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, types);
        typeSpinner.setAdapter(adapter);
        typeSpinner.setText("Text", false);
    }

    private void setupClickListeners() {
        btnGenerate.setOnClickListener(v -> generateQR());
        btnSave.setOnClickListener(v -> saveQR());
        
        colorForeground.setOnClickListener(v -> {
            // Color picker dialog would go here
            Toast.makeText(requireContext(), "Color picker coming soon", Toast.LENGTH_SHORT).show();
        });
        
        colorBackground.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Color picker coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void generateQR() {
        String content = contentInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter content", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            
            int fgColor = Color.parseColor(currentFgColor);
            int bgColor = Color.parseColor(currentBgColor);
            
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? fgColor : bgColor);
                }
            }
            
            currentQRBitmap = bitmap;
            qrPreview.setImageBitmap(bitmap);
            Toast.makeText(requireContext(), "QR Code generated", Toast.LENGTH_SHORT).show();
            
        } catch (WriterException e) {
            Toast.makeText(requireContext(), "Error generating QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveQR() {
        if (currentQRBitmap == null) {
            Toast.makeText(requireContext(), "Generate QR first", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = contentInput.getText().toString().trim();
        String type = typeSpinner.getText().toString();
        
        QRItem item = new QRItem(content, type, System.currentTimeMillis());
        item.setGenerated(true);
        item.setColorForeground(currentFgColor);
        item.setColorBackground(currentBgColor);
        
        viewModel.insert(item);
        Toast.makeText(requireContext(), "QR Code saved", Toast.LENGTH_SHORT).show();
    }
}