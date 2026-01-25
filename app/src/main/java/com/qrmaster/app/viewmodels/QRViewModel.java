// QRViewModel.java
package com.qrmaster.app.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.qrmaster.app.data.QRRepository;
import com.qrmaster.app.models.QRItem;
import java.util.List;

public class QRViewModel extends AndroidViewModel {
    private QRRepository repository;
    private LiveData<List<QRItem>> allItems;
    private LiveData<List<QRItem>> savedItems;

    public QRViewModel(@NonNull Application application) {
        super(application);
        repository = new QRRepository(application);
        allItems = repository.getAllItems();
        savedItems = repository.getSavedItems();
    }

    public void insert(QRItem item) {
        repository.insert(item);
    }

    public void update(QRItem item) {
        repository.update(item);
    }

    public void delete(QRItem item) {
        repository.delete(item);
    }

    public void deleteMultiple(List<Integer> ids) {
        repository.deleteMultiple(ids);
    }

    public LiveData<List<QRItem>> getAllItems() {
        return allItems;
    }

    public LiveData<List<QRItem>> getSavedItems() {
        return savedItems;
    }

    public LiveData<List<QRItem>> getItemsByType(String type) {
        return repository.getItemsByType(type);
    }
}