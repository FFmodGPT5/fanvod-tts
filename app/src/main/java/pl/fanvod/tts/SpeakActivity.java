package pl.fanvod.tts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SpeakActivity extends Activity {

    private static final String TAG = "FanVodTTS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String text = null;

        // Tekst może przyjść jako extra lub jako data URI
        if (intent != null) {
            text = intent.getStringExtra("text");
            if (text == null && intent.getData() != null) {
                text = intent.getData().getQueryParameter("text");
            }
        }

        if (text != null && !text.trim().isEmpty()) {
            Log.i(TAG, "SpeakActivity: tekst=" + text.substring(0, Math.min(60, text.length())));
            if (TtsService.ttsReady && TtsService.ttsInstance != null) {
                TtsService.speak(text);
            } else {
                // TtsService nie gotowy — uruchom go z tekstem
                Intent serviceIntent = new Intent(this, TtsService.class);
                serviceIntent.setAction("pl.fanvod.tts.SPEAK");
                serviceIntent.putExtra("text", text);
                startForegroundService(serviceIntent);
            }
        } else {
            Log.w(TAG, "SpeakActivity: brak tekstu w intencie");
        }

        finish(); // zamknij Activity natychmiast — niewidoczna dla użytkownika
    }
}
