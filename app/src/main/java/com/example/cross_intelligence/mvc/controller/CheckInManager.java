package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

/**
 * 打卡管理
 * 器：负责 GPS + QR 双重验证、离线记录、状态查询。
 */
public class CheckInManager {

    public interface CheckInCallback {
        void onSuccess(@NonNull CheckInRecord record);

        void onFailure(@NonNull Throwable throwable);
    }

    private static final double DEFAULT_RADIUS_METERS = 50.0;

    public void checkIn(@NonNull String raceId,
                        @NonNull String userId,
                        @NonNull CheckPoint checkPoint,
                        double currentLat,
                        double currentLng,
                        @NonNull String scannedQr,
                        boolean isOffline,
                        @NonNull CheckInCallback callback) {

        double distance = DistanceUtil.distanceMeters(currentLat, currentLng,
                checkPoint.getLatitude(), checkPoint.getLongitude());
        if (distance > DEFAULT_RADIUS_METERS) {
            callback.onFailure(new IllegalStateException("未进入打卡范围"));
            return;
        }
        if (!scannedQr.equals(checkPoint.getQrCodePayload())) {
            callback.onFailure(new IllegalArgumentException("二维码信息不匹配"));
            return;
        }

        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            CheckInRecord record = bgRealm.createObject(CheckInRecord.class, UUID.randomUUID().toString());
            record.setRaceId(raceId);
            record.setUserId(userId);
            record.setCheckPointId(checkPoint.getCheckPointId());
            record.setLatitude(currentLat);
            record.setLongitude(currentLng);
            record.setTimestamp(new Date());
            record.setOffline(isOffline);
            record.setSynced(!isOffline);
        }, () -> {
            CheckInRecord stored = realm.where(CheckInRecord.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .equalTo("checkPointId", checkPoint.getCheckPointId())
                    .findAll()
                    .last();
            if (stored != null) {
                callback.onSuccess(realm.copyFromRealm(stored));
            } else {
                callback.onFailure(new IllegalStateException("记录读取失败"));
            }
            realm.close();
        }, (@NonNull Throwable error) -> {
            realm.close();
            callback.onFailure(error);
        });
    }
}


