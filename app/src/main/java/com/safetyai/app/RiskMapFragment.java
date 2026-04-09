package com.safetyai.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Marker;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

public class RiskMapFragment extends Fragment {

    private MapView map;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());

        View view = inflater.inflate(R.layout.fragment_risk_map, container, false);

        map = view.findViewById(R.id.mapview);
        map.setMultiTouchControls(true);

        // Apply a futuristic Dark Mode filter to the native map tiles by inverting colors
        ColorMatrix inverseMatrix = new ColorMatrix(new float[] {
                -1.0f,  0.0f,  0.0f, 0.0f, 255f, // red
                 0.0f, -1.0f,  0.0f, 0.0f, 255f, // green
                 0.0f,  0.0f, -1.0f, 0.0f, 255f, // blue
                 0.0f,  0.0f,  0.0f, 1.0f,   0.0f  // alpha
        });
        map.getOverlayManager().getTilesOverlay().setColorFilter(new ColorMatrixColorFilter(inverseMatrix));

        map.getController().setZoom(5.2);
        map.getController().setCenter(new GeoPoint(22.0, 78.0));

        // High Risk (Red)
        addPreciseMarker(28.6, 77.2, "CRITICAL", "Delhi NCR (High Alert)");
        addPreciseMarker(26.8, 80.9, "CRITICAL", "Uttar Pradesh");
        addPreciseMarker(25.0, 85.3, "CRITICAL", "Bihar");
        addPreciseMarker(23.6, 87.8, "CRITICAL", "West Bengal");
        addPreciseMarker(29.0, 76.0, "CRITICAL", "Haryana");
        addPreciseMarker(23.6, 85.3, "CRITICAL", "Jharkhand");
        addPreciseMarker(28.98, 77.7, "CRITICAL", "Meerut Zone");
        addPreciseMarker(25.59, 85.13, "CRITICAL", "Patna Sector");
        addPreciseMarker(26.21, 78.17, "CRITICAL", "Gwalior Zone");
        addPreciseMarker(28.40, 77.31, "CRITICAL", "Faridabad Sector");

        // Moderate Risk (Yellow)
        addPreciseMarker(19.7, 75.7, "MODERATE", "Maharashtra");
        addPreciseMarker(22.2, 71.1, "MODERATE", "Gujarat");
        addPreciseMarker(22.9, 78.6, "MODERATE", "Madhya Pradesh");
        addPreciseMarker(20.9, 85.0, "MODERATE", "Odisha");
        addPreciseMarker(31.1, 75.3, "MODERATE", "Punjab");
        addPreciseMarker(21.2, 81.6, "MODERATE", "Chhattisgarh");
        addPreciseMarker(21.17, 72.83, "MODERATE", "Surat Hub");
        addPreciseMarker(33.7, 76.5, "MODERATE", "J&K Territory");
        addPreciseMarker(23.25, 77.41, "MODERATE", "Bhopal Sector");
        addPreciseMarker(21.25, 81.62, "MODERATE", "Raipur Zone");
        addPreciseMarker(22.71, 75.85, "MODERATE", "Indore Core");

        // Safe Risk (Green)
        addPreciseMarker(15.3, 76.8, "SAFE", "Karnataka");
        addPreciseMarker(11.1, 78.6, "SAFE", "Tamil Nadu");
        addPreciseMarker(10.8, 76.2, "SAFE", "Kerala");
        addPreciseMarker(15.9, 79.7, "SAFE", "Andhra Pradesh");
        addPreciseMarker(18.1, 79.0, "SAFE", "Telangana");
        addPreciseMarker(27.0, 74.2, "SAFE", "Rajasthan");
        addPreciseMarker(30.0, 79.0, "SAFE", "Uttarakhand");
        addPreciseMarker(31.1, 77.1, "SAFE", "Himachal Pradesh");
        addPreciseMarker(26.2, 92.9, "SAFE", "Assam Sector");
        addPreciseMarker(15.2, 74.1, "SAFE", "Goa Hub");
        addPreciseMarker(9.93, 76.26, "SAFE", "Kochi Coast");
        addPreciseMarker(18.52, 73.85, "SAFE", "Pune Metro");
        addPreciseMarker(8.52, 76.93, "SAFE", "Trivandrum Zone");
        addPreciseMarker(31.10, 77.17, "SAFE", "Shimla Hub");

        map.invalidate(); 
        return view;
    }

    private void addPreciseMarker(double lat, double lon, String level, String title) {
        Marker marker = new Marker(map);
        marker.setPosition(new GeoPoint(lat, lon));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        marker.setSubDescription("AI Threat Level: " + level);
        
        map.getOverlays().add(marker);
        
        // True Soft Heat-Map Blur Layers (Massive size, zero borders, highly transparent)
        Polygon heatBlur = new Polygon(map);
        heatBlur.setPoints(Polygon.pointsAsCircle(new GeoPoint(lat, lon), 150000.0)); // 150km soft spread
        
        int color;
        if (level.equals("CRITICAL")) color = Color.RED;
        else if (level.equals("MODERATE")) color = Color.YELLOW;
        else color = Color.GREEN;
        
        heatBlur.setFillColor(Color.argb(35, Color.red(color), Color.green(color), Color.blue(color)));
        heatBlur.setStrokeWidth(0.0f); // NO border, allows seamless visual merging!
        map.getOverlays().add(0, heatBlur); // Insert beneath markers
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
