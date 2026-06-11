package com.visabot.canada;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Collections;
import java.util.List;

public class BookingsFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup c, Bundle s) {
        return inf.inflate(R.layout.fragment_bookings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        AppState app = AppState.instance;
        LinearLayout list = v.findViewById(R.id.bookings_list);
        list.removeAllViews();

        List<AppState.Booking> bookings = app.getBookings();
        Collections.reverse(bookings);

        if (bookings.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No bookings yet. Check slots and tap Book.");
            empty.setPadding(0, 32, 0, 0);
            empty.setTextColor(0xFF6B7280);
            list.addView(empty);
            return;
        }

        for (AppState.Booking b : bookings) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_booking, list, false);

            TextView tvTitle  = row.findViewById(R.id.tv_booking_title);
            TextView tvVisa   = row.findViewById(R.id.tv_booking_visa);
            TextView tvDate   = row.findViewById(R.id.tv_booking_date);
            TextView tvStatus = row.findViewById(R.id.tv_booking_status);
            TextView tvMeta   = row.findViewById(R.id.tv_booking_meta);
            Button   btnView  = row.findViewById(R.id.btn_view_ais);

            tvTitle.setText(b.facilityName + (b.autoBooked ? " [Auto]" : ""));
            tvVisa.setText(b.visaType);
            tvDate.setText(b.date + " at " + b.time);
            tvStatus.setText(b.status.toUpperCase());
            tvStatus.setTextColor("booked".equals(b.status) ? 0xFF065f46 : 0xFF991B1B);
            tvMeta.setText(b.createdAt);

            if (b.confirmationUrl != null && !b.confirmationUrl.isEmpty()) {
                btnView.setVisibility(View.VISIBLE);
                btnView.setOnClickListener(x -> startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(b.confirmationUrl))));
            } else {
                btnView.setVisibility(View.GONE);
            }
            list.addView(row);
        }
    }
}
