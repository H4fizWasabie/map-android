package app.map.android;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.TextView;

public final class AudioFocusProbeActivity extends Activity {
    public static final String EXTRA_PERMANENT_FOCUS = "permanent_focus";

    private AudioManager audioManager;
    private AudioFocusRequest request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = getSystemService(AudioManager.class);
        TextView label = new TextView(this);
        label.setText("Temporary audio focus owner");
        setContentView(label);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || request != null) return;
        int focusGain = getIntent().getBooleanExtra(EXTRA_PERMANENT_FOCUS, false)
                ? AudioManager.AUDIOFOCUS_GAIN
                : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
        request = new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audioManager.requestAudioFocus(request);
    }

    @Override
    protected void onDestroy() {
        if (request != null) audioManager.abandonAudioFocusRequest(request);
        super.onDestroy();
    }
}
