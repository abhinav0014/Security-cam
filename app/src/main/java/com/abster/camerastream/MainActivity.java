package com.abster.camerastream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.abster.camerastream.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private ActivityMainBinding binding;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;
    private boolean isStreaming = false;
    private StreamQuality currentQuality = StreamQuality.MEDIUM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUI();
        checkAndRequestPermissions();
    }

    private void setupUI() {
        // Camera selector
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        
        binding.switchCamera.setOnClickListener(v -> {
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                binding.switchCamera.setImageResource(R.drawable.ic_camera_front);
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                binding.switchCamera.setImageResource(R.drawable.ic_camera_rear);
            }
            if (isStreaming) {
                startCamera();
            }
        });

        // Start/Stop streaming
        binding.btnStartStop.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                startStreaming();
            }
        });

        // Quality selection
        binding.chipGroupQuality.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int chipId = checkedIds.get(0);
                if (chipId == R.id.chipLow) {
                    currentQuality = StreamQuality.LOW;
                } else if (chipId == R.id.chipMedium) {
                    currentQuality = StreamQuality.MEDIUM;
                } else if (chipId == R.id.chipHigh) {
                    currentQuality = StreamQuality.HIGH;
                }
                
                if (isStreaming) {
                    // Restart camera with new quality
                    startCamera();
                }
            }
        });

        updateUIState(false);
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            initializeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                initializeCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startCamera() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(currentQuality.getResolution())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        if (isStreaming) {
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this),
                    StreamingService.getImageAnalyzer());
        }

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(this, "Error binding camera: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startStreaming() {
        Intent serviceIntent = new Intent(this, StreamingService.class);
        serviceIntent.putExtra("quality", currentQuality.name());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        isStreaming = true;
        startCamera();
        updateUIState(true);
        displayStreamingInfo();
    }

    private void stopStreaming() {
        Intent serviceIntent = new Intent(this, StreamingService.class);
        stopService(serviceIntent);
        
        isStreaming = false;
        updateUIState(false);
    }

    private void updateUIState(boolean streaming) {
        if (streaming) {
            binding.btnStartStop.setText("Stop Streaming");
            binding.btnStartStop.setBackgroundColor(getColor(R.color.red_500));
            binding.statusIndicator.setBackgroundResource(R.drawable.status_active);
            binding.tvStatus.setText("Streaming Active");
            binding.chipGroupQuality.setEnabled(false);
        } else {
            binding.btnStartStop.setText("Start Streaming");
            binding.btnStartStop.setBackgroundColor(getColor(R.color.green_500));
            binding.statusIndicator.setBackgroundResource(R.drawable.status_inactive);
            binding.tvStatus.setText("Ready to Stream");
            binding.chipGroupQuality.setEnabled(true);
            binding.urlContainer.setVisibility(View.GONE);
        }
    }

    private void displayStreamingInfo() {
        String ipAddress = getLocalIpAddress();
        int port = StreamingService.PORT;
        
        String url = "http://" + ipAddress + ":" + port;
        binding.tvUrl.setText(url);
        binding.urlContainer.setVisibility(View.VISIBLE);
        
        binding.btnCopyUrl.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Stream URL", url);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        
        @SuppressWarnings("deprecation")
        String ipAddress = Formatter.formatIpAddress(ip);
        return ipAddress;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isStreaming) {
            stopStreaming();
        }
    }
}
