package com.amresh.taskreminder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.widget.Toast;

/**
 * Single place where a spoken (or typed) command is understood and carried out.
 * Every trigger point (Quick Settings tile now; notification, widget later) calls handle().
 *
 * Right now the only command is "add a task / reminder". Later commands such as
 * "open WhatsApp", "open notes.pdf" or "close" are checked at the top of handle(),
 * before falling back to adding a task.
 */
public class CommandHandler {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void handle(Context ctx, String spoken) {
        final Context app = ctx.getApplicationContext();

        // FUTURE: if (OpenAppCommand.tryHandle(app, spoken)) return;
        // FUTURE: if (CloseCommand.tryHandle(app, spoken)) return;

        addTask(app, spoken);
    }

    private static void addTask(final Context app, String spoken) {
        TimeParser.Result r = TimeParser.parse(spoken);
        final Task t = new Task();
        t.title = r.title;
        t.dueMillis = r.dueMillis;
        t.done = false;

        AppDatabase.IO.execute(() -> {
            t.id = AppDatabase.get(app).taskDao().insert(t);
            AlarmScheduler.schedule(app, t);
        });

        String message;
        if (t.dueMillis > 0) {
            message = app.getString(R.string.cmd_task_set_time, t.title,
                    DateFormat.format("EEE, d MMM, h:mm a", t.dueMillis));
        } else {
            message = app.getString(R.string.cmd_task_added, t.title);
        }
        toast(app, message);
    }

    static void toast(final Context app, final String message) {
        MAIN.post(() -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
    }
}
