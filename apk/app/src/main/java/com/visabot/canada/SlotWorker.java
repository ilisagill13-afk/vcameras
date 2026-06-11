package com.visabot.canada;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class SlotWorker extends Worker {

    public SlotWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull @Override
    public Result doWork() {
        AppState app = AppState.instance;
        if (app == null || app.aisClient == null || !app.aisClient.loggedIn) return Result.success();

        List<AppState.Monitor> monitors = app.getMonitors();
        for (AppState.Monitor monitor : monitors) {
            if (!monitor.active) continue;
            try {
                Facilities.Facility facility = Facilities.ALL.get(monitor.location);
                if (facility == null) continue;

                List<AisClient.Slot> slots = app.aisClient.getSlots(facility.id, facility.name);
                app.updateMonitor(monitor.id, new java.util.Date().toString(), slots.size(), monitor.active);

                if (slots.isEmpty()) continue;

                if (monitor.autoBook) {
                    AisClient.Slot first = slots.get(0);
                    AisClient.BookResult result = app.aisClient.bookAppointment(first.facilityId, first.date, first.time);

                    AppState.Booking booking = new AppState.Booking();
                    booking.id            = String.valueOf(System.currentTimeMillis());
                    booking.facilityName  = first.facilityName;
                    booking.visaType      = monitor.visaType;
                    booking.date          = first.date;
                    booking.time          = first.time;
                    booking.status        = result.success ? "booked" : "failed";
                    booking.autoBooked    = true;
                    booking.createdAt     = new java.util.Date().toString();
                    booking.confirmationUrl = result.confirmationUrl;
                    app.addBooking(booking);

                    if (result.success) {
                        app.updateMonitor(monitor.id, new java.util.Date().toString(), slots.size(), false);
                        Notifier.send(getApplicationContext(),
                                "Appointment Booked!",
                                facility.name + " — " + first.date + " at " + first.time);
                        continue;
                    }
                }

                Notifier.send(getApplicationContext(),
                        slots.size() + " Slot(s) Available!",
                        facility.name + " — " + monitor.visaType + ". Open app to book.");
            } catch (Exception ignored) {}
        }
        return Result.success();
    }
}
