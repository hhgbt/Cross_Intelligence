package com.example.cross_intelligence.mvc.view.login;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.testutil.ToastMatcher;
import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.realm.Realm;
import io.realm.RealmConfiguration;

@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> rule = new ActivityScenarioRule<>(LoginActivity.class);

    private Realm realm;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name("login-test.realm")
                .inMemory()
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
        realm = Realm.getInstance(config);
        realm.executeTransaction(r -> {
            User user = r.createObject(User.class, "20230001");
            user.setRole("选手");
            user.setName("测试选手");
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
    public void emptyInputs_showErrors() {
        onView(withId(R.id.btnLogin)).perform(click());
        onView(withId(R.id.tilAccount)).check(matches(hasTextInputLayoutErrorText("请输入账号")));
        onView(withId(R.id.tilPassword)).check(matches(hasTextInputLayoutErrorText("请输入密码")));
        onView(withId(R.id.tilRole)).check(matches(hasTextInputLayoutErrorText("请选择角色")));
    }

    @Test
    public void nonExistingUser_showErrorToast() {
        realm.executeTransaction(Realm::deleteAll);
        typeBasicCredentials("20239999", "123456", "选手");
        onView(withId(R.id.btnLogin)).perform(click());
        SystemClock.sleep(600);
        onView(withText("用户不存在"))
                .inRoot(new ToastMatcher())
                .check(matches(withText("用户不存在")));
    }

    @Test
    public void validLogin_showSuccessToast() {
        typeBasicCredentials("20230001", "123456", "选手");
        onView(withId(R.id.btnLogin)).perform(click());
        SystemClock.sleep(600);
        onView(withText("登录成功：选手"))
                .inRoot(new ToastMatcher())
                .check(matches(withText("登录成功：选手")));
    }

    private void typeBasicCredentials(String account, String password, String role) {
        onView(withId(R.id.etAccount)).perform(replaceText(account), closeSoftKeyboard());
        onView(withId(R.id.etPassword)).perform(replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.actRole)).perform(click());
        onData(is(role)).inRoot(isPlatformPopup()).perform(click());
    }

    private static Matcher<View> hasTextInputLayoutErrorText(String expected) {
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(View view) {
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

