package com.visabot.canada;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginFragment extends Fragment {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        AppState app = AppState.instance;

        LinearLayout formLayout      = v.findViewById(R.id.form_layout);
        LinearLayout connectedLayout = v.findViewById(R.id.connected_layout);
        EditText emailInput          = v.findViewById(R.id.input_email);
        EditText passInput           = v.findViewById(R.id.input_password);
        Button connectBtn            = v.findViewById(R.id.btn_connect);
        Button disconnectBtn         = v.findViewById(R.id.btn_disconnect);
        TextView statusLabel         = v.findViewById(R.id.tv_connected_email);
        TextView apptLabel           = v.findViewById(R.id.tv_appointment_id);
        TextView errorLabel          = v.findViewById(R.id.tv_error);

        Runnable refreshUI = () -> {
            if (app.aisStatus.equals("connected") && app.aisClient != null) {
                formLayout.setVisibility(View.GONE);
                connectedLayout.setVisibility(View.VISIBLE);
                statusLabel.setText(app.getSavedEmail());
                apptLabel.setText(app.aisClient.appointmentId != null
                        ? "App ID: " + app.aisClient.appointmentId : "");
                errorLabel.setVisibility(View.GONE);
            } else {
                formLayout.setVisibility(View.VISIBLE);
                connectedLayout.setVisibility(View.GONE);
                if (app.aisError != null) {
                    errorLabel.setText(app.aisError);
                    errorLabel.setVisibility(View.VISIBLE);
                } else {
                    errorLabel.setVisibility(View.GONE);
                }
            }
        };
        refreshUI.run();

        // Auto-fill saved email
        String savedEmail = app.getSavedEmail();
        if (savedEmail != null) emailInput.setText(savedEmail);

        connectBtn.setOnClickListener(x -> {
            String email = emailInput.getText().toString().trim();
            String pass  = passInput.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            connectBtn.setEnabled(false);
            connectBtn.setText("Connecting...");
            app.aisStatus = "connecting";
            errorLabel.setVisibility(View.GONE);

            exec.execute(() -> {
                AisClient client = new AisClient(email, pass);
                try {
                    client.login();
                    app.aisClient = client;
                    app.aisStatus = "connected";
                    app.aisError  = null;
                    app.saveCreds(email, pass);
                } catch (Exception e) {
                    app.aisStatus = "error";
                    app.aisError  = e.getMessage();
                    app.aisClient = null;
                }
                main.post(() -> {
                    connectBtn.setEnabled(true);
                    connectBtn.setText("Connect to AIS Portal");
                    refreshUI.run();
                });
            });
        });

        disconnectBtn.setOnClickListener(x -> {
            app.clearCreds();
            refreshUI.run();
        });

        // Auto-login on fragment load if credentials saved
        String savedPass = app.getSavedPassword();
        if (savedEmail != null && savedPass != null && app.aisStatus.equals("disconnected")) {
            connectBtn.performClick();
        }
    }
}
