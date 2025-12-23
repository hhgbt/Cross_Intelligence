package com.example.cross_intelligence.mvc.model;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * 比赛会话模型 - 存储选手的实时比赛状态
 * 用于实现比赛状态机逻辑
 */
@RealmClass
public class RaceSession extends RealmObject {

    /**
     * 比赛状态枚举
     */
    public static final String STATUS_NOT_STARTED = "未开始";  // 未开始
    public static final String STATUS_IN_PROGRESS = "进行中";  // 进行中
    public static final String STATUS_FINISHED = "已完成";     // 已完成
    public static final String STATUS_DNF = "未完成";         // Did Not Finish

    @PrimaryKey
    private String sessionId;       // 会话ID

    @Index
    private String raceId;          // 赛事ID

    @Index
    private String userId;          // 选手ID

    private String status;          // 当前状态：STATUS_NOT_STARTED, STATUS_IN_PROGRESS, STATUS_FINISHED, STATUS_DNF

    private Date startTime;         // 起点打卡时间（开始计时）
    private Date endTime;           // 终点到达时间（结束计时）
    private long totalMillis;       // 总用时（毫秒）

    private double startLatitude;   // 起点打卡位置
    private double startLongitude;

    private double endLatitude;     // 终点到达位置
    private double endLongitude;

    private int checkpointsChecked; // 已打卡的检查点数量
    private int totalCheckpoints;   // 总检查点数量

    private boolean trackingEnabled; // 轨迹记录是否开启

    private Date createTime;        // 会话创建时间
    private Date updateTime;        // 最后更新时间

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public void setTotalMillis(long totalMillis) {
        this.totalMillis = totalMillis;
    }

    public double getStartLatitude() {
        return startLatitude;
    }

    public void setStartLatitude(double startLatitude) {
        this.startLatitude = startLatitude;
    }

    public double getStartLongitude() {
        return startLongitude;
    }

    public void setStartLongitude(double startLongitude) {
        this.startLongitude = startLongitude;
    }

    public double getEndLatitude() {
        return endLatitude;
    }

    public void setEndLatitude(double endLatitude) {
        this.endLatitude = endLatitude;
    }

    public double getEndLongitude() {
        return endLongitude;
    }

    public void setEndLongitude(double endLongitude) {
        this.endLongitude = endLongitude;
    }

    public int getCheckpointsChecked() {
        return checkpointsChecked;
    }

    public void setCheckpointsChecked(int checkpointsChecked) {
        this.checkpointsChecked = checkpointsChecked;
    }

    public int getTotalCheckpoints() {
        return totalCheckpoints;
    }

    public void setTotalCheckpoints(int totalCheckpoints) {
        this.totalCheckpoints = totalCheckpoints;
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        this.trackingEnabled = trackingEnabled;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 判断是否已开始
     */
    public boolean isStarted() {
        return STATUS_IN_PROGRESS.equals(status) || STATUS_FINISHED.equals(status) || STATUS_DNF.equals(status);
    }

    /**
     * 判断是否已完成
     */
    public boolean isFinished() {
        return STATUS_FINISHED.equals(status) || STATUS_DNF.equals(status);
    }

    /**
     * 判断是否正在进行中
     */
    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }
}










