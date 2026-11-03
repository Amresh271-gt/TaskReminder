package com.amresh.taskreminder;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Small wrapper around SharedPreferences for the user name and theme choices. */
public class Prefs {

    public static final int ACCENT_PURPLE = 0;
    public static final int ACCENT_TEAL = 1;
    public static final int ACCENT_BLUE = 2;
    public static final int ACCENT_ORANGE = 3;
    public static final int ACCENT_PINK = 4;

    private static final String FILE = "taskreminder_prefs";
    private static final String K_NAME = "user_name";
    private static final String K_NAME_ASKED = "name_asked";
    private static final String K_NIGHT = "night_mode";
    private static final String K_ACCENT = "accent";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getName(Context c) {
        return sp(c).getString(K_NAME, "");
    }

    public static void setName(Context c, String name) {
        sp(c).edit().putString(K_NAME, name == null ? "" : name.trim()).apply();
    }

    public static boolean wasNameAsked(Context c) {
        return sp(c).getBoolean(K_NAME_ASKED, false);
    }

    public static void setNameAsked(Context c) {
        sp(c).edit().putBoolean(K_NAME_ASKED, true).apply();
    }

    public static int getNightMode(Context c) {
        return sp(c).getInt(K_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setNightMode(Context c, int mode) {
        sp(c).edit().putInt(K_NIGHT, mode).apply();
    }

    public static int getAccent(Context c) {
        return sp(c).getInt(K_ACCENT, ACCENT_PURPLE);
    }

    public static void setAccent(Context c, int accent) {
        sp(c).edit().putInt(K_ACCENT, accent).apply();
    }
}
