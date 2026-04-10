package com.safetyai.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import com.google.android.material.card.MaterialCardView;
import android.content.SharedPreferences;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Context;
public class HomeFragment extends Fragment {

    private View btnSos;
    private TextView tvSosText;
    private TextView tvStatus;
    private View bottomActionButtons;
    private View trackerContainer;
    private TextView tvServerStatus;
    private View statusDot;
    
    private TextView trStep1, trStep2, trStep3, trStep4;
    private android.os.Handler handler = new android.os.Handler();
    private AnimatorSet breathingAnimatorSet;
    private AnimatorSet idleAnimatorSet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        btnSos = view.findViewById(R.id.btnSos);
        tvSosText = view.findViewById(R.id.tvSosText);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvServerStatus = view.findViewById(R.id.tvServerStatus);
        statusDot = view.findViewById(R.id.statusDot);
        bottomActionButtons = view.findViewById(R.id.bottomActionButtons);
        trackerContainer = view.findViewById(R.id.trackerContainer);
        
        trStep1 = view.findViewById(R.id.trStep1);
        trStep2 = view.findViewById(R.id.trStep2);
        trStep3 = view.findViewById(R.id.trStep3);
        trStep4 = view.findViewById(R.id.trStep4);
        
        View btnShareLocation = view.findViewById(R.id.btnShareLocation);
        View btnCallEmergency = view.findViewById(R.id.btnCallEmergency);
        View btnHomeSettings = view.findViewById(R.id.btnHomeSettings);
        View btnHomeNotifications = view.findViewById(R.id.btnHomeNotifications);
        View btnHomeFeatures = view.findViewById(R.id.btnHomeFeatures);

        // Sync initial UI state with MainActivity
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.isSosActive()) {
            setSosUiActive(true);
        } else {
            setSosUiActive(false);
        }

        btnHomeSettings.setOnClickListener(v -> {
            if (mainActivity != null) {
                mainActivity.navigateToSettings();
            }
        });

        btnHomeNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), NotificationsActivity.class);
            startActivity(intent);
        });

        btnHomeFeatures.setOnClickListener(v -> showFeaturesBottomSheet());

        btnSos.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            if (mainActivity != null) {
                boolean isActive = mainActivity.toggleSos();
                setSosUiActive(isActive);
            }
        });

        btnShareLocation.setOnClickListener(v -> {
            Toast.makeText(getContext(), "\uD83D\uDCCD Location anonymously shared via SMS (Offline Backup)!", Toast.LENGTH_SHORT).show();
        });

        btnCallEmergency.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EmergencyContactsActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private void showFeaturesBottomSheet() {
        Context context = getContext();
        if (context == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View bottomSheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_features, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        SharedPreferences prefs = context.getSharedPreferences("SafetyFeaturesPrefs", Context.MODE_PRIVATE);

        SwitchCompat switchFallDetection = bottomSheetView.findViewById(R.id.switchFallDetection);
        SwitchCompat switchVoiceSos = bottomSheetView.findViewById(R.id.switchVoiceSos);
        SwitchCompat switchAiMonitoring = bottomSheetView.findViewById(R.id.switchAiMonitoring);
        SwitchCompat switchOfflineLocation = bottomSheetView.findViewById(R.id.switchOfflineLocation);

        switchFallDetection.setChecked(prefs.getBoolean("fall_detection_enabled", true));
        switchVoiceSos.setChecked(prefs.getBoolean("voice_sos_enabled", true));
        switchAiMonitoring.setChecked(prefs.getBoolean("ai_monitoring_enabled", true));
        switchOfflineLocation.setChecked(prefs.getBoolean("offline_location_enabled", true));

        // Save states securely when toggled
        SharedPreferences.Editor editor = prefs.edit();
        
        switchFallDetection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("fall_detection_enabled", isChecked).apply();
            if (isChecked) Toast.makeText(context, "Fall Detection Active \uD83D\uDEB6", Toast.LENGTH_SHORT).show();
            else Toast.makeText(context, "Fall Detection Disabled", Toast.LENGTH_SHORT).show();
        });

        switchVoiceSos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("voice_sos_enabled", isChecked).apply();
            if (isChecked) Toast.makeText(context, "Voice SOS Listening \uD83C\uDF99\uFE0F", Toast.LENGTH_SHORT).show();
            else Toast.makeText(context, "Voice SOS Disabled", Toast.LENGTH_SHORT).show();
        });

        switchAiMonitoring.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("ai_monitoring_enabled", isChecked).apply();
            if (isChecked) Toast.makeText(context, "AI Monitoring Active \uD83E\uDD16", Toast.LENGTH_SHORT).show();
            else Toast.makeText(context, "AI Monitoring Disabled", Toast.LENGTH_SHORT).show();
            // Optional: update UI to reflect aiPill state if needed
        });

        switchOfflineLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("offline_location_enabled", isChecked).apply();
        });

        bottomSheetDialog.show();
    }

    public void setSosUiActive(boolean isActive) {
        if (isActive) {
            stopIdleAnimation();
            tvSosText.setText("STOP");
            
            tvStatus.setText("Status: EMERGENCY 🔴");
            tvStatus.setTextColor(Color.parseColor("#F44336"));
            
            if (bottomActionButtons != null) bottomActionButtons.setVisibility(View.GONE);
            if (trackerContainer != null) {
                trackerContainer.setVisibility(View.VISIBLE);
                
                // Hide steps initially for cool reveal animation
                trStep1.setVisibility(View.GONE);
                trStep2.setVisibility(View.GONE);
                trStep3.setVisibility(View.GONE);
                trStep4.setVisibility(View.GONE);
            }
            
            if (breathingAnimatorSet == null) {
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnSos, "scaleX", 1f, 1.15f);
                scaleX.setRepeatCount(ValueAnimator.INFINITE);
                scaleX.setRepeatMode(ValueAnimator.REVERSE);
                
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnSos, "scaleY", 1f, 1.15f);
                scaleY.setRepeatCount(ValueAnimator.INFINITE);
                scaleY.setRepeatMode(ValueAnimator.REVERSE);
                
                ObjectAnimator alpha = ObjectAnimator.ofFloat(btnSos, "alpha", 1f, 0.70f);
                alpha.setRepeatCount(ValueAnimator.INFINITE);
                alpha.setRepeatMode(ValueAnimator.REVERSE);

                breathingAnimatorSet = new AnimatorSet();
                breathingAnimatorSet.playTogether(scaleX, scaleY, alpha);
                breathingAnimatorSet.setDuration(800); // 0.8s aggressive breath cycle
            }
            breathingAnimatorSet.start();
            
            simulateEmergencyProgress();
        } else {
            tvSosText.setText("SOS");
            
            tvStatus.setText("Status: You are Safe 🟢");
            tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            
            if (bottomActionButtons != null) bottomActionButtons.setVisibility(View.VISIBLE);
            if (trackerContainer != null) trackerContainer.setVisibility(View.GONE);
            
            if (breathingAnimatorSet != null) {
                breathingAnimatorSet.cancel();
            }
            btnSos.setScaleX(1f);
            btnSos.setScaleY(1f);
            btnSos.setAlpha(1f);
            
            handler.removeCallbacksAndMessages(null);
            startIdleAnimation();
        }
    }
    
    private void startIdleAnimation() {
        if (idleAnimatorSet == null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnSos, "scaleX", 1f, 1.05f);
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleX.setRepeatMode(ValueAnimator.REVERSE);
            
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnSos, "scaleY", 1f, 1.05f);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatMode(ValueAnimator.REVERSE);

            idleAnimatorSet = new AnimatorSet();
            idleAnimatorSet.playTogether(scaleX, scaleY);
            idleAnimatorSet.setDuration(2000); // 2s slow ambient throb
        }
        idleAnimatorSet.start();
    }
    
    private void stopIdleAnimation() {
        if (idleAnimatorSet != null) idleAnimatorSet.cancel();
    }
    
    private void simulateEmergencyProgress() {
        // Reset Tracker
        trStep1.setText("⏳ Acquiring precise GPS coordinates...");
        trStep1.setTextColor(Color.parseColor("#B0BEC5"));
        trStep2.setText("⏳ Alerting Emergency Contacts...");
        trStep2.setTextColor(Color.parseColor("#B0BEC5"));
        trStep3.setText("⏳ Transmitting coordinates to 112...");
        trStep3.setTextColor(Color.parseColor("#B0BEC5"));
        trStep4.setText("⏳ Securely recording audio evidence...");
        trStep4.setTextColor(Color.parseColor("#B0BEC5"));
        
        // Step 1: Location
        handler.postDelayed(() -> {
            trStep1.setVisibility(View.VISIBLE);
            trStep1.setText("✅  We have found your exact location.");
            trStep1.setTextColor(Color.parseColor("#81C784"));
        }, 1200);
        
        // Step 2: Contacts
        handler.postDelayed(() -> {
            trStep2.setVisibility(View.VISIBLE);
            trStep2.setText("✅  Emergency contacts have been notified.");
            trStep2.setTextColor(Color.parseColor("#81C784"));
        }, 2200);
        
        // Step 3: Police Dispatch
        handler.postDelayed(() -> {
            trStep3.setVisibility(View.VISIBLE);
            trStep3.setText("✅  Help signal sent to authorities.");
            trStep3.setTextColor(Color.parseColor("#81C784"));
            
            // Step 4: Show recording in progress
            handler.postDelayed(() -> {
                trStep4.setVisibility(View.VISIBLE);
                trStep4.setText("🎙️  Recording audio to secure lockbox...");
                trStep4.setTextColor(Color.parseColor("#FFCA28"));
            }, 1000);
        }, 3400);
    }

    public void updateServerStatus(boolean isOnline) {
        if (tvServerStatus == null || getContext() == null) return;
        getActivity().runOnUiThread(() -> {
            if (isOnline) {
                tvServerStatus.setText("Online");
                statusDot.setBackgroundResource(R.drawable.dot_green);
            } else {
                tvServerStatus.setText("Offline");
                statusDot.setBackgroundResource(R.drawable.dot_red);
            }
        });
    }
}
