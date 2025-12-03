package com.example.cross_intelligence.mvc.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.mvc.model.Result;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.security.SecureRandom;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

@RunWith(AndroidJUnit4.class)
public class RealmMigrationInstrumentedTest {

    private Context context;
    private byte[] key;
    private File realmFile;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        key = new byte[64];
        new SecureRandom().nextBytes(key);
        realmFile = new File(context.getFilesDir(), "migration-test.realm");
        if (realmFile.exists()) {
            realmFile.delete();
        }
    }

    @After
    public void tearDown() {
        if (realmFile.exists()) {
            realmFile.delete();
        }
    }

    @Test
    public void migrate_addsRankField() {
        RealmConfiguration oldConfig = new RealmConfiguration.Builder()
                .name(realmFile.getName())
                .directory(realmFile.getParentFile())
                .encryptionKey(key)
                .schemaVersion(1)
                .build();

        try (DynamicRealm dynamicRealm = DynamicRealm.getInstance(oldConfig)) {
            RealmSchema schema = dynamicRealm.getSchema();
            if (!schema.contains("Result")) {
                RealmObjectSchema resultSchema = schema.create("Result")
                        .addField("resultId", String.class, FieldAttribute.PRIMARY_KEY)
                        .addField("raceId", String.class)
                        .addField("userId", String.class)
                        .addField("elapsedSeconds", long.class)
                        .addField("penaltySeconds", long.class)
                        .addField("totalSeconds", long.class)
                        .addField("status", String.class);
                resultSchema.transform(obj -> {
                    obj.setString("resultId", "legacy");
                    obj.setString("raceId", "race");
                    obj.setString("userId", "user");
                    obj.setLong("elapsedSeconds", 1);
                    obj.setLong("penaltySeconds", 0);
                    obj.setLong("totalSeconds", 1);
                    obj.setString("status", Result.Status.FINISHED.name());
                });
            }
        }

        RealmConfiguration newConfig = new RealmConfiguration.Builder()
                .name(realmFile.getName())
                .directory(realmFile.getParentFile())
                .encryptionKey(key)
                .schemaVersion(RealmConstants.SCHEMA_VERSION)
                .migration(new RealmMigrationImpl())
                .build();

        try (Realm realm = Realm.getInstance(newConfig)) {
            Result migrated = realm.where(Result.class).equalTo("resultId", "legacy").findFirst();
            assertNotNull(migrated);
            assertEquals(-1, migrated.getRank());
        }
    }
}






