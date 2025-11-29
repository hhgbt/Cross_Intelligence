package com.example.cross_intelligence;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Base64;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.example.cross_intelligence.mvc.db.RealmConstants;
import com.example.cross_intelligence.mvc.db.RealmHelper;

import java.security.SecureRandom;

/**
 * 集中初始化第三方 SDK，应用在 AndroidManifest 中将 name 指向该类。
 */
public class CrossIntelligenceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initRealm();
        initAmapPrivacy();
    }

    private void initRealm() {
        RealmHelper.init(this, loadRealmKey(), RealmConstants.DEFAULT_REALM_NAME);
    }

    private void initAmapPrivacy() {
        // 定位隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
        // 地图隐私合规（3D 地图）
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
    }

    private byte[] loadRealmKey() {
        SharedPreferences sp = getSharedPreferences("realm_secure_prefs", MODE_PRIVATE);
        String stored = sp.getString("realm_key", null);
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP);
        }
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        sp.edit().putString("realm_key", Base64.encodeToString(key, Base64.NO_WRAP)).apply();
        return key;
    }
}

