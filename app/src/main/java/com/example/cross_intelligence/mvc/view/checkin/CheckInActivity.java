package com.example.cross_intelligence.mvc.view.checkin;

import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.model.LatLng;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityCheckInBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.CheckInManager;
import com.example.cross_intelligence.mvc.location.MapLocationManager;
import com.example.cross_intelligence.mvc.location.RaceMapController;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

/**
 * 打卡界面示例：GPS + ZXing 扫码双重验证。
 */
public class CheckInActivity extends BaseActivity implements
        MapLocationManager.LocationCallback,
        RaceMapController.MapEventListener {

    private ActivityCheckInBinding binding;
    private MapLocationManager locationManager;
    private RaceMapController mapController;
    private CheckInManager checkInManager;
    private CheckPoint currentPoint;
    private double lastLat;
    private double lastLng;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), this::handleScanResult);

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapController = new RaceMapController(binding.mapView);
        mapController.onCreate(savedInstanceState);
        mapController.setMapEventListener(this);
        initView();
        initData();
        locationManager = new MapLocationManager(this, this);
    }

    @Override
    protected void initView() {
        binding.btnScanQr.setOnClickListener(v -> startScan());
    }

    @Override
    protected void initData() {
        checkInManager = new CheckInManager();
        currentPoint = new CheckPoint();
        currentPoint.setCheckPointId("cp-demo");
        currentPoint.setName("示例打卡点");
        currentPoint.setLatitude(30.0000);
        currentPoint.setLongitude(120.0000);
        currentPoint.setQrCodePayload("DEMO_QR");
        mapController.addCheckPoints(java.util.Collections.singletonList(currentPoint));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapController != null) {
            mapController.onResume();
        }
        if (locationManager != null) {
            locationManager.setHighPrecision(true);
            locationManager.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapController != null) {
            mapController.onPause();
        }
        if (locationManager != null) {
            locationManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapController != null) {
            mapController.onDestroy();
        }
        if (locationManager != null) {
            locationManager.destroy();
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setBeepEnabled(false);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("请对准打卡点二维码");
        qrLauncher.launch(options);
    }

    private void handleScanResult(ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            UIUtil.showToast(this, "未识别到二维码");
            return;
        }
        binding.tvQrContent.setText(result.getContents());
        performCheckIn(result.getContents());
    }

    private void performCheckIn(String qrContent) {
        binding.progressBar.setVisibility(View.VISIBLE);
        checkInManager.checkIn("race-demo", "user-demo", currentPoint,
                lastLat, lastLng, qrContent, !binding.switchOnline.isChecked(),
                new CheckInManager.CheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull com.example.cross_intelligence.mvc.model.CheckInRecord record) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.tvStatus.setText(record.isOffline() ? "离线打卡成功" : "打卡成功");
                            UIUtil.showToast(CheckInActivity.this, binding.tvStatus.getText().toString());
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.tvStatus.setText(throwable.getMessage());
                            UIUtil.showToast(CheckInActivity.this,
                                    throwable.getMessage() != null ? throwable.getMessage() : "打卡失败");
                        });
                    }
                });
    }

    @Override
    public void onLocationUpdate(double lat, double lng, float accuracy) {
        lastLat = lat;
        lastLng = lng;
        runOnUiThread(() -> {
            binding.tvLocation.setText(getString(R.string.location_format, lat, lng, accuracy));
            mapController.moveCamera(lat, lng);
        });
    }

    @Override
    public void onLocationError(int errorCode, String errorInfo) {
        runOnUiThread(() -> binding.tvStatus.setText(
                getString(R.string.location_error_format, errorCode, errorInfo)));
    }

    @Override
    public void onMapClicked(@NonNull LatLng latLng) {
        UIUtil.showToast(this, getString(R.string.map_click_format, latLng.latitude, latLng.longitude));
    }

    @Override
    public void onMarkerClicked(@NonNull CheckPoint point) {
        UIUtil.showToast(this, "当前打卡点：" + point.getName());
    }
}


