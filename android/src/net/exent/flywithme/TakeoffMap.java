package net.exent.flywithme;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Flightlog;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener, OnCameraChangeListener {
    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();
    }

    private static final int DEFAULT_MAX_AIRSPACE_DISTANCE = 100;
    private static View view;
    private static Map<Marker, Takeoff> markers = new HashMap<Marker, Takeoff>();
    private static Map<Polygon, PolygonOptions> polygons = new HashMap<Polygon, PolygonOptions>();
    private static Bitmap markerBitmap;
    private static Bitmap markerNorthBitmap;
    private static Bitmap markerNortheastBitmap;
    private static Bitmap markerEastBitmap;
    private static Bitmap markerSoutheastBitmap;
    private static Bitmap markerSouthBitmap;
    private static Bitmap markerSouthwestBitmap;
    private static Bitmap markerWestBitmap;
    private static Bitmap markerNorthwestBitmap;
    private TakeoffMapListener callback;

    public void drawMap() {
        Log.d(getClass().getSimpleName(), "drawMap()");
        if (callback == null) {
            Log.w(getClass().getSimpleName(), "callback is null, returning");
            return;
        }
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        final GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        /* need to do this here or it'll end up with a reference to an old instance of "this", somehow */
        map.setOnInfoWindowClickListener(this);
        map.setOnCameraChangeListener(this);
        /* add icons */
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showTakeoffs = prefs.getBoolean("pref_map_show_takeoffs", true);
        final ImageButton markerButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        markerButton.setImageResource(showTakeoffs ? R.drawable.takeoffs_enabled : R.drawable.takeoffs_disabled);
        markerButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                boolean markersEnabled = !prefs.getBoolean("pref_map_show_takeoffs", true);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pref_map_show_takeoffs", markersEnabled);
                editor.commit();
                markerButton.setImageResource(markersEnabled ? R.drawable.takeoffs_enabled : R.drawable.takeoffs_disabled);
                map.clear();
                redrawMap(map);
            }
        });
        boolean showAirspace = prefs.getBoolean("pref_map_show_airspaces", true);
        final ImageButton polygonButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        polygonButton.setImageResource(showAirspace ? R.drawable.airspace_enabled : R.drawable.airspace_disabled);
        polygonButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                boolean polygonsEnabled = !prefs.getBoolean("pref_map_show_airspace", true);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pref_map_show_airspace", polygonsEnabled);
                editor.commit();
                polygonButton.setImageResource(polygonsEnabled ? R.drawable.airspace_enabled : R.drawable.airspace_disabled);
                map.clear();
                redrawMap(map);
            }
        });
        /* draw map */
        redrawMap(map);
    }

    public void onInfoWindowClick(Marker marker) {
        Log.d(getClass().getSimpleName(), "onInfoWindowClick(" + marker + ")");
        if (callback == null) {
            Log.w(getClass().getSimpleName(), "callback is null, returning");
            return;
        }
        Takeoff takeoff = markers.get(marker.getId());

        /* tell main activity to show takeoff details */
        callback.showTakeoffDetails(takeoff);
    }

    public void onCameraChange(CameraPosition cameraPosition) {
        Log.d(getClass().getName(), "Camera target: " + cameraPosition.target);
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        final GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        redrawMap(map);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d(getClass().getSimpleName(), "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffMapListener) activity;
        if (markerBitmap == null) {
            markerBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker);
            markerNorthBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_n);
            markerNortheastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_ne);
            markerEastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_e);
            markerSoutheastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_se);
            markerSouthBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_s);
            markerSouthwestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_sw);
            markerWestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_w);
            markerNorthwestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_nw);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null)
                parent.removeView(view);
        }
        try {
            boolean zoom = getFragmentManager().findFragmentById(R.id.takeoffMapFragment) == null;
            view = inflater.inflate(R.layout.takeoff_map, container, false);
            GoogleMap map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment)).getMap();
            map.setMyLocationEnabled(true);
            map.getUiSettings().setZoomControlsEnabled(false);
            if (zoom) {
                Location loc = callback.getLocation();
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), (float) 10.0));
            }
        } catch (InflateException e) {
            /* map is already there, just return view as it is */
        }
        return view;
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        drawMap();
    }

    private void redrawMap(GoogleMap map) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Location location = callback.getLocation();
        LatLng latLng = map.getCameraPosition().target;
        if (latLng.latitude != 0.0 && latLng.longitude != 0.0) {
            location.setLatitude(latLng.latitude);
            location.setLongitude(latLng.longitude);
        }
        if (prefs.getBoolean("pref_map_show_takeoffs", true))
            new DrawMarkersTask().execute(location);
        if (prefs.getBoolean("pref_map_show_airspace", true))
            new DrawPolygonsTask().execute(location);
    }

    private class DrawMarkersTask extends AsyncTask<Location, Void, Runnable> {
        @Override
        protected Runnable doInBackground(Location... locations) {
            Log.d(getClass().getSimpleName(), "doInBackground(" + locations + ")");
            final Map<Takeoff, Bitmap> newMarkers = new HashMap<Takeoff, Bitmap>();

            List<Takeoff> takeoffs;
            takeoffs = Flightlog.getTakeoffs(locations[0]);
            for (Takeoff takeoff : takeoffs) {
                Bitmap bitmap = Bitmap.createBitmap(markerBitmap.getWidth(), markerBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawBitmap(markerBitmap, 0, 0, null);
                if (takeoff.hasNorthExit())
                    canvas.drawBitmap(markerNorthBitmap, 0, 0, null);
                if (takeoff.hasNortheastExit())
                    canvas.drawBitmap(markerNortheastBitmap, 0, 0, null);
                if (takeoff.hasEastExit())
                    canvas.drawBitmap(markerEastBitmap, 0, 0, null);
                if (takeoff.hasSoutheastExit())
                    canvas.drawBitmap(markerSoutheastBitmap, 0, 0, null);
                if (takeoff.hasSouthExit())
                    canvas.drawBitmap(markerSouthBitmap, 0, 0, null);
                if (takeoff.hasSouthwestExit())
                    canvas.drawBitmap(markerSouthwestBitmap, 0, 0, null);
                if (takeoff.hasWestExit())
                    canvas.drawBitmap(markerWestBitmap, 0, 0, null);
                if (takeoff.hasNorthwestExit())
                    canvas.drawBitmap(markerNorthwestBitmap, 0, 0, null);
                newMarkers.put(takeoff, bitmap);
            }

            return new Runnable() {
                public void run() {
                    drawTakeoffMarkers(newMarkers);
                }
            };
        }

        @Override
        protected void onPostExecute(Runnable runnable) {
            Log.d(getClass().getSimpleName(), "onPostExecute(" + runnable + ")");
            runnable.run();
        }

        private void drawTakeoffMarkers(Map<Takeoff, Bitmap> newMarkers) {
            SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
            if (fragment == null)
                return;
            final GoogleMap map = fragment.getMap();
            if (map == null)
                return;
            /* remove markers that should not be visible */
            for (Iterator<Map.Entry<Marker, Takeoff>> it = markers.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Marker, Takeoff> entry = it.next();
                if (newMarkers.containsKey(entry.getValue()))
                    continue;
                entry.getKey().remove();
                it.remove();
            }
            /* draw markers that should be visible */
            for (Map.Entry<Takeoff, Bitmap> entry : newMarkers.entrySet()) {
                if (markers.containsValue(entry.getKey()))
                    continue;
                Takeoff takeoff = entry.getKey();
                Bitmap bitmap = entry.getValue();
                Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet("Height: " + takeoff.getHeight()).icon(BitmapDescriptorFactory.fromBitmap(bitmap)).anchor(0.5f, 0.875f));
                markers.put(marker, takeoff);
            }
        }
    }

    private class DrawPolygonsTask extends AsyncTask<Location, Void, Runnable> {
        @Override
        protected Runnable doInBackground(Location... locations) {
            Log.d(getClass().getSimpleName(), "doInBackground(" + locations + ")");
            Location location = locations[0];
            Location tmpLocation = new Location(location);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            int maxAirspaceDistance = DEFAULT_MAX_AIRSPACE_DISTANCE; // TODO: just set to fixed 100km?
            try {
                maxAirspaceDistance = Integer.parseInt(prefs.getString("pref_max_airspace_distance", "" + DEFAULT_MAX_AIRSPACE_DISTANCE));
            } catch (NumberFormatException e) {
                Log.w(getClass().getSimpleName(), "Unable to parse max airspace distance setting as integer", e);
            }
            maxAirspaceDistance *= 1000;

            final Map<PolygonOptions, Polygon> removePolygons = new HashMap<PolygonOptions, Polygon>();
            for (Map.Entry<Polygon, PolygonOptions> entry : polygons.entrySet())
                removePolygons.put(entry.getValue(), entry.getKey());
            polygons.clear();
            final Set<PolygonOptions> newPolygons = new HashSet<PolygonOptions>();

            for (Map.Entry<String, List<PolygonOptions>> entry : Airspace.getAirspaceMap().entrySet()) {
                if (entry.getKey() == null || prefs.getBoolean("pref_airspace_enabled_" + entry.getKey().trim(), true) == false)
                    continue;
                for (PolygonOptions polygon : entry.getValue()) {
                    if (showPolygon(polygon, location, tmpLocation, maxAirspaceDistance)) {
                        if (removePolygons.containsKey(polygon)) {
                            polygons.put(removePolygons.get(polygon), polygon);
                            removePolygons.remove(polygon);
                            continue;
                        }
                        newPolygons.add(polygon);
                    }
                }
            }

            return new Runnable() {
                public void run() {
                    drawAirspaceMap(newPolygons);
                }
            };
        }

        @Override
        protected void onPostExecute(Runnable runnable) {
            Log.d(getClass().getSimpleName(), "onPostExecute()");
            runnable.run();
        }

        /**
         * Figure out whether to draw polygon or not. The parameters "myLocation" and "tmpLocation" are only used to prevent excessive allocations.
         * 
         * @param polygon
         *            The polygon we want to figure out whether to draw or not.
         * @param myLocation
         *            Users current location.
         * @param tmpLocation
         *            Location object only used for determining distance from polygon points to user location.
         * @param maxAirspaceDistance
         *            User must be within a polygon or within this distance to one of the polygon points in order to be drawn.
         * @return Whether polygon should be drawn.
         */
        private boolean showPolygon(PolygonOptions polygon, Location myLocation, Location tmpLocation, int maxAirspaceDistance) {
            boolean userSouthOfNorthernmostPoint = false;
            boolean userNorthOfSouthernmostPoint = false;
            boolean userWestOfEasternmostPoint = false;
            boolean userEastOfWesternmostPoint = false;
            for (LatLng loc : polygon.getPoints()) {
                tmpLocation.setLatitude(loc.latitude);
                tmpLocation.setLongitude(loc.longitude);
                if (maxAirspaceDistance == 0 || myLocation.distanceTo(tmpLocation) < maxAirspaceDistance)
                    return true;
                if (myLocation.getLatitude() < loc.latitude)
                    userSouthOfNorthernmostPoint = true;
                else
                    userNorthOfSouthernmostPoint = true;
                if (myLocation.getLongitude() < loc.longitude)
                    userWestOfEasternmostPoint = true;
                else
                    userEastOfWesternmostPoint = true;
            }
            return userEastOfWesternmostPoint && userNorthOfSouthernmostPoint && userSouthOfNorthernmostPoint && userWestOfEasternmostPoint;
        }
    }

    private void drawAirspaceMap(Set<PolygonOptions> newPolygons) {
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        final GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        /* remove polygons that should not be visible */
        for (Iterator<Map.Entry<Polygon, PolygonOptions>> it = polygons.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Polygon, PolygonOptions> entry = it.next();
            if (newPolygons.contains(entry.getValue()))
                continue;
            entry.getKey().remove();
            it.remove();
        }
        /* draw polygons that should be visible */
        for (PolygonOptions polygon : newPolygons)
            polygons.put(map.addPolygon(polygon), polygon);
    }
}
