// QRDatabase.java
package com.qrmaster.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.qrmaster.app.models.QRItem;

@Database(entities = {QRItem.class}, version = 1, exportSchema = false)
public abstract class QRDatabase extends RoomDatabase {
    private static QRDatabase instance;
    
    public abstract QRDao qrDao();
    
    public static synchronized QRDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                QRDatabase.class,
                "qr_database"
            ).fallbackToDestructiveMigration().build();
        }
        return instance;
    }
}
