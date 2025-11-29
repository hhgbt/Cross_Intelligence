package com.example.cross_intelligence.mvc.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.db.RealmConstants;
import com.example.cross_intelligence.mvc.db.RealmMigrationImpl;
import com.example.cross_intelligence.mvc.model.CheckInRecord;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class SyncManagerInstrumentedTest {

    private Realm realm;
    private MockSyncApi mockSyncApi;
    private FakeNetworkProvider networkProvider;
    private SyncManager syncManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("sync-manager-test.realm")
                .schemaVersion(RealmConstants.SCHEMA_VERSION)
                .migration(new RealmMigrationImpl())
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        mockSyncApi = new MockSyncApi();
        networkProvider = new FakeNetworkProvider();
        syncManager = new SyncManager(mockSyncApi, networkProvider);
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void syncOffline_emitsOfflineState() throws InterruptedException {
        networkProvider.setConnected(false);
        CountDownLatch latch = new CountDownLatch(1);
        Observer<SyncState> observer = state -> {
            if (state != null && state.getStatus() == SyncState.Status.OFFLINE) {
                latch.countDown();
            }
        };
        syncManager.getSyncState().observeForever(observer);
        try {
            syncManager.syncPendingCheckIns();
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } finally {
            syncManager.getSyncState().removeObserver(observer);
        }
    }

    @Test
    public void syncSuccess_marksRecordsSynced() throws InterruptedException {
        insertRecord("rec1", 1000);
        networkProvider.setConnected(true);
        CountDownLatch latch = new CountDownLatch(1);
        Observer<SyncState> observer = state -> {
            if (state != null && state.getStatus() == SyncState.Status.SUCCESS) {
                latch.countDown();
            }
        };
        syncManager.getSyncState().observeForever(observer);
        try {
            syncManager.syncPendingCheckIns();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } finally {
            syncManager.getSyncState().removeObserver(observer);
        }
        CheckInRecord record = realm.where(CheckInRecord.class).equalTo("recordId", "rec1").findFirst();
        assertNotNull(record);
        assertTrue(record.isSynced());
    }

    @Test
    public void syncConflict_localOlder_deleted() throws InterruptedException {
        insertRecord("rec2", 500);
        mockSyncApi.seedRemote("cp1", "user1", 1000);
        networkProvider.setConnected(true);
        CountDownLatch latch = new CountDownLatch(1);
        Observer<SyncState> observer = state -> {
            if (state != null && state.getStatus() == SyncState.Status.SUCCESS) {
                latch.countDown();
            }
        };
        syncManager.getSyncState().observeForever(observer);
        try {
            syncManager.syncPendingCheckIns();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } finally {
            syncManager.getSyncState().removeObserver(observer);
        }
        CheckInRecord record = realm.where(CheckInRecord.class).equalTo("recordId", "rec2").findFirst();
        assertNull(record);
    }

    private void insertRecord(String recordId, long timestamp) {
        realm.executeTransaction(r -> {
            CheckInRecord record = r.createObject(CheckInRecord.class, recordId);
            record.setRaceId("race1");
            record.setUserId("user1");
            record.setCheckPointId("cp1");
            record.setTimestamp(new Date(timestamp));
            record.setLatitude(30);
            record.setLongitude(120);
            record.setOffline(true);
            record.setSynced(false);
        });
    }

    private static class FakeNetworkProvider implements NetworkStatusProvider {
        private final MutableLiveData<Boolean> liveData = new MutableLiveData<>(false);

        void setConnected(boolean connected) {
            liveData.postValue(connected);
        }

        @Override
        public boolean isConnected() {
            Boolean v = liveData.getValue();
            return v != null && v;
        }

        @Override
        public LiveData<Boolean> getNetworkStatus() {
            return liveData;
        }
    }
}

