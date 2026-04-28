package com.safetyai.app;

import android.content.Context;
import android.location.Location;
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class TrackFragment extends Fragment {
    private MapView map;
    private MyLocationNewOverlay mLocationOverlay;
    private Polygon pulsePolygon;
    private android.os.Handler pulseHandler = new android.os.Handler();
    private double currentRadius = 10.0;

    private GeoPoint lastKnownPoint = null;

    private Runnable pulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (map != null) {
                GeoPoint loc = lastKnownPoint;
                if (loc == null && mLocationOverlay != null) {
                    loc = mLocationOverlay.getMyLocation();
                }
                
                if (loc != null) {
                    if (pulsePolygon == null) {
                        pulsePolygon = new Polygon(map);
                        pulsePolygon.setStrokeWidth(0);
                        map.getOverlays().add(0, pulsePolygon);
                    }
                    
                    currentRadius += 15.0; // Expand pulse
                    if (currentRadius > 1000.0) currentRadius = 10.0; // Reset
                    
                    pulsePolygon.setPoints(Polygon.pointsAsCircle(loc, currentRadius));
                    
                    // Fade out as it expands
                    int alpha = (int) (120 * (1 - (currentRadius / 1000.0)));
                    pulsePolygon.setFillColor(android.graphics.Color.argb(Math.max(0, alpha), 33, 150, 243)); // Glowing Blue
                    
                    map.invalidate();
                }
            }
            pulseHandler.postDelayed(this, 30); // ~30fps smooth pulse
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());

        View view = inflater.inflate(R.layout.fragment_track, container, false);

        map = view.findViewById(R.id.mapview);
        map.setMultiTouchControls(true); 

        // Default look before GPS lock
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(22.0, 78.0));

        // Use Native OSMDroid Location Overlay for authentic pulsing tracking dot
        mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation(); // Automatically center map
        
        map.getOverlays().add(mLocationOverlay);

        return view;
    }

    public void updateLocation(Location location) {
        if (map != null && location != null) {
            lastKnownPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
            // Force the map to center on the provided location immediately
            map.getController().animateTo(lastKnownPoint);
            
            // Also update the pulse polygon location artificially in case the native overlay hasn't synced
            if (pulsePolygon == null) {
                pulsePolygon = new Polygon(map);
                pulsePolygon.setStrokeWidth(0);
                map.getOverlays().add(0, pulsePolygon);
            }
            pulsePolygon.setPoints(Polygon.pointsAsCircle(lastKnownPoint, currentRadius));
            map.invalidate();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (mLocationOverlay != null) mLocationOverlay.enableMyLocation();
        
        if (MainActivity.instance != null && MainActivity.instance.getLastKnownLocation() != null) {
            Location loc = MainActivity.instance.getLastKnownLocation();
            lastKnownPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            if (map != null && map.getController() != null) {
                map.getController().setCenter(lastKnownPoint);
            }
        }
        
        pulseHandler.post(pulseRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (mLocationOverlay != null) mLocationOverlay.disableMyLocation();
        pulseHandler.removeCallbacks(pulseRunnable);
    }
}
