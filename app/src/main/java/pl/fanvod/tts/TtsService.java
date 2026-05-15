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

import java.util.Locale;

public class TtsService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG        = "FanVodTTS";
    private static final int    NOTIF_ID   = 1;
    private static final String CHANNEL_ID = "fanvod_tts";

    // Instancja TTS dostępna statycznie dla SpeakReceiver
    static TextToSpeech ttsInstance;
    static boolean      ttsReady = false;

    // -------------------------------------------------------------------------
    // Cykl życia serwisu
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        ttsInstance = new TextToSpeech(this, this);
        Log.i(TAG, "TtsService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Obsługa intenta SPEAK bezpośrednio w serwisie (alternatywa dla receivera)
        if (intent != null && "pl.fanvod.tts.SPEAK".equals(intent.getAction())) {
            String text = intent.getStringExtra("text");
            if (text != null && !text.isEmpty() && ttsReady) {
                speak(text);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        ttsReady = false;
        if (ttsInstance != null) {
            ttsInstance.stop();
            ttsInstance.shutdown();
            ttsInstance = null;
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
        int result = ttsInstance.setLanguage(new Locale("pl", "PL"));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Polski TTS niedostepny — uzycie domyslnego");
            ttsInstance.setLanguage(Locale.getDefault());
        }
        ttsReady = true;
        Log.i(TAG, "TTS gotowy");
    }

    // -------------------------------------------------------------------------
    // Synteza mowy
    // -------------------------------------------------------------------------

    static void speak(String text) {
        if (ttsInstance == null || !ttsReady) {
            Log.w("FanVodTTS", "speak() wywolane ale TTS nie gotowy");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ttsInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "fanvod_" + System.currentTimeMillis());
        } else {
            //noinspection deprecation
            ttsInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
        Log.i("FanVodTTS", "Speaking chars=" + text.length()
                + " preview=" + text.substring(0, Math.min(60, text.length())));
    }

    // -------------------------------------------------------------------------
    // Notyfikacja foreground
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FanVod TTS", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Serwis głosu FanVod");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
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
                .setOngoing(true)
                .build();
    }
}
