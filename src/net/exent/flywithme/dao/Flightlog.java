package net.exent.flywithme.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.util.Log;

public class Flightlog extends SQLiteOpenHelper {
	
	public Flightlog(Context context) {
		super(context, "flywithme.db", null, 1);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i("Flightlog", "Creating database");
		db.execSQL("CREATE TABLE takeoff(id INTEGER PRIMARY KEY, name TEXT, latitude REAL, longitude REAL)");
		// TODO: database should be supplied with FWM, this is a hack while testing:
		crawl(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
	
	public List<Takeoff> getTakeoffs(double maxDegrees) {
		SQLiteDatabase db = getWritableDatabase();
		final Location loc = FlyWithMe.getLocation();
		String where = "ABS(latitude - " + loc.getLatitude() + ") < " + maxDegrees + " AND ABS(longitude - " + loc.getLongitude() + ") < " + maxDegrees;
		Cursor cursor = db.query(false, "takeoff", new String[] {"id AS _id", "name", "latitude", "longitude"}, where, null, null, null, null, null);
		List<Takeoff> takeoffs = new ArrayList<Takeoff>();
		while (cursor.moveToNext())
			takeoffs.add(new Takeoff(cursor.getInt(0), cursor.getString(1), cursor.getFloat(2), cursor.getFloat(3)));
		
		/* sorting by pilots present/pilots coming/distance */
		Collections.sort(takeoffs, new Comparator<Takeoff>() {
			public int compare(Takeoff lhs, Takeoff rhs) {
				if (lhs.getPilotsPresent() < rhs.getPilotsPresent())
					return 1;
				else if (lhs.getPilotsPresent() > rhs.getPilotsPresent())
					return -1;
				if (lhs.getPilotsComing() < rhs.getPilotsComing())
					return 1;
				else if (lhs.getPilotsComing() > rhs.getPilotsComing())
					return -1;
				if (loc.distanceTo(lhs.getLocation()) > loc.distanceTo(rhs.getLocation()))
					return 1;
				else if (loc.distanceTo(lhs.getLocation()) < loc.distanceTo(rhs.getLocation()))
					return -1;
				return 0;
			}
		});
		
		return takeoffs;
	}

	/*
	 * http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=4
	 * we can set "country_id" to a fixed value, it only means that wrong country will be displayed (which we don't care about)
	 */
	public void crawl(SQLiteDatabase db) {
		Log.i("Flightlog", "crawling");
		SQLiteStatement addTakeoff = db.compileStatement("INSERT OR REPLACE INTO takeoff(id, name, latitude, longitude) VALUES (?, ?, ?, ?)");
		int takeoff = 0;
		int lastValidTakeoff = 0;
		while (takeoff++ < lastValidTakeoff + 50) { // when we haven't found a takeoff within the last 50 fetches from flightlog, assume all is found
			try {
				URL url = new URL("http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=" + takeoff);
				HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
				switch (httpUrlConnection.getResponseCode()) {
				case HttpURLConnection.HTTP_OK:
					String charset = getCharsetFromHeaderValue(httpUrlConnection.getContentType());
					StringBuilder sb = new StringBuilder();
					BufferedReader br = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), charset), 32768);
					char[] buffer = new char[32768];
					int read;
					while ((read = br.read(buffer)) != -1)
						sb.append(buffer, 0, read);
					br.close();
					
					String text = sb.toString();
					Pattern namePattern = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
					Matcher nameMatcher = namePattern.matcher(text);
					Pattern coordPattern = Pattern.compile(".*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
					Matcher coordMatcher = coordPattern.matcher(text);
					
					if (nameMatcher.matches() && coordMatcher.matches()) {
						String takeoffName = nameMatcher.group(1).trim();
	
						String northOrSouth = coordMatcher.group(1);
						int latDeg = Integer.parseInt(coordMatcher.group(2));
						int latMin = Integer.parseInt(coordMatcher.group(3));
						int latSec = Integer.parseInt(coordMatcher.group(4));
						float latitude = 0;
						latitude = (float) latDeg + (float) (latMin * 60 + latSec) / (float) 3600;
						if ("S".equals(northOrSouth))
							latitude *= -1.0;
	
						String eastOrWest = coordMatcher.group(5);
						int lonDeg = Integer.parseInt(coordMatcher.group(6));
						int lonMin = Integer.parseInt(coordMatcher.group(7));
						int lonSec = Integer.parseInt(coordMatcher.group(8));
						float longitude = 0;
						longitude = (float) lonDeg + (float) (lonMin * 60 + lonSec) / (float) 3600;
						if ("W".equals(eastOrWest))
							longitude *= -1.0;
	
						Log.i("Flightlog", "Adding takeoff: " + takeoff + ", " + takeoffName + ", " + latitude + ", " + longitude);
						addTakeoff.bindLong(1, takeoff);
						addTakeoff.bindString(2, takeoffName);
						addTakeoff.bindDouble(3, latitude);
						addTakeoff.bindDouble(4, longitude);
						addTakeoff.executeInsert();
						lastValidTakeoff = takeoff;
					}
					break;
	
				default:
					Log.w("Flightlog", "Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching takeoff with ID " + takeoff);
					break;
				}
			} catch (IOException e) {
				Log.w("Flightlog", "Exception when trying to fetch takeoff", e);
			}
		}
	}
	
	private static String getCharsetFromHeaderValue(String text) {
        int start = text.indexOf("charset=");
        if (start >= 0) {
            start += 8;
            int end = text.indexOf(";", start);
            int pos = text.indexOf(" ", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            pos = text.indexOf("\n", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            if (end == -1)
                end = text.length();
            if (text.charAt(start) == '"' && text.charAt(end - 1) == '"') {
                ++start;
                --end;
            }
            return text.substring(start, end);
        }
        return "iso-8859-1";
	}
}
