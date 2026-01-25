// QRDao.java - Database Access
package com.qrmaster.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.qrmaster.app.models.QRItem;
import java.util.List;

@Dao
public interface QRDao {
    @Insert
    long insert(QRItem item);

    @Update
    void update(QRItem item);

    @Delete
    void delete(QRItem item);

    @Query("SELECT * FROM qr_items ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getAllItems();

    @Query("SELECT * FROM qr_items WHERE isSaved = 1 ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getSavedItems();

    @Query("SELECT * FROM qr_items WHERE type = :type ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getItemsByType(String type);

    @Query("DELETE FROM qr_items WHERE id IN (:ids)")
    void deleteMultiple(List<Integer> ids);
}
