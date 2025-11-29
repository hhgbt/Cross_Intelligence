package com.example.cross_intelligence.mvc.rules;

import java.time.ZoneId;

public class RaceRuleConfig {

    private final ZoneId zoneId;
    private final long penaltyPerMissingCheckpointSeconds;
    private final int maxMissingAllowed;
    private final long overtimePenaltyPerMinuteSeconds;
    private final long dnfPenaltySeconds;

    private RaceRuleConfig(Builder builder) {
        this.zoneId = builder.zoneId;
        this.penaltyPerMissingCheckpointSeconds = builder.penaltyPerMissingCheckpointSeconds;
        this.maxMissingAllowed = builder.maxMissingAllowed;
        this.overtimePenaltyPerMinuteSeconds = builder.overtimePenaltyPerMinuteSeconds;
        this.dnfPenaltySeconds = builder.dnfPenaltySeconds;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public long getPenaltyPerMissingCheckpointSeconds() {
        return penaltyPerMissingCheckpointSeconds;
    }

    public int getMaxMissingAllowed() {
        return maxMissingAllowed;
    }

    public long getOvertimePenaltyPerMinuteSeconds() {
        return overtimePenaltyPerMinuteSeconds;
    }

    public long getDnfPenaltySeconds() {
        return dnfPenaltySeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        private long penaltyPerMissingCheckpointSeconds = 60;
        private int maxMissingAllowed = 0;
        private long overtimePenaltyPerMinuteSeconds = 30;
        private long dnfPenaltySeconds = 24 * 3600;

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        public Builder penaltyPerMissingCheckpointSeconds(long seconds) {
            this.penaltyPerMissingCheckpointSeconds = seconds;
            return this;
        }

        public Builder maxMissingAllowed(int maxMissingAllowed) {
            this.maxMissingAllowed = maxMissingAllowed;
            return this;
        }

        public Builder overtimePenaltyPerMinuteSeconds(long seconds) {
            this.overtimePenaltyPerMinuteSeconds = seconds;
            return this;
        }

        public Builder dnfPenaltySeconds(long seconds) {
            this.dnfPenaltySeconds = seconds;
            return this;
        }

        public RaceRuleConfig build() {
            return new RaceRuleConfig(this);
        }
    }
}




