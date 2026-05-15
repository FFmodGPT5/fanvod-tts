package pl.fanvod.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class SpeakReceiver extends BroadcastReceiver {

    private static final String TAG    = "FanVodTTS";
    private static final String ACTION = "pl.fanvod.tts.SPEAK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;

        String text = intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "SpeakReceiver: brak tekstu w intencie");
            return;
        }

        Log.i(TAG, "SpeakReceiver: odebrano tekst chars=" + text.length());

        // Jeśli TtsService już działa — użyj jego instancji TTS bezpośrednio
        if (TtsService.ttsReady && TtsService.ttsInstance != null) {
            TtsService.speak(text);
            return;
        }

        // TtsService nie działa — uruchom go z tekstem przez startService
        Intent serviceIntent = new Intent(context, TtsService.class);
        serviceIntent.setAction(ACTION);
        serviceIntent.putExtra("text", text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
