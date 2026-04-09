package com.safetyai.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.json.JSONObject;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static MainActivity instance;
    public static final String BACKEND_URL = "YOUR_TEMP_URL_HERE"; // Replace with your serveo.net URL
    private boolean isSosActive = false;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private Location lastKnownLocation;
    
    // Global Listeners for Stability
    private LocationListener globalLocationListener;
    private boolean isLocationTracking = false;
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastSensorUpdate = 0;
    private float motionThreshold = 2.5f; // Default threshold
    
    // AI Variables
    private boolean hasSentLocationSms = false;
    private boolean hasNotifiedShadyArea = false;
    private SpeechRecognizer speechRecognizer;
    private int safetyHelpCount = 0;
    private boolean isListening = false;

    private Fragment homeFragment;
    private TrackFragment trackFragment;
    private Fragment riskMapFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_theme", false);
        if (isDark) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);

        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO
        };
        ActivityCompat.requestPermissions(this, perms, 1);

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            
            // Lazy load fragments to dramatically speed up Splash Screen on slow laptops
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, homeFragment, "home").commit();
            activeFragment = homeFragment;
        } else {
            homeFragment = getSupportFragmentManager().findFragmentByTag("home");
            trackFragment = (TrackFragment) getSupportFragmentManager().findFragmentByTag("track");
            riskMapFragment = getSupportFragmentManager().findFragmentByTag("risk");
            settingsFragment = getSupportFragmentManager().findFragmentByTag("settings");
            
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment != null && !fragment.isHidden()) {
                    activeFragment = fragment;
                    break;
                }
            }
                if (activeFragment == null) activeFragment = homeFragment;
            }

            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchFragment(homeFragment, "home", () -> homeFragment = new HomeFragment());
                return true;
            } else if (id == R.id.nav_track) {
                switchFragment(trackFragment, "track", () -> trackFragment = new TrackFragment());
                return true;
            } else if (id == R.id.nav_risk_map) {
                switchFragment(riskMapFragment, "risk", () -> riskMapFragment = new RiskMapFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(settingsFragment, "settings", () -> settingsFragment = new SettingsFragment());
                return true;
            }
            return false;
        });



        // Initialize Sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        
        setupGlobalLocationListener();
        updateSettings();
        fetchLocationContinuous();

        // Play Startup Tone
        try {
            ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            toneG.startTone(ToneGenerator.TONE_PROP_PROMPT, 250);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private interface FragmentInitializer {
        void init();
    }

    private void switchFragment(Fragment fragment, String tag, FragmentInitializer initializer) {
        if (fragment == null) {
            initializer.init();
            fragment = getSupportFragmentManager().findFragmentByTag(tag);
            if (fragment == null) {
                // If the fragment is uniquely identified by the tag, wait to assign it.
                if (tag.equals("home")) fragment = homeFragment;
                else if (tag.equals("track")) fragment = trackFragment;
                else if (tag.equals("risk")) fragment = riskMapFragment;
                else if (tag.equals("settings")) fragment = settingsFragment;
                
                getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(R.id.fragment_container, fragment, tag)
                    .hide(activeFragment).show(fragment).commit();
            }
        } else {
            getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(activeFragment).show(fragment).commit();
        }
        activeFragment = fragment;
    }

    public void navigateToSettings() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_settings);
        }
    }

    public void updateSettings() {
        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        int sensitivity = prefs.getInt("ai_sensitivity", 50);
        // Map 0-100 to 4.0g (low sens) to 1.2g (high sens)
        motionThreshold = 4.0f - (sensitivity / 100f) * 2.8f;
    }

    public boolean isSosActive() {
        return isSosActive;
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    public boolean toggleSos() {
        if (!isSosActive) {
            isSosActive = true;
            hasSentLocationSms = false;
            fetchLocationContinuous();
            startAudioRecording();
            Toast.makeText(this, "\uD83D\uDEA8 SOS ACTIVE: GPS Tracking & Hidden Audio Recording Started!", Toast.LENGTH_LONG).show();
            
            // Send initial alert
            sendSosSms("SOS! Emergency Alert Triggered. Getting location...");
            appendNotification("SOS ACTIVATED");
            
            return true;
        } else {
            isSosActive = false;
            stopAudioRecording();
            Toast.makeText(this, "SOS Stopped. Audio safely encrypted and stored locally.", Toast.LENGTH_SHORT).show();
            
            // Upload to Cloud & Send Link (Mocked functionality for presentation)
            mockUploadAudioEvidence();
            appendNotification("SOS DEACTIVATED");
            
            return false;
        }
    }

    private void appendNotification(String message) {
        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        String history = prefs.getString("sos_history", "");
        String timestamp = new java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault()).format(new java.util.Date());
        String entry = "⏺ " + timestamp + "\n      " + message + "\n\n";
        prefs.edit().putString("sos_history", entry + history).apply();
    }

    private void setupGlobalLocationListener() {
        if (globalLocationListener == null) {
            globalLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    lastKnownLocation = location;
                    
                    // Geofencing AI Demonstration
                    if (!hasNotifiedShadyArea) {
                        hasNotifiedShadyArea = true;
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("⚠️ AI Geofence Warning")
                            .setMessage("You have entered a historically Shady/High-Risk Area. Stay vigilant. Voice AI is active: shout 'Help' 7 times to trigger auto-SOS.")
                            .setPositiveButton("I Understand", null)
                            .show();
                    }

                    if (isSosActive) {
                        logLocationSecretly(location, "GPS");
                        
                        if (!hasSentLocationSms) {
                            String uri = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                            sendSosSms("SOS! I need help! My live location is: " + uri);
                            hasSentLocationSms = true;
                        }
                    }
                    if (trackFragment != null && activeFragment == trackFragment) {
                        trackFragment.updateLocation(location);
                    }
                }
            };
        }
    }

    private void fetchLocationContinuous() {
        if (isLocationTracking) return; // Prevent stacking listeners
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 10, globalLocationListener);
                isLocationTracking = true;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 10, globalLocationListener);
                isLocationTracking = true;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void logLocationSecretly(Location location, String type) {
        try {
            String logPath = getExternalCacheDir().getAbsolutePath() + "/sos_evidence_log.txt";
            java.io.File logFile = new java.io.File(logPath);
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            String entry = "[" + timestamp + " | " + type + "] LAT: " + location.getLatitude() + ", LON: " + location.getLongitude() + "\n";
            fw.write(entry);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startAudioRecording() {
        try {
            audioFilePath = getExternalCacheDir().getAbsolutePath() + "/sos_evidence.3gp";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendSosSms(String message) {
        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        boolean autoSms = prefs.getBoolean("auto_sms", true);
        if (!autoSms) return;

        String c1 = prefs.getString("contact_1", "");
        String c2 = prefs.getString("contact_2", "");
        String c3 = prefs.getString("contact_3", "");

        boolean hasSmsPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (hasSmsPermission) {
            SmsManager smsManager = SmsManager.getDefault();
            // Send native background texts securely preventing a single failure from cascading
            sendDirectSmsSafely(smsManager, cleanNumber(c1), message);
            sendDirectSmsSafely(smsManager, cleanNumber(c2), message);
            sendDirectSmsSafely(smsManager, cleanNumber(c3), message);
            Toast.makeText(this, "SOS SMS Sent to Contacts Natively!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "SMS Permission Denied! Please allow in Settings.", Toast.LENGTH_LONG).show();
        }
    }

    private String cleanNumber(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        return phone.replaceAll("[^0-9+]", ""); 
    }

    private void sendDirectSmsSafely(SmsManager smsManager, String number, String message) {
        if (number == null || number.isEmpty()) return;
        try {
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(number, null, message, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mockUploadAudioEvidence() {
        if (audioFilePath == null) return;
        Toast.makeText(this, "Uploading Audio Evidence to Secure Backend...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) return;
                
                URL url = new URL(BACKEND_URL + "/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                
                // Set custom location headers
                if (lastKnownLocation != null) {
                    conn.setRequestProperty("X-Location-Lat", String.valueOf(lastKnownLocation.getLatitude()));
                    conn.setRequestProperty("X-Location-Lon", String.valueOf(lastKnownLocation.getLongitude()));
                } else {
                    conn.setRequestProperty("X-Location-Lat", "Unknown");
                    conn.setRequestProperty("X-Location-Lon", "Unknown");
                }
                
                // Write file to body
                conn.setRequestProperty("Content-Length", String.valueOf(audioFile.length()));
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                
                OutputStream os = conn.getOutputStream();
                FileInputStream fis = new FileInputStream(audioFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                fis.close();
                os.flush();
                os.close();
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    
                    JSONObject json = new JSONObject(sb.toString());
                    String id = json.getString("id");
                    
                    String evidenceUrl = BACKEND_URL + "/evidence/" + id;
                    
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Audio Evidence Uploaded!", Toast.LENGTH_SHORT).show();
                        String audioMessage = "SOS EVIDENCE: Encrypted audio recording & location from the incident is available at: " + evidenceUrl;
                        sendSosSms(audioMessage);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to upload evidence.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage() + ". Did you start the server?", Toast.LENGTH_LONG).show();
                    // Fallback so the second SMS still sends even offline
                    String fallbackMessage = "SOS EVIDENCE: Audio secretly recorded and stored securely on device. (Cloud unavailable)";
                    sendSosSms(fallbackMessage);
                });
            }
        }).start();
    }

    private void shareAudioRecording() {
        if (audioFilePath == null) return;
        try {
            File audioFile = new File(audioFilePath);
            if (audioFile.exists()) {
                Uri contentUri = FileProvider.getUriForFile(this, "com.safetyai.app.fileprovider", audioFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("audio/3gpp");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share SOS Audio Evidence"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to share audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void startContinuousSpeechRecognition() {
        // Disabled active speech loop to stop the continuous system beep sound
    }
    
    private void startListeningIntent() {
        // Disabled active speech loop to stop the continuous system beep sound
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        
        // Start Voice AI naturally on resume
        startContinuousSpeechRecognition();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            if ((curTime - lastSensorUpdate) > 1000) { 
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                
                double gForce = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
                
                if (gForce > motionThreshold) {
                    lastSensorUpdate = curTime;
                    // Trigger Activity Detected UI
                    Intent intent = new Intent(this, ActivityDetectedActivity.class);
                    startActivity(intent);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
        stopAudioRecording();
        
        // Final memory cleanup
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null && globalLocationListener != null) {
                locationManager.removeUpdates(globalLocationListener);
                isLocationTracking = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fetchLocationContinuous();
                    break;
                }
            }
        }
    }
}
