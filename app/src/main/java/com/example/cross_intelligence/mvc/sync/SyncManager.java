package com.example.cross_intelligence.mvc.sync;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.cross_intelligence.mvc.model.CheckInRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;

public class SyncManager {

    private final SyncApi syncApi;
    private final NetworkStatusProvider networkStatusProvider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<SyncState> syncState = new MutableLiveData<>(SyncState.idle());
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncManager(@NonNull SyncApi syncApi, @NonNull NetworkStatusProvider networkStatusProvider) {
        this.syncApi = syncApi;
        this.networkStatusProvider = networkStatusProvider;
    }

    public LiveData<SyncState> getSyncState() {
        return syncState;
    }

    public void syncPendingCheckIns() {
        if (running.get()) return;
        executor.execute(() -> {
            if (!networkStatusProvider.isConnected()) {
                syncState.postValue(SyncState.offline());
                return;
            }
            running.set(true);
            List<CheckInRecord> pending = loadPendingRecords();
            if (pending.isEmpty()) {
                syncState.postValue(SyncState.success());
                running.set(false);
                return;
            }
            int processed = 0;
            for (CheckInRecord record : pending) {
                syncState.postValue(SyncState.running(processed, pending.size()));
                SyncApi.SyncResult result = syncApi.uploadCheckIn(record);
                handleResult(record, result);
                processed++;
            }
            syncState.postValue(SyncState.success());
            running.set(false);
        });
    }

    private List<CheckInRecord> loadPendingRecords() {
        try (Realm realm = Realm.getDefaultInstance()) {
            RealmResults<CheckInRecord> results = realm.where(CheckInRecord.class)
                    .equalTo("synced", false)
                    .findAll();
            List<CheckInRecord> list = realm.copyFromRealm(results);
            list.sort(Comparator.comparing(CheckInRecord::getTimestamp));
            return list;
        }
    }

    private void handleResult(CheckInRecord record, SyncApi.SyncResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                markSynced(record);
                break;
            case CONFLICT:
                resolveConflict(record, result.getRemoteTimestamp());
                break;
            case FAILED:
                syncState.postValue(SyncState.error(result.getMessage() != null ? result.getMessage() : "同步失败"));
                break;
        }
    }

    private void markSynced(CheckInRecord record) {
        try (Realm realm = Realm.getDefaultInstance()) {
            realm.executeTransaction(r -> {
                CheckInRecord managed = r.where(CheckInRecord.class)
                        .equalTo("recordId", record.getRecordId())
                        .findFirst();
                if (managed != null) {
                    managed.setSynced(true);
                    managed.setOffline(false);
                    managed.setLastSyncedAt(System.currentTimeMillis());
                }
            });
        }
    }

    private void resolveConflict(CheckInRecord record, long remoteTimestamp) {
        long localTimestamp = record.getTimestamp() != null ? record.getTimestamp().getTime() : 0;
        if (localTimestamp >= remoteTimestamp) {
            markSynced(record);
        } else {
            try (Realm realm = Realm.getDefaultInstance()) {
                realm.executeTransaction(r -> {
                    CheckInRecord managed = r.where(CheckInRecord.class)
                            .equalTo("recordId", record.getRecordId())
                            .findFirst();
                    if (managed != null) {
                        managed.deleteFromRealm();
                    }
                });
            }
        }
    }
}

