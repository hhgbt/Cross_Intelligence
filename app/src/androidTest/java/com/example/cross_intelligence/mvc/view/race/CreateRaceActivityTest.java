package com.example.cross_intelligence.mvc.view.race;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.testutil.ToastMatcher;
import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class CreateRaceActivityTest {

    private Realm realm;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("create-race-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void createRace_successFlow() {
        try (ActivityScenario<CreateRaceActivity> scenario = ActivityScenario.launch(CreateRaceActivity.class)) {
            onView(withId(R.id.etRaceName)).perform(replaceText("越野校赛"), closeSoftKeyboard());
            onView(withId(R.id.etStartTime)).perform(replaceText("2025-01-01 09:00"), closeSoftKeyboard());
            onView(withId(R.id.etEndTime)).perform(replaceText("2025-01-01 11:00"), closeSoftKeyboard());

            addPoint("起点", "30.0000", "120.0000");
            addPoint("终点", "30.0100", "120.0200");

            onView(withId(R.id.btnSaveRace)).perform(click());
            SystemClock.sleep(800);
            onView(withText("赛事已保存")).inRoot(new ToastMatcher()).check(matches(withText("赛事已保存")));
        }

        realm.refresh();
        Race race = realm.where(Race.class).equalTo("name", "越野校赛").findFirst();
        org.junit.Assert.assertNotNull(race);
        org.junit.Assert.assertEquals(2, race.getCheckPoints().size());
    }

    @Test
    public void createRace_missingName_showsError() {
        try (ActivityScenario<CreateRaceActivity> scenario = ActivityScenario.launch(CreateRaceActivity.class)) {
            onView(withId(R.id.btnSaveRace)).perform(click());
            onView(withId(R.id.tilRaceName)).check(matches(hasTextInputLayoutErrorText("请输入赛事名称")));
        }
    }

    private void addPoint(String name, String lat, String lng) {
        onView(withId(R.id.btnAddManual)).perform(click());
        onView(withId(R.id.etName)).inRoot(isDialog()).perform(replaceText(name));
        onView(withId(R.id.etLatitude)).inRoot(isDialog()).perform(replaceText(lat));
        onView(withId(R.id.etLongitude)).inRoot(isDialog()).perform(replaceText(lng), closeSoftKeyboard());
        onView(withText("确定")).inRoot(isDialog()).perform(click());
        SystemClock.sleep(200);
    }

    private static Matcher<View> hasTextInputLayoutErrorText(String expected) {
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(@NonNull View view) {
                if (!(view instanceof TextInputLayout)) {
                    return false;
                }
                CharSequence error = ((TextInputLayout) view).getError();
                return error != null && expected.contentEquals(error);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("TextInputLayout error text: ").appendText(expected);
            }
        };
    }
}

