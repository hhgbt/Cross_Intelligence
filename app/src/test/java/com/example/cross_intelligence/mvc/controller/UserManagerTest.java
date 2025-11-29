package com.example.cross_intelligence.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.mvc.model.User;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

import io.realm.Realm;
import io.realm.RealmQuery;

public class UserManagerTest {

    @Test
    public void login_success_callbackInvoked() {
        Realm realm = mockRealmWithUser();

        UserManager manager = new UserManager();
        AtomicReference<User> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        try (MockedStatic<Realm> realmStatic = Mockito.mockStatic(Realm.class)) {
            realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);

            manager.login("admin001", "123456", "管理员", new UserManager.LoginCallback() {
                @Override
                public void onSuccess(@NonNull User user) {
                    result.set(user);
                }

                @Override
                public void onFailure(@NonNull Throwable throwable) {
                    error.set(throwable);
                }
            });
        }

        assertNotNull(result.get());
        assertEquals("admin001", result.get().getUserId());
        assertTrue(error.get() == null);
    }

    @Test
    public void login_userMissing_callbackFailure() {
        Realm realm = mockRealmWithoutUser();

        UserManager manager = new UserManager();
        AtomicReference<Throwable> error = new AtomicReference<>();

        try (MockedStatic<Realm> realmStatic = Mockito.mockStatic(Realm.class)) {
            realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);

            manager.login("player001", "123456", "选手", new UserManager.LoginCallback() {
                @Override
                public void onSuccess(@NonNull User user) {
                }

                @Override
                public void onFailure(@NonNull Throwable throwable) {
                    error.set(throwable);
                }
            });
        }

        assertTrue(error.get() instanceof IllegalStateException);
    }

    @Test
    public void updateProfile_success_callbackInvoked() {
        Realm realm = mockRealmWithUser();
        UserManager manager = new UserManager();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);

        try (MockedStatic<Realm> realmStatic = Mockito.mockStatic(Realm.class)) {
            realmStatic.when(Realm::getDefaultInstance).thenReturn(realm);

            User profile = new User();
            profile.setUserId("admin001");
            profile.setName("新名字");

            manager.updateProfile(profile, new UserManager.CompletionCallback() {
                @Override
                public void onComplete() {
                    completed.set(true);
                }

                @Override
                public void onError(@NonNull Throwable throwable) {
                }
            });
        }

        assertTrue(completed.get());
    }

    private Realm mockRealmWithUser() {
        Realm realm = mock(Realm.class);
        User user = new User();
        user.setUserId("admin001");
        user.setRole("管理员");
        when(realm.copyFromRealm(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RealmQuery<User> query = mockRealmQuery(user, realm);
        when(realm.where(User.class)).thenReturn(query);

        doAnswer(invocation -> {
            Realm.Transaction transaction = invocation.getArgument(0);
            Realm.Transaction.OnSuccess success = invocation.getArgument(1);
            transaction.execute(realm);
            if (success != null) {
                success.onSuccess();
            }
            return null;
        }).when(realm).executeTransactionAsync(any(), any(), any());
        return realm;
    }

    private Realm mockRealmWithoutUser() {
        Realm realm = mock(Realm.class);
        when(realm.copyFromRealm(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RealmQuery<User> query = mockRealmQuery(null, realm);
        when(realm.where(User.class)).thenReturn(query);

        doAnswer(invocation -> {
            Realm.Transaction transaction = invocation.getArgument(0);
            Realm.Transaction.OnSuccess success = invocation.getArgument(1);
            Realm.Transaction.OnError error = invocation.getArgument(2);
            try {
                transaction.execute(realm);
                if (success != null) {
                    success.onSuccess();
                }
            } catch (Throwable throwable) {
                if (error != null) {
                    error.onError(throwable);
                }
            }
            return null;
        }).when(realm).executeTransactionAsync(any(), any(), any());
        return realm;
    }

    private RealmQuery<User> mockRealmQuery(User target, Realm realm) {
        RealmQuery<User> query = mock(RealmQuery.class);
        when(query.equalTo(anyString(), anyString())).thenReturn(query);
        when(query.findFirst()).thenReturn(target);
        return query;
    }
}

