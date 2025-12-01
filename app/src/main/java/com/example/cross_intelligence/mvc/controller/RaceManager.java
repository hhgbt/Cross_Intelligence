package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * 赛事控制器，处理创建、编辑、查询及打卡点同步。
 */
public class RaceManager {

    /**
     * CheckPoint 数据容器，用于在不同线程间传递数据
     */
    public static class CheckPointData {
        public String checkPointId;
        public String name;
        public double latitude;
        public double longitude;
        public String type;
        public double checkRadius;
        public int orderIndex;
    }

    public interface RaceListCallback {
        void onLoaded(@NonNull List<Race> races);
    }


    public interface SaveCallback {
        void onSuccess();
        void onError(@NonNull Throwable error);
    }

    public void createRace(String name, String description, Date start, Date end, List<CheckPointData> pointsData, String organizerId, @NonNull SaveCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(
            bgRealm -> {
                // 计算序号：获取该管理员已有的赛事数量 + 1
                long existingCount = bgRealm.where(Race.class)
                        .equalTo("organizerId", organizerId)
                        .count();
                int sequenceNumber = (int) existingCount + 1;
                
                // 创建赛事
                Race race = bgRealm.createObject(Race.class, UUID.randomUUID().toString());
                String raceId = race.getRaceId();
                race.setName(name);
                race.setDescription(description != null ? description : "");
                race.setStartTime(start);
                race.setEndTime(end);
                race.setOrganizerId(organizerId);
                race.setCreateTime(new Date()); // 设置创建时间
                race.setSequenceNumber(sequenceNumber); // 设置序号
                
                // 在后台线程创建 CheckPoint 对象
                RealmList<CheckPoint> realmPoints = new RealmList<>();
                for (CheckPointData data : pointsData) {
                    // 创建新的 CheckPoint 对象（使用新的 UUID 避免冲突）
                    String newId = UUID.randomUUID().toString();
                    CheckPoint newPoint = bgRealm.createObject(CheckPoint.class, newId);
                    newPoint.setRaceId(raceId);
                    newPoint.setName(data.name);
                    newPoint.setLatitude(data.latitude);
                    newPoint.setLongitude(data.longitude);
                    newPoint.setType(data.type != null ? data.type : "检查点");
                    newPoint.setCheckRadius(data.checkRadius > 0 ? data.checkRadius : 50.0);
                    newPoint.setOrderIndex(data.orderIndex);
                    realmPoints.add(newPoint);
                }
                race.setCheckPoints(realmPoints);
            },
            () -> {
                realm.close();
                callback.onSuccess();
            },
            error -> {
                realm.close();
                callback.onError(error);
            }
        );
    }



    public void queryUpcomingRaces(@NonNull RaceListCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Race> results = realm.where(Race.class)
                .greaterThan("endTime", new Date())
                .findAllAsync();
        results.addChangeListener((races, changeSet) -> callback.onLoaded(realm.copyFromRealm(races)));
    }

    /**
     * 查询指定管理员创建的赛事列表
     */
    public void queryRacesByOrganizer(@NonNull String organizerId, @NonNull RaceListCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Race> results = realm.where(Race.class)
                .equalTo("organizerId", organizerId)
                .findAll();
        // 转换为 List 后按序号排序（从小到大，先创建的在前）
        List<Race> raceList = realm.copyFromRealm(results);
        raceList.sort((r1, r2) -> {
            int seq1 = r1.getSequenceNumber();
            int seq2 = r2.getSequenceNumber();
            // 如果序号相同或为0，按创建时间排序
            if (seq1 == seq2 || (seq1 == 0 && seq2 == 0)) {
                Date d1 = r1.getCreateTime();
                Date d2 = r2.getCreateTime();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d1.compareTo(d2); // 创建时间早的在前
            }
            // 按序号排序：序号小的在前
            return Integer.compare(seq1, seq2);
        });
        callback.onLoaded(raceList);
        realm.close();
    }

    /**
     * 根据赛事ID查询赛事详情
     */
    @androidx.annotation.Nullable
    public Race getRaceById(@NonNull String raceId) {
        Realm realm = Realm.getDefaultInstance();
        Race race = realm.where(Race.class)
                .equalTo("raceId", raceId)
                .findFirst();
        Race result = race != null ? realm.copyFromRealm(race) : null;
        realm.close();
        return result;
    }

    /**
     * 删除指定赛事
     */
    public void deleteRace(@NonNull String raceId) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(
            bgRealm -> {
                Race race = bgRealm.where(Race.class)
                        .equalTo("raceId", raceId)
                        .findFirst();
                if (race != null) {
                    // 删除关联的打卡点
                    if (race.getCheckPoints() != null) {
                        race.getCheckPoints().deleteAllFromRealm();
                    }
                    // 删除赛事
                    race.deleteFromRealm();
                }
            },
            realm::close,
            error -> {
                realm.close();
                error.printStackTrace();
            }
        );
    }

    /**
     * 更新赛事信息
     */
    public void updateRace(@NonNull String raceId, String name, String description, Date start, Date end, List<CheckPointData> pointsData, @NonNull SaveCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(
            bgRealm -> {
                Race race = bgRealm.where(Race.class)
                        .equalTo("raceId", raceId)
                        .findFirst();
                if (race != null) {
                    race.setName(name);
                    race.setDescription(description != null ? description : "");
                    race.setStartTime(start);
                    race.setEndTime(end);
                    
                    // 删除旧的打卡点
                    if (race.getCheckPoints() != null) {
                        race.getCheckPoints().deleteAllFromRealm();
                    }
                    
                    // 添加新的打卡点
                    RealmList<CheckPoint> realmPoints = new RealmList<>();
                    for (CheckPointData data : pointsData) {
                        // 创建新的 CheckPoint 对象（使用新的 UUID 避免冲突）
                        String newId = UUID.randomUUID().toString();
                        CheckPoint newPoint = bgRealm.createObject(CheckPoint.class, newId);
                        newPoint.setRaceId(raceId);
                        newPoint.setName(data.name);
                        newPoint.setLatitude(data.latitude);
                        newPoint.setLongitude(data.longitude);
                        newPoint.setType(data.type != null ? data.type : "检查点");
                        newPoint.setCheckRadius(data.checkRadius > 0 ? data.checkRadius : 50.0);
                        newPoint.setOrderIndex(data.orderIndex);
                        realmPoints.add(newPoint);
                    }
                    race.setCheckPoints(realmPoints);
                } else {
                    throw new IllegalStateException("赛事不存在：raceId=" + raceId);
                }
            },
            () -> {
                realm.close();
                callback.onSuccess();
            },
            error -> {
                realm.close();
                callback.onError(error);
            }
        );
    }


}




