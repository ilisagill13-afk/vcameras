package com.visabot.canada;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class AisClient {

    public static final String BASE = "https://ais.usvisa-info.com/en-ca/niv";
    private static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static class Slot {
        public final String date;
        public final String time;
        public final int facilityId;
        public final String facilityName;
        public Slot(String date, String time, int facilityId, String facilityName) {
            this.date = date; this.time = time;
            this.facilityId = facilityId; this.facilityName = facilityName;
        }
    }

    public static class BookResult {
        public final boolean success;
        public final String confirmationUrl;
        public BookResult(boolean success, String url) {
            this.success = success; this.confirmationUrl = url;
        }
    }

    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final Map<String, String> jar = new HashMap<>();
    private String csrfToken;
    public String appointmentId;
    public boolean loggedIn = false;
    private final String email;
    private final String password;
    private long lastActivity = 0;
    private static final long SESSION_TTL = 20 * 60 * 1000L;

    private static final Gson GSON = new Gson();

    public AisClient(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // ── Cookie management ─────────────────────────────────────────────────────

    private void mergeCookies(String setCookie) {
        if (setCookie == null) return;
        String[] parts = setCookie.split(";");
        String pair = parts[0].trim();
        int eq = pair.indexOf('=');
        if (eq > 0) jar.put(pair.substring(0, eq), pair.substring(eq + 1));
    }

    private void mergeCookiesFromHeaders(okhttp3.Headers headers) {
        for (String v : headers.values("set-cookie")) mergeCookies(v);
    }

    private String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    private String extract(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void login() throws IOException {
        // 1. Load sign-in page
        Request req = new Request.Builder()
                .url(BASE + "/users/sign_in")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .build();
        try (Response r = http.newCall(req).execute()) {
            mergeCookiesFromHeaders(r.headers());
            String body = r.body().string();
            csrfToken = extract(body, "name=\"authenticity_token\"[^>]+value=\"([^\"]+)\"");
            if (csrfToken == null) throw new IOException("CSRF token not found on sign-in page");
        }

        // 2. POST credentials
        FormBody form = new FormBody.Builder()
                .addEncoded("utf8", "%E2%9C%93")
                .add("user[email]", email)
                .add("user[password]", password)
                .add("policy_confirmed", "1")
                .add("commit", "Sign In")
                .build();

        Request login = new Request.Builder()
                .url(BASE + "/users/sign_in")
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("X-CSRF-Token", csrfToken)
                .header("Referer", BASE + "/users/sign_in")
                .header("Origin", "https://ais.usvisa-info.com")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .post(form)
                .build();

        try (Response r = http.newCall(login).execute()) {
            mergeCookiesFromHeaders(r.headers());
            String body = r.body().string();
            String finalUrl = r.request().url().toString();

            String id = extract(finalUrl, "/schedule/(\\d+)");
            if (id == null) id = extract(body, "/en-ca/niv/schedule/(\\d+)");
            if (id == null) throw new IOException("Login failed. No appointment ID found. Check credentials.");

            appointmentId = id;
            String meta = extract(body, "<meta[^>]+name=\"csrf-token\"[^>]+content=\"([^\"]+)\"");
            if (meta != null) csrfToken = meta;
        }

        loggedIn = true;
        lastActivity = System.currentTimeMillis();
    }

    private void ensureSession() throws IOException {
        if (!loggedIn || System.currentTimeMillis() - lastActivity > SESSION_TTL) login();
    }

    public List<Slot> getSlots(int facilityId, String facilityName) throws IOException {
        ensureSession();
        List<String> dates = getAvailableDates(facilityId);
        List<Slot> slots = new ArrayList<>();
        for (String date : dates) {
            List<String> times = getAvailableTimes(facilityId, date);
            for (String time : times) slots.add(new Slot(date, time, facilityId, facilityName));
        }
        return slots;
    }

    private List<String> getAvailableDates(int facilityId) throws IOException {
        String url = BASE + "/schedule/" + appointmentId + "/appointment/days/" + facilityId + ".json?appointments[expedite]=false";
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE + "/schedule/" + appointmentId + "/appointment")
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (r.code() == 401 || r.code() == 403) {
                loggedIn = false;
                login();
                return getAvailableDates(facilityId);
            }
            mergeCookiesFromHeaders(r.headers());
            lastActivity = System.currentTimeMillis();
            String body = r.body().string();
            List<String> dates = new ArrayList<>();
            JsonArray arr = GSON.fromJson(body, JsonArray.class);
            if (arr != null) for (JsonElement e : arr) {
                JsonObject obj = e.getAsJsonObject();
                if (obj.has("date")) dates.add(obj.get("date").getAsString());
            }
            return dates;
        }
    }

    private List<String> getAvailableTimes(int facilityId, String date) throws IOException {
        String url = BASE + "/schedule/" + appointmentId + "/appointment/times/" + facilityId + ".json?date=" + date + "&appointments[expedite]=false";
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE + "/schedule/" + appointmentId + "/appointment")
                .build();

        try (Response r = http.newCall(req).execute()) {
            mergeCookiesFromHeaders(r.headers());
            lastActivity = System.currentTimeMillis();
            String body = r.body().string();
            List<String> times = new ArrayList<>();
            JsonObject obj = GSON.fromJson(body, JsonObject.class);
            if (obj != null && obj.has("available_times")) {
                for (JsonElement e : obj.getAsJsonArray("available_times"))
                    times.add(e.getAsString());
            }
            return times;
        }
    }

    public BookResult bookAppointment(int facilityId, String date, String time) throws IOException {
        ensureSession();

        // Refresh CSRF from appointment page
        Request page = new Request.Builder()
                .url(BASE + "/schedule/" + appointmentId + "/appointment")
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .build();
        try (Response r = http.newCall(page).execute()) {
            mergeCookiesFromHeaders(r.headers());
            String fresh = extract(r.body().string(), "name=\"authenticity_token\"[^>]+value=\"([^\"]+)\"");
            if (fresh != null) csrfToken = fresh;
        }

        FormBody body = new FormBody.Builder()
                .addEncoded("utf8", "%E2%9C%93")
                .add("authenticity_token", csrfToken)
                .add("confirmed_limit_message", "1")
                .add("use_consulate_appointment_capacity", "true")
                .add("appointments[consulate_appointment][facility_id]", String.valueOf(facilityId))
                .add("appointments[consulate_appointment][date]", date)
                .add("appointments[consulate_appointment][time]", time)
                .build();

        Request req = new Request.Builder()
                .url(BASE + "/schedule/" + appointmentId + "/appointment")
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("X-CSRF-Token", csrfToken)
                .header("Referer", BASE + "/schedule/" + appointmentId + "/appointment")
                .header("Origin", "https://ais.usvisa-info.com")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .post(body)
                .build();

        try (Response r = http.newCall(req).execute()) {
            mergeCookiesFromHeaders(r.headers());
            lastActivity = System.currentTimeMillis();
            String finalUrl = r.request().url().toString();
            boolean ok = (r.code() == 200 || r.code() == 302) && finalUrl.contains("/appointment");
            return new BookResult(ok, BASE + "/schedule/" + appointmentId + "/appointment");
        }
    }
}
