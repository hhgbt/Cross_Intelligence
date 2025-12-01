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
            int currentVersion = (int) oldVersion;
            switch (currentVersion) {
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
                case 2: {
                    // 版本2到3：无需额外操作，如果有的话
                    oldVersion++;
                    break;
                }
                case 3: {
                    // 版本3到4：为 CheckPoint 添加 type 和 checkRadius 字段
                    RealmObjectSchema checkpointSchema = schema.get("CheckPoint");
                    if (checkpointSchema != null) {
                        // 添加 type 字段，默认值为 "检查点"
                        if (!checkpointSchema.hasField("type")) {
                            checkpointSchema.addField("type", String.class);
                            // 为所有现有记录设置默认值
                            checkpointSchema.transform(obj -> {
                                String existingType = obj.getString("type");
                                if (existingType == null || existingType.isEmpty()) {
                                    obj.setString("type", "检查点");
                                }
                            });
                        }
                        // 添加 checkRadius 字段，默认值为 50.0
                        if (!checkpointSchema.hasField("checkRadius")) {
                            checkpointSchema.addField("checkRadius", double.class);
                            // 为所有现有记录设置默认值
                            checkpointSchema.transform(obj -> {
                                double existingRadius = obj.getDouble("checkRadius");
                                if (existingRadius == 0.0) {
                                    obj.setDouble("checkRadius", 50.0);
                                }
                            });
                        }
                    }
                    oldVersion++;
                    break;
                }
                case 4: {
                    // 版本4到5：为 Race 添加 createTime 和 sequenceNumber 字段
                    RealmObjectSchema raceSchema = schema.get("Race");
                    if (raceSchema != null) {
                        // 添加 createTime 字段
                        if (!raceSchema.hasField("createTime")) {
                            raceSchema.addField("createTime", java.util.Date.class)
                                    .transform(obj -> {
                                        // 如果 createTime 为 null，使用 startTime 作为默认值
                                        java.util.Date startTime = obj.getDate("startTime");
                                        obj.setDate("createTime", startTime != null ? startTime : new java.util.Date());
                                    });
                        }
                        // 添加 sequenceNumber 字段，默认值为 0
                        if (!raceSchema.hasField("sequenceNumber")) {
                            raceSchema.addField("sequenceNumber", int.class)
                                    .transform(obj -> {
                                        // 默认设置为0，后续需要通过代码重新计算
                                        obj.setInt("sequenceNumber", 0);
                                    });
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

