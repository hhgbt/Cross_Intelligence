package com.example.cross_intelligence;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.example.cross_intelligence.mvc.db.RealmConstants;
import com.example.cross_intelligence.mvc.db.RealmHelper;

import java.security.SecureRandom;

import cn.leancloud.LeanCloud;

/**
 * 集中初始化第三方 SDK，应用在 AndroidManifest 中将 name 指向该类。
 */
public class CrossIntelligenceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initRealm();
        initAmapPrivacy();
        initLeanCloud();
    }

    private void initLeanCloud() {
        // 开启调试日志，可以在 Logcat 中看到详细的请求和响应
        LeanCloud.setLogLevel(cn.leancloud.LCLogger.Level.DEBUG);

        // 提供 LeanCloud AppID 和 AppKey
        String appId = "jdGmkFC5sNL62QRe38jnPW3F-MdYXbMMI";
        String appKey = "OSzWIEpohPw5CNRxCmuqIOwQ";
        // 注意：LeanCloud 国际版 API 域名是固定的，不需要自定义域名
        String serverUrl = "https://jdgmkfc5.api.lncldglobal.com";

        try {
            LeanCloud.initialize(this, appId, appKey, serverUrl);
            android.util.Log.d("LeanCloud", "Initialization success: " + serverUrl);
        } catch (Exception e) {
            android.util.Log.e("LeanCloud", "Initialization failed", e);
            e.printStackTrace();
        }
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
        SharedPreferences sp = getSharedPreferences("realm_secure_prefs", Context.MODE_PRIVATE);
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

