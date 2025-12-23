package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cross_intelligence.mvc.model.RaceSession;

import io.realm.Realm;

/**
 * 比赛状态恢复管理器
 * 用于实现"断点续赛"功能：App崩溃或重启后，自动恢复正在进行的比赛
 */
public class RaceRecoveryManager {

    /**
     * 恢复信息回调
     */
    public interface RecoveryCallback {
        void onRecovered(@NonNull RaceSession session);
        void onNoNeedRecovery();
    }

    /**
     * 检查并恢复未完成的比赛
     * 在 Activity 的 onCreate 中调用
     *
     * @param userId   当前用户ID
     * @param callback 回调
     */
    public void checkAndRecover(@NonNull String userId, @NonNull RecoveryCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        try {
            // 查找状态为"进行中"的比赛会话
            RaceSession session = realm.where(RaceSession.class)
                    .equalTo("userId", userId)
                    .equalTo("status", RaceSession.STATUS_IN_PROGRESS)
                    .findFirst();

            if (session != null) {
                // 发现未完成的比赛，需要恢复
                RaceSession copiedSession = realm.copyFromRealm(session);
                callback.onRecovered(copiedSession);
            } else {
                // 没有需要恢复的比赛
                callback.onNoNeedRecovery();
            }
        } finally {
            realm.close();
        }
    }

    /**
     * 检查是否有未完成的比赛（同步方法，用于快速判断）
     *
     * @param userId 当前用户ID
     * @return 未完成的比赛会话，如果没有则返回 null
     */
    @Nullable
    public RaceSession getUnfinishedRace(@NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            RaceSession session = realm.where(RaceSession.class)
                    .equalTo("userId", userId)
                    .equalTo("status", RaceSession.STATUS_IN_PROGRESS)
                    .findFirst();

            return session != null ? realm.copyFromRealm(session) : null;
        } finally {
            realm.close();
        }
    }

    /**
     * 检查指定赛事是否有未完成的比赛
     *
     * @param raceId 赛事ID
     * @param userId 用户ID
     * @return 未完成的比赛会话，如果没有则返回 null
     */
    @Nullable
    public RaceSession getUnfinishedRaceForEvent(@NonNull String raceId, @NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            RaceSession session = realm.where(RaceSession.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .equalTo("status", RaceSession.STATUS_IN_PROGRESS)
                    .findFirst();

            return session != null ? realm.copyFromRealm(session) : null;
        } finally {
            realm.close();
        }
    }

    /**
     * 判断是否应该恢复比赛状态
     * 考虑时间因素：如果比赛开始时间超过24小时，可能不需要恢复
     *
     * @param session 会话
     * @return true = 应该恢复，false = 不需要恢复
     */
    public boolean shouldRecover(@NonNull RaceSession session) {
        if (session.getStartTime() == null) {
            return false;
        }

        // 计算比赛已经过了多久
        long elapsedHours = (System.currentTimeMillis() - session.getStartTime().getTime()) / (1000 * 60 * 60);

        // 如果超过24小时，可能是异常会话，不自动恢复
        if (elapsedHours > 24) {
            return false;
        }

        return session.isInProgress();
    }

    /**
     * 清理异常会话（超时或状态异常的会话）
     *
     * @param userId 用户ID
     * @return 清理的会话数量
     */
    public int cleanupAbnormalSessions(@NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            final int[] count = {0};
            realm.executeTransaction(r -> {
                // 查找状态为"进行中"但开始时间超过24小时的会话
                long threshold = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                
                // 注意：Realm 的日期查询需要使用 Date 对象
                var sessions = r.where(RaceSession.class)
                        .equalTo("userId", userId)
                        .equalTo("status", RaceSession.STATUS_IN_PROGRESS)
                        .findAll();

                for (RaceSession session : sessions) {
                    if (session.getStartTime() != null && 
                        session.getStartTime().getTime() < threshold) {
                        // 标记为未完成（DNF）
                        session.setStatus(RaceSession.STATUS_DNF);
                        session.setTrackingEnabled(false);
                        count[0]++;
                    }
                }
            });
            return count[0];
        } finally {
            realm.close();
        }
    }
}










