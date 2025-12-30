package com.example.cross_intelligence.mvc.util;

import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.leancloud.LCObject;
import io.realm.RealmList;

/**
 * 数据转换工具类：Realm <-> LeanCloud
 */
public class DataConverter {

    /**
     * 将 Realm 的 Race 对象转换为 LeanCloud 对象
     * 注意：这里只做数据拷贝，不涉及 Realm 事务
     */
    public static LCObject toLeanCloud(Race race) {
        LCObject lcRace;
        // 如果本地已经保存了云端 ID，说明是更新操作
        if (race.getCloudId() != null && !race.getCloudId().isEmpty()) {
            try {
                lcRace = LCObject.createWithoutData("Race", race.getCloudId());
            } catch (Exception e) {
                lcRace = new LCObject("Race");
            }
        } else {
            lcRace = new LCObject("Race");
        }

        lcRace.put("raceId", race.getRaceId()); // 保持 UUID 一致
        lcRace.put("name", race.getName());
        lcRace.put("description", race.getDescription());
        lcRace.put("startTime", race.getStartTime());
        lcRace.put("endTime", race.getEndTime());
        lcRace.put("organizerId", race.getOrganizerId());
        lcRace.put("createTime", race.getCreateTime());
        lcRace.put("sequenceNumber", race.getSequenceNumber());
        // 缩略图路径通常是本地路径，云端同步需要先上传文件，这里暂时只同步元数据
        // lcRace.put("thumbnailPath", race.getThumbnailPath()); 
        
        // 【关键修复】同步打卡点数据
        if (race.getCheckPoints() != null && !race.getCheckPoints().isEmpty()) {
            List<Map<String, Object>> pointsList = new ArrayList<>();
            for (CheckPoint cp : race.getCheckPoints()) {
                Map<String, Object> map = new HashMap<>();
                map.put("checkPointId", cp.getCheckPointId());
                map.put("name", cp.getName());
                map.put("latitude", cp.getLatitude());
                map.put("longitude", cp.getLongitude());
                map.put("type", cp.getType());
                map.put("checkRadius", cp.getCheckRadius());
                map.put("orderIndex", cp.getOrderIndex());
                map.put("qrCodePayload", cp.getQrCodePayload());
                pointsList.add(map);
            }
            lcRace.put("checkPoints", pointsList);
        }

        return lcRace;
    }

    /**
     * 将 LeanCloud 对象转换为 Realm 的 Race 对象
     */
    public static Race toRealm(LCObject lcRace) {
        Race race = new Race();
        race.setRaceId(lcRace.getString("raceId"));
        race.setName(lcRace.getString("name"));
        race.setDescription(lcRace.getString("description"));
        race.setStartTime(lcRace.getDate("startTime"));
        race.setEndTime(lcRace.getDate("endTime"));
        race.setOrganizerId(lcRace.getString("organizerId"));
        race.setCreateTime(lcRace.getDate("createTime"));
        race.setSequenceNumber(lcRace.getInt("sequenceNumber"));
        
        race.setCloudId(lcRace.getObjectId()); // 重要：保存云端 ID
        race.setSynced(true); // 既然是从云端下来的，肯定是已同步状态
        
        // 【关键修复】还原打卡点数据
        List<Object> pointsList = lcRace.getList("checkPoints");
        if (pointsList != null && !pointsList.isEmpty()) {
            RealmList<CheckPoint> realmPoints = new RealmList<>();
            for (Object obj : pointsList) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    CheckPoint cp = new CheckPoint();
                    // 安全转换
                    Object idObj = map.get("checkPointId");
                    cp.setCheckPointId(idObj != null ? idObj.toString() : java.util.UUID.randomUUID().toString());
                    
                    Object nameObj = map.get("name");
                    cp.setName(nameObj != null ? nameObj.toString() : "未命名");
                    
                    cp.setLatitude(getDouble(map.get("latitude")));
                    cp.setLongitude(getDouble(map.get("longitude")));
                    
                    Object typeObj = map.get("type");
                    cp.setType(typeObj != null ? typeObj.toString() : "检查点");
                    
                    cp.setCheckRadius(getDouble(map.get("checkRadius")));
                    
                    Object orderObj = map.get("orderIndex");
                    cp.setOrderIndex(orderObj instanceof Number ? ((Number) orderObj).intValue() : 0);
                    
                    Object qrObj = map.get("qrCodePayload");
                    if (qrObj != null) {
                        cp.setQrCodePayload(qrObj.toString());
                    }
                    
                    cp.setRaceId(race.getRaceId());
                    realmPoints.add(cp);
                }
            }
            race.setCheckPoints(realmPoints);
        }
        
        return race;
    }

    // 辅助方法：安全获取 double
    private static double getDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }

    /**
     * 将 Realm 的 User 对象转换为 LeanCloud 对象
     * 注意：LeanCloud 有内置的 _User 表，但为了简单起见，我们也可以用自定义的 AppUser 表
     * 或者如果使用内置 _User 表，需要使用 LCUser 对象。
     * 这里为了不破坏现有逻辑，我们同步到一个名为 "AppUser" 的自定义表。
     */
    public static LCObject toLeanCloud(User user) {
        LCObject lcUser;
        if (user.getCloudId() != null && !user.getCloudId().isEmpty()) {
            try {
                lcUser = LCObject.createWithoutData("AppUser", user.getCloudId());
            } catch (Exception e) {
                lcUser = new LCObject("AppUser");
            }
        } else {
            lcUser = new LCObject("AppUser");
        }

        lcUser.put("userId", user.getUserId());
        lcUser.put("name", user.getName());
        lcUser.put("role", user.getRole());
        lcUser.put("phone", user.getPhone());
        lcUser.put("email", user.getEmail());
        lcUser.put("bio", user.getBio());
        lcUser.put("avatarUrl", user.getAvatarUrl());

        return lcUser;
    }

    /**
     * 将 LeanCloud 对象转换为 Realm 的 User 对象
     */
    public static User toRealmUser(LCObject lcUser) {
        User user = new User();
        user.setUserId(lcUser.getString("userId"));
        user.setName(lcUser.getString("name"));
        user.setRole(lcUser.getString("role"));
        user.setPhone(lcUser.getString("phone"));
        user.setEmail(lcUser.getString("email"));
        user.setBio(lcUser.getString("bio"));
        user.setAvatarUrl(lcUser.getString("avatarUrl"));
        
        user.setCloudId(lcUser.getObjectId());
        
        return user;
    }

    /**
     * Realm Result -> LeanCloud
     */
    public static LCObject toLeanCloud(Result result) {
        LCObject lcResult;
        if (result.getCloudId() != null && !result.getCloudId().isEmpty()) {
            try {
                lcResult = LCObject.createWithoutData("Result", result.getCloudId());
            } catch (Exception e) {
                lcResult = new LCObject("Result");
            }
        } else {
            lcResult = new LCObject("Result");
        }

        lcResult.put("resultId", result.getResultId());
        lcResult.put("raceId", result.getRaceId());
        lcResult.put("userId", result.getUserId());
        lcResult.put("elapsedSeconds", result.getElapsedSeconds());
        lcResult.put("penaltySeconds", result.getPenaltySeconds());
        lcResult.put("totalSeconds", result.getTotalSeconds());
        lcResult.put("rank", result.getRank());
        lcResult.put("status", result.getStatusRaw());
        // 缩略图路径通常是本地路径，云端同步需要先上传文件，这里暂时只同步元数据
        // lcResult.put("thumbnailPath", result.getThumbnailPath());

        return lcResult;
    }

    /**
     * LeanCloud -> Realm Result
     */
    public static Result toRealmResult(LCObject lcResult) {
        Result result = new Result();
        result.setResultId(lcResult.getString("resultId"));
        result.setRaceId(lcResult.getString("raceId"));
        result.setUserId(lcResult.getString("userId"));
        result.setElapsedSeconds(lcResult.getLong("elapsedSeconds"));
        result.setPenaltySeconds(lcResult.getLong("penaltySeconds"));
        result.setTotalSeconds(lcResult.getLong("totalSeconds"));
        result.setRank(lcResult.getInt("rank"));
        result.setStatusRaw(lcResult.getString("status"));
        
        result.setCloudId(lcResult.getObjectId());
        
        return result;
    }

    /**
     * Realm RaceSignup -> LeanCloud
     */
    public static LCObject toLeanCloud(RaceSignup signup) {
        LCObject lcSignup;
        if (signup.getCloudId() != null && !signup.getCloudId().isEmpty()) {
            try {
                lcSignup = LCObject.createWithoutData("RaceSignup", signup.getCloudId());
            } catch (Exception e) {
                lcSignup = new LCObject("RaceSignup");
            }
        } else {
            lcSignup = new LCObject("RaceSignup");
        }

        lcSignup.put("id", signup.getId());
        lcSignup.put("userId", signup.getUserId());
        lcSignup.put("raceId", signup.getRaceId());
        lcSignup.put("signupTime", signup.getSignupTime());
        lcSignup.put("contact", signup.getContact());

        return lcSignup;
    }

    /**
     * LeanCloud -> Realm RaceSignup
     */
    public static RaceSignup toRealmSignup(LCObject lcSignup) {
        RaceSignup signup = new RaceSignup();
        signup.setId(lcSignup.getString("id"));
        signup.setUserId(lcSignup.getString("userId"));
        signup.setRaceId(lcSignup.getString("raceId"));
        signup.setSignupTime(lcSignup.getDate("signupTime"));
        signup.setContact(lcSignup.getString("contact"));
        
        signup.setCloudId(lcSignup.getObjectId());
        
        return signup;
    }
}
