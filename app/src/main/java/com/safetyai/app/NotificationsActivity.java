 package com.safetyai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmptyText;
    private NotificationAdapter adapter;
    private List<NotificationAdapter.NotificationModel> notificationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmptyText = findViewById(R.id.tvEmptyText);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnClear = findViewById(R.id.btnClear);

        btnBack.setOnClickListener(v -> finish());
        
        btnClear.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("SafetyNotifications", Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            loadNotifications();
        });

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        loadNotifications();
    }

    private void loadNotifications() {
        notificationList.clear();
        SharedPreferences prefs = getSharedPreferences("SafetyNotifications", Context.MODE_PRIVATE);
        String history = prefs.getString("history", "");

        if (history.isEmpty()) {
            tvEmptyText.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmptyText.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            
            String[] lines = history.split("\n");
            for (String line : lines) {
                if (line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        notificationList.add(new NotificationAdapter.NotificationModel(parts[0], parts[1]));
                    }
                }
            }
            
            adapter = new NotificationAdapter(notificationList);
            rvNotifications.setAdapter(adapter);
        }
    }
}
