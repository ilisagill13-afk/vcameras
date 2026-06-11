package com.visabot.canada;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitorsFragment extends Fragment {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String selectedFacilityKey = "toronto";
    private String selectedVisaType    = "B1/B2";
    private LinearLayout monitorsList;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        return inf.inflate(R.layout.fragment_monitors, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        AppState app = AppState.instance;
        monitorsList = v.findViewById(R.id.monitors_list);

        Spinner spCity    = v.findViewById(R.id.sp_city_mon);
        Spinner spVisa    = v.findViewById(R.id.sp_visa_mon);
        CheckBox cbAuto   = v.findViewById(R.id.cb_autobook);
        Button btnAdd     = v.findViewById(R.id.btn_add_monitor);

        List<String> cityKeys = new ArrayList<>(Facilities.ALL.keySet());
        List<Facilities.Facility> fList = new ArrayList<>(Facilities.ALL.values());
        String[] cityNames = new String[fList.size()];
        for (int i = 0; i < fList.size(); i++) cityNames[i] = fList.get(i).name;
        ArrayAdapter<String> ca = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, cityNames);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCity.setAdapter(ca);
        spCity.setSelection(cityKeys.indexOf("toronto"));
        spCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View vv, int pos, long id) { selectedFacilityKey = cityKeys.get(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        ArrayAdapter<String> va = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, Facilities.VISA_LABELS);
        va.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVisa.setAdapter(va);
        spVisa.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View vv, int pos, long id) { selectedVisaType = Facilities.VISA_TYPES[pos]; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnAdd.setOnClickListener(x -> {
            AppState.Monitor m = new AppState.Monitor();
            m.id          = UUID.randomUUID().toString();
            m.location    = selectedFacilityKey;
            m.facilityId  = Facilities.ALL.get(selectedFacilityKey).id;
            m.facilityName= Facilities.ALL.get(selectedFacilityKey).name;
            m.visaType    = selectedVisaType;
            m.autoBook    = cbAuto.isChecked();
            m.active      = true;
            m.slotsFound  = 0;
            m.createdAt   = new Date().toString();
            app.addMonitor(m);
            Toast.makeText(requireContext(), "Monitor added", Toast.LENGTH_SHORT).show();
            renderMonitors();
        });

        renderMonitors();
    }

    private void renderMonitors() {
        AppState app = AppState.instance;
        monitorsList.removeAllViews();
        List<AppState.Monitor> monitors = app.getMonitors();

        if (monitors.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No monitors yet. Add one above.");
            empty.setPadding(0, 32, 0, 0);
            empty.setTextColor(0xFF6B7280);
            monitorsList.addView(empty);
            return;
        }

        for (AppState.Monitor m : monitors) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_monitor, monitorsList, false);

            TextView tvTitle   = row.findViewById(R.id.tv_mon_title);
            TextView tvMeta    = row.findViewById(R.id.tv_mon_meta);
            Button   btnToggle = row.findViewById(R.id.btn_mon_toggle);
            Button   btnDelete = row.findViewById(R.id.btn_mon_delete);
            Button   btnRun    = row.findViewById(R.id.btn_mon_run);

            tvTitle.setText(m.facilityName + " — " + m.visaType + (m.autoBook ? " [Auto-Book]" : ""));
            tvMeta.setText((m.lastChecked != null ? "Last: " + m.lastChecked : "Not checked yet")
                    + (m.slotsFound > 0 ? " · " + m.slotsFound + " found" : ""));
            btnToggle.setText(m.active ? "Pause" : "Resume");

            btnToggle.setOnClickListener(x -> {
                List<AppState.Monitor> list = app.getMonitors();
                for (AppState.Monitor mon : list) if (mon.id.equals(m.id)) mon.active = !mon.active;
                app.saveMonitors(list);
                renderMonitors();
            });

            btnDelete.setOnClickListener(x -> new AlertDialog.Builder(requireContext())
                    .setTitle("Remove Monitor")
                    .setMessage("Delete this monitor?")
                    .setPositiveButton("Remove", (d, w) -> {
                        List<AppState.Monitor> list = app.getMonitors();
                        list.removeIf(mon -> mon.id.equals(m.id));
                        app.saveMonitors(list);
                        renderMonitors();
                    })
                    .setNegativeButton("Cancel", null).show());

            btnRun.setOnClickListener(x -> runCheckNow(m, btnRun, app));

            monitorsList.addView(row);
        }
    }

    private void runCheckNow(AppState.Monitor monitor, Button btn, AppState app) {
        if (app.aisClient == null || !app.aisClient.loggedIn) {
            Toast.makeText(requireContext(), "Connect AIS account first", Toast.LENGTH_SHORT).show();
            return;
        }
        btn.setEnabled(false);
        btn.setText("Checking...");

        exec.execute(() -> {
            try {
                Facilities.Facility facility = Facilities.ALL.get(monitor.location);
                List<AisClient.Slot> slots = app.aisClient.getSlots(facility.id, facility.name);
                app.updateMonitor(monitor.id, new Date().toString(), slots.size(), monitor.active);

                if (!slots.isEmpty() && monitor.autoBook) {
                    AisClient.Slot first = slots.get(0);
                    AisClient.BookResult result = app.aisClient.bookAppointment(first.facilityId, first.date, first.time);
                    AppState.Booking booking = new AppState.Booking();
                    booking.id = String.valueOf(System.currentTimeMillis());
                    booking.facilityName = first.facilityName;
                    booking.visaType = monitor.visaType;
                    booking.date = first.date; booking.time = first.time;
                    booking.status = result.success ? "booked" : "failed";
                    booking.autoBooked = true; booking.createdAt = new Date().toString();
                    booking.confirmationUrl = result.confirmationUrl;
                    app.addBooking(booking);

                    if (result.success) {
                        app.updateMonitor(monitor.id, new Date().toString(), slots.size(), false);
                        Notifier.send(requireContext(), "Booked!", first.facilityName + " " + first.date + " " + first.time);
                    }
                }

                final int found = slots.size();
                main.post(() -> {
                    btn.setEnabled(true); btn.setText("Check Now");
                    Toast.makeText(requireContext(),
                            found > 0 ? found + " slot(s) found!" : "No slots found",
                            Toast.LENGTH_SHORT).show();
                    if (found > 0) Notifier.send(requireContext(), found + " Slots!", facility.name + " has open slots");
                    renderMonitors();
                });
            } catch (Exception e) {
                main.post(() -> {
                    btn.setEnabled(true); btn.setText("Check Now");
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
