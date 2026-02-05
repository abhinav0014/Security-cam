package com.abster.camerastream;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    private boolean isStreaming = false;
    private boolean serviceBound = false;

    private StreamQuality currentQuality = StreamQuality.MEDIUM;
    private StreamingService streamingService;
    private ImageAnalysis.Analyzer analyzer;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StreamingService.LocalBinder binder =
                    (StreamingService.LocalBinder) service;

            streamingService = binder.getService();
            analyzer = streamingService.getImageAnalyzer();

            if (analyzer == null) {
                Toast.makeText(MainActivity.this,
                        "Analyzer not ready yet", Toast.LENGTH_SHORT).show();
                return;
            }

            serviceBound = true;
            startCamera();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            streamingService = null;
            analyzer = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUI();
        checkAndRequestPermissions();
    }

    private void setupUI() {

        binding.switchCamera.setOnClickListener(v -> {
            cameraSelector =
                    cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
                            ? CameraSelector.DEFAULT_FRONT_CAMERA
                            : CameraSelector.DEFAULT_BACK_CAMERA;

            if (cameraProvider != null) {
                startCamera();
            }
        });

        binding.btnStartStop.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                startStreaming();
            }
        });

        binding.chipGroupQuality.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int id = checkedIds.get(0);
            if (id == R.id.chipLow) currentQuality = StreamQuality.LOW;
            else if (id == R.id.chipMedium) currentQuality = StreamQuality.MEDIUM;
            else if (id == R.id.chipHigh) currentQuality = StreamQuality.HIGH;

            if (isStreaming && serviceBound && streamingService != null) {
                streamingService.setQuality(currentQuality);
                startCamera();
            }
        });

        updateUIState(false);
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            initializeCamera();
        }
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this,
                        "Camera error: " + e.getMessage(),
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
                .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        if (isStreaming && serviceBound && analyzer != null) {
            imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(this),
                    analyzer
            );
        }

        cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
        );
    }

    private void startStreaming() {
        Intent intent = new Intent(this, StreamingService.class);
        intent.putExtra("quality", currentQuality.name());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        isStreaming = true;
        updateUIState(true);
        displayStreamingInfo();
    }

    private void stopStreaming() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        stopService(new Intent(this, StreamingService.class));

        isStreaming = false;
        streamingService = null;
        analyzer = null;

        updateUIState(false);
        startCamera();
    }

    private void updateUIState(boolean streaming) {
        binding.btnStartStop.setText(streaming ? "Stop Streaming" : "Start Streaming");
        binding.chipGroupQuality.setEnabled(!streaming);
        binding.urlContainer.setVisibility(streaming ? View.VISIBLE : View.GONE);
    }

    private void displayStreamingInfo() {
        String url = "http://" + getLocalIpAddress() + ":" + StreamingService.PORT;
        binding.tvUrl.setText(url);
    }

    private String getLocalIpAddress() {
        WifiManager wifiManager =
                (WifiManager) getApplicationContext()
                        .getSystemService(WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        return Formatter.formatIpAddress(info.getIpAddress());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isStreaming) stopStreaming();
    }
}