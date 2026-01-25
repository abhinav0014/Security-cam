// QRRepository.java
package com.qrmaster.app.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.qrmaster.app.models.QRItem;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QRRepository {
    private QRDao qrDao;
    private LiveData<List<QRItem>> allItems;
    private LiveData<List<QRItem>> savedItems;
    private ExecutorService executorService;

    public QRRepository(Application application) {
        QRDatabase database = QRDatabase.getInstance(application);
        qrDao = database.qrDao();
        allItems = qrDao.getAllItems();
        savedItems = qrDao.getSavedItems();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(QRItem item) {
        executorService.execute(() -> qrDao.insert(item));
    }

    public void update(QRItem item) {
        executorService.execute(() -> qrDao.update(item));
    }

    public void delete(QRItem item) {
        executorService.execute(() -> qrDao.delete(item));
    }

    public void deleteMultiple(List<Integer> ids) {
        executorService.execute(() -> qrDao.deleteMultiple(ids));
    }

    public LiveData<List<QRItem>> getAllItems() {
        return allItems;
    }

    public LiveData<List<QRItem>> getSavedItems() {
        return savedItems;
    }

    public LiveData<List<QRItem>> getItemsByType(String type) {
        return qrDao.getItemsByType(type);
    }
}