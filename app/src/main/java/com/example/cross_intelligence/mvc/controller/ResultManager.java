package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
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
        long penaltySeconds = 0;
        if (missing > config.getMaxMissingAllowed()) {
            status = Result.Status.DNF;
            penaltySeconds = config.getDnfPenaltySeconds();
        } else {
            penaltySeconds += missing * config.getPenaltyPerMissingCheckpointSeconds();
            status = missing > 0 ? Result.Status.FINISHED_WITH_PENALTY : Result.Status.FINISHED;
        }

        long elapsedSeconds = Math.max(0, Duration.between(start, finishTime).getSeconds());
        if (finishTime.isAfter(raceEnd)) {
            long secondsOver = Duration.between(raceEnd, finishTime).getSeconds();
            long minutes = (secondsOver + 59) / 60; // round up
            if (minutes > 0) {
                penaltySeconds += minutes * config.getOvertimePenaltyPerMinuteSeconds();
                status = Result.Status.FINISHED_WITH_PENALTY;
            }
        }

        long totalSeconds = elapsedSeconds + penaltySeconds;

        Result result = new Result();
        result.setResultId(UUID.randomUUID().toString());
        result.setRaceId(race.getRaceId());
        result.setUserId(userId);
        result.setElapsedSeconds(elapsedSeconds);
        result.setPenaltySeconds(penaltySeconds);
        result.setTotalSeconds(totalSeconds);
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
        sortable.sort(Comparator
                .comparing((Result r) -> r.getStatus() == Result.Status.DNF)
                .thenComparingLong(Result::getTotalSeconds));
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
}

