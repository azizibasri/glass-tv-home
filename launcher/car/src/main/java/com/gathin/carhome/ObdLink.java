package com.gathin.carhome;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Talks to a Bluetooth OBD-II adapter (ELM327 type, e.g. Carista) on its own thread, only while Home is on
 * screen: connects to the paired adapter, sets it up, then asks for speed and rpm about 4 times a second and
 * coolant, fuel level and torque every few seconds. Reports values and its state; reconnects when the
 * connection drops (engine off, adapter unplugged), waiting a little longer each time.
 */
final class ObdLink {
    /** The standard Bluetooth serial port, which ELM327 adapters use. */
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "CarHome.Obd";

    static final int OFF = 0, CONNECTING = 1, CONNECTED = 2, FAILED = 3, NO_BLUETOOTH = 4, NOT_PAIRED = 5;

    interface Listener {
        /** {@code request} is one of ObdParser's PIDs; called on the link's thread. */
        void onValue(String request, int[] data);

        void onState(int state, String detail);
    }

    private final String address;
    private final Listener listener;
    private volatile boolean running;
    private volatile BluetoothSocket socket;
    private Thread thread;

    ObdLink(String address, Listener listener) {
        this.address = address;
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "car-obd");
        thread.start();
    }

    void stop() {
        running = false;
        closeQuietly();
        if (thread != null) thread.interrupt();
        listener.onState(OFF, "");
    }

    private void loop() {
        long backoff = 2_000;
        while (running) {
            try {
                connect();
                backoff = 2_000;
                poll();
            } catch (IOException | SecurityException e) {
                if (!running) break;
                Log.i(TAG, "OBD connection failed: " + e);
                listener.onState(FAILED, String.valueOf(e.getMessage()));
            } catch (InterruptedException e) {
                break;
            } finally {
                closeQuietly();
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(30_000, backoff * 2);
        }
    }

    @SuppressLint("MissingPermission")
    private void connect() throws IOException {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null || !bt.isEnabled()) {
            listener.onState(NO_BLUETOOTH, "");
            throw new IOException("Bluetooth is off or missing");
        }
        BluetoothDevice dev = address == null ? null : bt.getRemoteDevice(address);
        if (dev == null || dev.getBondState() != BluetoothDevice.BOND_BONDED) {
            listener.onState(NOT_PAIRED, "");
            throw new IOException("adapter not paired");
        }
        listener.onState(CONNECTING, dev.getName());
        bt.cancelDiscovery();
        BluetoothSocket s;
        try {
            s = dev.createRfcommSocketToServiceRecord(SPP);
            socket = s;
            s.connect();
        } catch (IOException first) {
            // Some cheap adapters only take an insecure connection.
            closeQuietly();
            s = dev.createInsecureRfcommSocketToServiceRecord(SPP);
            socket = s;
            s.connect();
        }
        // Reset, no echo, no line feeds, no spaces, no headers, find the car's protocol by itself.
        for (String cmd : new String[]{"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"}) ask(cmd, 3_000);
        listener.onState(CONNECTED, dev.getName());
    }

    private void poll() throws IOException, InterruptedException {
        String[] slow = {ObdParser.COOLANT, ObdParser.FUEL_LEVEL, ObdParser.TORQUE_PERCENT,
                ObdParser.TORQUE_REFERENCE, ObdParser.ODOMETER};
        int round = 0;
        while (running) {
            report(ObdParser.SPEED);
            report(ObdParser.RPM);
            if (round % 3 == 0) report(slow[(round / 3) % slow.length]);
            round++;
            Thread.sleep(150);
        }
    }

    private void report(String pid) throws IOException {
        String reply = ask(pid, 2_000);
        int[] data = ObdParser.data(reply, pid);
        if (data != null) listener.onValue(pid, data);
    }

    /** Sends one command and reads up to the adapter's ">" prompt. */
    private String ask(String cmd, long timeoutMs) throws IOException {
        BluetoothSocket s = socket;
        if (s == null) throw new IOException("closed");
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        out.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long until = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < until) {
            if (in.available() > 0) {
                int c = in.read();
                if (c < 0) throw new IOException("adapter closed the connection");
                if (c == '>') return sb.toString();
                sb.append((char) c);
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new IOException("stopped");
                }
            }
        }
        return sb.toString();
    }

    private void closeQuietly() {
        BluetoothSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }
}
