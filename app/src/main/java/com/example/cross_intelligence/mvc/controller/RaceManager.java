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

import com.example.cross_intelligence.mvc.util.DataConverter;
import cn.leancloud.LCObject;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import cn.leancloud.LCQuery;

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
        public String qrCodePayload; // 二维码内容
    }

    public interface RaceListCallback {
        void onLoaded(@NonNull List<Race> races);
    }


    public interface SaveCallback {
        void onSuccess();
        void onError(@NonNull Throwable error);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull Throwable error);
    }

    public void createRace(String name, String description, Date start, Date end, List<CheckPointData> pointsData, String organizerId, @NonNull SaveCallback callback) {
        createRaceWithId(UUID.randomUUID().toString(), name, description, start, end, pointsData, organizerId, null, callback);
    }

    /**
     * 使用指定 ID 创建赛事（用于二维码生成）
     */
    public void createRaceWithId(String raceId, String name, String description, Date start, Date end, List<CheckPointData> pointsData, String organizerId, String thumbnailPath, @NonNull SaveCallback callback) {
        android.util.Log.d("RaceManager", "========== createRaceWithId() 被调用 ==========");
        android.util.Log.d("RaceManager", "raceId: " + raceId);
        android.util.Log.d("RaceManager", "接收到的 thumbnailPath: " + thumbnailPath);
        
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(
            bgRealm -> {
                // 计算序号：获取该管理员已有的赛事数量 + 1
                long existingCount = bgRealm.where(Race.class)
                        .equalTo("organizerId", organizerId)
                        .count();
                int sequenceNumber = (int) existingCount + 1;
                
                // 创建赛事（使用指定的 raceId）
                Race race = bgRealm.createObject(Race.class, raceId);
                race.setName(name);
                race.setDescription(description != null ? description : "");
                race.setStartTime(start);
                race.setEndTime(end);
                race.setOrganizerId(organizerId);
                race.setCreateTime(new Date()); // 设置创建时间
                race.setSequenceNumber(sequenceNumber); // 设置序号
                
                // 【关键】缓略图路径的设置
                android.util.Log.d("RaceManager", "【事务】设置 thumbnailPath 前，race.getThumbnailPath(): " + race.getThumbnailPath());
                race.setThumbnailPath(thumbnailPath);
                android.util.Log.d("RaceManager", "【事务】设置 thumbnailPath 后，race.getThumbnailPath(): " + race.getThumbnailPath());
                android.util.Log.d("RaceManager", "【事务】参数 thumbnailPath: " + thumbnailPath);
                
                race.setSynced(false); // 标记为未同步
                
                // 在后台线程创建 CheckPoint 对象
                RealmList<CheckPoint> realmPoints = new RealmList<>();
                for (CheckPointData data : pointsData) {
                    // 创建新的 CheckPoint 对象（使用新的 UUID 避免冲突）
                    String newId = UUID.randomUUID().toString();
                    CheckPoint newPoint = bgRealm.createObject(CheckPoint.class, newId);
                    newPoint.setRaceId(race.getRaceId());
                    newPoint.setName(data.name);
                    newPoint.setLatitude(data.latitude);
                    newPoint.setLongitude(data.longitude);
                    newPoint.setType(data.type != null ? data.type : "检查点");
                    newPoint.setCheckRadius(data.checkRadius > 0 ? data.checkRadius : 50.0);
                    newPoint.setOrderIndex(data.orderIndex);
                    newPoint.setQrCodePayload(data.qrCodePayload); // 设置二维码内容
                    realmPoints.add(newPoint);
                }
                race.setCheckPoints(realmPoints);
                
                android.util.Log.d("RaceManager", "事务内 - Race 对象创建完成，thumbnailPath: " + race.getThumbnailPath());
            },
            () -> {
                android.util.Log.d("RaceManager", "事务成功完成");
                
                // 验证数据库中的数据
                Realm verifyRealm = Realm.getDefaultInstance();
                Race savedRace = verifyRealm.where(Race.class).equalTo("raceId", raceId).findFirst();
                if (savedRace != null) {
                    android.util.Log.d("RaceManager", "【验证】数据库中的 thumbnailPath: " + savedRace.getThumbnailPath());
                } else {
                    android.util.Log.e("RaceManager", "【验证失败】无法从数据库读取刚创建的赛事");
                }
                verifyRealm.close();
                
                realm.close();
                callback.onSuccess();
                
                // 【新增】异步同步到 LeanCloud
                syncRaceToCloud(raceId);
            },
            error -> {
                android.util.Log.e("RaceManager", "【错误】事务执行失败: " + error.getMessage(), error);
                error.printStackTrace();
                realm.close();
                callback.onError(error);
            }
        );
    }

    /**
     * 将本地赛事同步到 LeanCloud
     */
    private void syncRaceToCloud(String raceId) {
        // 在新线程中读取数据，避免阻塞 UI
        Realm bgRealm = Realm.getDefaultInstance();
        Race race = bgRealm.where(Race.class).equalTo("raceId", raceId).findFirst();
        
        if (race != null) {
            // 转换为 LeanCloud 对象（需要在主线程或者有 Looper 的线程操作 Realm 对象，或者使用 copyFromRealm）
            Race raceCopy = bgRealm.copyFromRealm(race);
            bgRealm.close(); // 尽早关闭
            
            LCObject lcRace = DataConverter.toLeanCloud(raceCopy);
            lcRace.saveInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject lcObject) {
                    // 上传成功，更新本地状态
                    try (Realm r = Realm.getDefaultInstance()) {
                        r.executeTransaction(t -> {
                            Race localRace = t.where(Race.class).equalTo("raceId", raceId).findFirst();
                            if (localRace != null) {
                                localRace.setCloudId(lcObject.getObjectId());
                                localRace.setSynced(true);
                            }
                        });
                    }
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                    // 上传失败，保持 isSynced = false，下次有机会再同步
                }

                @Override
                public void onComplete() {}
            });
        } else {
            bgRealm.close();
        }
    }



    /**
     * 从 LeanCloud 拉取赛事数据并同步到本地 Realm
     * @param organizerId 如果不为 null，则只拉取该管理员创建的赛事；如果为 null，则拉取所有赛事
     */
    public void fetchRacesFromCloud(@androidx.annotation.Nullable String organizerId) {
        LCQuery<LCObject> query = new LCQuery<>("Race");
        if (organizerId != null && !organizerId.isEmpty()) {
            query.whereEqualTo("organizerId", organizerId);
        }
        query.orderByDescending("updatedAt"); // 按更新时间降序
        query.limit(100); // 限制每次拉取100条，避免数据量过大
        
        query.findInBackground().subscribe(new Observer<List<LCObject>>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(List<LCObject> cloudRaces) {
                if (cloudRaces != null) {
                    Realm realm = Realm.getDefaultInstance();
                    realm.executeTransactionAsync(bgRealm -> {
                        for (LCObject lcRace : cloudRaces) {
                            // 将 LeanCloud 对象转换为 Realm 对象
                            Race race = DataConverter.toRealm(lcRace);
                            
                            // 【关键修复】保留本地的 thumbnailPath，避免被云端的 null 覆盖
                            Race existingRace = bgRealm.where(Race.class)
                                    .equalTo("raceId", race.getRaceId())
                                    .findFirst();
                            if (existingRace != null && existingRace.getThumbnailPath() != null) {
                                // 保留本地已有的缩略图路径
                                race.setThumbnailPath(existingRace.getThumbnailPath());
                            }
                            
                            // copyToRealmOrUpdate 会根据主键 (raceId) 自动判断是插入还是更新
                            bgRealm.copyToRealmOrUpdate(race);
                        }
                    }, () -> {
                        realm.close();
                        // 成功后无需手动通知 UI，因为 UI 已经通过 RealmChangeListener 监听了数据库变化
                        // 数据库一变，UI 自动刷新
                    }, error -> {
                        realm.close();
                        error.printStackTrace();
                    });
                }
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onComplete() {}
        });
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
    public void deleteRace(@NonNull String raceId, @NonNull DeleteCallback callback) {
        // 先获取 cloudId
        Realm realm = Realm.getDefaultInstance();
        Race race = realm.where(Race.class).equalTo("raceId", raceId).findFirst();
        String cloudId = (race != null) ? race.getCloudId() : null;
        realm.close();

        android.util.Log.d("RaceManager", "========== deleteRace 开始 ==========");
        android.util.Log.d("RaceManager", "raceId: " + raceId + ", cloudId: " + cloudId);

        if (cloudId != null && !cloudId.isEmpty()) {
            // 如果已同步到云端，先从云端删除
            android.util.Log.d("RaceManager", "赛事已同步到云端，先删除云端数据");
            LCObject object = LCObject.createWithoutData("Race", cloudId);
            object.deleteInBackground().subscribe(new Observer<Object>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(Object result) {
                    android.util.Log.d("RaceManager", "云端赛事删除成功，现在删除本地数据");
                    // 云端删除成功，再删除本地
                    deleteLocalRaceSync(raceId);
                    android.util.Log.d("RaceManager", "本地赛事删除成功");
                    callback.onSuccess();
                }

                @Override
                public void onError(Throwable e) {
                    // 云端删除失败，但仍然尝试删除本地
                    android.util.Log.e("RaceManager", "云端赛事删除失败: " + e.getMessage(), e);
                    android.util.Log.d("RaceManager", "继续删除本地数据");
                    
                    try {
                        deleteLocalRaceSync(raceId);
                        android.util.Log.d("RaceManager", "本地赛事已删除，但云端删除失败");
                        // 返回本地删除成功（云端失败是网络问题，不应该阻断本地删除）
                        callback.onSuccess();
                    } catch (Exception localError) {
                        android.util.Log.e("RaceManager", "本地赛事删除也失败: " + localError.getMessage(), localError);
                        callback.onError(new Exception("云端删除失败且本地删除也失败", e));
                    }
                }

                @Override
                public void onComplete() {}
            });
        } else {
            // 未同步到云端，直接删除本地
            android.util.Log.d("RaceManager", "赛事未同步到云端，直接删除本地数据");
            deleteLocalRaceAsync(raceId, callback);
        }
    }

    /**
     * 删除本地赛事数据（同步）
     */
    private void deleteLocalRaceSync(@NonNull String raceId) {
        try (Realm realm = Realm.getDefaultInstance()) {
            realm.executeTransaction(r -> {
                Race race = r.where(Race.class).equalTo("raceId", raceId).findFirst();
                if (race != null) {
                    // 删除关联的打卡点
                    if (race.getCheckPoints() != null) {
                        race.getCheckPoints().deleteAllFromRealm();
                    }
                    // 删除赛事
                    race.deleteFromRealm();
                }
            });
        }
    }

    /**
     * 删除本地赛事数据（异步）
     */
    private void deleteLocalRaceAsync(@NonNull String raceId, @NonNull DeleteCallback callback) {
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
            () -> {
                realm.close();
                callback.onSuccess();
            },
            error -> {
                realm.close();
                error.printStackTrace();
                callback.onError(error);
            }
        );
    }

    /**
     * 删除指定赛事（无回调，兼容旧代码）
     */
    public void deleteRace(@NonNull String raceId) {
        deleteRace(raceId, new DeleteCallback() {
            @Override
            public void onSuccess() {
                // 无操作
            }

            @Override
            public void onError(@NonNull Throwable error) {
                // 无操作
            }
        });
    }

    /**
     * 获取所有赛事（供选手浏览赛事大厅）
     */
    public void getAllRaces(@NonNull RaceListCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Race> results = realm.where(Race.class)
                .findAll()
                .sort("createTime"); // 按创建时间排序
        // 转换为普通列表并返回
        List<Race> raceList = realm.copyFromRealm(results);
        // 按创建时间从新到旧排序（最新的在前面）
        raceList.sort((r1, r2) -> {
            if (r1.getCreateTime() == null && r2.getCreateTime() == null) return 0;
            if (r1.getCreateTime() == null) return 1;
            if (r2.getCreateTime() == null) return -1;
            return r2.getCreateTime().compareTo(r1.getCreateTime()); // 降序，新的在前
        });
        callback.onLoaded(raceList);
        realm.close();
    }

    /**
     * 根据用户报名记录获取已报名的赛事列表
     * @param userSignedUpRaceIds 用户已报名的赛事ID列表
     * @param callback 回调函数
     */
    public void getRacesByIds(@NonNull List<String> userSignedUpRaceIds, @NonNull RaceListCallback callback) {
        if (userSignedUpRaceIds.isEmpty()) {
            callback.onLoaded(new ArrayList<>());
            return;
        }
        Realm realm = Realm.getDefaultInstance();
        List<Race> races = new ArrayList<>();
        for (String raceId : userSignedUpRaceIds) {
            Race race = realm.where(Race.class)
                    .equalTo("raceId", raceId)
                    .findFirst();
            if (race != null) {
                races.add(realm.copyFromRealm(race));
            }
        }
        callback.onLoaded(races);
        realm.close();
    }

    /**
     * 更新赛事信息
     */
    public void updateRace(@NonNull String raceId, String name, String description, Date start, Date end, List<CheckPointData> pointsData, String thumbnailPath, @NonNull SaveCallback callback) {
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
                    if (thumbnailPath != null) {
                        race.setThumbnailPath(thumbnailPath); // 更新缩略图路径
                    }
                    
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
                        newPoint.setQrCodePayload(data.qrCodePayload); // 设置二维码内容
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




