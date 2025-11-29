package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckPoint;

import java.util.UUID;

import io.realm.Realm;

/**
 * 打卡点控制器，负责增删改查以及顺序维护。
 */
public class CheckPointManager {

    public interface OperationCallback {
        void onComplete();
    }

    public void addCheckPoint(String raceId, @NonNull CheckPoint point, @NonNull OperationCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            point.setCheckPointId(UUID.randomUUID().toString());
            point.setRaceId(raceId);
            bgRealm.insertOrUpdate(point);
        }, callback::onComplete);
    }

    public void removeCheckPoint(String checkPointId, @NonNull OperationCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            CheckPoint target = bgRealm.where(CheckPoint.class)
                    .equalTo("checkPointId", checkPointId)
                    .findFirst();
            if (target != null) {
                target.deleteFromRealm();
            }
        }, callback::onComplete);
    }
}




