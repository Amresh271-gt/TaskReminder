package com.amresh.taskreminder;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exports tasks and settings to a JSON string and restores them again. */
public class BackupManager {

    private static final String APP_ID = "TaskReminder";

    public static class Result {
        public int added;
        public int skipped;
        public String userName;   // null when the backup had no name
        public Integer nightMode; // null when the backup had no valid value
        public Integer accent;    // null when the backup had no valid value
    }

    public static String export(Context ctx, List<Task> tasks) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", APP_ID);
        root.put("formatVersion", 1);
        root.put("exportedAt", System.currentTimeMillis());

        JSONObject settings = new JSONObject();
        settings.put("userName", Prefs.getName(ctx));
        settings.put("nightMode", Prefs.getNightMode(ctx));
        settings.put("accent", Prefs.getAccent(ctx));
        root.put("settings", settings);

        JSONArray arr = new JSONArray();
        for (Task t : tasks) {
            JSONObject o = new JSONObject();
            o.put("title", t.title == null ? "" : t.title);
            o.put("notes", t.notes == null ? "" : t.notes);
            o.put("dueMillis", t.dueMillis);
            o.put("done", t.done);
            o.put("deletedAt", t.deletedAt);
            arr.put(o);
        }
        root.put("tasks", arr);
        return root.toString(2);
    }

    /**
     * Adds the tasks from a backup. Tasks that already exist (same title, time and bin state)
     * are skipped, so restoring twice does not create duplicates. Must run on a background thread.
     */
    public static Result importJson(Context ctx, AppDatabase db, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!APP_ID.equals(root.optString("app"))) {
            throw new JSONException("Not a TaskReminder backup");
        }

        Result r = new Result();

        JSONObject s = root.optJSONObject("settings");
        if (s != null) {
            String name = s.optString("userName", "").trim();
            if (!name.isEmpty()) r.userName = name;
            if (s.has("nightMode")) {
                int m = s.optInt("nightMode", Integer.MIN_VALUE);
                if (m == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        || m == AppCompatDelegate.MODE_NIGHT_NO
                        || m == AppCompatDelegate.MODE_NIGHT_YES) {
                    r.nightMode = m;
                }
            }
            if (s.has("accent")) {
                int a = s.optInt("accent", -1);
                if (a >= Prefs.ACCENT_PURPLE && a <= Prefs.ACCENT_PINK) r.accent = a;
            }
        }

        Set<String> existing = new HashSet<>();
        for (Task t : db.taskDao().getAll()) {
            existing.add(key(t.title, t.dueMillis, t.deletedAt));
        }

        long now = System.currentTimeMillis();
        JSONArray arr = root.optJSONArray("tasks");
        if (arr == null) arr = new JSONArray();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String title = o.optString("title", "").trim();
            if (title.isEmpty()) continue;

            long due = o.optLong("dueMillis", 0);
            long deletedAt = o.optLong("deletedAt", 0);
            // Bin items past the 15-day limit are not worth bringing back.
            if (deletedAt > 0 && deletedAt < now - AppDatabase.BIN_RETENTION_MILLIS) continue;

            if (!existing.add(key(title, due, deletedAt))) {
                r.skipped++;
                continue;
            }

            Task t = new Task();
            t.title = title;
            String notes = o.optString("notes", "");
            t.notes = notes.isEmpty() ? null : notes;
            t.dueMillis = due;
            t.done = o.optBoolean("done", false);
            t.deletedAt = deletedAt;
            t.id = db.taskDao().insert(t);
            if (t.deletedAt == 0 && !t.done) {
                AlarmScheduler.schedule(ctx, t);
            }
            r.added++;
        }
        return r;
    }

    private static String key(String title, long due, long deletedAt) {
        return title + "|" + due + "|" + (deletedAt > 0 ? "bin" : "active");
    }
}
