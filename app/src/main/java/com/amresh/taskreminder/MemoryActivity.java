package com.amresh.taskreminder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Place to keep facts and info that are not reminders. Search, tag filter, pin, copy. */
public class MemoryActivity extends AppCompatActivity implements MemoryAdapter.Listener {

    private AppDatabase db;
    private MemoryAdapter adapter;
    private EditText etSearch;
    private ChipGroup chipGroup;
    private TextView tvEmpty;

    private List<Memory> all = new ArrayList<>();
    /** Currently selected tag filter, or null for "All". */
    private String selectedTag = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);
        setTitle(R.string.mem_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = AppDatabase.get(this);

        etSearch = findViewById(R.id.etSearchMemory);
        chipGroup = findViewById(R.id.chipGroupTags);
        tvEmpty = findViewById(R.id.tvMemoryEmpty);
        FloatingActionButton fab = findViewById(R.id.fabAddMemory);

        RecyclerView rv = findViewById(R.id.rvMemory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemoryAdapter(this);
        rv.setAdapter(adapter);

        db.memoryDao().observeAll().observe(this, list -> {
            all = list == null ? new ArrayList<>() : list;
            render();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });

        fab.setOnClickListener(v -> showEditDialog(null));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ------------------------------------------------------------ rendering

    /** Rebuilds the tag chips and applies search + tag filter to the list. */
    private void render() {
        // Distinct tags, case-insensitive, sorted.
        TreeSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Memory m : all) {
            if (m.tag != null && !m.tag.trim().isEmpty()) tags.add(m.tag.trim());
        }
        if (selectedTag != null && !tags.contains(selectedTag)) selectedTag = null;

        chipGroup.removeAllViews();
        if (!tags.isEmpty()) {
            addChip(getString(R.string.mem_all_tags), null);
            for (String t : tags) addChip(t, t);
        }

        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<Memory> shown = new ArrayList<>();
        for (Memory m : all) {
            if (selectedTag != null) {
                if (m.tag == null || !m.tag.trim().equalsIgnoreCase(selectedTag)) continue;
            }
            if (!q.isEmpty() && !matches(m, q)) continue;
            shown.add(m);
        }
        adapter.setItems(shown);

        if (shown.isEmpty()) {
            tvEmpty.setText(all.isEmpty() ? R.string.mem_empty : R.string.mem_no_match);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private static boolean matches(Memory m, String q) {
        return contains(m.title, q) || contains(m.content, q) || contains(m.tag, q);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.getDefault()).contains(q);
    }

    private void addChip(String label, final String tag) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        boolean isSelected = (tag == null && selectedTag == null)
                || (tag != null && tag.equalsIgnoreCase(selectedTag));
        chip.setChecked(isSelected);
        chip.setOnClickListener(v -> {
            // Tapping the active tag again goes back to "All".
            if (tag != null && tag.equalsIgnoreCase(selectedTag)) {
                selectedTag = null;
            } else {
                selectedTag = tag;
            }
            render();
        });
        chipGroup.addView(chip);
    }

    // -------------------------------------------------------------- actions

    @Override
    public void onPin(final Memory memory) {
        memory.pinned = !memory.pinned;
        AppDatabase.IO.execute(() -> db.memoryDao().update(memory));
    }

    @Override
    public void onCopy(Memory memory) {
        String text = (memory.content != null && !memory.content.trim().isEmpty())
                ? memory.content : memory.title;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(memory.title, text));
            Toast.makeText(this, R.string.mem_copied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEdit(Memory memory) {
        showEditDialog(memory);
    }

    @Override
    public void onDelete(final Memory memory) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mem_delete_title)
                .setMessage(R.string.mem_delete_msg)
                .setPositiveButton(R.string.mem_delete_yes, (d, w) -> {
                    AppDatabase.IO.execute(() -> db.memoryDao().delete(memory));
                    Toast.makeText(this, R.string.mem_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the add/edit dialog. Pass null to add a new memory. */
    private void showEditDialog(final Memory existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_memory, null);
        final EditText etTitle = view.findViewById(R.id.etMemTitle);
        final EditText etContent = view.findViewById(R.id.etMemContent);
        final EditText etTag = view.findViewById(R.id.etMemTag);
        final CheckBox cbPinned = view.findViewById(R.id.cbMemPinned);

        if (existing != null) {
            etTitle.setText(existing.title);
            etTitle.setSelection(etTitle.getText().length());
            etContent.setText(existing.content == null ? "" : existing.content);
            etTag.setText(existing.tag == null ? "" : existing.tag);
            cbPinned.setChecked(existing.pinned);
        } else if (selectedTag != null) {
            etTag.setText(selectedTag); // new memory inherits the tag you are browsing
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.mem_add : R.string.mem_edit)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        // Set here so a missing title keeps the dialog open instead of closing it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.mem_title_required, Toast.LENGTH_SHORT).show();
                return;
            }
            String content = etContent.getText().toString().trim();
            String tag = etTag.getText().toString().trim();
            long now = System.currentTimeMillis();

            final Memory m = existing != null ? existing : new Memory();
            m.title = title;
            m.content = content;
            m.tag = tag.isEmpty() ? null : tag;
            m.pinned = cbPinned.isChecked();
            m.updatedAt = now;
            if (existing == null) m.createdAt = now;

            AppDatabase.IO.execute(() -> {
                if (existing == null) {
                    m.id = db.memoryDao().insert(m);
                } else {
                    db.memoryDao().update(m);
                }
            });
            Toast.makeText(this, R.string.mem_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }
}
