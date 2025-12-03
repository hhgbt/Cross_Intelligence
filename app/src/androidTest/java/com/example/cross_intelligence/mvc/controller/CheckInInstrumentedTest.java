package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class CheckInInstrumentedTest {

    private Realm realm;
    private CheckPoint point;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("checkin-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        point = new CheckPoint();
        point.setCheckPointId("cp001");
        point.setLatitude(30.000);
        point.setLongitude(120.000);
        point.setQrCodePayload("QR-123");
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void checkIn_offlineRecordSaved() throws InterruptedException {
        CheckInManager manager = new CheckInManager();
        CountDownLatch latch = new CountDownLatch(1);
        final CheckInRecord[] resultHolder = new CheckInRecord[1];
        manager.checkIn("race1", "user1", point,
                30.0000, 120.0000, "QR-123", true,
                new CheckInManager.CheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull @NonNull CheckInRecord record) {
                        resultHolder[0] = record;
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        latch.countDown();
                    }
                });

        latch.await(2, TimeUnit.SECONDS);
        assertNotNull(resultHolder[0]);
        assertEquals(true, resultHolder[0].isOffline());
    }
}






