package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.RaceSession;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

/**
 * 比赛会话管理器
 * 负责管理选手的比赛状态机
 */
public class RaceSessionManager {

    /**
     * 获取或创建比赛会话
     */
    public RaceSession getOrCreateSession(@NonNull String raceId, @NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            RaceSession session = realm.where(RaceSession.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .findFirst();

            if (session != null) {
                return realm.copyFromRealm(session);
            }

            // 创建新会话
            String sessionId = UUID.randomUUID().toString();
            RaceSession newSession = new RaceSession();
            newSession.setSessionId(sessionId);
            newSession.setRaceId(raceId);
            newSession.setUserId(userId);
            newSession.setStatus(RaceSession.STATUS_NOT_STARTED);
            newSession.setCreateTime(new Date());
            newSession.setUpdateTime(new Date());
            newSession.setCheckpointsChecked(0);
            newSession.setTrackingEnabled(false);

            // 【修复】保存到 Realm 并重新查询，返回托管对象的副本
            realm.executeTransaction(r -> r.copyToRealm(newSession));
            
            // 重新查询刚创建的会话（因为 newSession 是非托管对象，不能直接 copyFromRealm）
            RaceSession createdSession = realm.where(RaceSession.class)
                    .equalTo("sessionId", sessionId)
                    .findFirst();
            
            return createdSession != null ? realm.copyFromRealm(createdSession) : newSession;
        } finally {
            realm.close();
        }
    }

    /**
     * 获取会话（不创建）
     */
    @Nullable
    public RaceSession getSession(@NonNull String raceId, @NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            RaceSession session = realm.where(RaceSession.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .findFirst();
            return session != null ? realm.copyFromRealm(session) : null;
        } finally {
            realm.close();
        }
    }

    /**
     * 起点打卡：激活赛程
     */
    public void startRace(@NonNull String sessionId, double lat, double lng) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null && !session.isStarted()) {
                    session.setStatus(RaceSession.STATUS_IN_PROGRESS);
                    session.setStartTime(new Date());
                    session.setStartLatitude(lat);
                    session.setStartLongitude(lng);
                    session.setTrackingEnabled(true);
                    session.setUpdateTime(new Date());
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 检查点打卡：增加计数
     */
    public void checkCheckpoint(@NonNull String sessionId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null && session.isInProgress()) {
                    session.setCheckpointsChecked(session.getCheckpointsChecked() + 1);
                    session.setUpdateTime(new Date());
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 终点打卡：完成比赛
     */
    public void finishRace(@NonNull String sessionId, double lat, double lng) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null && session.isInProgress()) {
                    Date endTime = new Date();
                    session.setStatus(RaceSession.STATUS_FINISHED);
                    session.setEndTime(endTime);
                    session.setEndLatitude(lat);
                    session.setEndLongitude(lng);
                    session.setTrackingEnabled(false);

                    // 计算总用时
                    if (session.getStartTime() != null) {
                        long totalMillis = endTime.getTime() - session.getStartTime().getTime();
                        session.setTotalMillis(totalMillis);
                    }

                    session.setUpdateTime(new Date());
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 设置总检查点数量
     */
    public void setTotalCheckpoints(@NonNull String sessionId, int total) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null) {
                    session.setTotalCheckpoints(total);
                    session.setUpdateTime(new Date());
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 标记为未完成（DNF）
     */
    public void markAsDNF(@NonNull String sessionId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null) {
                    session.setStatus(RaceSession.STATUS_DNF);
                    session.setTrackingEnabled(false);
                    session.setUpdateTime(new Date());
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 删除会话
     */
    public void deleteSession(@NonNull String sessionId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            realm.executeTransaction(r -> {
                RaceSession session = r.where(RaceSession.class)
                        .equalTo("sessionId", sessionId)
                        .findFirst();
                if (session != null) {
                    session.deleteFromRealm();
                }
            });
        } finally {
            realm.close();
        }
    }

    /**
     * 判断是否可以开始比赛（在起点打卡）
     */
    public boolean canStartRace(@NonNull CheckPoint checkPoint, @NonNull RaceSession session) {
        return CheckPoint.TYPE_START.equals(checkPoint.getType()) 
                && !session.isStarted();
    }

    /**
     * 判断是否可以在检查点打卡
     */
    public boolean canCheckCheckpoint(@NonNull CheckPoint checkPoint, @NonNull RaceSession session) {
        return CheckPoint.TYPE_CHECKPOINT.equals(checkPoint.getType()) 
                && session.isInProgress();
    }

    /**
     * 判断是否可以完成比赛（在终点）
     */
    public boolean canFinishRace(@NonNull CheckPoint checkPoint, @NonNull RaceSession session) {
        return CheckPoint.TYPE_FINISH.equals(checkPoint.getType()) 
                && session.isInProgress();
    }
}

