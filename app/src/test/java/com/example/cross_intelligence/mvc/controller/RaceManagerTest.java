package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class RaceManagerTest {

    @Test
    public void createRace_persistsData() {
        Realm realm = mockRealm();
        RaceManager manager = new RaceManager();

        try (MockedStatic<Realm> realmStatic = Mockito.mockStatic(Realm.class)) {
            realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);

            manager.createRace("校园赛",
                    new Date(),
                    new Date(System.currentTimeMillis() + 3600_000),
                    mockPoints(2));

            assertTrue(executedTransaction);
        }
    }

    @Test
    public void queryUpcomingRaces_callbackReceivesData() {
        Realm realm = mockRealm();
        RaceManager manager = new RaceManager();
        AtomicBoolean called = new AtomicBoolean(false);

        try (MockedStatic<Realm> realmStatic = Mockito.mockStatic(Realm.class)) {
            realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);

            manager.queryUpcomingRaces(races -> called.set(true));
        }

        assertTrue(called.get());
    }

    private final AtomicBoolean executedTransaction = new AtomicBoolean(false);

    private Realm mockRealm() {
        Realm realm = mock(Realm.class);
        when(realm.copyFromRealm(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Race race = new Race();
        when(realm.createObject(eq(Race.class), anyString())).thenReturn(race);

        RealmQuery<Race> query = mock(RealmQuery.class);
        RealmResults<Race> results = mock(RealmResults.class);
        when(query.greaterThan(anyString(), any(Date.class))).thenReturn(query);
        when(query.findAllAsync()).thenReturn(results);
        when(realm.where(Race.class)).thenReturn(query);

        doAnswer(invocation -> {
            Realm.Transaction transaction = invocation.getArgument(0);
            transaction.execute(realm);
            executedTransaction.set(true);
            Realm.Transaction.OnSuccess success = invocation.getArgument(1);
            if (success != null) success.onSuccess();
            return null;
        }).when(realm).executeTransaction(any(Realm.Transaction.class));

        doAnswer(invocation -> {
            Realm.Transaction transaction = invocation.getArgument(0);
            Realm.Transaction.OnSuccess success = invocation.getArgument(1);
            Realm.Transaction.OnError error = invocation.getArgument(2);
            try {
                transaction.execute(realm);
                executedTransaction.set(true);
                if (success != null) success.onSuccess();
            } catch (Throwable throwable) {
                if (error != null) error.onError(throwable);
            }
            return null;
        }).when(realm).executeTransactionAsync(any(), any(), any());

        return realm;
    }

    private List<CheckPoint> mockPoints(int count) {
        List<CheckPoint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CheckPoint point = new CheckPoint();
            point.setCheckPointId("cp" + i);
            point.setName("P" + i);
            point.setLatitude(30 + i);
            point.setLongitude(120 + i);
            list.add(point);
        }
        return list;
    }
}

