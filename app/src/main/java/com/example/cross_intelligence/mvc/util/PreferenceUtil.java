package com.example.cross_intelligence.mvc.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SharedPreferences 工具，提供线程安全的读写与链式 Editor。
 */
public final class PreferenceUtil {

    private static final String DEFAULT_NAME = "cross_intelligence_prefs";
    private static final ConcurrentHashMap<String, SharedPreferences> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PreferenceUtil() {
    }

    private static SharedPreferences getPrefs(@NonNull Context context, @NonNull String name) {
        return CACHE.computeIfAbsent(name, key ->
                context.getApplicationContext().getSharedPreferences(key, Context.MODE_PRIVATE));
    }

    public static Editor editor(@NonNull Context context) {
        return new Editor(getPrefs(context, DEFAULT_NAME));
    }

    public static Editor editor(@NonNull Context context, @NonNull String name) {
        return new Editor(getPrefs(context, name));
    }

    public static String getString(@NonNull Context context, @NonNull String key, String def) {
        return getPrefs(context, DEFAULT_NAME).getString(key, def);
    }

    public static boolean getBoolean(@NonNull Context context, @NonNull String key, boolean def) {
        return getPrefs(context, DEFAULT_NAME).getBoolean(key, def);
    }

    public static int getInt(@NonNull Context context, @NonNull String key, int def) {
        return getPrefs(context, DEFAULT_NAME).getInt(key, def);
    }

    public static final class Editor {
        private final SharedPreferences.Editor editor;

        private Editor(SharedPreferences preferences) {
            this.editor = preferences.edit();
        }

        public Editor putString(String key, String value) {
            editor.putString(key, value);
            return this;
        }

        public Editor putBoolean(String key, boolean value) {
            editor.putBoolean(key, value);
            return this;
        }

        public Editor putInt(String key, int value) {
            editor.putInt(key, value);
            return this;
        }

        public Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        public Editor clear() {
            editor.clear();
            return this;
        }

        public void applyAsync() {
            EXECUTOR.execute(editor::apply);
        }

        public boolean commit() {
            return editor.commit();
        }
    }
}






