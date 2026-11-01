package com.amresh.taskreminder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Task.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** How long deleted tasks stay in the recycle bin: 15 days. */
    public static final long BIN_RETENTION_MILLIS = 15L * 24 * 60 * 60 * 1000;

    public abstract TaskDao taskDao();

    /** Single background thread for all database writes. */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** v1 -> v2: adds recycle-bin time and notes. Existing tasks are kept as they are. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT");
        }
    };

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "tasks.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
