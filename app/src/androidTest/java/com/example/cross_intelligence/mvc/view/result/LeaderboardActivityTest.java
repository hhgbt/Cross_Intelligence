package com.example.cross_intelligence.mvc.view.result;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.Result;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class LeaderboardActivityTest {

    private Realm realm;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("result-ui-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        seedResults();
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    private void seedResults() {
        realm.executeTransaction(r -> {
            Result result1 = r.createObject(Result.class, UUID.randomUUID().toString());
            result1.setRaceId("race-ui");
            result1.setUserId("user1");
            result1.setElapsedSeconds(3600);
            result1.setPenaltySeconds(0);
            result1.setTotalSeconds(3600);
            result1.setStatus(Result.Status.FINISHED);
            result1.setRank(1);

            Result result2 = r.createObject(Result.class, UUID.randomUUID().toString());
            result2.setRaceId("race-ui");
            result2.setUserId("user2");
            result2.setElapsedSeconds(4000);
            result2.setPenaltySeconds(30);
            result2.setTotalSeconds(4030);
            result2.setStatus(Result.Status.FINISHED_WITH_PENALTY);
            result2.setRank(2);
        });
    }

    @Test
    public void leaderboardDisplaysAndNavigates() {
        Intent intent = new Intent(context, LeaderboardActivity.class);
        intent.putExtra(LeaderboardActivity.EXTRA_RACE_ID, "race-ui");
        try (ActivityScenario<LeaderboardActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.rvLeaderboard))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
            onView(withId(R.id.tvUser)).check(matches(withText(containsString("user1"))));
        }
    }

    @Test
    public void shareFromDetail_launchesChooser() {
        Intent intent = new Intent(context, LeaderboardActivity.class);
        intent.putExtra(LeaderboardActivity.EXTRA_RACE_ID, "race-ui");
        try (ActivityScenario<LeaderboardActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.rvLeaderboard))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
            onView(withId(R.id.tvUser)).check(matches(withText(containsString("user1"))));
            Intents.init();
            Intents.intending(hasAction(Intent.ACTION_CHOOSER))
                    .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, null));
            onView(withId(R.id.btnShare)).perform(click());
            intended(hasAction(Intent.ACTION_CHOOSER));
            Intents.release();
        }
    }

    @Test
    public void exportLeaderboard_launchesChooser() {
        Intent intent = new Intent(context, LeaderboardActivity.class);
        intent.putExtra(LeaderboardActivity.EXTRA_RACE_ID, "race-ui");
        try (ActivityScenario<LeaderboardActivity> scenario = ActivityScenario.launch(intent)) {
            Intents.init();
            Intents.intending(hasAction(Intent.ACTION_CHOOSER))
                    .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, null));
            onView(withId(R.id.btnExport)).perform(click());
            intended(hasAction(Intent.ACTION_CHOOSER));
            Intents.release();
        }
    }
}

