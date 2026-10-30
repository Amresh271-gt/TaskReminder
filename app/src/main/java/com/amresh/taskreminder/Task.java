package com.amresh.taskreminder;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;

    /** Due time in epoch millis, or 0 when no time was found. */
    public long dueMillis;

    public boolean done;
}
