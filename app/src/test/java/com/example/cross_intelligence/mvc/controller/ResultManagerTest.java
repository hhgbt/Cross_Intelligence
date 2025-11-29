package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.rules.RaceRuleConfig;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.realm.RealmList;

public class ResultManagerTest {

    private Race race;
    private RaceRuleConfig defaultRule;

    @Before
    public void setUp() {
        race = new Race();
        race.setRaceId("race1");
        Date start = Date.from(Instant.parse("2025-01-01T01:00:00Z"));
        Date end = Date.from(Instant.parse("2025-01-01T04:00:00Z"));
        race.setStartTime(start);
        race.setEndTime(end);

        RealmList<CheckPoint> points = new RealmList<>();
        for (int i = 0; i < 3; i++) {
            CheckPoint cp = new CheckPoint();
            cp.setCheckPointId("cp" + i);
            cp.setName("CP" + i);
            points.add(cp);
        }
        race.setCheckPoints(points);

        defaultRule = RaceRuleConfig.builder()
                .zoneId(ZoneId.of("Asia/Shanghai"))
                .penaltyPerMissingCheckpointSeconds(120)
                .overtimePenaltyPerMinuteSeconds(30)
                .maxMissingAllowed(1)
                .dnfPenaltySeconds(99999)
                .build();
    }

    @Test
    public void calculateResult_allComplete() {
        ResultManager manager = new ResultManager();
        List<CheckInRecord> records = new ArrayList<>();
        records.add(record("cp0", "2025-01-01T01:30:00Z"));
        records.add(record("cp1", "2025-01-01T02:15:00Z"));
        records.add(record("cp2", "2025-01-01T02:45:00Z"));
        Result result = manager.calculateResult(race, "user1", records, defaultRule);
        assertEquals(Result.Status.FINISHED, result.getStatus());
        assertEquals(6300, result.getElapsedSeconds()); // 1h45m -> 6300s
        assertEquals(0, result.getPenaltySeconds());
    }

    @Test
    public void calculateResult_missingOneWithinAllowed() {
        ResultManager manager = new ResultManager();
        List<CheckInRecord> records = Arrays.asList(
                record("cp0", "2025-01-01T01:30:00Z"),
                record("cp1", "2025-01-01T02:15:00Z"));
        Result result = manager.calculateResult(race, "user1", records, defaultRule);
        assertEquals(Result.Status.FINISHED_WITH_PENALTY, result.getStatus());
        assertEquals(4500, result.getElapsedSeconds());
        assertEquals(120, result.getPenaltySeconds());
        assertEquals(4620, result.getTotalSeconds());
    }

    @Test
    public void calculateResult_missingExceedAllowed_dnf() {
        RaceRuleConfig strictRule = RaceRuleConfig.builder()
                .maxMissingAllowed(0)
                .dnfPenaltySeconds(3600)
                .build();
        ResultManager manager = new ResultManager();
        List<CheckInRecord> records = Arrays.asList(record("cp0", "2025-01-01T01:05:00Z"));
        Result result = manager.calculateResult(race, "user1", records, strictRule);
        assertEquals(Result.Status.DNF, result.getStatus());
        assertEquals(3600, result.getPenaltySeconds());
    }

    @Test
    public void calculateResult_overtimePenalty() {
        ResultManager manager = new ResultManager();
        List<CheckInRecord> records = Arrays.asList(
                record("cp0", "2025-01-01T03:30:00Z"),
                record("cp1", "2025-01-01T03:45:30Z"),
                record("cp2", "2025-01-01T04:05:00Z"));
        Result result = manager.calculateResult(race, "user1", records, defaultRule);
        assertEquals(Result.Status.FINISHED_WITH_PENALTY, result.getStatus());
        assertTrue(result.getPenaltySeconds() >= 150); // 10 min overtime -> 300s, but uses per minute rule
    }

    @Test
    public void rankResults_assignsRankAndDnF() {
        Result r1 = new Result();
        r1.setStatus(Result.Status.FINISHED);
        r1.setTotalSeconds(1000);
        Result r2 = new Result();
        r2.setStatus(Result.Status.FINISHED_WITH_PENALTY);
        r2.setTotalSeconds(1100);
        Result r3 = new Result();
        r3.setStatus(Result.Status.DNF);
        r3.setTotalSeconds(99999);

        ResultManager manager = new ResultManager();
        List<Result> ranked = manager.rankResults(Arrays.asList(r3, r2, r1));
        assertEquals(1, ranked.get(0).getRank());
        assertEquals(Result.Status.FINISHED, ranked.get(0).getStatus());
        assertEquals(-1, ranked.get(2).getRank());
    }

    private CheckInRecord record(String cpId, String isoInstant) {
        CheckInRecord record = new CheckInRecord();
        record.setCheckPointId(cpId);
        record.setTimestamp(Date.from(java.time.Instant.parse(isoInstant)));
        return record;
    }
}

