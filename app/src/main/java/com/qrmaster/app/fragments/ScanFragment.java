// ScanFragment.java
package com.qrmaster.app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.util.concurrent.ExecutionException;

public class ScanFragment extends Fragment {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private PreviewView previewView;
    private MaterialButton btnFlash, btnGallery;
    private Camera camera;
    private boolean flashEnabled = false;
    private QRViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan, container, false);
        
        previewView = view.findViewById(R.id.preview_view);
        btnFlash = view.findViewById(R.id.btn_flash);
        btnGallery = view.findViewById(R.id.btn_gallery);
        
        viewModel = new ViewModelProvider(this).get(QRViewModel.class);
        
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnGallery.setOnClickListener(v -> openGallery());
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
        
        return view;
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), image -> {
            processImage(image);
        });

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Camera binding failed", Toast.LENGTH_SHORT).show();
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
            imageProxy.getImage(),
            imageProxy.getImageInfo().getRotationDegrees()
        );

        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener(barcodes -> {
                for (Barcode barcode : barcodes) {
                    String content = barcode.getRawValue();
                    if (content != null) {
                        handleScannedCode(content, barcode.getValueType());
                        break;
                    }
                }
            })
            .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleScannedCode(String content, int type) {
        String qrType = getQRType(type);
        QRItem item = new QRItem(content, qrType, System.currentTimeMillis());
        item.setGenerated(false);
        
        viewModel.insert(item);
        
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), "QR Code scanned: " + qrType, Toast.LENGTH_SHORT).show();
        });
    }

    private String getQRType(int type) {
        switch (type) {
            case Barcode.TYPE_URL: return "URL";
            case Barcode.TYPE_WIFI: return "WiFi";
            case Barcode.TYPE_EMAIL: return "Email";
            case Barcode.TYPE_PHONE: return "Phone";
            case Barcode.TYPE_SMS: return "SMS";
            case Barcode.TYPE_CONTACT_INFO: return "Contact";
            case Barcode.TYPE_GEO: return "Location";
            default: return "Text";
        }
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            flashEnabled = !flashEnabled;
            camera.getCameraControl().enableTorch(flashEnabled);
            btnFlash.setIcon(ContextCompat.getDrawable(requireContext(), 
                flashEnabled ? R.drawable.ic_flash_on : R.drawable.ic_flash_off));
        }
    }

    private void openGallery() {
        Toast.makeText(requireContext(), "Gallery feature coming soon", Toast.LENGTH_SHORT).show();
    }
}
