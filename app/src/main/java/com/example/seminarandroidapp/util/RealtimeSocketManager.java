package com.example.seminarandroidapp.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.seminarandroidapp.api.ApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RealtimeSocketManager {
    public interface Listener {
        void onRealtimeEvent(JSONObject event);
        void onRealtimeConnected();
    }

    private static final RealtimeSocketManager INSTANCE = new RealtimeSocketManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private OkHttpClient client;
    private Context appContext;
    private WebSocket webSocket;
    private Runnable reconnectRunnable;
    private boolean foreground;
    private volatile boolean connected;
    private int reconnectAttempt;

    private RealtimeSocketManager() {
    }

    public static RealtimeSocketManager getInstance() {
        return INSTANCE;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public synchronized void onForeground(Context context) {
        appContext = context.getApplicationContext();
        foreground = true;
        connectIfNeeded(appContext);
    }

    public synchronized void onBackground() {
        foreground = false;
        connected = false;
        reconnectAttempt = 0;
        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            current.close(1000, "App backgrounded");
        }
    }

    public synchronized void onAuthStateChanged(Context context) {
        if (!Prefs.isLoggedIn(context)) {
            connected = false;
            reconnectAttempt = 0;
            if (reconnectRunnable != null) {
                mainHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }
            WebSocket current = webSocket;
            webSocket = null;
            if (current != null) {
                current.close(1000, "Signed out");
            }
            return;
        }
        if (foreground) {
            connectIfNeeded(context.getApplicationContext());
        }
    }

    private synchronized void connectIfNeeded(Context context) {
        if (!foreground || webSocket != null) {
            return;
        }
        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }

        String token = Prefs.getToken(context);
        if (token == null || token.isEmpty()) {
            return;
        }

        if (client == null) {
            client = ApiClient.getOkHttpClient(context)
                    .newBuilder()
                    .pingInterval(30, TimeUnit.SECONDS)
                    .build();
        }

        Request request = new Request.Builder()
                .url(ApiClient.getWebSocketUrl())
                .header("Authorization", "Bearer " + token)
                .build();

        WebSocketListener socketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                synchronized (RealtimeSocketManager.this) {
                    if (webSocket != socket || !foreground) {
                        socket.close(1000, "Connection no longer needed");
                        return;
                    }
                    connected = true;
                    reconnectAttempt = 0;
                    reconnectRunnable = null;
                }
                dispatchConnected();
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                if (socket != currentSocket()) {
                    return;
                }
                try {
                    JSONObject event = new JSONObject(text);
                    if (event.has("type")) {
                        dispatchEvent(event);
                    }
                } catch (JSONException ignored) {
                    // Ignore malformed or unsupported frames.
                }
            }

            @Override
            public void onClosing(WebSocket socket, int code, String reason) {
                socket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket socket, int code, String reason) {
                handleDisconnect(socket);
            }

            @Override
            public void onFailure(WebSocket socket, Throwable error, Response response) {
                handleDisconnect(socket);
            }
        };

        webSocket = client.newWebSocket(request, socketListener);
    }

    private synchronized WebSocket currentSocket() {
        return webSocket;
    }

    private void handleDisconnect(WebSocket socket) {
        synchronized (this) {
            if (webSocket != socket) {
                return;
            }
            webSocket = null;
            connected = false;
            scheduleReconnect();
        }
    }

    private synchronized void scheduleReconnect() {
        if (!foreground || reconnectRunnable != null) {
            return;
        }

        int exponent = Math.min(reconnectAttempt++, 5);
        long delayMillis = Math.min(30_000L, 1_000L << exponent);
        reconnectRunnable = () -> {
            synchronized (RealtimeSocketManager.this) {
                reconnectRunnable = null;
                if (appContext != null) {
                    connectIfNeeded(appContext);
                }
            }
        };
        mainHandler.postDelayed(reconnectRunnable, delayMillis);
    }

    private void dispatchConnected() {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onRealtimeConnected();
            }
        });
    }

    private void dispatchEvent(JSONObject event) {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onRealtimeEvent(event);
            }
        });
    }
}
