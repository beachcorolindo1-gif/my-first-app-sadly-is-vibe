package com.lanbuddy.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceService extends Service {
    private static final String CHANNEL_ID = "lanbuddy_voice_channel";
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SIZE = 640; // 20ms of 16-bit mono PCM at 16kHz

    private final AtomicBoolean active = new AtomicBoolean(false);
    private Thread txThread;
    private Thread rxThread;
    private DatagramSocket udpSocket;
    private WifiManager.MulticastLock multicastLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String targetIp = intent != null ? intent.getStringExtra("TARGET_IP") : "239.255.42.99";
        int udpPort = intent != null ? intent.getIntExtra("UDP_PORT", 50005) : 50005;

        startForegroundNotification(targetIp, udpPort);

        if (active.compareAndSet(false, true)) {
            acquireMulticastLock();
            startAudioThreads(targetIp, udpPort);
        }
        return START_STICKY;
    }

    private void startForegroundNotification(String ip, int port) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LAN Buddy Voice Active Stream",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("LAN Buddy Voice Active")
                    .setContentText("UDP Audio Stream on " + ip + ":" + port)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .build();
            startForeground(1, notification);
        }
    }

    private void acquireMulticastLock() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("lanbuddy_multicast_lock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
        }
    }

    private void startAudioThreads(final String targetIp, final int udpPort) {
        try {
            udpSocket = new DatagramSocket(udpPort);
            udpSocket.setBroadcast(true);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Transmit Thread (Microphone -> UDP)
        txThread = new Thread(() -> {
            int minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            AudioRecord recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, FRAME_SIZE * 4)
            );

            byte[] buffer = new byte[FRAME_SIZE];
            try {
                InetAddress dest = InetAddress.getByName(targetIp);
                recorder.startRecording();
                while (active.get()) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0 && udpSocket != null && !udpSocket.isClosed()) {
                        DatagramPacket packet = new DatagramPacket(buffer, read, dest, udpPort);
                        udpSocket.send(packet);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    recorder.stop();
                    recorder.release();
                } catch (Exception ignored) {}
            }
        }, "LANBuddy-TX");

        // Receive Thread (UDP -> Speaker)
        rxThread = new Thread(() -> {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            AudioTrack speaker = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    Math.max(minBuf, FRAME_SIZE * 4),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            byte[] rxBuf = new byte[FRAME_SIZE];
            speaker.play();
            try {
                while (active.get() && udpSocket != null && !udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(rxBuf, rxBuf.length);
                    udpSocket.receive(packet);
                    speaker.write(packet.getData(), 0, packet.getLength());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    speaker.stop();
                    speaker.release();
                } catch (Exception ignored) {}
            }
        }, "LANBuddy-RX");

        txThread.start();
        rxThread.start();
    }

    @Override
    public void onDestroy() {
        active.set(false);
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
