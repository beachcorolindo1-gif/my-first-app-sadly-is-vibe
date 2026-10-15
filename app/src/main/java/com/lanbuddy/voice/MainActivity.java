package com.lanbuddy.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 1001;
    private EditText ipInput;
    private EditText portInput;
    private TextView statusLabel;
    private Button toggleBtn;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("LAN Buddy Voice");
        title.setTextSize(24f);
        root.addView(title);

        ipInput = new EditText(this);
        ipInput.setHint("Peer / Multicast IP (e.g. 239.255.42.99 or 192.168.1.50)");
        ipInput.setText("239.255.42.99");
        root.addView(ipInput);

        portInput = new EditText(this);
        portInput.setHint("UDP Port (default 50005)");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText("50005");
        root.addView(portInput);

        statusLabel = new TextView(this);
        statusLabel.setText("Status: Standby (16kHz PCM Mono UDP)");
        statusLabel.setPadding(0, 24, 0, 24);
        root.addView(statusLabel);

        toggleBtn = new Button(this);
        toggleBtn.setText("Start Voice Service");
        toggleBtn.setOnClickListener(v -> {
            if (!isRunning) {
                checkPermissionsAndStart();
            } else {
                stopVoiceService();
            }
        });
        root.addView(toggleBtn);

        setContentView(root);
    }

    private void checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                return;
            }
        }
        startVoiceService();
    }

    private void startVoiceService() {
        String ip = ipInput.getText().toString().trim();
        int port = 50005;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        Intent serviceIntent = new Intent(this, VoiceService.class);
        serviceIntent.putExtra("TARGET_IP", ip);
        serviceIntent.putExtra("UDP_PORT", port);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        isRunning = true;
        statusLabel.setText("Status: Streaming UDP Audio -> " + ip + ":" + port);
        toggleBtn.setText("Stop Voice Service");
    }

    private void stopVoiceService() {
        Intent serviceIntent = new Intent(this, VoiceService.class);
        stopService(serviceIntent);
        isRunning = false;
        statusLabel.setText("Status: Stopped");
        toggleBtn.setText("Start Voice Service");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceService();
            } else {
                Toast.makeText(this, "Microphone permission required for LAN voice", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
