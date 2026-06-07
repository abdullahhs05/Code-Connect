package com.codeconnect.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Java Sockets-based local event bus for real-time message broadcast across windows
 * (and across processes on the same machine if multiple clients run).
 *
 * Wire protocol: line-delimited UTF-8 plain text.
 * Each line: TOPIC|PAYLOAD
 *   TOPIC examples: "ROOM:42", "USER:7"
 *   PAYLOAD: arbitrary text (no newlines), typically a small descriptor
 *
 * Use:
 *   LocalEventBus.getInstance().subscribe("ROOM:42", payload -> ...);
 *   LocalEventBus.getInstance().publish("ROOM:42", "newmsg:101");
 *
 * The first instance to bind starts a ServerSocket; subsequent instances on the
 * same machine become clients of that server. If binding fails (port taken by
 * another client), this instance becomes a client too.
 */
public final class LocalEventBus {

    private static final int DEFAULT_PORT = 39817;

    /** Server address other clients try first; defaults to localhost. Override with -Dcc.bus.host or BUS_HOST env. */
    private static final String BUS_HOST = pick("cc.bus.host", "BUS_HOST", "127.0.0.1");
    private static final int    BUS_PORT = parseInt(pick("cc.bus.port", "BUS_PORT", String.valueOf(DEFAULT_PORT)), DEFAULT_PORT);

    private static String pick(String sys, String env, String def) {
        String v = System.getProperty(sys);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(env);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static volatile LocalEventBus INSTANCE;

    public static LocalEventBus getInstance() {
        if (INSTANCE == null) {
            synchronized (LocalEventBus.class) {
                if (INSTANCE == null) INSTANCE = new LocalEventBus();
            }
        }
        return INSTANCE;
    }

    private final Map<String, List<Consumer<String>>> subs = new HashMap<>();
    private final List<PrintWriter> peers = new CopyOnWriteArrayList<>();
    private boolean isServer = false;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter clientOut;

    private LocalEventBus() {
        // 1) If a remote server is reachable at BUS_HOST:BUS_PORT, prefer to join it as a client.
        //    This means a single "hub" machine on the LAN can serve multiple desktops.
        if (!isLoopback(BUS_HOST) && tryConnectAsClient(BUS_HOST, BUS_PORT)) {
            isServer = false;
            System.out.println("[Bus] Connected as client to " + BUS_HOST + ":" + BUS_PORT);
            return;
        }
        // 2) Otherwise, try to become the server (binding 0.0.0.0 so LAN peers can reach us).
        try {
            serverSocket = new ServerSocket(BUS_PORT, 50, java.net.InetAddress.getByName("0.0.0.0"));
            isServer = true;
            startServerLoop();
            System.out.println("[Bus] Server listening on 0.0.0.0:" + BUS_PORT);
        } catch (IOException e) {
            // 3) Fall back to client mode against loopback (another instance on this PC).
            isServer = false;
            if (!tryConnectAsClient("127.0.0.1", BUS_PORT)) {
                System.err.println("[Bus] Standalone mode (no server reachable).");
            } else {
                System.out.println("[Bus] Connected as client to 127.0.0.1:" + BUS_PORT);
            }
        }
    }

    private static boolean isLoopback(String host) {
        return host == null || host.isEmpty() || "localhost".equalsIgnoreCase(host)
                || host.startsWith("127.") || "::1".equals(host);
    }

    private void startServerLoop() {
        Thread t = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket peer = serverSocket.accept();
                    handlePeer(peer);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) e.printStackTrace();
                    break;
                }
            }
        }, "EventBus-Server");
        t.setDaemon(true);
        t.start();
    }

    private void handlePeer(Socket sock) {
        try {
            PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);
            peers.add(pw);
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // Re-broadcast to all other peers and dispatch locally
                        for (PrintWriter other : peers) if (other != pw) other.println(line);
                        dispatchLocal(line);
                    }
                } catch (IOException ignored) {}
                finally { peers.remove(pw); }
            }, "EventBus-PeerReader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private boolean tryConnectAsClient(String host, int port) {
        try {
            Socket sock = new Socket();
            sock.connect(new java.net.InetSocketAddress(host, port), 800);
            clientSocket = sock;
            clientOut = new PrintWriter(new java.io.OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) dispatchLocal(line);
                } catch (IOException ignored) {}
            }, "EventBus-ClientReader");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (IOException e) {
            clientOut = null;
            clientSocket = null;
            return false;
        }
    }

    private void dispatchLocal(String line) {
        int sep = line.indexOf('|');
        if (sep <= 0) return;
        String topic = line.substring(0, sep);
        String payload = line.substring(sep + 1);
        List<Consumer<String>> handlers;
        synchronized (subs) {
            handlers = subs.get(topic);
            if (handlers == null) return;
            handlers = new ArrayList<>(handlers);
        }
        for (Consumer<String> h : handlers) {
            try { h.accept(payload); } catch (Exception ignored) {}
        }
    }

    public void subscribe(String topic, Consumer<String> handler) {
        synchronized (subs) {
            subs.computeIfAbsent(topic, k -> new ArrayList<>()).add(handler);
        }
    }

    public void unsubscribe(String topic, Consumer<String> handler) {
        synchronized (subs) {
            List<Consumer<String>> lst = subs.get(topic);
            if (lst != null) lst.remove(handler);
        }
    }

    public void publish(String topic, String payload) {
        String line = topic + "|" + payload;
        if (isServer) {
            // Fan-out to peers + dispatch locally
            for (PrintWriter p : peers) p.println(line);
            dispatchLocal(line);
        } else if (clientOut != null) {
            clientOut.println(line);
            // Server will echo back to us via reader thread; no local dispatch here to avoid duplicates.
        } else {
            // Standalone fallback
            dispatchLocal(line);
        }
    }

    public boolean isServer() { return isServer; }

    public void shutdown() {
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }
}
