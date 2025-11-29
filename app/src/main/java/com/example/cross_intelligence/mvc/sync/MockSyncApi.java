package com.example.cross_intelligence.mvc.sync;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示用同步 API，实现简单冲突规则：以时间戳较大的为准。
 */
public class MockSyncApi implements SyncApi {

    private final Map<String, Long> remoteRecords = new HashMap<>();

    @Override
    public SyncResult uploadCheckIn(@NonNull CheckInRecord record) {
        String key = record.getCheckPointId() + "_" + record.getUserId();
        Long remoteTimestampObj = remoteRecords.get(key);
        long remoteTimestamp = remoteTimestampObj != null ? remoteTimestampObj : 0L;
        long localTimestamp = record.getTimestamp() != null ? record.getTimestamp().getTime() : 0;
        if (remoteTimestamp > localTimestamp) {
            return SyncResult.conflict(remoteTimestamp);
        }
        remoteRecords.put(key, localTimestamp);
        return SyncResult.success();
    }

    public void seedRemote(String checkpointId, String userId, long timestamp) {
        remoteRecords.put(checkpointId + "_" + userId, timestamp);
    }
}

