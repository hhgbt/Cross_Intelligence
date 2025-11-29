package com.example.cross_intelligence.mvc.view.profile;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class ProfileFragmentTest {

    private Realm realm;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("account", "user001").apply();

        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .inMemory()
                .name("profile-test.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        realm.executeTransaction(r -> {
            com.example.cross_intelligence.mvc.model.User user =
                    r.createObject(com.example.cross_intelligence.mvc.model.User.class, "user001");
            user.setName("李越野");
            user.setRole("选手");
            user.setPhone("13800000000");
            user.setEmail("user@example.com");
            user.setBio("爱好越野跑");
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
    public void profileFragment_displayUserInfo() {
        FragmentScenario.launchInContainer(ProfileFragment.class, null, R.style.Theme_Cross_Intelligence, null);
        onView(withId(R.id.tvName)).check(matches(withText("李越野")));
        onView(withId(R.id.tvRole)).check(matches(withText("角色：选手")));
        onView(withId(R.id.tvPhone)).check(matches(withText("电话：13800000000")));
        onView(withId(R.id.tvEmail)).check(matches(withText("邮箱：user@example.com")));
        onView(withId(R.id.tvBio)).check(matches(withText("简介：爱好越野跑")));
    }
}

