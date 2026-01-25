// HistoryFragment.java
package com.qrmaster.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qrmaster.app.R;
import com.qrmaster.app.adapters.QRAdapter;
import com.qrmaster.app.viewmodels.QRViewModel;

public class HistoryFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyView;
    private QRAdapter adapter;
    private QRViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QRAdapter(requireContext(), viewModel);
        recyclerView.setAdapter(adapter);
        
        viewModel = new ViewModelProvider(requireActivity()).get(QRViewModel.class);
        
        viewModel.getAllItems().observe(getViewLifecycleOwner(), items -> {
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
}