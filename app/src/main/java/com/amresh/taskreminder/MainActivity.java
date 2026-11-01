package com.amresh.taskreminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;

public class MainActivity extends AppCompatActivity implements TaskAdapter.Listener {

    private static final int[] NIGHT_MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
    };

    private AppDatabase db;
    private TaskAdapter adapter;
    private EditText etTask;
    private TextView tvGreeting, tvSubtitle;
    private TextView tvTotal, tvDone, tvLeft;

    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> texts =
                            result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (texts != null && !texts.isEmpty()) {
                        addTask(texts.get(0)); // spoken tasks are saved straight away
                    }
                }
            });

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) doExport(uri);
            });

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) doImport(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.get(this);
        ReminderReceiver.createChannel(this);

        // Remove bin items that are older than 15 days.
        AppDatabase.IO.execute(() ->
                db.taskDao().purgeBinOlderThan(System.currentTimeMillis() - AppDatabase.BIN_RETENTION_MILLIS));

        tvGreeting = findViewById(R.id.tvGreeting);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        etTask = findViewById(R.id.etTask);
        tvTotal = findViewById(R.id.tvTotal);
        tvDone = findViewById(R.id.tvDone);
        tvLeft = findViewById(R.id.tvLeft);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnMic = findViewById(R.id.btnMic);

        RecyclerView rv = findViewById(R.id.rvTasks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(this);
        rv.setAdapter(adapter);

        db.taskDao().observeActive().observe(this, tasks -> {
            adapter.setTasks(tasks);
            updateCounters(tasks);
        });

        btnAdd.setOnClickListener(v -> {
            String text = etTask.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.empty_task, Toast.LENGTH_SHORT).show();
                return;
            }
            addTask(text);
            etTask.setText("");
        });
        btnMic.setOnClickListener(v -> startVoiceInput());

        askForPermissions();

        if (!Prefs.wasNameAsked(this)) {
            Prefs.setNameAsked(this);
            showNameDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
    }

    // ---------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_bin) {
            startActivity(new Intent(this, BinActivity.class));
            return true;
        } else if (id == R.id.menu_theme_mode) {
            showThemeModeDialog();
            return true;
        } else if (id == R.id.menu_accent) {
            showAccentDialog();
            return true;
        } else if (id == R.id.menu_name) {
            showNameDialog();
            return true;
        } else if (id == R.id.menu_backup) {
            String stamp = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            exportLauncher.launch("TaskReminder-backup-" + stamp + ".json");
            return true;
        } else if (id == R.id.menu_restore) {
            importLauncher.launch(new String[]{"*/*"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------ name + greeting

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello;
        if (hour >= 5 && hour < 12) hello = "Good morning";
        else if (hour >= 12 && hour < 17) hello = "Good afternoon";
        else if (hour >= 17 && hour < 21) hello = "Good evening";
        else hello = "Hello";
        String name = Prefs.getName(this);
        tvGreeting.setText(name.isEmpty() ? hello : hello + ", " + name);
    }

    private void showNameDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_name);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        input.setText(Prefs.getName(this));
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.ask_name_title)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Prefs.setName(this, input.getText().toString());
                    updateGreeting();
                })
                .setNegativeButton(R.string.skip, null)
                .show();
    }

    // ---------------------------------------------------------------- theme

    private void showThemeModeDialog() {
        int current = Prefs.getNightMode(this);
        int checked = 0;
        for (int i = 0; i < NIGHT_MODES.length; i++) {
            if (NIGHT_MODES[i] == current) checked = i;
        }
        String[] names = {
                getString(R.string.mode_system), getString(R.string.mode_light), getString(R.string.mode_dark)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_theme_mode)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    Prefs.setNightMode(this, NIGHT_MODES[which]);
                    d.dismiss();
                    AppCompatDelegate.setDefaultNightMode(NIGHT_MODES[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAccentDialog() {
        String[] names = {
                getString(R.string.accent_purple), getString(R.string.accent_teal),
                getString(R.string.accent_blue), getString(R.string.accent_orange),
                getString(R.string.accent_pink)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_accent)
                .setSingleChoiceItems(names, Prefs.getAccent(this), (d, which) -> {
                    Prefs.setAccent(this, which);
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------- backup/restore

    private void doExport(final Uri uri) {
        final android.content.Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                List<Task> all = db.taskDao().getAll();
                String json = BackupManager.export(app, all);
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    if (os == null) throw new IOException("No output stream");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                toast(getString(R.string.backup_done, all.size()));
            } catch (Exception e) {
                toast(getString(R.string.backup_failed));
            }
        });
    }

    private void doImport(final Uri uri) {
        final android.content.Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                String json = readAll(uri);
                final BackupManager.Result r = BackupManager.importJson(app, db, json);
                runOnUiThread(() -> applyRestoredSettings(r));
                toast(getString(R.string.restore_done, r.added, r.skipped));
            } catch (JSONException e) {
                toast(getString(R.string.restore_bad_file));
            } catch (Exception e) {
                toast(getString(R.string.restore_failed));
            }
        });
    }

    private String readAll(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No input stream");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 5 * 1024 * 1024) throw new IOException("File too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void applyRestoredSettings(BackupManager.Result r) {
        boolean recreateNeeded = false;
        if (r.userName != null) {
            Prefs.setName(this, r.userName);
            updateGreeting();
        }
        if (r.accent != null && r.accent != Prefs.getAccent(this)) {
            Prefs.setAccent(this, r.accent);
            recreateNeeded = true;
        }
        if (r.nightMode != null && r.nightMode != Prefs.getNightMode(this)) {
            Prefs.setNightMode(this, r.nightMode);
            AppCompatDelegate.setDefaultNightMode(r.nightMode); // recreates the activity itself
            recreateNeeded = false;
        }
        if (recreateNeeded) recreate();
    }

    private void toast(final String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    // ----------------------------------------------------------- permissions

    private void askForPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                } catch (Exception ignored) {
                    // Reminders still work, just not to-the-minute.
                }
            }
        }
    }

    private void startVoiceInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_prompt));
        try {
            speechLauncher.launch(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_speech, Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------------------------------------------------- tasks

    private void addTask(String rawText) {
        TimeParser.Result r = TimeParser.parse(rawText);
        final Task t = new Task();
        t.title = r.title;
        t.dueMillis = r.dueMillis;
        t.done = false;
        AppDatabase.IO.execute(() -> {
            t.id = db.taskDao().insert(t);
            AlarmScheduler.schedule(getApplicationContext(), t);
        });
    }

    private void updateCounters(List<Task> tasks) {
        int total = tasks == null ? 0 : tasks.size();
        int done = 0;
        if (tasks != null) {
            for (Task t : tasks) if (t.done) done++;
        }
        int left = total - done;
        tvTotal.setText("Total: " + total);
        tvDone.setText("Done: " + done);
        tvLeft.setText("Left: " + left);

        if (total == 0) {
            tvSubtitle.setText(R.string.subtitle_none);
        } else if (left == 0) {
            tvSubtitle.setText(R.string.subtitle_all_done);
        } else {
            tvSubtitle.setText(getResources().getQuantityString(R.plurals.subtitle_left, left, left));
        }
    }

    @Override
    public void onToggle(Task task, boolean done) {
        task.done = done;
        AppDatabase.IO.execute(() -> {
            db.taskDao().update(task);
            if (done) {
                AlarmScheduler.cancel(getApplicationContext(), task);
            } else {
                AlarmScheduler.schedule(getApplicationContext(), task);
            }
        });
    }

    @Override
    public void onEdit(final Task task) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null);
        final EditText etTitle = view.findViewById(R.id.etEditTitle);
        final EditText etNotes = view.findViewById(R.id.etEditNotes);
        final TextView tvDue = view.findViewById(R.id.tvEditDue);
        Button btnPick = view.findViewById(R.id.btnPickTime);
        Button btnClear = view.findViewById(R.id.btnClearTime);

        etTitle.setText(task.title);
        etTitle.setSelection(etTitle.getText().length());
        etNotes.setText(task.notes == null ? "" : task.notes);

        final long[] due = {task.dueMillis};
        showDue(tvDue, due[0]);

        btnPick.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            if (due[0] > 0) c.setTimeInMillis(due[0]);
            new DatePickerDialog(this, (dp, year, month, day) ->
                    new TimePickerDialog(this, (tp, hour, minute) -> {
                        Calendar picked = Calendar.getInstance();
                        picked.set(year, month, day, hour, minute, 0);
                        picked.set(Calendar.MILLISECOND, 0);
                        due[0] = picked.getTimeInMillis();
                        showDue(tvDue, due[0]);
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                            DateFormat.is24HourFormat(this)).show(),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        btnClear.setOnClickListener(v -> {
            due[0] = 0;
            showDue(tvDue, 0);
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.edit_task)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        // Set here so an empty title keeps the dialog open instead of closing it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newTitle = etTitle.getText().toString().trim();
            if (newTitle.isEmpty()) {
                Toast.makeText(this, R.string.empty_task, Toast.LENGTH_SHORT).show();
                return;
            }
            String newNotes = etNotes.getText().toString().trim();
            task.title = newTitle;
            task.notes = newNotes.isEmpty() ? null : newNotes;
            task.dueMillis = due[0];
            AppDatabase.IO.execute(() -> {
                AlarmScheduler.cancel(getApplicationContext(), task);
                db.taskDao().update(task);
                AlarmScheduler.schedule(getApplicationContext(), task);
            });
            dialog.dismiss();
        });
    }

    private void showDue(TextView tv, long millis) {
        if (millis > 0) {
            tv.setText(DateFormat.format("EEE, d MMM yyyy, h:mm a", millis));
        } else {
            tv.setText(R.string.no_time);
        }
    }

    @Override
    public void onDelete(Task task) {
        // Deleting moves the task to the recycle bin; it is removed for good after 15 days.
        task.deletedAt = System.currentTimeMillis();
        Toast.makeText(this, R.string.moved_to_bin, Toast.LENGTH_SHORT).show();
        AppDatabase.IO.execute(() -> {
            AlarmScheduler.cancel(getApplicationContext(), task);
            db.taskDao().update(task);
        });
    }
}
