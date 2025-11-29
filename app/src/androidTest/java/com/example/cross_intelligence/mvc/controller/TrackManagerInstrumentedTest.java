package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.model.TrackPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class TrackManagerInstrumentedTest {

    private Realm realm;
    private TrackManager manager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("track-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        manager = new TrackManager();
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void recordAndQueryTrack() {
        long base = System.currentTimeMillis();
        manager.onLocationUpdate("race1", "user1", 30.0, 120.0, 10f, 3f, base);
        manager.onLocationUpdate("race1", "user1", 30.0005, 120.0005, 10f, 3f, base + 5000);
        manager.flushAsync();

        List<TrackPoint> track = manager.queryTrack("race1", "user1");
        assertEquals(2, track.size());
        assertFalse(track.get(0).getTimestamp().after(track.get(1).getTimestamp()));
    }
}




