package com.example.cross_intelligence.mvc.db;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

public class RealmMigrationImpl implements RealmMigration {
    @Override
    public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {
        RealmSchema schema = realm.getSchema();

        while (oldVersion < newVersion) {
            switch ((int) oldVersion) {
                case 0: {
                    if (schema.contains("Result")) {
                        RealmObjectSchema resultSchema = schema.get("Result");
                        if (resultSchema != null && !resultSchema.hasField("rank")) {
                            resultSchema.addField("rank", int.class, FieldAttribute.REQUIRED)
                                    .transform(obj -> obj.setInt("rank", -1));
                        }
                    }
                    oldVersion++;
                    break;
                }
                case 1: {
                    if (schema.contains("CheckInRecord")) {
                        RealmObjectSchema recordSchema = schema.get("CheckInRecord");
                        if (recordSchema != null && !recordSchema.hasField("lastSyncedAt")) {
                            recordSchema.addField("lastSyncedAt", long.class, FieldAttribute.REQUIRED)
                                    .transform(obj -> obj.setLong("lastSyncedAt", 0));
                        }
                    }
                    oldVersion++;
                    break;
                }
                default:
                    oldVersion = newVersion;
                    break;
            }
        }
    }
}

