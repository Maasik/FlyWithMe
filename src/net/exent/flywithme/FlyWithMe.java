package net.exent.flywithme;

import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.R;
import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;
import net.exent.flywithme.widget.TakeoffArrayAdapter;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class FlyWithMe extends FragmentActivity {
	private static Location location;
	private static Flightlog flightlog;
	private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();
	
	private LocationListener locationListener = new LocationListener() {
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
		
		public void onProviderEnabled(String provider) {
		}
		
		public void onProviderDisabled(String provider) {
		}
		
		public void onLocationChanged(Location newLocation) {
			updateLocation(newLocation);
		}
	};
	
	public static Location getLocation() {
		return location;
	}
	
	public static List<Takeoff> getTakeoffs() {
		return takeoffs;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.fly_with_me);

		/* create our database handler */
		flightlog = new Flightlog(this);
		
		/* spinner (dropdown menu) for what to sort after */
		/* TODO: do we really need this? just sort first after pilots present, if equal then pilots coming, if equal then distance */
		Spinner takeoffsSortSpinner = (Spinner) findViewById(R.id.takeoffsSortSpinner);
		List<String> list = new ArrayList<String>();
		list.add("Sort by distance");
		list.add("Sort by pilots present");
		list.add("Sort by pilots coming");
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		takeoffsSortSpinner.setAdapter(dataAdapter);
		
		/* set initial location & listener */
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, /* TODO: setting */300000, /* TODO: setting */100, locationListener);
		Location newLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (newLocation == null)
			newLocation = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
		updateLocation(newLocation);

		TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(this);
		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.setAdapter(adapter);
		takeoffsView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final Takeoff takeoff = takeoffs.get(position);

				TextView takeoffName = (TextView) findViewById(R.id.takeoffDetailName);
				TextView takeoffCoordinates = (TextView) findViewById(R.id.takeoffDetailCoordinates);
				ImageButton mapButton = (ImageButton) findViewById(R.id.takeoffDetailMapButton);
				
				takeoffName.setText(takeoff.getName());
				takeoffCoordinates.setText("Loc: " + takeoff.getLocation().getLatitude() + ", " + takeoff.getLocation().getLongitude());
				mapButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						Location loc = takeoff.getLocation();
						String uri = "http://maps.google.com/maps?saddr=" + location.getLatitude() + "," + location.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude(); 
						Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
						startActivity(intent);
					}
				});
				
				ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.viewSwitcher1);
				switcher.showNext();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.menu_settings:
	            Intent settingsActivity = new Intent(this, Preferences.class);
            	startActivity(settingsActivity);
	            return true;

	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	private void updateLocation(Location newLocation) {
		if (newLocation == null)
			return;
		location = newLocation;
		/* TODO: maxDegrees (parameter to updateTakeoffsList) should be a setting */
		takeoffs = flightlog.getTakeoffs(1);

		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.invalidateViews();
	}
}
