package com.visabot.canada;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class AppState extends Application {

    public static AppState instance;
    private static final String PREFS = "visa_bot_prefs";
    private static final Gson GSON = new Gson();

    // Runtime state
    public AisClient aisClient;
    public String aisStatus = "disconnected"; // disconnected|connected|error
    public String aisError = null;

    // Persistent models
    public static class Monitor {
        public String id;
        public String location;     // facility key e.g. "toronto"
        public String facilityName;
        public int facilityId;
        public String visaType;
        public boolean autoBook;
        public boolean active;
        public String lastChecked;
        public int slotsFound;
        public String createdAt;
    }

    public static class Booking {
        public String id;
        public String facilityName;
        public String visaType;
        public String date;
        public String time;
        public String status; // booked|failed|error
        public boolean autoBooked;
        public String createdAt;
        public String confirmationUrl;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveCreds(String email, String password) {
        prefs().edit().putString("email", email).putString("pass", password).apply();
    }

    public String getSavedEmail()    { return prefs().getString("email", null); }
    public String getSavedPassword() { return prefs().getString("pass", null); }

    public void clearCreds() {
        prefs().edit().remove("email").remove("pass").apply();
        aisClient = null; aisStatus = "disconnected"; aisError = null;
    }

    // ── Monitors ──────────────────────────────────────────────────────────────

    public List<Monitor> getMonitors() {
        String json = prefs().getString("monitors", "[]");
        return GSON.fromJson(json, new TypeToken<List<Monitor>>(){}.getType());
    }

    public void saveMonitors(List<Monitor> monitors) {
        prefs().edit().putString("monitors", GSON.toJson(monitors)).apply();
    }

    public void addMonitor(Monitor m) {
        List<Monitor> list = getMonitors(); list.add(m); saveMonitors(list);
    }

    public void updateMonitor(String id, String lastChecked, int slotsFound, boolean active) {
        List<Monitor> list = getMonitors();
        for (Monitor m : list) if (m.id.equals(id)) {
            m.lastChecked = lastChecked; m.slotsFound = slotsFound; m.active = active;
        }
        saveMonitors(list);
    }

    // ── Bookings ──────────────────────────────────────────────────────────────

    public List<Booking> getBookings() {
        String json = prefs().getString("bookings", "[]");
        return GSON.fromJson(json, new TypeToken<List<Booking>>(){}.getType());
    }

    public void addBooking(Booking b) {
        List<Booking> list = getBookings(); list.add(b); saveBookings(list);
    }

    private void saveBookings(List<Booking> list) {
        // Keep last 50
        if (list.size() > 50) list = new ArrayList<>(list.subList(list.size() - 50, list.size()));
        prefs().edit().putString("bookings", GSON.toJson(list)).apply();
    }
}
