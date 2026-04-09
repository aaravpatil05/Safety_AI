package com.safetyai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EmergencyContactsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        TextInputEditText etContact1 = findViewById(R.id.etContact1);
        TextInputEditText etContact2 = findViewById(R.id.etContact2);
        TextInputEditText etContact3 = findViewById(R.id.etContact3);

        SharedPreferences prefs = getSharedPreferences("SafetyPrefs", Context.MODE_PRIVATE);
        etContact1.setText(stripPrefix(prefs.getString("contact_1", "")));
        etContact2.setText(stripPrefix(prefs.getString("contact_2", "")));
        etContact3.setText(stripPrefix(prefs.getString("contact_3", "")));

        MaterialButton btnSave = findViewById(R.id.btnSaveContacts);
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            
            String c1 = etContact1.getText() != null ? formatToIndia(etContact1.getText().toString().trim()) : "";
            String c2 = etContact2.getText() != null ? formatToIndia(etContact2.getText().toString().trim()) : "";
            String c3 = etContact3.getText() != null ? formatToIndia(etContact3.getText().toString().trim()) : "";

            editor.putString("contact_1", c1);
            editor.putString("contact_2", c2);
            editor.putString("contact_3", c3);
            editor.apply();

            Toast.makeText(this, "Emergency Contacts Saved Successfully!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private String stripPrefix(String number) {
        if (number != null && number.startsWith("+91")) {
            return number.substring(3);
        }
        return number;
    }

    private String formatToIndia(String number) {
        if (number != null && number.length() == 10) {
            return "+91" + number;
        }
        return number;
    }
}
