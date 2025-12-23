package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.RaceSession;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.QrPayloadParser;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * 打卡管理器：负责 GPS + QR 双重验证、状态查询。
 * 
 * 打卡流程：
 * 1. 地理围栏判断：使用 DistanceUtil 计算距离，超出打卡半径则禁止打卡
 * 2. 二维码解析：解析 JSON 格式 {"raceId":"xxx","cpId":"xxx"}
 * 3. 匹配验证：比对 raceId 和 cpId 是否与当前打卡点一致
 * 4. 记录存储：验证通过后创建 CheckInRecord
 */
public class CheckInManager {

    public void checkIn(String race1, String user1, CheckPoint point, double v, double v1, String s, boolean b, CheckInCallback checkInCallback) {
    }

    public interface CheckInCallback {
        void onSuccess(@NonNull CheckInRecord record);

        void onFailure(@NonNull Throwable throwable);
    }

    /**
     * 状态机打卡回调（包含会话状态和打卡点类型）
     */
    public interface StateCheckInCallback {
        void onSuccess(@NonNull CheckInRecord record, @NonNull RaceSession session, @NonNull String checkPointType);

        void onFailure(@NonNull Throwable throwable);
    }

    private static final double DEFAULT_RADIUS_METERS = 50.0;
    private final RaceSessionManager sessionManager = new RaceSessionManager();

    /**
     * 比赛完成回调（包含 Result）
     */
    public interface RaceFinishCallback {
        void onFinished(@NonNull Result result);
        void onFailure(@NonNull Throwable throwable);
    }

    /**
     * 检查是否在打卡范围内
     *
     * @param checkPoint 打卡点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @return 距离（米）
     */
    public double calculateDistance(@NonNull CheckPoint checkPoint, double currentLat, double currentLng) {
        return DistanceUtil.distanceMeters(currentLat, currentLng,
                checkPoint.getLatitude(), checkPoint.getLongitude());
    }

    /**
     * 检查是否在打卡范围内
     *
     * @param checkPoint 打卡点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @return 是否在范围内
     */
    public boolean isInRange(@NonNull CheckPoint checkPoint, double currentLat, double currentLng) {
        double distance = calculateDistance(checkPoint, currentLat, currentLng);
        double radius = checkPoint.getCheckRadius() > 0 ? checkPoint.getCheckRadius() : DEFAULT_RADIUS_METERS;
        return distance <= radius;
    }

    /**
     * 执行打卡
     * 
     * 验证流程：
     * 1. 地理围栏判断：检查当前位置是否在打卡半径内
     * 2. 二维码解析：解析 JSON 格式的二维码内容
     * 3. 匹配验证：验证 raceId 和 cpId 是否与当前打卡点一致
     * 4. 存储记录：验证通过后创建 CheckInRecord
     *
     * @param raceId     赛事ID
     * @param userId     用户ID
     * @param checkPoint 打卡点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param scannedQr  扫描的二维码内容
     * @param callback   回调
     */
    public void checkIn(@NonNull String raceId,
                        @NonNull String userId,
                        @NonNull CheckPoint checkPoint,
                        double currentLat,
                        double currentLng,
                        @NonNull String scannedQr,
                        @NonNull CheckInCallback callback) {

        // 1. 地理围栏判断
        double distance = calculateDistance(checkPoint, currentLat, currentLng);
        double radius = checkPoint.getCheckRadius() > 0 ? checkPoint.getCheckRadius() : DEFAULT_RADIUS_METERS;
        
        if (distance > radius) {
            callback.onFailure(new IllegalStateException(
                    String.format("未进入打卡范围（当前距离：%.1f米，需要：%.1f米内）", distance, radius)));
            return;
        }

        // 2. 二维码解析
        QrPayloadParser.ParseResult parseResult = QrPayloadParser.parse(scannedQr);
        if (!parseResult.isSuccess()) {
            callback.onFailure(new IllegalArgumentException("二维码解析失败：" + parseResult.getErrorMessage()));
            return;
        }

        // 3. 匹配验证：比对 raceId
        if (!raceId.equals(parseResult.getRaceId())) {
            callback.onFailure(new IllegalArgumentException("二维码不属于当前赛事"));
            return;
        }

        // 3. 匹配验证：比对 cpId
        if (!checkPoint.getCheckPointId().equals(parseResult.getCpId())) {
            callback.onFailure(new IllegalArgumentException("二维码与当前打卡点不匹配"));
            return;
        }

        // 4. 结果存储：创建 CheckInRecord
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            // 检查是否已经打卡过
            CheckInRecord existing = bgRealm.where(CheckInRecord.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .equalTo("checkPointId", checkPoint.getCheckPointId())
                    .findFirst();
            
            if (existing != null) {
                throw new IllegalStateException("该打卡点已完成打卡");
            }

            // 创建打卡记录
            CheckInRecord record = bgRealm.createObject(CheckInRecord.class, UUID.randomUUID().toString());
            record.setRaceId(raceId);
            record.setUserId(userId);
            record.setCheckPointId(checkPoint.getCheckPointId());
            record.setLatitude(currentLat);
            record.setLongitude(currentLng);
            record.setTimestamp(new Date());
            record.setOffline(false);
            record.setSynced(true);
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

    /**
     * 状态机打卡（根据打卡点类型执行不同逻辑）
     * 
     * - 起点：激活赛程，记录开始时间，启动轨迹记录
     * - 检查点：常规记录，不停止计时
     * - 终点：自动截断，记录结束时间，停止轨迹记录
     *
     * @param raceId     赛事ID
     * @param userId     用户ID
     * @param checkPoint 打卡点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param scannedQr  扫描的二维码内容（终点可为 null）
     * @param callback   回调
     */
    public void checkInWithStateMachine(@NonNull String raceId,
                                        @NonNull String userId,
                                        @NonNull CheckPoint checkPoint,
                                        double currentLat,
                                        double currentLng,
                                        String scannedQr,
                                        @NonNull StateCheckInCallback callback) {

        // 获取或创建会话
        RaceSession session = sessionManager.getOrCreateSession(raceId, userId);
        String checkPointType = checkPoint.getType();

        // 1. 地理围栏判断（所有类型都需要）
        double distance = calculateDistance(checkPoint, currentLat, currentLng);
        double radius = checkPoint.getCheckRadius() > 0 ? checkPoint.getCheckRadius() : DEFAULT_RADIUS_METERS;

        if (distance > radius) {
            callback.onFailure(new IllegalStateException(
                    String.format("未进入打卡范围（当前距离：%.1f米，需要：%.1f米内）", distance, radius)));
            return;
        }

        // 2. 根据类型判断是否需要二维码
        boolean requireQrCode = !CheckPoint.TYPE_FINISH.equals(checkPointType); // 终点不需要扫码

        if (requireQrCode) {
            if (scannedQr == null || scannedQr.isEmpty()) {
                callback.onFailure(new IllegalArgumentException("请扫描打卡点二维码"));
                return;
            }

            // 二维码解析
            QrPayloadParser.ParseResult parseResult = QrPayloadParser.parse(scannedQr);
            if (!parseResult.isSuccess()) {
                callback.onFailure(new IllegalArgumentException("二维码解析失败：" + parseResult.getErrorMessage()));
                return;
            }

            // 匹配验证：比对 raceId
            if (!raceId.equals(parseResult.getRaceId())) {
                callback.onFailure(new IllegalArgumentException("二维码不属于当前赛事"));
                return;
            }

            // 匹配验证：比对 cpId
            if (!checkPoint.getCheckPointId().equals(parseResult.getCpId())) {
                callback.onFailure(new IllegalArgumentException("二维码与当前打卡点不匹配"));
                return;
            }
        }

        // 3. 状态验证
        if (CheckPoint.TYPE_START.equals(checkPointType)) {
            if (session.isStarted()) {
                callback.onFailure(new IllegalStateException("已经开始比赛，不能重复打卡起点"));
                return;
            }
        } else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
            if (!session.isInProgress()) {
                callback.onFailure(new IllegalStateException("请先在起点开始比赛"));
                return;
            }
        } else if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
            if (!session.isInProgress()) {
                callback.onFailure(new IllegalStateException("请先在起点开始比赛"));
                return;
            }
            // 防止终点误触：验证起点是否已打卡
            if (session.getStartTime() == null) {
                callback.onFailure(new IllegalStateException("请先在起点打卡开始比赛"));
                return;
            }
            // 额外验证：确保比赛已经进行了一定时间（避免起点终点在同一位置导致瞬间完成）
            long raceMinDuration = 60 * 1000; // 最少比赛1分钟
            long elapsedTime = System.currentTimeMillis() - session.getStartTime().getTime();
            if (elapsedTime < raceMinDuration) {
                callback.onFailure(new IllegalStateException(
                    String.format("比赛时间过短（%d秒），请确保完成比赛后再打卡终点", elapsedTime / 1000)));
                return;
            }
        }

        // 4. 创建打卡记录 + 更新会话状态
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            // 检查是否已经打卡过
            CheckInRecord existing = bgRealm.where(CheckInRecord.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .equalTo("checkPointId", checkPoint.getCheckPointId())
                    .findFirst();

            if (existing != null) {
                throw new IllegalStateException("该打卡点已完成打卡");
            }

            // 创建打卡记录
            CheckInRecord record = bgRealm.createObject(CheckInRecord.class, UUID.randomUUID().toString());
            record.setRaceId(raceId);
            record.setUserId(userId);
            record.setCheckPointId(checkPoint.getCheckPointId());
            record.setLatitude(currentLat);
            record.setLongitude(currentLng);
            record.setTimestamp(new Date());
            record.setOffline(false);
            record.setSynced(true);

            // 更新会话状态（在同一事务中）
            RaceSession dbSession = bgRealm.where(RaceSession.class)
                    .equalTo("sessionId", session.getSessionId())
                    .findFirst();

            if (dbSession != null) {
                if (CheckPoint.TYPE_START.equals(checkPointType)) {
                    // 起点：激活赛程
                    dbSession.setStatus(RaceSession.STATUS_IN_PROGRESS);
                    dbSession.setStartTime(new Date());
                    dbSession.setStartLatitude(currentLat);
                    dbSession.setStartLongitude(currentLng);
                    dbSession.setTrackingEnabled(true);
                } else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
                    // 检查点：增加计数
                    dbSession.setCheckpointsChecked(dbSession.getCheckpointsChecked() + 1);
                } else if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
                    // 终点：完成比赛
                    Date endTime = new Date();
                    dbSession.setStatus(RaceSession.STATUS_FINISHED);
                    dbSession.setEndTime(endTime);
                    dbSession.setEndLatitude(currentLat);
                    dbSession.setEndLongitude(currentLng);
                    dbSession.setTrackingEnabled(false);

                    // 计算总用时
                    long totalMillis = 0;
                    if (dbSession.getStartTime() != null) {
                        totalMillis = endTime.getTime() - dbSession.getStartTime().getTime();
                        dbSession.setTotalMillis(totalMillis);
                    }
                    
                    // 【关键修复】创建并保存 Result 成绩对象
                    Result result = bgRealm.createObject(Result.class, java.util.UUID.randomUUID().toString());
                    result.setRaceId(raceId);
                    result.setUserId(userId);
                    result.setElapsedSeconds(totalMillis / 1000);
                    result.setPenaltySeconds(0);  // 默认无罚时
                    result.setTotalSeconds(totalMillis / 1000);
                    result.setStatus(Result.Status.FINISHED);
                    result.setRank(0);  // 排名需要后续计算
                }
                dbSession.setUpdateTime(new Date());
            }
        }, () -> {
            // 成功：读取最新的记录和会话
            CheckInRecord stored = realm.where(CheckInRecord.class)
                    .equalTo("raceId", raceId)
                    .equalTo("userId", userId)
                    .equalTo("checkPointId", checkPoint.getCheckPointId())
                    .findAll()
                    .last();

            RaceSession updatedSession = realm.where(RaceSession.class)
                    .equalTo("sessionId", session.getSessionId())
                    .findFirst();

            if (stored != null && updatedSession != null) {
                callback.onSuccess(
                        realm.copyFromRealm(stored),
                        realm.copyFromRealm(updatedSession),
                        checkPointType
                );
            } else {
                callback.onFailure(new IllegalStateException("记录读取失败"));
            }
            realm.close();
        }, (@NonNull Throwable error) -> {
            realm.close();
            callback.onFailure(error);
        });
    }

    /**
     * 开始比赛（起点打卡）
     * 记录开始时间并启动轨迹记录服务
     *
     * @param raceId     赛事ID
     * @param userId     用户ID
     * @param checkPoint 起点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param scannedQr  扫描的二维码
     * @param callback   回调
     */
    public void startRace(@NonNull String raceId,
                          @NonNull String userId,
                          @NonNull CheckPoint checkPoint,
                          double currentLat,
                          double currentLng,
                          @NonNull String scannedQr,
                          @NonNull StateCheckInCallback callback) {
        
        // 验证是起点
        if (!CheckPoint.TYPE_START.equals(checkPoint.getType())) {
            callback.onFailure(new IllegalArgumentException("只能在起点开始比赛"));
            return;
        }

        // 使用状态机打卡
        checkInWithStateMachine(raceId, userId, checkPoint, currentLat, currentLng, scannedQr, callback);
    }

    /**
     * 完成比赛（终点打卡）
     * 确保数据原子性：先刷新轨迹数据，再停止服务，最后保存 Result
     *
     * @param raceId     赛事ID
     * @param userId     用户ID
     * @param checkPoint 终点
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param callback   回调
     */
    public void finishRace(@NonNull String raceId,
                           @NonNull String userId,
                           @NonNull CheckPoint checkPoint,
                           double currentLat,
                           double currentLng,
                           @NonNull RaceFinishCallback callback) {

        // 验证是终点
        if (!CheckPoint.TYPE_FINISH.equals(checkPoint.getType())) {
            callback.onFailure(new IllegalArgumentException("只能在终点完成比赛"));
            return;
        }

        // 【关键】先强制刷新轨迹数据，确保最后一段轨迹被保存
        TrackManager trackManager = new TrackManager();
        trackManager.flushAsync(); // 立即将待写入的轨迹点保存到数据库

        // 终点打卡（无需二维码）
        checkInWithStateMachine(raceId, userId, checkPoint, currentLat, currentLng, null,
                new StateCheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull CheckInRecord record,
                                        @NonNull RaceSession session,
                                        @NonNull String checkPointType) {
                        // 创建并保存 Result
                        // 注意：此时轨迹已经保存，可以安全停止服务
                        saveResultToDatabase(session, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        callback.onFailure(throwable);
                    }
                });
    }

    /**
     * 保存比赛结果到数据库
     */
    private void saveResultToDatabase(@NonNull RaceSession session, @NonNull RaceFinishCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            // 创建 Result 对象
            Result result = bgRealm.createObject(Result.class, UUID.randomUUID().toString());
            result.setRaceId(session.getRaceId());
            result.setUserId(session.getUserId());

            // 计算用时（秒）
            long totalSeconds = session.getTotalMillis() / 1000;
            result.setElapsedSeconds(totalSeconds);
            result.setPenaltySeconds(0); // 默认无罚时
            result.setTotalSeconds(totalSeconds);

            // 设置状态
            result.setStatus(Result.Status.FINISHED);

            // 暂时不设置排名（需要在查询所有成绩后计算）
            result.setRank(0);

        }, () -> {
            // 成功：读取刚创建的 Result
            Result savedResult = realm.where(Result.class)
                    .equalTo("raceId", session.getRaceId())
                    .equalTo("userId", session.getUserId())
                    .findAll()
                    .last();

            if (savedResult != null) {
                callback.onFinished(realm.copyFromRealm(savedResult));
            } else {
                callback.onFailure(new IllegalStateException("结果保存失败"));
            }
            realm.close();
        }, (@NonNull Throwable error) -> {
            realm.close();
            callback.onFailure(error);
        });
    }
    
    /**
     * 查询指定用户在某赛事的所有打卡记录
     * @param raceId 赛事ID
     * @param userId 用户ID
     * @return 打卡记录列表，按时间排序
     */
    public List<CheckInRecord> queryCheckInRecords(@NonNull String raceId, @NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<CheckInRecord> results = realm.where(CheckInRecord.class)
                .equalTo("raceId", raceId)
                .equalTo("userId", userId)
                .sort("timestamp")
                .findAll();
        List<CheckInRecord> copy = realm.copyFromRealm(results);
        realm.close();
        return copy;
    }
}


