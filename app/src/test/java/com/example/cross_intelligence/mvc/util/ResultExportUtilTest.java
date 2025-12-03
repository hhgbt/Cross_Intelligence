package com.example.cross_intelligence.mvc.util;

import static org.junit.Assert.assertTrue;

import com.example.cross_intelligence.mvc.model.Result;

import org.junit.Test;

import java.util.Arrays;

public class ResultExportUtilTest {

    @Test
    public void buildCsvContent_containsHeadersAndRows() {
        Result r1 = new Result();
        r1.setRank(1);
        r1.setUserId("user1");
        r1.setElapsedSeconds(3600);
        r1.setPenaltySeconds(0);
        r1.setTotalSeconds(3600);
        r1.setStatus(Result.Status.FINISHED);

        String csv = ResultExportUtil.buildCsvContent(Arrays.asList(r1));
        assertTrue(csv.startsWith("排名,用户ID"));
        assertTrue(csv.contains("user1"));
        assertTrue(csv.trim().endsWith("FINISHED"));
    }
}






