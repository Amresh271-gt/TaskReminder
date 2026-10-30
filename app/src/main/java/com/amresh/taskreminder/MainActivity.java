package com.amresh.taskreminder;

import android.Manifest;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TaskAdapter.Listener {

    private AppDatabase db;
    private TaskAdapter adapter;
    private EditText etTask;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.get(this);
        ReminderReceiver.createChannel(this);

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

        db.taskDao().observeAll().observe(this, tasks -> {
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
    }

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
        tvTotal.setText("Total: " + total);
        tvDone.setText("Done: " + done);
        tvLeft.setText("Left: " + (total - done));
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
    public void onDelete(Task task) {
        AppDatabase.IO.execute(() -> {
            AlarmScheduler.cancel(getApplicationContext(), task);
            db.taskDao().delete(task);
        });
    }
}
