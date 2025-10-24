// create a simple kotlin file for settings activity with viewbinding and a back button to return 
package com.onnet.securitycam

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.onnet.securitycam.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable the Up button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

}