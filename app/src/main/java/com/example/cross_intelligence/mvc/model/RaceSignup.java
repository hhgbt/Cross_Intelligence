package com.example.cross_intelligence.mvc.model;

import java.util.Date;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

public class RaceSignup extends RealmObject { // 若用Realm存储，需继承RealmObject
    @PrimaryKey
    private String id;
    private String userId;
    private String raceId;
    private Date signupTime;
    private String contact;

    public RaceSignup() {}

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRaceId() { return raceId; }
    public void setRaceId(String raceId) { this.raceId = raceId; }
    public Date getSignupTime() { return signupTime; }
    public void setSignupTime(Date signupTime) { this.signupTime = signupTime; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
}
