// MainActivity.java
package com.qrmaster.app;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qrmaster.app.fragments.ScanFragment;
import com.qrmaster.app.fragments.CreateFragment;
import com.qrmaster.app.fragments.HistoryFragment;
import com.qrmaster.app.fragments.SavedFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabQuickScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        //fabQuickScan = findViewById(R.id.fab_quick_scan);

        bottomNav.setOnItemSelectedListener(navListener);
        
        // Load default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ScanFragment())
                .commit();
        }

        // Quick scan FAB
        /*fabQuickScan.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ScanFragment())
                .commit();
            bottomNav.setSelectedItemId(R.id.nav_scan);
        });*/
    }

    private BottomNavigationView.OnItemSelectedListener navListener = 
        new BottomNavigationView.OnItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_scan) {
                selectedFragment = new ScanFragment();
            } else if (itemId == R.id.nav_create) {
                selectedFragment = new CreateFragment();
            } else if (itemId == R.id.nav_history) {
                selectedFragment = new HistoryFragment();
            } else if (itemId == R.id.nav_saved) {
                selectedFragment = new SavedFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
                return true;
            }
            return false;
        }
    };
}