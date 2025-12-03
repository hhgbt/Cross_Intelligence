package com.example.cross_intelligence.mvc.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.model.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.List;

import io.realm.Realm;

@RunWith(AndroidJUnit4.class)
public class RealmHelperInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        RealmHelper.init(context, key, "realm-helper-test.realm");
    }

    @After
    public void tearDown() {
        RealmHelper.clearAll();
    }

    @Test
    public void crudOperations_work() {
        User user = new User();
        user.setUserId("u100");
        user.setName("Test User");
        user.setRole("player");

        RealmHelper.insertOrUpdate(user);
        List<User> users = RealmHelper.queryAll(User.class);
        assertEquals(1, users.size());

        RealmHelper.delete(User.class, query -> query.equalTo("userId", "u100"));
        assertTrue(RealmHelper.queryAll(User.class).isEmpty());
    }
}






