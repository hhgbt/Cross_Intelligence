package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.RealmQuery;

public class CheckInManagerTest {

    private Realm realm;
    private MockedStatic<Realm> realmStatic;

    @Before
    public void setUp() {
        realm = mock(Realm.class);
        realmStatic = Mockito.mockStatic(Realm.class);
        realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);
    }

    @After
    public void tearDown() {
        if (realmStatic != null) {
            realmStatic.close();
        }
    }

    @Test
    public void checkIn_successWithinRadius() {
        CheckPoint point = mockPoint();
        CheckInRecord stored = new CheckInRecord();
        stored.setRecordId(UUID.randomUUID().toString());
        stored.setRaceId("race1");
        stored.setUserId("user1");
        stored.setCheckPointId(point.getCheckPointId());
        stored.setTimestamp(new Date());

        mockRealmTransaction(stored);

        CheckInManager manager = new CheckInManager();
        AtomicBoolean success = new AtomicBoolean(false);
        manager.checkIn("race1", "user1", point,
                point.getLatitude(), point.getLongitude(),
                "QR-001", false,
                new CheckInManager.CheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull CheckInRecord record) {
                        success.set(true);
                        assertEquals("race1", record.getRaceId());
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        throw new AssertionError(throwable);
                    }
                });
        assertTrue(success.get());
    }

    @Test
    public void checkIn_outOfRadius_fail() {
        CheckPoint point = mockPoint();
        CheckInManager manager = new CheckInManager();
        AtomicBoolean failed = new AtomicBoolean(false);
        manager.checkIn("race1", "user1", point,
                point.getLatitude() + 1, point.getLongitude() + 1,
                "QR-001", false,
                new CheckInManager.CheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull CheckInRecord record) {
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        failed.set(true);
                    }
                });
        assertTrue(failed.get());
    }

    @Test
    public void checkIn_qrMismatch_fail() {
        CheckPoint point = mockPoint();
        CheckInManager manager = new CheckInManager();
        AtomicBoolean failed = new AtomicBoolean(false);
        manager.checkIn("race1", "user1", point,
                point.getLatitude(), point.getLongitude(),
                "WRONG", false,
                new CheckInManager.CheckInCallback() {
                    @Override
                    public void onSuccess(@NonNull CheckInRecord record) {
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        failed.set(true);
                    }
                });
        assertTrue(failed.get());
    }

    private CheckPoint mockPoint() {
        CheckPoint point = new CheckPoint();
        point.setCheckPointId("cp1");
        point.setLatitude(30.0);
        point.setLongitude(120.0);
        point.setQrCodePayload("QR-001");
        return point;
    }

    private void mockRealmTransaction(CheckInRecord storedRecord) {
        RealmQuery<CheckInRecord> query = mock(RealmQuery.class);
        RealmResults<CheckInRecord> results = mock(RealmResults.class);
        when(realm.where(CheckInRecord.class)).thenReturn(query);
        when(query.equalTo(anyString(), anyString())).thenReturn(query);
        when(query.findAll()).thenReturn(results);
        when(results.last()).thenReturn(storedRecord);
        when(realm.copyFromRealm(storedRecord)).thenReturn(storedRecord);

        doAnswer(invocation -> {
            Realm.Transaction tx = invocation.getArgument(0);
            Realm.Transaction.OnSuccess success = invocation.getArgument(1);
            tx.execute(realm);
            if (success != null) success.onSuccess();
            return null;
        }).when(realm).executeTransactionAsync(Mockito.any(), Mockito.any(), Mockito.any());
    }
}





