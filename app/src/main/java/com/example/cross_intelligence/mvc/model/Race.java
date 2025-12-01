package com.example.cross_intelligence.mvc.model;

import java.util.Date;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

@RealmClass
public class Race extends RealmObject {

    @PrimaryKey
    private String raceId;
    private String name;
    private String description;
    private Date startTime;
    private Date endTime;
    private RealmList<CheckPoint> checkPoints;
    private String organizerId;
    private Date createTime; // 创建时间
    private int sequenceNumber; // 序号：按创建顺序编号，从1开始

    public String getRaceId() {
        return raceId;
    }

    public void setRaceId(String raceId) {
        this.raceId = raceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public RealmList<CheckPoint> getCheckPoints() {
        return checkPoints;
    }

    public void setCheckPoints(RealmList<CheckPoint> checkPoints) {
        this.checkPoints = checkPoints;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}





