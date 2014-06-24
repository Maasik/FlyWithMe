package net.exent.flywithme;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Text;

import javax.servlet.http.HttpServletResponse;
import java.beans.XMLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by canidae on 6/24/14.
 */
public class ScheduleHandler {
    private static final Logger log = Logger.getLogger(ScheduleHandler.class.getName());

    /* schedule for favourited takeoffs */
    public static synchronized int getScheduleV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffCount = inputStream.readUnsignedShort();
        if (takeoffCount > 200)
            takeoffCount = 200; // don't allow asking for more than 200 takeoffs

        StringBuilder sb = new StringBuilder("Get schedule: ");
        for (int i = 0; i < takeoffCount; ++i) {
            int takeoffId = inputStream.readUnsignedShort();
            TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
            SortedMap<Integer, Set<Pilot>> schedule;
            if (takeoffSchedule == null || (schedule = takeoffSchedule.getEntries()).isEmpty()) {
                // no schedule for this takeoff, but in case we've removed a single entry we need to tell the client that it's empty (or the client won't remove that entry)
                outputStream.writeShort(takeoffId);
                outputStream.writeShort(0);
                continue;
            }
            outputStream.writeShort(takeoffId);
            outputStream.writeShort(schedule.size());
            sb.append(takeoffId);
            sb.append(',').append(schedule.size());
            int timestampCounter = 0;
            for (Map.Entry<Integer, Set<Pilot>> scheduleEntry : schedule.entrySet()) {
                if (++timestampCounter > 10)
                    break; // max 10 timestamps per takeoff returned
                outputStream.writeInt(scheduleEntry.getKey());
                sb.append(',').append(scheduleEntry.getKey());
                Set<Pilot> pilots = scheduleEntry.getValue();
                outputStream.writeShort(pilots.size());
                sb.append(',').append(pilots.size());
                int pilotCounter = 0;
                for (Pilot pilot : pilots) {
                    if (++pilotCounter > 10)
                        break; // max 10 pilots per timestamp
                    // make sure we don't send long strings by capping name to 40 and phone to 20 characters
                    outputStream.writeUTF(pilot.getName().length() > 40 ? pilot.getName().substring(0, 40) : pilot.getName());
                    outputStream.writeUTF(pilot.getPhone().length() > 20 ? pilot.getPhone().substring(0, 20) : pilot.getPhone());
                    sb.append(',').append(pilot.getName());
                    sb.append(',').append(pilot.getPhone());
                }
            }
            sb.append(" | ");
        }
        outputStream.writeShort(0);
        log.info(sb.toString());
        return HttpServletResponse.SC_OK;
    }

    /* fetch schedule for all takeoffs */
    private static synchronized int getScheduleV2(final DataOutputStream outputStream) throws IOException {
        StringBuilder sb = new StringBuilder("Get full schedule: ");
        for (Map.Entry<Integer, TakeoffSchedule> takeoffSchedule : schedules.entrySet()) {
            int takeoffId = takeoffSchedule.getKey();
            SortedMap<Integer, Set<Pilot>> schedule = takeoffSchedule.getValue().getEntries();
            outputStream.writeShort(takeoffId);
            outputStream.writeShort(schedule.size());
            sb.append(takeoffId);
            sb.append(',').append(schedule.size());
            for (Map.Entry<Integer, Set<Pilot>> scheduleEntry : schedule.entrySet()) {
                outputStream.writeInt(scheduleEntry.getKey());
                sb.append(',').append(scheduleEntry.getKey());
                Set<Pilot> pilots = scheduleEntry.getValue();
                outputStream.writeShort(pilots.size());
                sb.append(',').append(pilots.size());
                for (Pilot pilot : pilots) {
                    // make sure we don't send long strings by capping name to 40 and phone to 20 characters
                    outputStream.writeUTF(pilot.getName().length() > 40 ? pilot.getName().substring(0, 40) : pilot.getName());
                    outputStream.writeUTF(pilot.getPhone().length() > 20 ? pilot.getPhone().substring(0, 20) : pilot.getPhone());
                    sb.append(',').append(pilot.getName());
                    sb.append(',').append(pilot.getPhone());
                }
            }
            sb.append(" | ");
        }
        outputStream.writeShort(0);
        log.info(sb.toString());
        return HttpServletResponse.SC_OK;
    }

    /* register a flight at a takeoff */
    private static synchronized int registerScheduleEntryV1(final DataInputStream inputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        int timestamp = inputStream.readInt() / 60 * 60; // "/ 60 * 60" sets seconds to 0
        long pilotId = inputStream.readLong();
        if (pilotId == 0)
            return HttpServletResponse.SC_BAD_REQUEST;
        String pilotName = inputStream.readUTF();
        if ("".equals(pilotName.trim()))
            return HttpServletResponse.SC_BAD_REQUEST;
        String pilotPhone = inputStream.readUTF();
        log.info("Scheduling, takeoff ID: " + takeoffId + ", timestamp: " + timestamp + ", pilot ID: " + pilotId + ", name: " + pilotName + ", phone: " + pilotPhone);
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule == null) {
            takeoffSchedule = new TakeoffSchedule();
            schedules.put(takeoffId, takeoffSchedule);
        }
        takeoffSchedule.addPilotToSchedule(timestamp, new Pilot(pilotId, pilotName, pilotPhone));
        storeSchedules();
        return HttpServletResponse.SC_OK;
    }

    /* unregister a flight at a takeoff */
    private static synchronized int unregisterScheduleEntryV1(final DataInputStream inputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        int timestamp = inputStream.readInt() / 60 * 60; // "/ 60 * 60" sets seconds to 0
        long pilotId = inputStream.readLong();
        log.info("Unscheduling, takeoff ID: " + takeoffId + ", timestamp: " + timestamp + ", pilot ID: " + pilotId);
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule != null)
            takeoffSchedule.removePilotFromSchedule(timestamp, pilotId);
        storeSchedules();
        return HttpServletResponse.SC_OK;
    }

    private static void storeSchedules() {
        Entity entity = new Entity(DATASTORE_SCHEDULES_KEY);
        for (Map.Entry<Integer, TakeoffSchedule> schedule : schedules.entrySet()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XMLEncoder xmlEncoder = new XMLEncoder(bos);
            xmlEncoder.writeObject(schedule.getValue());
            xmlEncoder.close();
            entity.setProperty(schedule.getKey().toString(), new Text(bos.toString()));
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        datastore.put(entity);
    }

    // NOTE! even though it may appear like the setters and getters are not in use, they are!
    // deserializing from datastore use the default constructor and the setters to rebuild the object!
    public static class Pilot {
        private long id;
        private String name;
        private String phone;

        public Pilot() {
            // required for deserialization
        }

        public Pilot(long id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Pilot) && other.hashCode() == hashCode();
        }
    }

    // NOTE! even though it may appear like the setters and getters are not in use, they are!
    // deserializing from datastore use the default constructor and the setters to rebuild the object!
    public static class TakeoffSchedule {
        private SortedMap<Integer, Set<Pilot>> entries = new TreeMap<>();

        public void addPilotToSchedule(int timestamp, Pilot pilot) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule == null) {
                schedule = new HashSet<>();
                entries.put(timestamp, schedule);
            }
            schedule.add(pilot);
        }

        public void removePilotFromSchedule(int timestamp, long pilotId) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule != null) {
                schedule.remove(new Pilot(pilotId, "doesn't matter", "neither does this"));
                if (schedule.isEmpty())
                    entries.remove(timestamp);
            }
        }

        public void removeExpired(int expireTime) {
            Iterator<SortedMap.Entry<Integer, Set<Pilot>>> timestampIterator = entries.entrySet().iterator();
            while (timestampIterator.hasNext()) {
                SortedMap.Entry<Integer, Set<Pilot>> timestampEntry = timestampIterator.next();
                if (timestampEntry.getKey() < expireTime)
                    timestampIterator.remove();
            }
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public SortedMap<Integer, Set<Pilot>> getEntries() {
            return entries;
        }

        public void setEntries(SortedMap<Integer, Set<Pilot>> entries) {
            this.entries = entries;
        }
    }
}
