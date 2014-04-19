package net.exent.flywithme.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Pilot;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

/**
 * Created by canidae on 3/10/14.
 */
public class ScheduleService extends IntentService {
    private static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";
    private static final int NOTIFICATION_ID = 42;
    private static final long MS_IN_DAY = 86400000;
    private static long pilotId = 0;
    private static String pilotName = null;
    private static String pilotPhone = null;
    private static int fetchTakeoffs = 0;
    private static int startTime = 0;
    private static int stopTime = 0;
    private static long updateInterval = 0;
    private static boolean showNotification = false;
    private long lastUpdate = 0;

    public ScheduleService() {
        super("ScheduleService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getName(), "onHandleIntent(" + intent.toString() + ")");

        // setup notification builder
        List<String> notificationTakeoffs = new ArrayList<>();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
        notificationBuilder.setContentTitle(getString(R.string.get_your_wing));
        notificationBuilder.setAutoCancel(true);
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, new Intent(this, FlyWithMe.class), PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(notificationIntent);

        while (true) {

            long now = System.currentTimeMillis();
            long localHour = (now + TimeZone.getDefault().getOffset(now)) % MS_IN_DAY;

            // check for changes to settings
            updatePreferenceValues();

            // these two values tells us whether we're between start time and stop time
            // if startTime == stopTime then we always want to update (setting maxValue to 24 hours)
            long timeValue = (localHour - startTime + MS_IN_DAY) % MS_IN_DAY;
            long maxValue = startTime == stopTime ? MS_IN_DAY : (stopTime - startTime + MS_IN_DAY) % MS_IN_DAY;

            if (fetchTakeoffs == -1 || now < lastUpdate + updateInterval || timeValue > maxValue) {
                // no update at this time, wait a minute and check again
                SystemClock.sleep(60000);
                continue;
            }
            lastUpdate = now;

            updateSchedule();

            // show/update notification
            if (showNotification) {
                List<String> takeoffsWithScheduledFlightsToday = Database.getTakeoffsWithScheduledFlightsToday(pilotName);
                if (!notificationTakeoffs.containsAll(takeoffsWithScheduledFlightsToday)) {
                    // notify the user that people are planning to fly today
                    notificationTakeoffs = takeoffsWithScheduledFlightsToday;
                    String notificationText = "";
                    for (String takeoff : notificationTakeoffs)
                        notificationText += "".equals(notificationText) ? takeoff : " | " + takeoff;
                    notificationBuilder.setContentText(notificationText);
                    notificationBuilder.setWhen(System.currentTimeMillis());
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                }
            }
        }
    }

    public static void updateSchedule() {
        Location location = FlyWithMe.getInstance().getLocation();
        List<Takeoff> takeoffs = Database.getTakeoffs(location.getLatitude(), location.getLongitude(), fetchTakeoffs, false);
        try {
            Log.i(ScheduleService.class.getName(), "Fetching schedule from server");
            HttpURLConnection con = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(0);
            outputStream.writeShort(takeoffs.size());
            for (Takeoff takeoff : takeoffs)
                outputStream.writeShort(takeoff.getId());
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
            parseScheduleResponse(new DataInputStream(con.getInputStream()));
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Fetching flight schedule failed unexpectedly", e);
        }
    }

    public static void scheduleFlight(int takeoffId, long timestamp) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(1);
            outputStream.writeShort(takeoffId);
            outputStream.writeInt((int) (timestamp / 1000)); // timestamp is sent as seconds since epoch, not ms
            outputStream.writeLong(pilotId);
            outputStream.writeUTF(pilotName);
            outputStream.writeUTF(pilotPhone);
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Scheduling flight failed unexpectedly", e);
        }
    }

    public static void unscheduleFlight(int takeoffId, long timestamp) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(2);
            outputStream.writeShort(takeoffId);
            outputStream.writeInt((int) (timestamp / 1000)); // timestamp is sent as seconds since epoch, not ms
            outputStream.writeLong(pilotId);
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Unscheduling flight failed unexpectedly", e);
        }
    }

    private static void parseScheduleResponse(DataInputStream inputStream) {
        try {
            while (true) {
                int takeoffId = inputStream.readUnsignedShort();
                if (takeoffId == 0)
                    break; // no more data to be read
                int timestamps = inputStream.readUnsignedShort();
                Map<Long, List<Pilot>> schedule = new HashMap<>();
                for (int b = 0; b < timestamps; ++b) {
                    long timestamp = (long) inputStream.readInt() * 1000L; // timestamp is received as seconds since epoch, not ms
                    List<Pilot> pilotList = new ArrayList<>();
                    int pilots = inputStream.readUnsignedShort();
                    for (int c = 0; c < pilots; ++c) {
                        String pilotName = inputStream.readUTF();
                        String pilotPhone = inputStream.readUTF();
                        pilotList.add(new Pilot(pilotName, pilotPhone));
                    }
                    schedule.put(timestamp, pilotList);
                }
                Database.updateTakeoffSchedule(takeoffId, schedule);
            }
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Fetching flight schedule failed unexpectedly", e);
        }
    }

    private void updatePreferenceValues() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pilotId = prefs.getLong("pref_schedule_pilot_id", 0);
        while (pilotId == 0) {
            // generate a random ID for identifying the pilot's registrations
            pilotId = (new Random()).nextLong();
            prefs.edit().putLong("pref_schedule_pilot_id", pilotId).commit();
        }
        pilotName = prefs.getString("pref_schedule_pilot_name", "");
        pilotPhone = prefs.getString("pref_schedule_pilot_phone", "");
        fetchTakeoffs = Integer.parseInt(prefs.getString("pref_schedule_fetch_takeoffs", "-1"));
        startTime = Integer.parseInt(prefs.getString("pref_schedule_start_fetch_time", "28800")) * 1000;
        stopTime = Integer.parseInt(prefs.getString("pref_schedule_stop_fetch_time", "72000")) * 1000;
        updateInterval = Integer.parseInt(prefs.getString("pref_schedule_update_interval", "3600")) * 1000;
        showNotification = prefs.getBoolean("pref_schedule_notification", true);
    }
}