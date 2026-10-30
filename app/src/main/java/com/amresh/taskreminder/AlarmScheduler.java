package com.amresh.taskreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmScheduler {

    public static final String EXTRA_ID = "task_id";
    public static final String EXTRA_TITLE = "task_title";

    private static PendingIntent pendingIntent(Context ctx, long id, String title, int extraFlags) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra(EXTRA_ID, id);
        i.putExtra(EXTRA_TITLE, title);
        return PendingIntent.getBroadcast(ctx, (int) id, i,
                PendingIntent.FLAG_IMMUTABLE | extraFlags);
    }

    public static void schedule(Context ctx, Task task) {
        if (task.dueMillis <= System.currentTimeMillis() || task.done) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(ctx, task.id, task.title, PendingIntent.FLAG_UPDATE_CURRENT);

        boolean exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueMillis, pi);
        } else {
            // Exact-alarm permission not granted: still fire, just possibly a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueMillis, pi);
        }
    }

    public static void cancel(Context ctx, Task task) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx, task.id, task.title, PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
