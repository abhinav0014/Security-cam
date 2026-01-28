// ScanFragment.java - Fixed version with single scan and detailed dialog
package com.qrmaster.app.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.os.Build;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private boolean isScanning = true; // Control flag
    private long lastScanTime = 0;
    private static final long SCAN_COOLDOWN = 2000; // 2 seconds cooldown

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
            if (isScanning) {
                processImage(image);
            } else {
                image.close();
            }
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

        // Check cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_COOLDOWN) {
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
                    if (content != null && isScanning) {
                        isScanning = false; // Stop scanning
                        lastScanTime = System.currentTimeMillis();
                        handleScannedCode(content, barcode);
                        break;
                    }
                }
            })
            .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleScannedCode(String content, Barcode barcode) {
        String qrType = getQRType(barcode.getValueType(), content);
        
        requireActivity().runOnUiThread(() -> {
            showQRDetailDialog(content, qrType, barcode);
        });
    }

    private void showQRDetailDialog(String content, String type, Barcode barcode) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_detail, null);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("QR Code Scanned")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                saveQRCode(content, type);
                isScanning = true; // Resume scanning
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                isScanning = true; // Resume scanning
            })
            .setOnDismissListener(dialog -> {
                isScanning = true; // Resume scanning
            });

        // Setup dialog content based on type
        setupDialogContent(dialogView, content, type, barcode);
        
        builder.show();
    }

    private void setupDialogContent(View view, String content, String type, Barcode barcode) {
        android.widget.TextView tvType = view.findViewById(R.id.tv_qr_type);
        android.widget.TextView tvContent = view.findViewById(R.id.tv_qr_content);
        android.widget.LinearLayout actionButtons = view.findViewById(R.id.action_buttons);
        
        tvType.setText(type);
        
        actionButtons.removeAllViews();
        
        switch (type) {
            case "URL":
                tvContent.setText(content);
                addActionButton(actionButtons, "Open in Browser", () -> openUrl(content));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(content));
                break;
                
            case "WiFi":
                String wifiInfo = parseWiFiInfo(content);
                tvContent.setText(wifiInfo);
                addActionButton(actionButtons, "Connect", () -> connectToWiFi(content));
                addActionButton(actionButtons, "Copy Password", () -> {
                    String password = extractWiFiPassword(content);
                    copyToClipboard(password);
                });
                break;
                
            case "Email":
                String email = extractEmail(barcode);
                tvContent.setText(email);
                addActionButton(actionButtons, "Send Email", () -> sendEmail(email));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(email));
                break;
                
            case "Phone":
                String phone = extractPhone(barcode);
                tvContent.setText(phone);
                addActionButton(actionButtons, "Call", () -> dialPhone(phone));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(phone));
                break;
                
            case "SMS":
                String smsInfo = parseSMSInfo(barcode);
                tvContent.setText(smsInfo);
                addActionButton(actionButtons, "Send SMS", () -> sendSMS(barcode));
                break;
                
            case "Contact":
                String contactInfo = parseContactInfo(barcode);
                tvContent.setText(contactInfo);
                addActionButton(actionButtons, "Add to Contacts", () -> addContact(barcode));
                break;
                
            case "Location":
                String locationInfo = parseLocationInfo(barcode);
                tvContent.setText(locationInfo);
                addActionButton(actionButtons, "Open in Maps", () -> openLocation(barcode));
                break;
                
            default:
                tvContent.setText(content);
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(content));
                break;
        }
    }

    private void addActionButton(android.widget.LinearLayout container, String text, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        
        container.addView(button);
    }

    // WiFi Connection Methods
    private String parseWiFiInfo(String content) {
        String ssid = "";
        String password = "";
        String security = "";
        
        if (content.startsWith("WIFI:")) {
            String[] parts = content.substring(5).split(";");
            for (String part : parts) {
                if (part.startsWith("S:")) ssid = part.substring(2);
                else if (part.startsWith("P:")) password = part.substring(2);
                else if (part.startsWith("T:")) security = part.substring(2);
            }
        }
        
        return "Network: " + ssid + "\nPassword: " + password + "\nSecurity: " + security;
    }

    private String extractWiFiPassword(String content) {
        if (content.startsWith("WIFI:")) {
            String[] parts = content.substring(5).split(";");
            for (String part : parts) {
                if (part.startsWith("P:")) return part.substring(2);
            }
        }
        return "";
    }

    private void connectToWiFi(String content) {
        String ssid = "";
        String password = "";
        String security = "";
        
        if (content.startsWith("WIFI:")) {
            String[] parts = content.substring(5).split(";");
            for (String part : parts) {
                if (part.startsWith("S:")) ssid = part.substring(2);
                else if (part.startsWith("P:")) password = part.substring(2);
                else if (part.startsWith("T:")) security = part.substring(2);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWiFiAndroid10Plus(ssid, password, security);
        } else {
            connectWiFiLegacy(ssid, password, security);
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectWiFiAndroid10Plus(String ssid, String password, String security) {

    WifiNetworkSuggestion.Builder builder =
            new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid);

    if ("WPA".equalsIgnoreCase(security) || "WPA2".equalsIgnoreCase(security)) {
        builder.setWpa2Passphrase(password);
    } else if ("WPA3".equalsIgnoreCase(security)) {
        builder.setWpa3Passphrase(password);
    } else {
        builder.setIsAppInteractionRequired(true);
    }

    ArrayList<WifiNetworkSuggestion> suggestions = new ArrayList<>();
    suggestions.add(builder.build());

    Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
    intent.putParcelableArrayListExtra(
            Settings.EXTRA_WIFI_NETWORK_LIST,
            suggestions
    );

    startActivity(intent);
}

    @SuppressWarnings("deprecation")
    private void connectWiFiLegacy(String ssid, String password, String security) {
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + ssid + "\"";
        
        if (security.equals("WPA") || security.equals("WPA2")) {
            wifiConfig.preSharedKey = "\"" + password + "\"";
        } else if (security.equals("WEP")) {
            wifiConfig.wepKeys[0] = "\"" + password + "\"";
            wifiConfig.wepTxKeyIndex = 0;
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else {
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        
        int netId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
        
        Toast.makeText(requireContext(), "Connecting to WiFi...", Toast.LENGTH_SHORT).show();
    }

    // Email, Phone, SMS Methods
    private String extractEmail(Barcode barcode) {
        if (barcode.getEmail() != null) {
            return barcode.getEmail().getAddress();
        }
        return barcode.getRawValue();
    }

    private String extractPhone(Barcode barcode) {
        if (barcode.getPhone() != null) {
            return barcode.getPhone().getNumber();
        }
        return barcode.getRawValue();
    }

    private String parseSMSInfo(Barcode barcode) {
        if (barcode.getSms() != null) {
            return "To: " + barcode.getSms().getPhoneNumber() + "\nMessage: " + barcode.getSms().getMessage();
        }
        return barcode.getRawValue();
    }

    private String parseContactInfo(Barcode barcode) {
        if (barcode.getContactInfo() != null) {
            Barcode.ContactInfo contact = barcode.getContactInfo();
            StringBuilder info = new StringBuilder();
            if (contact.getName() != null) info.append("Name: ").append(contact.getName().getFormattedName()).append("\n");
            if (contact.getPhones() != null && !contact.getPhones().isEmpty()) {
                info.append("Phone: ").append(contact.getPhones().get(0).getNumber()).append("\n");
            }
            if (contact.getEmails() != null && !contact.getEmails().isEmpty()) {
                info.append("Email: ").append(contact.getEmails().get(0).getAddress());
            }
            return info.toString();
        }
        return barcode.getRawValue();
    }

    private String parseLocationInfo(Barcode barcode) {
        if (barcode.getGeoPoint() != null) {
            return "Latitude: " + barcode.getGeoPoint().getLat() + "\nLongitude: " + barcode.getGeoPoint().getLng();
        }
        return barcode.getRawValue();
    }

    // Action Methods
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void sendEmail(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        startActivity(intent);
    }

    private void dialPhone(String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        startActivity(intent);
    }

    private void sendSMS(Barcode barcode) {
        if (barcode.getSms() != null) {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + barcode.getSms().getPhoneNumber()));
            intent.putExtra("sms_body", barcode.getSms().getMessage());
            startActivity(intent);
        }
    }

    private void addContact(Barcode barcode) {
        if (barcode.getContactInfo() != null) {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
            startActivity(intent);
        }
    }

    private void openLocation(Barcode barcode) {
        if (barcode.getGeoPoint() != null) {
            String uri = "geo:" + barcode.getGeoPoint().getLat() + "," + barcode.getGeoPoint().getLng();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(intent);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("QR Code", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void saveQRCode(String content, String type) {
        QRItem item = new QRItem(content, type, System.currentTimeMillis());
        item.setGenerated(false);
        viewModel.insert(item);
        Toast.makeText(requireContext(), "Saved to history", Toast.LENGTH_SHORT).show();
    }

    private String getQRType(int barcodeType, String content) {
        switch (barcodeType) {
            case Barcode.TYPE_URL: return "URL";
            case Barcode.TYPE_WIFI: return "WiFi";
            case Barcode.TYPE_EMAIL: return "Email";
            case Barcode.TYPE_PHONE: return "Phone";
            case Barcode.TYPE_SMS: return "SMS";
            case Barcode.TYPE_CONTACT_INFO: return "Contact";
            case Barcode.TYPE_GEO: return "Location";
            default:
                // Additional type detection based on content
                if (content.startsWith("http://") || content.startsWith("https://")) return "URL";
                if (content.startsWith("WIFI:")) return "WiFi";
                if (content.startsWith("mailto:")) return "Email";
                if (content.startsWith("tel:")) return "Phone";
                if (content.startsWith("smsto:")) return "SMS";
                if (content.startsWith("BEGIN:VCARD")) return "Contact";
                if (content.startsWith("geo:")) return "Location";
                return "Text";
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

    @Override
    public void onResume() {
        super.onResume();
        isScanning = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        isScanning = false;
    }
}
