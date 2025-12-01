package com.example.cross_intelligence.mvc.sync;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.CheckInRecord;

public interface SyncApi {

    SyncResult uploadCheckIn(@NonNull CheckInRecord record);

    class SyncResult {
        public enum Status {SUCCESS, CONFLICT, FAILED}

        private final Status status;
        private final long remoteTimestamp;
        private final String message;

        private SyncResult(Status status, long remoteTimestamp, String message) {
            this.status = status;
            this.remoteTimestamp = remoteTimestamp;
            this.message = message;
        }

        public static SyncResult success() {
            return new SyncResult(Status.SUCCESS, 0, null);
        }

        public static SyncResult conflict(long remoteTimestamp) {
            return new SyncResult(Status.CONFLICT, remoteTimestamp, "服务器存在更新数据");
        }

        public static SyncResult failed(String msg) {
            return new SyncResult(Status.FAILED, 0, msg);
        }

        public Status getStatus() {
            return status;
        }

        public long getRemoteTimestamp() {
            return remoteTimestamp;
        }

        public String getMessage() {
            return message;
        }
    }
}





