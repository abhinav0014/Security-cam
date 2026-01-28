// SavedFragment.java - Enhanced with multi-select and delete all
package com.qrmaster.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qrmaster.app.R;
import com.qrmaster.app.adapters.QRAdapter;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.util.ArrayList;
import java.util.List;

public class SavedFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyView;
    private MaterialToolbar toolbar;
    private QRAdapter adapter;
    private QRViewModel viewModel;
    private ActionMode actionMode;
    private List<QRItem> selectedItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        toolbar = view.findViewById(R.id.toolbar);
        
        setupToolbar();
        
        viewModel = new ViewModelProvider(this).get(QRViewModel.class);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QRAdapter(requireContext(), viewModel, new QRAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(QRItem item) {
                if (actionMode != null) {
                    toggleSelection(item);
                } else {
                    // Show detail dialog
                    adapter.showDetailDialog(item, requireActivity());
                }
            }

            @Override
            public void onItemLongClick(QRItem item) {
                if (actionMode == null) {
                    actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
                }
                toggleSelection(item);
            }

            @Override
            public void onMenuClick(QRItem item) {
                showItemMenu(item);
            }
        });
        recyclerView.setAdapter(adapter);
        
        viewModel.getSavedItems().observe(getViewLifecycleOwner(), items -> {
            adapter.setItems(items);
            if (items.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
        
        return view;
    }

    private void setupToolbar() {
        toolbar.setTitle("Saved");
        toolbar.inflateMenu(R.menu.saved_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_delete_all) {
                showDeleteAllDialog();
                return true;
            } else if (itemId == R.id.action_select_all) {
                if (actionMode == null) {
                    actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
                }
                selectAll();
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(QRItem item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        
        adapter.setSelectedItems(selectedItems);
        
        if (actionMode != null) {
            if (selectedItems.isEmpty()) {
                actionMode.finish();
            } else {
                actionMode.setTitle(selectedItems.size() + " selected");
            }
        }
    }

    private void selectAll() {
        selectedItems.clear();
        viewModel.getSavedItems().observe(getViewLifecycleOwner(), items -> {
            selectedItems.addAll(items);
            adapter.setSelectedItems(selectedItems);
            if (actionMode != null) {
                actionMode.setTitle(selectedItems.size() + " selected");
            }
        });
    }

    private void showItemMenu(QRItem item) {
        String[] options = {"Delete", "Remove from Saved", "Share", "Edit"};
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Delete
                        deleteItem(item);
                        break;
                    case 1: // Remove from Saved
                        removeFromSaved(item);
                        break;
                    case 2: // Share
                        adapter.shareQRCode(item, requireContext());
                        break;
                    case 3: // Edit
                        // TODO: Implement edit functionality
                        Toast.makeText(requireContext(), "Edit coming soon", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .show();
    }

    private void deleteItem(QRItem item) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete QR Code")
            .setMessage("Are you sure you want to delete this QR code?")
            .setPositiveButton("Delete", (dialog, which) -> {
                viewModel.delete(item);
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void removeFromSaved(QRItem item) {
        item.setSaved(false);
        viewModel.update(item);
        Toast.makeText(requireContext(), "Removed from saved", Toast.LENGTH_SHORT).show();
    }

    private void showDeleteAllDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Saved")
            .setMessage("Are you sure you want to delete all saved QR codes?")
            .setPositiveButton("Delete All", (dialog, which) -> {
                viewModel.getSavedItems().observe(getViewLifecycleOwner(), items -> {
                    List<Integer> ids = new ArrayList<>();
                    for (QRItem item : items) {
                        ids.add(item.getId());
                    }
                    viewModel.deleteMultiple(ids);
                    Toast.makeText(requireContext(), "All saved items deleted", Toast.LENGTH_SHORT).show();
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteSelectedItems() {
        List<Integer> ids = new ArrayList<>();
        for (QRItem item : selectedItems) {
            ids.add(item.getId());
        }
        viewModel.deleteMultiple(ids);
        Toast.makeText(requireContext(), selectedItems.size() + " items deleted", Toast.LENGTH_SHORT).show();
        
        selectedItems.clear();
        adapter.setSelectedItems(selectedItems);
        
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.action_mode_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_delete) {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Selected")
                    .setMessage("Delete " + selectedItems.size() + " items?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteSelectedItems())
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selectedItems.clear();
            adapter.setSelectedItems(selectedItems);
        }
    };
}
