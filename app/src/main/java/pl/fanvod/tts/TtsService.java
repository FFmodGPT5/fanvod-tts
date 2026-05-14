package pl.fanvod.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;

public class TtsService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG        = "FanVodTTS";
    private static final int    PORT       = 7799;
    private static final int    NOTIF_ID   = 1;
    private static final String CHANNEL_ID = "fanvod_tts";

    private TextToSpeech tts;
    private ServerSocket serverSocket;
    private Thread       serverThread;

    private volatile boolean running  = false;
    private volatile boolean ttsReady = false;

    // -------------------------------------------------------------------------
    // Cykl życia serwisu
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        tts = new TextToSpeech(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY — Android restartuje serwis jeśli go ubije
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Inicjalizacja TTS
    // -------------------------------------------------------------------------

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed status=" + status);
            return;
        }
        int result = tts.setLanguage(new Locale("pl", "PL"));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Brak polskiego TTS — uzycie domyslnego jezyka systemu");
            tts.setLanguage(Locale.getDefault());
        }
        ttsReady = true;
        Log.i(TAG, "TTS zainicjalizowany OK");
        startHttpServer();
    }

    // -------------------------------------------------------------------------
    // HTTP serwer na localhost:7799
    // -------------------------------------------------------------------------

    private void startHttpServer() {
        running = true;
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT, 10,
                            InetAddress.getByName("127.0.0.1"));
                    Log.i(TAG, "HTTP serwer nasłuchuje na 127.0.0.1:" + PORT);
                    while (running) {
                        try {
                            Socket client = serverSocket.accept();
                            handleClient(client);
                        } catch (IOException e) {
                            if (running) {
                                Log.e(TAG, "Accept error: " + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "ServerSocket error: " + e.getMessage());
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    // -------------------------------------------------------------------------
    // Obsługa pojedynczego żądania HTTP
    // -------------------------------------------------------------------------

    private void handleClient(Socket client) {
        try {
            byte[] buf = new byte[8192];
            int len = client.getInputStream().read(buf);
            if (len <= 0) {
                sendResponse(client, 400, "Bad Request", "empty");
                return;
            }
            String request   = new String(buf, 0, len, "UTF-8");
            String firstLine = request.split("\r?\n")[0];

            // Obsługujemy tylko GET /speak
            if (!firstLine.startsWith("GET /speak")) {
                sendResponse(client, 404, "Not Found", "unknown path");
                return;
            }

            String text = extractParam(firstLine, "text");
            if (text == null || text.trim().isEmpty()) {
                sendResponse(client, 400, "Bad Request", "missing text");
                return;
            }

            if (!ttsReady) {
                sendResponse(client, 503, "Service Unavailable", "tts not ready");
                return;
            }

            // QUEUE_FLUSH — nowy tekst przerywa poprzedni
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                        "fanvod_" + System.currentTimeMillis());
            } else {
                //noinspection deprecation
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }

            sendResponse(client, 200, "OK", "speaking");
            Log.i(TAG, "Speaking: " + text.substring(0, Math.min(80, text.length())));

        } catch (Exception e) {
            Log.e(TAG, "handleClient error: " + e.getMessage());
            try {
                sendResponse(client, 500, "Internal Server Error", "error");
            } catch (Exception ignored) {}
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Pomocnicze: wyciągnij parametr z query string
    // -------------------------------------------------------------------------

    private String extractParam(String requestLine, String param) {
        try {
            int qMark = requestLine.indexOf('?');
            int space = requestLine.lastIndexOf(' ');
            if (qMark < 0) return null;
            String query = requestLine.substring(qMark + 1, space > qMark ? space : requestLine.length());
            for (String part : query.split("&")) {
                String prefix = param + "=";
                if (part.startsWith(prefix)) {
                    return URLDecoder.decode(part.substring(prefix.length()), "UTF-8");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "extractParam error: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Pomocnicze: wyślij odpowiedź HTTP
    // -------------------------------------------------------------------------

    private void sendResponse(Socket client, int code, String status, String body)
            throws IOException {
        byte[] bodyBytes = body.getBytes("UTF-8");
        String header = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(header.getBytes("UTF-8"));
        out.write(bodyBytes);
        out.flush();
    }

    // -------------------------------------------------------------------------
    // Notyfikacja foreground (wymagana przez Android od API 26)
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FanVod TTS", NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Serwis głosu FanVod");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            //noinspection deprecation
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("FanVod TTS")
                .setContentText("Serwis głosu aktywny")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }
}
