package com.example.cross_intelligence.mvc.db;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmModel;
import io.realm.RealmQuery;

public final class RealmHelper {

    private static RealmConfiguration configuration;
    private static final Object LOCK = new Object();

    private RealmHelper() {
    }

    public static void init(@NonNull Context context, @NonNull byte[] encryptionKey, @NonNull String realmName) {
        synchronized (LOCK) {
            Realm.init(context.getApplicationContext());
            configuration = new RealmConfiguration.Builder()
                    .name(realmName)
                    .encryptionKey(encryptionKey)
                    .schemaVersion(RealmConstants.SCHEMA_VERSION)
                    .migration(new RealmMigrationImpl())
                    .allowQueriesOnUiThread(true)
                    .allowWritesOnUiThread(false)
                    .compactOnLaunch()
                    .build();
            Realm.setDefaultConfiguration(configuration);
        }
    }

    public static Realm getRealmInstance() {
        if (configuration == null) {
            throw new IllegalStateException("RealmHelper not initialized");
        }
        return Realm.getInstance(configuration);
    }

    public static <T extends RealmModel> void insertOrUpdate(@NonNull T data) {
        try (Realm realm = getRealmInstance()) {
            realm.executeTransaction(r -> r.insertOrUpdate(data));
        }
    }

    public static <T extends RealmModel> void insertOrUpdate(@NonNull List<T> data) {
        try (Realm realm = getRealmInstance()) {
            realm.executeTransaction(r -> r.insertOrUpdate(data));
        }
    }

    public static <T extends RealmModel> List<T> queryAll(@NonNull Class<T> clazz) {
        try (Realm realm = getRealmInstance()) {
            return realm.copyFromRealm(realm.where(clazz).findAll());
        }
    }

    public static <T extends RealmModel> List<T> query(@NonNull Class<T> clazz, @NonNull QueryAction<T> queryAction) {
        try (Realm realm = getRealmInstance()) {
            RealmQuery<T> query = realm.where(clazz);
            queryAction.onQuery(query);
            return realm.copyFromRealm(query.findAll());
        }
    }

    public static <T extends RealmModel> void delete(@NonNull Class<T> clazz, @NonNull DeleteAction<T> deleteAction) {
        try (Realm realm = getRealmInstance()) {
            realm.executeTransaction(r -> {
                RealmQuery<T> query = r.where(clazz);
                deleteAction.onDelete(query);
                query.findAll().deleteAllFromRealm();
            });
        }
    }

    public static void clearAll() {
        try (Realm realm = getRealmInstance()) {
            realm.executeTransaction(r -> r.deleteAll());
        }
    }

    public interface QueryAction<T extends RealmModel> {
        void onQuery(RealmQuery<T> query);
    }

    public interface DeleteAction<T extends RealmModel> {
        void onDelete(RealmQuery<T> query);
    }
}




