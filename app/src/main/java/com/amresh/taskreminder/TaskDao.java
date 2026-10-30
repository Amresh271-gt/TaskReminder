package com.amresh.taskreminder;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN dueMillis = 0 THEN 1 ELSE 0 END, dueMillis ASC, id DESC")
    LiveData<List<Task>> observeAll();

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueMillis > 0")
    List<Task> getPendingWithTime();
}
