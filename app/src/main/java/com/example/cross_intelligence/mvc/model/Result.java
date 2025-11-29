package com.example.cross_intelligence.mvc.model;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

@RealmClass
public class Result extends RealmObject {

    public enum Status {
        FINISHED,
        FINISHED_WITH_PENALTY,
        DNF
    }

    @PrimaryKey
    private String resultId;
    private String raceId;
    private String userId;
    private long elapsedSeconds;
    private long penaltySeconds;
    private long totalSeconds;
    private int rank;
    private String status;

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public String getRaceId() {
        return raceId;
    }

    public void setRaceId(String raceId) {
        this.raceId = raceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public long getPenaltySeconds() {
        return penaltySeconds;
    }

    public void setPenaltySeconds(long penaltySeconds) {
        this.penaltySeconds = penaltySeconds;
    }

    public long getTotalSeconds() {
        return totalSeconds;
    }

    public void setTotalSeconds(long totalSeconds) {
        this.totalSeconds = totalSeconds;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Status getStatus() {
        if (status == null) {
            return null;
        }
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setStatus(Status status) {
        this.status = status != null ? status.name() : null;
    }

    public String getStatusRaw() {
        return status;
    }

    public void setStatusRaw(String statusValue) {
        this.status = statusValue;
    }
}


