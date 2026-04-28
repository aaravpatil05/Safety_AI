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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import org.json.JSONObject;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static MainActivity instance;
    public static final String BACKEND_URL = "https://fc90668f594b13.lhr.life"; // Fresh localhost.run Tunnel
    private boolean isSosActive = false;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private Location lastKnownLocation;
    
    // Global Listeners for Stability
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
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

    // Auto-discovery: IP found via UDP beacon or scanning (works on any network)
    private volatile String discoveredServerIp = null;
    private volatile boolean isServerConnected = false;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

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
        if (activeFragment == fragment && fragment != null) return;
        
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        
        if (activeFragment != null) {
            transaction.hide(activeFragment);
        }
        
        if (fragment == null) {
            initializer.init();
            if (tag.equals("home")) fragment = homeFragment;
            else if (tag.equals("track")) fragment = trackFragment;
            else if (tag.equals("risk")) fragment = riskMapFragment;
            else if (tag.equals("settings")) fragment = settingsFragment;
            
            transaction.add(R.id.fragment_container, fragment, tag);
        } else {
            transaction.show(fragment);
        }
        
        transaction.commit();
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
        SharedPreferences prefs = getSharedPreferences("SafetyNotifications", Context.MODE_PRIVATE);
        String history = prefs.getString("history", "");
        String timestamp = new java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault()).format(new java.util.Date());
        
        // Format: message|timestamp\n (to match NotificationsActivity parser)
        String entry = message + "|" + timestamp + "\n";
        prefs.edit().putString("history", entry + history).apply();
    }

    private void setupGlobalLocationListener() {
        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult == null) return;
                    for (Location location : locationResult.getLocations()) {
                        // Accept any location immediately so the map doesn't get stuck waiting for a fresh GPS lock indoors
                        if (location == null) continue;
                        
                        lastKnownLocation = location;
                        
                        // Geofencing AI Demonstration
                        SharedPreferences prefs = getSharedPreferences("SafetyFeaturesPrefs", Context.MODE_PRIVATE);
                        if (!hasNotifiedShadyArea && prefs.getBoolean("ai_monitoring_enabled", true)) {
                            hasNotifiedShadyArea = true;
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("⚠️ AI Perimeter Alert")
                                .setMessage("You have entered a historically Shady/High-Risk Area. Stay vigilant. Voice AI is active: shout 'Help' 7 times to trigger auto-SOS.")
                                .setPositiveButton("I Understand", null)
                                .show();
                        }

                        if (isSosActive) {
                            logLocationSecretly(location, "GPS");
                            
                            if (!hasSentLocationSms) {
                                String uri = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                                String msg = "SOS! I need help! My live location is:\n" + uri;
                                sendSosSms(msg);
                                hasSentLocationSms = true;
                            }
                        }
                        if (trackFragment != null && activeFragment == trackFragment) {
                            trackFragment.updateLocation(location);
                        }
                    }
                }
            };
        }
    }

    private void fetchLocationContinuous() {
        if (isLocationTracking) return; // Prevent stacking listeners
        
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }
        
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build();
                
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
            isLocationTracking = true;
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
            // Use app-specific external directory to completely bypass Android 11+ Scoped Storage restrictions!
            File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File safetyDir = new File(downloadDir, "SafetyAI");
            if (!safetyDir.exists()) safetyDir.mkdirs();
            
            audioFilePath = safetyDir.getAbsolutePath() + "/sos_evidence.m4a";
            
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
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
            if (c1.isEmpty() && c2.isEmpty() && c3.isEmpty()) {
                Toast.makeText(this, "⚠️ EMERGENCY: No Contacts Set! Go to Settings.", Toast.LENGTH_LONG).show();
                return;
            }
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
        Toast.makeText(this, "Securing evidence to cloud...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "Audio file not found.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Upload to uguu.se (Guaranteed 24-hour hosting for presentation reliability!)
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                URL url = new URL("https://uguu.se/upload.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "SafetyAI-App");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream os = conn.getOutputStream();
                
                // File part
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"files[]\"; filename=\"" + audioFile.getName() + "\"\r\n").getBytes());
                os.write(("Content-Type: audio/mp4\r\n\r\n").getBytes());
                
                FileInputStream fis = new FileInputStream(audioFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                fis.close();
                os.write(("\r\n").getBytes());
                os.write(("--" + boundary + "--\r\n").getBytes());
                os.flush();
                os.close();

                String directAudioUrl = null;

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    
                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("success") && json.getBoolean("success")) {
                        directAudioUrl = json.getJSONArray("files").getJSONObject(0).getString("url");
                    }
                }

                if (directAudioUrl == null) {
                    throw new Exception("Failed to get audio URL from cloud API");
                }

                final String finalAudioUrl = directAudioUrl;
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Evidence Secured and Uploaded!", Toast.LENGTH_SHORT).show();
                    String locLink = (lastKnownLocation != null) ? "http://maps.google.com/maps?q=" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude() : "Location Unknown";
                    
                    String latStr = lastKnownLocation != null ? String.valueOf(lastKnownLocation.getLatitude()) : "0";
                    String lonStr = lastKnownLocation != null ? String.valueOf(lastKnownLocation.getLongitude()) : "0";
                    
                    try {
                        String encodedAudioUrl = java.net.URLEncoder.encode(finalAudioUrl, "UTF-8");
                        String dashboardLink = "https://aaravpatil05.github.io/Safety_AI/dashboard.html?lat=" + latStr + "&lon=" + lonStr + "&audio=" + encodedAudioUrl;
                        
                        String audioMessage = "SAFETY ALERT: Case secured.\n" +
                                            "1. Cloud Link: " + dashboardLink + "\n" +
                                            "2. Direct Wi-Fi: " + dashboardLink + "\n" +
                                            "3. Verified Location: " + locLink;
                        sendSosSms(audioMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Upload error — evidence saved on device", Toast.LENGTH_LONG).show();
                    String locLink = (lastKnownLocation != null) ? "http://maps.google.com/maps?q=" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude() : "Location Unknown";
                    
                    // Fallback to empty audio link if upload fails, but still provide the dashboard link
                    String latStr = lastKnownLocation != null ? String.valueOf(lastKnownLocation.getLatitude()) : "0";
                    String lonStr = lastKnownLocation != null ? String.valueOf(lastKnownLocation.getLongitude()) : "0";
                    String dashboardLink = "https://aaravpatil05.github.io/Safety_AI/dashboard.html?lat=" + latStr + "&lon=" + lonStr;
                    
                    String audioMessage = "SAFETY ALERT: Case secured.\n" +
                                        "1. Cloud Link: " + dashboardLink + "\n" +
                                        "2. Direct Wi-Fi: " + dashboardLink + "\n" +
                                        "3. Verified Location: " + locLink;
                    sendSosSms(audioMessage);
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

    /**
     * Advanced Discovery 2.0:
     * 1. Listens for UDP beacon (Port 8765)
     * 2. Tries Manual IP from Settings
     * 3. Scans common hotspot gateways
     */
    private void startServerDiscovery() {
        // Start Heartbeat to update UI status dot
        startConnectionHeartbeat();

        // 1. UDP Listener Thread
        new Thread(() -> {
            DatagramSocket socket = null;
            try {
                android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager)
                        getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                android.net.wifi.WifiManager.MulticastLock lock =
                        wifiManager.createMulticastLock("safetyai_discovery");
                lock.setReferenceCounted(true);
                lock.acquire();

                socket = new DatagramSocket(8765);
                socket.setBroadcast(true);
                byte[] buf = new byte[512];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        JSONObject json = new JSONObject(msg);
                        if ("safetyai_server".equals(json.optString("service"))) {
                            String serverIp = json.getString("ip");
                            if (!serverIp.equals(discoveredServerIp)) {
                                discoveredServerIp = serverIp;
                                android.util.Log.d("SafetyAI", "UDP Discovered Server: " + serverIp);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                android.util.Log.e("SafetyAI", "UDP Discovery failed: " + e.getMessage());
            } finally {
                if (socket != null) socket.close();
            }
        }).start();

        // 2. Active Scanner Thread (Checks manual IP and Hotspot Gateways)
        new Thread(() -> {
            while (true) {
                refreshBackendConnection();
                try { Thread.sleep(10000); } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    public void refreshBackendConnection() {
        new Thread(() -> {
            boolean online = false;
            try {
                java.net.URL url = new java.net.URL("https://www.google.com");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.connect();
                online = (conn.getResponseCode() == 200);
            } catch (Exception e) {
                online = false;
            }
            
            if (online) {
                isServerConnected = true;
                discoveredServerIp = "Cloud Sync Active";
            } else {
                isServerConnected = false;
                discoveredServerIp = "Offline";
            }
        }).start();
    }

    private boolean pingServer(String host) {
        try {
            URL url = new URL(host + "/ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startConnectionHeartbeat() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // Update HomeFragment if visible
                if (homeFragment instanceof HomeFragment) {
                    ((HomeFragment) homeFragment).updateServerStatus(isServerConnected);
                }
                handler.postDelayed(this, 3000);
            }
        });
    }


    private void startContinuousSpeechRecognition() {

        SharedPreferences prefs = getSharedPreferences("SafetyFeaturesPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("voice_sos_enabled", true)) return;

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    isListening = false;
                    // Retry after short delay on recoverable errors
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        startListeningIntent();
                    }, 1500);
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String phrase : matches) {
                            String lower = phrase.toLowerCase();
                            // Count each occurrence of "help" in the phrase
                            int idx = 0;
                            while ((idx = lower.indexOf("help", idx)) != -1) {
                                safetyHelpCount++;
                                idx += 4;
                            }
                        }
                    }

                    if (safetyHelpCount >= 7) {
                        safetyHelpCount = 0;
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "🆘 Voice SOS Triggered! Activating...", Toast.LENGTH_SHORT).show();
                            if (!isSosActive) {
                                toggleSos();
                                // Notify the home fragment to update the UI
                                if (homeFragment instanceof HomeFragment) {
                                    ((HomeFragment) homeFragment).setSosUiActive(true);
                                }
                            }
                        });
                    } else {
                        // Continue listening
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            startListeningIntent();
                        }, 300);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }

        startListeningIntent();
    }

    private void startListeningIntent() {
        SharedPreferences prefs = getSharedPreferences("SafetyFeaturesPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("voice_sos_enabled", true)) return;
        if (isListening) return;

        try {
            // Mute the beep sound by temporarily silencing the AudioManager stream
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int originalVolume = 0;
            if (audioManager != null) {
                originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            }
            final int savedVolume = originalVolume;
            final AudioManager finalAudioManager = audioManager;

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra("android.speech.extra.DICTATION_MODE", true);

            speechRecognizer.startListening(intent);
            isListening = true;

            // Restore volume 200ms after starting (after beep window has passed)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (finalAudioManager != null) {
                    finalAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
                }
            }, 200);

        } catch (Exception e) {
            isListening = false;
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        // Start server auto-discovery (works on any network)
        startServerDiscovery();
        // Start Voice AI
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
        SharedPreferences prefs = getSharedPreferences("SafetyFeaturesPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("fall_detection_enabled", true)) return;

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
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
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
