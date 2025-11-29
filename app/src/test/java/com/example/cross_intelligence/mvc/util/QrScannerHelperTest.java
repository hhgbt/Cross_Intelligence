package com.example.cross_intelligence.mvc.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.zxing.integration.android.IntentResult;

import org.junit.Test;

public class QrScannerHelperTest {

    @Test
    public void validatePayload_match() {
        IntentResult result = mock(IntentResult.class);
        when(result.getContents()).thenReturn("QR-123");
        assertTrue(QrScannerHelper.validatePayload("QR-123", result));
    }

    @Test
    public void validatePayload_nullOrMismatch() {
        IntentResult nullResult = null;
        assertFalse(QrScannerHelper.validatePayload("QR-123", nullResult));
        IntentResult mismatch = mock(IntentResult.class);
        when(mismatch.getContents()).thenReturn("OTHER");
        assertFalse(QrScannerHelper.validatePayload("QR-123", mismatch));
    }
}




