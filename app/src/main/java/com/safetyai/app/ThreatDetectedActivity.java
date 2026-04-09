package com.safetyai.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ThreatDetectedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_threat_detected);

        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            if (MainActivity.instance != null && MainActivity.instance.isSosActive()) {
                MainActivity.instance.toggleSos();
            }
            finish();
        });
    }
}
