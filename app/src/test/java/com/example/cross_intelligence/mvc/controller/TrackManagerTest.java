package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cross_intelligence.mvc.model.TrackPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class TrackManagerTest {

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
        realmStatic.close();
    }

    @Test
    public void onLocationUpdate_filtersShortDistance() {
        TrackManager manager = new TrackManager();
        AtomicInteger createCount = mockRealmInsert();

        long base = System.currentTimeMillis();
        manager.onLocationUpdate("race", "user", 30.0, 120.0, 10f, 3f, base);
        manager.onLocationUpdate("race", "user", 30.00001, 120.00001, 10f, 3f, base + 1000);
        manager.onLocationUpdate("race", "user", 30.001, 120.001, 10f, 3f, base + 6000);
        manager.flushAsync();

        assertEquals(2, createCount.get());
    }

    private AtomicInteger mockRealmInsert() {
        AtomicInteger counter = new AtomicInteger();
        doAnswer(invocation -> {
            Realm.Transaction transaction = invocation.getArgument(0);
            transaction.execute(realm);
            counter.addAndGet(1); // approximate per call
            return null;
        }).when(realm).executeTransactionAsync(any(), any(), any());
        return counter;
    }
}





