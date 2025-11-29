package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * 轨迹记录管理器，负责采样过滤、批量写入与查询。
 */
public class TrackManager {

    private static final double MIN_DISTANCE_METERS = 5.0;
    private static final long MIN_INTERVAL_MS = 4_000;
    private static final int BATCH_SIZE = 5;

    private final List<TrackPoint> pending = new ArrayList<>();
    private TrackPoint lastPoint;
    private long lastSaveTime;

    public synchronized void onLocationUpdate(@NonNull String raceId,
                                              @NonNull String userId,
                                              double lat,
                                              double lng,
                                              float accuracy,
                                              float speed,
                                              long timestampMillis) {
        if (accuracy > 30f) {
            return;
        }
        if (lastPoint != null) {
            double distance = DistanceUtil.distanceMeters(lastPoint.getLatitude(), lastPoint.getLongitude(), lat, lng);
            if (distance < MIN_DISTANCE_METERS && (timestampMillis - lastSaveTime) < MIN_INTERVAL_MS) {
                return;
            }
        }

        TrackPoint point = new TrackPoint();
        point.setPointId(UUID.randomUUID().toString());
        point.setRaceId(raceId);
        point.setUserId(userId);
        point.setLatitude(lat);
        point.setLongitude(lng);
        point.setSpeed(speed);
        point.setTimestamp(new Date(timestampMillis));

        pending.add(point);
        lastPoint = point;
        lastSaveTime = timestampMillis;
        if (pending.size() >= BATCH_SIZE) {
            flushAsync();
        }
    }

    public synchronized void flushAsync() {
        if (pending.isEmpty()) {
            return;
        }
        List<TrackPoint> batch = new ArrayList<>(pending);
        pending.clear();
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            for (TrackPoint p : batch) {
                TrackPoint target = bgRealm.createObject(TrackPoint.class, p.getPointId());
                target.setRaceId(p.getRaceId());
                target.setUserId(p.getUserId());
                target.setLatitude(p.getLatitude());
                target.setLongitude(p.getLongitude());
                target.setSpeed(p.getSpeed());
                target.setTimestamp(p.getTimestamp());
            }
        }, realm::close, error -> realm.close());
    }

    public List<TrackPoint> queryTrack(@NonNull String raceId, @NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<TrackPoint> results = realm.where(TrackPoint.class)
                .equalTo("raceId", raceId)
                .equalTo("userId", userId)
                .sort("timestamp")
                .findAll();
        List<TrackPoint> copy = realm.copyFromRealm(results);
        realm.close();
        return copy;
    }
}




