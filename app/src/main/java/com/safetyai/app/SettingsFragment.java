package com.safetyai.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

public class SettingsFragment extends Fragment {

    private SwitchCompat switchTheme;
    private SwitchCompat switchAlarm;
    private SwitchCompat switchSms;
    private SeekBar sliderSensitivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        switchTheme = view.findViewById(R.id.switchTheme);
        switchAlarm = view.findViewById(R.id.switchAlarm);
        switchSms = view.findViewById(R.id.switchSms);
        sliderSensitivity = view.findViewById(R.id.sliderSensitivity);
        TextView btnNotificationPermission = view.findViewById(R.id.btnNotificationPermission);
        MaterialCardView btnEmergencyContacts = view.findViewById(R.id.btnEmergencyContacts);

        SharedPreferences prefs = requireActivity().getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);

        switchTheme.setChecked(prefs.getBoolean("dark_theme", false));
        switchAlarm.setChecked(prefs.getBoolean("alarm_sound", true));
        switchSms.setChecked(prefs.getBoolean("auto_sms", true));
        sliderSensitivity.setProgress(prefs.getInt("ai_sensitivity", 50));

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_theme", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("alarm_sound", isChecked).apply();
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) activity.updateSettings();
        });

        switchSms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_sms", isChecked).apply();
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) activity.updateSettings();
        });

        sliderSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("ai_sensitivity", seekBar.getProgress()).apply();
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) activity.updateSettings();
            }
        });

        btnNotificationPermission.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Fallback for older APIs
                Intent fallbackIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallbackIntent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(fallbackIntent);
            }
        });

        btnEmergencyContacts.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EmergencyContactsActivity.class);
            startActivity(intent);
        });

        // Network Override Handling
        com.google.android.material.textfield.TextInputEditText etManualIp = view.findViewById(R.id.etManualIp);
        etManualIp.setText(prefs.getString("manual_server_ip", ""));
        
        etManualIp.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                prefs.edit().putString("manual_server_ip", s.toString()).apply();
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) activity.refreshBackendConnection();
            }
        });

        return view;
    }
}
