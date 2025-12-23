package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.rules.RaceRuleConfig;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * 负责成绩计算、罚时规则与排名。
 */
public class ResultManager {

    public Result calculateResult(@NonNull Race race,
                                  @NonNull String userId,
                                  @NonNull List<CheckInRecord> records,
                                  @NonNull RaceRuleConfig config) {
        ZoneId zoneId = config.getZoneId();
        ZonedDateTime start = ZonedDateTime.ofInstant(race.getStartTime().toInstant(), zoneId);
        ZonedDateTime raceEnd = ZonedDateTime.ofInstant(race.getEndTime().toInstant(), zoneId);

        Map<String, CheckInRecord> recordMap = records.stream()
                .collect(Collectors.toMap(CheckInRecord::getCheckPointId, rec -> rec, (a, b) -> a));

        int totalCheckPoints = race.getCheckPoints() != null ? race.getCheckPoints().size() : 0;
        int completed = 0;
        ZonedDateTime finishTime = start;

        if (race.getCheckPoints() != null) {
            for (CheckPoint cp : race.getCheckPoints()) {
                CheckInRecord record = recordMap.get(cp.getCheckPointId());
                if (record != null) {
                    ZonedDateTime recordTime = ZonedDateTime.ofInstant(record.getTimestamp().toInstant(), zoneId);
                    if (recordTime.isAfter(finishTime)) {
                        finishTime = recordTime;
                    }
                    completed++;
                }
            }
        }

        int missing = totalCheckPoints - completed;
        Result.Status status;
        // 删除罚时逻辑，不再计算罚时
        if (missing > config.getMaxMissingAllowed()) {
            status = Result.Status.DNF;
        } else {
            status = missing > 0 ? Result.Status.DNF : Result.Status.FINISHED;
        }

        long elapsedSeconds = Math.max(0, Duration.between(start, finishTime).getSeconds());

        Result result = new Result();
        result.setResultId(UUID.randomUUID().toString());
        result.setRaceId(race.getRaceId());
        result.setUserId(userId);
        result.setElapsedSeconds(elapsedSeconds);
        result.setPenaltySeconds(0); // 罚时始终为0
        result.setTotalSeconds(elapsedSeconds); // 总时间等于已用时间
        result.setStatus(status);
        result.setRank(-1);
        return result;
    }

    public void persistResult(@NonNull Result result) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(r -> r.insertOrUpdate(result));
        realm.close();
    }

    public Result loadResultById(@NonNull String resultId) {
        Realm realm = Realm.getDefaultInstance();
        Result result = realm.where(Result.class).equalTo("resultId", resultId).findFirst();
        Result copy = result != null ? realm.copyFromRealm(result) : null;
        realm.close();
        return copy;
    }

    public List<Result> rankResults(@NonNull List<Result> results) {
        List<Result> sortable = new ArrayList<>(results);
        // 删除罚时后，按已用时间排序
        sortable.sort(Comparator
                .comparing((Result r) -> r.getStatus() == Result.Status.DNF)
                .thenComparingLong(Result::getElapsedSeconds));
        int rank = 1;
        for (Result result : sortable) {
            if (result.getStatus() == Result.Status.DNF) {
                result.setRank(-1);
            } else {
                result.setRank(rank++);
            }
        }
        return sortable;
    }

    public List<Result> loadResults(@NonNull String raceId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Result> results = realm.where(Result.class).equalTo("raceId", raceId).findAll();
        List<Result> copy = realm.copyFromRealm(results);
        realm.close();
        return copy;
    }
    
    /**
     * 【新增】查询指定用户的所有成绩（用于"我的成绩"页面）
     * @param userId 用户ID
     * @return 该用户的所有成绩列表，按时间倒序排列
     */
    public List<Result> loadResultsByUserId(@NonNull String userId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Result> results = realm.where(Result.class)
                .equalTo("userId", userId)
                .findAll()
                .sort("resultId"); // 按ID排序（可改为按时间排序，如果Result增加timestamp字段）
        List<Result> copy = realm.copyFromRealm(results);
        realm.close();
        return copy;
    }
    
    /**
     * 【新增】检查赛事是否已结束，并为未完成选手自动创建空成绩记录
     * 在查看成绩详情或加载成绩列表时调用此方法
     * @param raceId 赛事ID
     */
    public void ensureUnfinishedResultsCreated(@NonNull String raceId) {
        Realm realm = Realm.getDefaultInstance();
        try {
            // 获取赛事信息
            Race race = realm.where(Race.class)
                    .equalTo("raceId", raceId)
                    .findFirst();
            
            if (race == null || race.getEndTime() == null) {
                return; // 赛事不存在或未设置结束时间
            }
            
            // 检查赛事是否已结束
            Date now = new Date();
            if (race.getEndTime().after(now)) {
                return; // 赛事未结束，不需要创建空成绩
            }
            
            // 获取所有已报名该赛事的用户
            RealmResults<RaceSignup> signups = realm.where(RaceSignup.class)
                    .equalTo("raceId", raceId)
                    .findAll();
            
            if (signups.isEmpty()) {
                return; // 没有报名记录
            }
            
            // 获取所有已有成绩的用户ID
            RealmResults<Result> existingResults = realm.where(Result.class)
                    .equalTo("raceId", raceId)
                    .findAll();
            
            java.util.Set<String> usersWithResults = new java.util.HashSet<>();
            for (Result result : existingResults) {
                usersWithResults.add(result.getUserId());
            }
            
            // 为没有成绩的已报名用户创建空成绩记录
            realm.beginTransaction();
            for (RaceSignup signup : signups) {
                String userId = signup.getUserId();
                if (!usersWithResults.contains(userId)) {
                    // 创建空成绩记录，状态为DNF（未完成）
                    Result emptyResult = realm.createObject(Result.class, UUID.randomUUID().toString());
                    emptyResult.setRaceId(raceId);
                    emptyResult.setUserId(userId);
                    emptyResult.setElapsedSeconds(0);
                    emptyResult.setPenaltySeconds(0);
                    emptyResult.setTotalSeconds(0);
                    emptyResult.setStatus(Result.Status.DNF);
                    emptyResult.setRank(-1);
                }
            }
            realm.commitTransaction();
        } catch (Exception e) {
            if (realm.isInTransaction()) {
                realm.cancelTransaction();
            }
            e.printStackTrace();
        } finally {
            realm.close();
        }
    }
}

