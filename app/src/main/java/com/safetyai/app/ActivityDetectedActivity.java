package com.safetyai.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ActivityDetectedActivity extends AppCompatActivity {

    private Ringtone ringtone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_detected);

        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        boolean playAlarm = prefs.getBoolean("alarm_sound", true);

        if (playAlarm) {
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                ringtone.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> closeAndStop());
        
        MaterialButton btnOk = findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> closeAndStop());

        MaterialButton btnSendSos = findViewById(R.id.btnSendSos);
        btnSendSos.setOnClickListener(v -> {
            stopAlarm();
            if (MainActivity.instance != null && !MainActivity.instance.isSosActive()) {
                MainActivity.instance.toggleSos();
                
                // Also handle the HomeFragment UI update if user presses send SOS from here
                // Note: toggleSos() in MainActivity handles the logic, but the UI in HomeFragment
                // might need explicit refreshing, but since it's a prototype, toggleSos makes a Toast.
            }
            startActivity(new Intent(this, ThreatDetectedActivity.class));
            finish();
        });
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void closeAndStop() {
        stopAlarm();
        finish();
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }
}
