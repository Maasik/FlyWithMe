package net.exent.flywithme;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import net.exent.flywithme.data.Database;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by canidae on 3/10/14.
 */
public class ScheduleService extends IntentService {
    public ScheduleService() {
        super("ScheduleService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getSimpleName(), "onHandleIntent(" + intent.toString() + ")");
        while (true) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            /* TODO: settings:
             * - fetch schedule at all?
             * - how often to fetch?
             * - fetch favourites within x km?
             * - fetch non-favourites within y km?
             * - max amount to fetch? could be a fixed amount, only 2 bytes per takeoff (but more data returned)
             * - stop fetching for a certain period, i.e. no fetching between 20.00 and 08.00?
             */
            long sleepTime = 3600000; // TODO: config setting

            Set<Integer> favourites = Database.getFavourites();

            try {
                Log.i(getClass().getSimpleName(), "Fetching schedule from server");
                HttpURLConnection con = (HttpURLConnection) new URL("http://flywithme-server.appspot.com/fwm").openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
                outputStream.writeByte(0);
                outputStream.writeShort(favourites.size());
                for (Integer favourite : favourites) {
                    outputStream.writeShort(favourite);
                }
                outputStream.close();
                int responseCode = con.getResponseCode();
                Log.d(getClass().getName(), "Response code: " + responseCode);
                DataInputStream inputStream = new DataInputStream(con.getInputStream());

                int takeoffs = inputStream.readUnsignedShort();
                Log.d(getClass().getName(), "Takeoffs: " + takeoffs);
                for (int a = 0; a < takeoffs; ++a) {
                    int takeoffId = inputStream.readUnsignedShort();
                    Log.d(getClass().getName(), "Takeoff ID: " + takeoffId);
                    int timestamps = inputStream.readUnsignedShort();
                    Log.d(getClass().getName(), "Timestamps: " + timestamps);
                    Map<Date, List<String>> schedule = new HashMap<>();
                    for (int b = 0; b < timestamps; ++b) {
                        long timestamp = inputStream.readLong();
                        Date date = new Date(timestamp * 1000);
                        List<String> pilotList = new ArrayList<>();
                        Log.d(getClass().getName(), "Timestamp: " + timestamp);
                        int pilots = inputStream.readUnsignedShort();
                        Log.d(getClass().getName(), "Pilots: " + pilots);
                        for (int c = 0; c < pilots; ++c) {
                            String pilot = inputStream.readUTF();
                            pilotList.add(pilot);
                            Log.d(getClass().getName(), "Pilot: " + pilot);
                        }
                        schedule.put(date, pilotList);
                        Database.updateTakeoffSchedule(takeoffId, schedule);
                    }
                }
            } catch (IOException e) {
                Log.w(getClass().getName(), "Fetching flight schedule failed unexpectedly", e);
            }

            // sleep until next update
            SystemClock.sleep(sleepTime - SystemClock.elapsedRealtime() % sleepTime);
        }
    }
}