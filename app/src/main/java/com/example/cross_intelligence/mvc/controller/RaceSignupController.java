package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import io.realm.Realm;
import io.realm.RealmResults;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Date;

public class RaceSignupController {
    private Realm realm; // 移除final，便于动态管理
    
    /**
     * 用户赛事列表回调接口
     */
    public interface UserRacesCallback {
        void onLoaded(@NonNull List<Race> races);
        void onError(@NonNull Exception e);
    }

    public RaceSignupController() {
        // 初始化Realm实例（确保Realm已配置）
        this.realm = Realm.getDefaultInstance();
    }

    /**
     * 报名赛事（适配详情页：无需contact参数，若需要可保留）
     * @param userId 用户ID
     * @param raceId 赛事ID
     * @return 报名是否成功
     */
    public boolean signupRace(String userId, String raceId) {
        // 重载方法：兼容无contact的场景（详情页报名可能不需要联系方式）
        return signupRace(userId, raceId, null);
    }

    /**
     * 报名赛事（带联系方式）
     * @param userId 用户ID
     * @param raceId 赛事ID
     * @param contact 联系方式（可选）
     * @return 报名是否成功
     */
    public boolean signupRace(String userId, String raceId, String contact) {
        // 校验参数
        if (userId == null || raceId == null) {
            return false;
        }

        try {
            realm.beginTransaction();

            // 检查是否已报名
            RaceSignup existing = realm.where(RaceSignup.class)
                    .equalTo("userId", userId)
                    .equalTo("raceId", raceId)
                    .findFirst();
            if (existing != null) {
                realm.cancelTransaction();
                return false; // 已报名，返回失败
            }

            // 创建新报名记录
            RaceSignup signup = realm.createObject(RaceSignup.class, UUID.randomUUID().toString());
            signup.setUserId(userId);
            signup.setRaceId(raceId);
            signup.setSignupTime(new Date());
            if (contact != null) {
                signup.setContact(contact);
            }

            realm.commitTransaction();
            return true;
        } catch (Exception e) {
            if (realm.isInTransaction()) {
                realm.cancelTransaction();
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 取消报名
     * @param userId 用户ID
     * @param raceId 赛事ID
     * @return 取消是否成功
     */
    public boolean cancelSignup(String userId, String raceId) {
        if (userId == null || raceId == null) {
            return false;
        }

        try {
            realm.beginTransaction();

            RaceSignup signup = realm.where(RaceSignup.class)
                    .equalTo("userId", userId)
                    .equalTo("raceId", raceId)
                    .findFirst();

            if (signup != null) {
                signup.deleteFromRealm();
                realm.commitTransaction();
                return true;
            }

            realm.cancelTransaction();
            return false;
        } catch (Exception e) {
            if (realm.isInTransaction()) {
                realm.cancelTransaction();
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查用户是否已报名赛事
     * @param userId 用户ID
     * @param raceId 赛事ID
     * @return 是否已报名
     */
    public boolean isUserSignedUp(String userId, String raceId) {
        if (userId == null || raceId == null) {
            return false;
        }

        RaceSignup existing = realm.where(RaceSignup.class)
                .equalTo("userId", userId)
                .equalTo("raceId", raceId)
                .findFirst();
        return existing != null;
    }

    /**
     * 获取赛事已报名人数（详情页需要检查名额）
     * @param raceId 赛事ID
     * @return 报名人数
     */
    public int getSignedUpCount(String raceId) {
        if (raceId == null) {
            return 0;
        }

        RealmResults<RaceSignup> results = realm.where(RaceSignup.class)
                .equalTo("raceId", raceId)
                .findAll();
        return results.size();
    }

    /**
     * 【解耦优化】获取用户已报名的赛事列表
     * 封装数据库查询逻辑，View层不直接访问Realm
     * 
     * @param userId 用户ID
     * @param callback 回调接口
     */
    public void getRacesForUser(@NonNull String userId, @NonNull UserRacesCallback callback) {
        Realm queryRealm = Realm.getDefaultInstance();
        try {
            // 1. 查询用户所有报名记录
            RealmResults<RaceSignup> signups = queryRealm.where(RaceSignup.class)
                    .equalTo("userId", userId)
                    .findAll();

            if (signups.isEmpty()) {
                callback.onLoaded(new ArrayList<>());
                return;
            }

            // 2. 提取赛事ID列表
            List<String> raceIds = new ArrayList<>();
            for (RaceSignup signup : signups) {
                raceIds.add(signup.getRaceId());
            }

            // 3. 批量查询赛事详情
            RealmResults<Race> races = queryRealm.where(Race.class)
                    .in("raceId", raceIds.toArray(new String[0]))
                    .findAll()
                    .sort("createTime"); // 按创建时间排序

            // 4. 复制到非托管对象（避免Realm线程问题）
            List<Race> raceList = queryRealm.copyFromRealm(races);
            callback.onLoaded(raceList);

        } catch (Exception e) {
            callback.onError(e);
        } finally {
            queryRealm.close();
        }
    }

    /**
     * 【解耦优化】获取用户已报名的赛事ID列表
     * 用于快速查询，不返回完整Race对象
     * 
     * @param userId 用户ID
     * @return 赛事ID列表
     */
    @NonNull
    public List<String> getUserSignedUpRaceIds(@NonNull String userId) {
        Realm queryRealm = Realm.getDefaultInstance();
        try {
            RealmResults<RaceSignup> signups = queryRealm.where(RaceSignup.class)
                    .equalTo("userId", userId)
                    .findAll();

            List<String> raceIds = new ArrayList<>();
            for (RaceSignup signup : signups) {
                raceIds.add(signup.getRaceId());
            }
            return raceIds;
        } finally {
            queryRealm.close();
        }
    }

    /**
     * 关闭Realm连接（避免内存泄漏）
     */
    public void close() {
        if (realm != null && !realm.isClosed()) {
            realm.close();
        }
    }
}