package com.visabot.canada;

import android.os.Bundle;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String WORK_TAG = "slot_check";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Notifier.createChannel(this);
        scheduleWorker(this);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);

        // Show login fragment by default
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
        }

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            androidx.fragment.app.Fragment frag;
            if (id == R.id.nav_login)    frag = new LoginFragment();
            else if (id == R.id.nav_slots)    frag = new SlotsFragment();
            else if (id == R.id.nav_monitors) frag = new MonitorsFragment();
            else                              frag = new BookingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, frag).commit();
            return true;
        });
    }

    public static void scheduleWorker(Context ctx) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(SlotWorker.class, 15, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, work);
    }
}
