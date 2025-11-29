package com.example.cross_intelligence.mvc.view.profile;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.testutil.ToastMatcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class UserSettingsActivityTest {

    private Realm realm;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("account", "settingsUser")
                .apply();

        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("settings-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        realm.executeTransaction(r -> {
            User user = r.createObject(User.class, "settingsUser");
            user.setName("初始姓名");
        });
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.executeTransaction(Realm::deleteAll);
            realm.close();
        }
    }

    @Test
    public void editProfile_saveToRealm() {
        try (ActivityScenario<UserSettingsActivity> scenario = ActivityScenario.launch(UserSettingsActivity.class)) {
            onView(withId(R.id.etName)).perform(replaceText("新姓名"), closeSoftKeyboard());
            onView(withId(R.id.etPhone)).perform(replaceText("13911112222"), closeSoftKeyboard());
            onView(withId(R.id.etEmail)).perform(replaceText("new@example.com"), closeSoftKeyboard());
            onView(withId(R.id.etBio)).perform(replaceText("新的简介"), closeSoftKeyboard());
            onView(withId(R.id.btnSave)).perform(click());

            SystemClock.sleep(600);
            onView(withText("保存成功")).inRoot(new ToastMatcher()).check(matches(withText("保存成功")));
        }

        realm.refresh();
        User updated = realm.where(User.class).equalTo("userId", "settingsUser").findFirst();
        org.junit.Assert.assertNotNull(updated);
        org.junit.Assert.assertEquals("新姓名", updated.getName());
        org.junit.Assert.assertEquals("13911112222", updated.getPhone());
        org.junit.Assert.assertEquals("new@example.com", updated.getEmail());
        org.junit.Assert.assertEquals("新的简介", updated.getBio());
    }
}

