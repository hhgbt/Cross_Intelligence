package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class RaceManagerInstrumentedTest {

    private Realm realm;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("race-manager-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void createRace_persistedInRealm() {
        RaceManager manager = new RaceManager();
        manager.createRace("城市赛",
                new Date(),
                new Date(System.currentTimeMillis() + 3600_000),
                samplePoints());

        Race stored = realm.where(Race.class).findFirst();
        assertNotNull(stored);
        assertEquals("城市赛", stored.getName());
        assertEquals(2, stored.getCheckPoints().size());
    }

    private List<CheckPoint> samplePoints() {
        List<CheckPoint> list = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            CheckPoint point = new CheckPoint();
            point.setCheckPointId("cp" + i);
            point.setName("P" + i);
            point.setLatitude(30 + i);
            point.setLongitude(120 + i);
            list.add(point);
        }
        return list;
    }
}





