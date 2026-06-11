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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlotsFragment extends Fragment {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String selectedFacilityKey = "toronto";
    private String selectedVisaType    = "B1/B2";
    private List<AisClient.Slot> currentSlots = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup container, Bundle saved) {
        return inf.inflate(R.layout.fragment_slots, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        AppState app = AppState.instance;

        Spinner spCity    = v.findViewById(R.id.sp_city);
        Spinner spVisa    = v.findViewById(R.id.sp_visa);
        Button  btnCheck  = v.findViewById(R.id.btn_check);
        TextView tvStatus = v.findViewById(R.id.tv_check_status);
        LinearLayout slotsContainer = v.findViewById(R.id.slots_container);

        // City spinner
        List<String> cityNames = new ArrayList<>(Facilities.ALL.keySet());
        List<Facilities.Facility> facilityList = new ArrayList<>(Facilities.ALL.values());
        String[] displayNames = new String[facilityList.size()];
        for (int i = 0; i < facilityList.size(); i++) displayNames[i] = facilityList.get(i).name;
        ArrayAdapter<String> cityAdp = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, displayNames);
        cityAdp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCity.setAdapter(cityAdp);
        spCity.setSelection(cityNames.indexOf("toronto"));
        spCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View vv, int pos, long id) {
                selectedFacilityKey = cityNames.get(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Visa spinner
        ArrayAdapter<String> visaAdp = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, Facilities.VISA_LABELS);
        visaAdp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVisa.setAdapter(visaAdp);
        spVisa.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View vv, int pos, long id) {
                selectedVisaType = Facilities.VISA_TYPES[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnCheck.setOnClickListener(x -> {
            if (app.aisClient == null || !app.aisClient.loggedIn) {
                Toast.makeText(requireContext(), "Connect AIS account first (Home tab)", Toast.LENGTH_SHORT).show();
                return;
            }
            btnCheck.setEnabled(false);
            tvStatus.setText("Checking...");
            tvStatus.setVisibility(View.VISIBLE);
            slotsContainer.removeAllViews();
            currentSlots.clear();

            Facilities.Facility facility = Facilities.ALL.get(selectedFacilityKey);

            exec.execute(() -> {
                try {
                    List<AisClient.Slot> slots = app.aisClient.getSlots(facility.id, facility.name);
                    currentSlots = slots;
                    main.post(() -> {
                        btnCheck.setEnabled(true);
                        if (slots.isEmpty()) {
                            tvStatus.setText("No open slots found for " + facility.name);
                        } else {
                            tvStatus.setText(slots.size() + " slot(s) found at " + facility.name);
                            for (AisClient.Slot slot : slots) addSlotRow(slotsContainer, slot, app);
                        }
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        btnCheck.setEnabled(true);
                        tvStatus.setText("Error: " + e.getMessage());
                    });
                }
            });
        });
    }

    private void addSlotRow(LinearLayout container, AisClient.Slot slot, AppState app) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_slot, container, false);
        TextView tvDate = row.findViewById(R.id.tv_slot_date);
        TextView tvTime = row.findViewById(R.id.tv_slot_time);
        Button   btnBook = row.findViewById(R.id.btn_book_slot);

        tvDate.setText(slot.date);
        tvTime.setText(slot.time);
        btnBook.setOnClickListener(x -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Booking")
                    .setMessage("Book appointment at " + slot.facilityName + "?\n\nDate: " + slot.date + "\nTime: " + slot.time)
                    .setPositiveButton("Book Now", (d, w) -> bookSlot(slot, btnBook, app))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        container.addView(row);
    }

    private void bookSlot(AisClient.Slot slot, Button btn, AppState app) {
        btn.setEnabled(false);
        btn.setText("Booking...");
        exec.execute(() -> {
            try {
                AisClient.BookResult result = app.aisClient.bookAppointment(slot.facilityId, slot.date, slot.time);

                AppState.Booking booking = new AppState.Booking();
                booking.id             = String.valueOf(System.currentTimeMillis());
                booking.facilityName   = slot.facilityName;
                booking.visaType       = "—";
                booking.date           = slot.date;
                booking.time           = slot.time;
                booking.status         = result.success ? "booked" : "failed";
                booking.autoBooked     = false;
                booking.createdAt      = new Date().toString();
                booking.confirmationUrl = result.confirmationUrl;
                app.addBooking(booking);

                main.post(() -> {
                    if (result.success) {
                        btn.setText("Booked!");
                        Notifier.send(requireContext(), "Appointment Booked!", slot.facilityName + " — " + slot.date + " at " + slot.time);
                        Toast.makeText(requireContext(), "Booking confirmed!", Toast.LENGTH_LONG).show();
                    } else {
                        btn.setEnabled(true);
                        btn.setText("Book");
                        Toast.makeText(requireContext(), "Booking failed. Try on AIS website.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    btn.setEnabled(true);
                    btn.setText("Book");
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
