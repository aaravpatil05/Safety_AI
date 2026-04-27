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

        // High Risk / Critical Zones (Specific Areas)
        addPreciseMarker(19.0356, 72.8400, "CRITICAL", "Mahim, Mumbai");
        addPreciseMarker(18.9400, 72.8355, "CRITICAL", "CSMT, Mumbai");
        addPreciseMarker(18.5160, 73.8400, "CRITICAL", "Deccan Gymkhana, Pune");
        addPreciseMarker(21.1420, 79.0980, "CRITICAL", "Mahal Area, Nagpur");
        addPreciseMarker(20.0050, 73.7910, "CRITICAL", "Panchavati, Nashik");
        addPreciseMarker(19.8780, 75.3280, "CRITICAL", "Shahgunj, Sambhajinagar");
        addPreciseMarker(19.1860, 72.9759, "CRITICAL", "Thane Station Area");
        addPreciseMarker(28.6640, 77.2684, "CRITICAL", "Seelampur, Delhi");
        addPreciseMarker(12.9645, 77.5750, "CRITICAL", "KR Market, Bengaluru");
        addPreciseMarker(22.5835, 88.3435, "CRITICAL", "Howrah Station, Kolkata");
        addPreciseMarker(13.0827, 80.2707, "CRITICAL", "Chennai Central Area");
        addPreciseMarker(26.8329, 80.9197, "CRITICAL", "Charbagh, Lucknow");
        addPreciseMarker(25.6110, 85.1440, "CRITICAL", "Patna Junction Area");

        // Moderate Risk Zones (Specific Areas)
        addPreciseMarker(19.1136, 72.8697, "MODERATE", "Andheri East, Mumbai");
        addPreciseMarker(18.5284, 73.8739, "MODERATE", "Pune Railway Station");
        addPreciseMarker(21.1458, 79.0882, "MODERATE", "Sitabuldi, Nagpur");
        addPreciseMarker(28.6304, 77.2177, "MODERATE", "Connaught Place, Delhi");
        addPreciseMarker(17.3616, 78.4747, "MODERATE", "Charminar Area, Hyderabad");
        addPreciseMarker(23.0225, 72.5714, "MODERATE", "Lal Darwaza, Ahmedabad");
        addPreciseMarker(26.9124, 75.7873, "MODERATE", "Sindhi Camp, Jaipur");

        // Safe Risk Zones (Specific Areas)
        addPreciseMarker(19.0596, 72.8295, "SAFE", "Bandra West, Mumbai");
        addPreciseMarker(18.5913, 73.7389, "SAFE", "Hinjewadi IT Park, Pune");
        addPreciseMarker(18.5362, 73.8967, "SAFE", "Koregaon Park, Pune");
        addPreciseMarker(21.1540, 79.0710, "SAFE", "Civil Lines, Nagpur");
        addPreciseMarker(20.0110, 73.7550, "SAFE", "College Road, Nashik");
        addPreciseMarker(19.8650, 75.3620, "SAFE", "CIDCO, Sambhajinagar");
        addPreciseMarker(19.2220, 72.9550, "SAFE", "Upvan Lake, Thane");
        addPreciseMarker(28.6129, 77.2295, "SAFE", "India Gate, Delhi");
        addPreciseMarker(12.9719, 77.6412, "SAFE", "Indiranagar, Bengaluru");
        addPreciseMarker(22.5535, 88.3510, "SAFE", "Park Street, Kolkata");
        addPreciseMarker(17.4140, 78.4350, "SAFE", "Banjara Hills, Hyderabad");
        addPreciseMarker(13.0002, 80.2668, "SAFE", "Besant Nagar, Chennai");

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
        
        // True Soft Heat-Map Blur Layers (City blocks)
        Polygon heatBlur = new Polygon(map);
        heatBlur.setPoints(Polygon.pointsAsCircle(new GeoPoint(lat, lon), 15000.0)); // 15km spread for visibility at country level
        
        int color;
        if (level.equals("CRITICAL")) color = Color.RED;
        else if (level.equals("MODERATE")) color = Color.YELLOW;
        else color = Color.GREEN;
        
        heatBlur.setFillColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
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
