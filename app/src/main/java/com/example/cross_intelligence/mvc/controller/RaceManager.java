package com.example.cross_intelligence.mvc.controller;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;

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

    public interface RaceListCallback {
        void onLoaded(@NonNull List<Race> races);
    }

    public void createRace(String name, Date start, Date end, List<CheckPoint> points) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(r -> {
            Race race = r.createObject(Race.class, UUID.randomUUID().toString());
            race.setName(name);
            race.setStartTime(start);
            race.setEndTime(end);
            RealmList<CheckPoint> realmPoints = new RealmList<>();
            for (CheckPoint point : points) {
                CheckPoint managed = r.copyToRealmOrUpdate(point);
                realmPoints.add(managed);
            }
            race.setCheckPoints(realmPoints);
        });
        realm.close();
    }

    public void queryUpcomingRaces(@NonNull RaceListCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Race> results = realm.where(Race.class)
                .greaterThan("endTime", new Date())
                .findAllAsync();
        results.addChangeListener((races, changeSet) -> callback.onLoaded(realm.copyFromRealm(races)));
    }
}




