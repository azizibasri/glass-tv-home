package com.gathin.carhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The music widget under the clock: cover art, song and artist, a thin progress line, and previous,
 * play/pause and next. It follows whichever app is playing (Spotify, YouTube Music, the radio, Bluetooth
 * audio from a phone), through Android's media sessions. Tap the song to open that app.
 */
final class NowPlaying extends LinearLayout {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ImageView art;
    private final TextView title, artist;
    private final ImageView playPause;
    private final View progressFill;
    private final FrameLayout progress;
    private final Runnable openMusicApp;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            refresh();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            refresh();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (running) main.postDelayed(this, 1_000);
        }
    };
    private MediaController controller;
    private boolean running, listening;

    NowPlaying(Context ctx, int heightPx, int widthPx, Runnable openMusicApp) {
        super(ctx);
        this.openMusicApp = openMusicApp;
        float d = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        // Narrow: smaller padding and buttons, and no "previous" button, so the song name keeps room.
        boolean narrow = widthPx > 0 && widthPx < 300 * d;
        int pad = Math.round((narrow ? 7 : 10) * d);
        setPadding(pad, pad, pad, pad);
        setBackground(darkPanel(ctx));

        int inner = widthPx > 0 ? widthPx - 2 * pad : Integer.MAX_VALUE;
        int innerH = heightPx - 2 * pad;
        // Tall widget: art and song on top, the buttons on their own row under them, as on CarPlay.
        boolean stacked = !narrow && heightPx >= 110 * d;
        int btnRowH = stacked ? Math.round(innerH * 0.42f) : 0;
        int topH = innerH - btnRowH;
        LinearLayout topRow = this, buttonRow = this;
        if (stacked) {
            setOrientation(VERTICAL);
            topRow = new LinearLayout(ctx);
            topRow.setOrientation(HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            addView(topRow, new LayoutParams(-1, topH));
            buttonRow = new LinearLayout(ctx);
            buttonRow.setOrientation(HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);
            addView(buttonRow, new LayoutParams(-1, btnRowH));
        }
        // A square art as tall as its row, but never more than a quarter of the width.
        int artPx = Math.min(topH, inner / 4);
        // Text and buttons grow with the widget (84 dp is the base size), up to 1.6 times.
        float k = Math.max(1f, Math.min(stacked ? 1.4f : 1.6f, heightPx / (84 * d)));

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * d);
            }
        });
        art.setClipToOutline(true);
        topRow.addView(art, new LayoutParams(artPx, artPx));
        if (narrow) art.setVisibility(GONE);   // narrow screens: the song name needs the room more

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(VERTICAL);
        text.setPadding(Math.round(12 * d), 0, Math.round(8 * d), 0);
        title = line(ctx, 16 * k, true);
        artist = line(ctx, 13 * k, false);
        artist.setAlpha(0.7f);
        text.addView(title);
        text.addView(artist);
        progress = new FrameLayout(ctx);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(2 * d);
        progress.setBackground(track);
        progressFill = new View(ctx);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xE6FFFFFF);
        fill.setCornerRadius(2 * d);
        progressFill.setBackground(fill);
        progress.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        LayoutParams plp = new LayoutParams(-1, Math.round(3 * k * d));
        plp.topMargin = Math.round(8 * k * d);
        text.addView(progress, plp);
        topRow.addView(text, new LayoutParams(0, -2, 1f));
        text.setOnClickListener(v -> openApp());

        int btn = Math.round(Math.min((narrow ? 34 : 44) * k * d, (stacked ? btnRowH : innerH) * 0.8f));
        // The buttons (2 or 3, play a bit larger, with gaps) share what the art and a third for the song
        // name leave.
        if (widthPx > 0 && !stacked) {
            int room = inner - (narrow ? 0 : artPx) - inner / 3;
            btn = Math.max(Math.round(30 * d), Math.min(btn, Math.round(room / (narrow ? 2.5f : 3.65f))));
        }
        if (!narrow) {
            buttonRow.addView(button(ctx, R.drawable.ic_prev, btn, v -> {
                if (controller != null) controller.getTransportControls().skipToPrevious();
            }));
        }
        playPause = button(ctx, R.drawable.ic_play, Math.round(btn * 1.15f), v -> togglePlay());
        buttonRow.addView(playPause);
        buttonRow.addView(button(ctx, R.drawable.ic_next, btn, v -> {
            if (controller != null) controller.getTransportControls().skipToNext();
        }));
        if (stacked) {   // room to spread the buttons out, easier to hit while driving
            for (int i = 0; i < buttonRow.getChildCount(); i++) {
                LayoutParams lp = (LayoutParams) buttonRow.getChildAt(i).getLayoutParams();
                lp.leftMargin = lp.rightMargin = btn / 2;
            }
        }
        this.buttonRow = buttonRow;
        showEmpty();
    }

    /** The row that holds the buttons: this widget itself, or its lower row when stacked. */
    private final LinearLayout buttonRow;

    /** The dark glass box behind the music widget, also used behind the dashboard clock. */
    static GradientDrawable darkPanel(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33000000);
        bg.setCornerRadius(18 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x1FFFFFFF);
        return bg;
    }

    private static TextView line(Context ctx, float sp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private static ImageView button(Context ctx, int icon, int size, View.OnClickListener l) {
        ImageView b = new ImageView(ctx);
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER);
        int pad = size / 5;
        b.setPadding(pad, pad, pad, pad);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable ripple = new GradientDrawable();
        ripple.setShape(GradientDrawable.OVAL);
        ripple.setColor(0x14FFFFFF);
        b.setBackground(ripple);
        b.setOnClickListener(l);
        b.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).start();
            else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        LayoutParams lp = new LayoutParams(size, size);
        lp.leftMargin = size / 6;
        b.setLayoutParams(lp);
        return b;
    }

    private ComponentName listener() {
        return new ComponentName(getContext(), MediaListener.class);
    }

    private boolean hasAccess() {
        String enabled = Settings.Secure.getString(getContext().getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(listener().flattenToString());
    }

    /** Follows the music while Home is on screen. */
    void start() {
        running = true;
        if (!hasAccess()) {
            showNoAccess();
            return;
        }
        MediaSessionManager msm = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessions, listener(), main);
                listening = true;
            }
        } catch (SecurityException e) {
            showNoAccess();
            return;
        }
        pick();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
        if (listening) {
            try {
                ((MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE))
                        .removeOnActiveSessionsChangedListener(sessions);
            } catch (RuntimeException ignored) {
            }
            listening = false;
        }
        setController(null);
    }

    /** The session that is playing, else the most recent one. */
    private void pick() {
        MediaController best = null;
        try {
            List<MediaController> list = ((MediaSessionManager) getContext()
                    .getSystemService(Context.MEDIA_SESSION_SERVICE)).getActiveSessions(listener());
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        } catch (SecurityException e) {
            showNoAccess();
            return;
        }
        setController(best);
    }

    private void setController(MediaController c) {
        if (controller != null && (c == null || !controller.getSessionToken().equals(c.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        boolean changed = controller == null || c == null || !controller.getSessionToken().equals(c.getSessionToken());
        controller = c;
        if (c != null && changed) c.registerCallback(callback, main);
        refresh();
    }

    private void refresh() {
        if (controller == null) {
            showEmpty();
            return;
        }
        MediaMetadata m = controller.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence a = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null && m != null) a = m.getText(MediaMetadata.METADATA_KEY_ALBUM);
        Bitmap cover = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null && m != null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        title.setText(t != null ? t : appLabel());
        artist.setText(a != null ? a : appLabel());
        if (cover != null) {
            art.setImageBitmap(cover);
            art.setBackground(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            noteArt();
        }
        PlaybackState s = controller.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        playPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setControlsEnabled(true);
        updateProgress();
    }

    private void updateProgress() {
        int w = progress.getWidth();
        float frac = 0f;
        if (controller != null) {
            PlaybackState s = controller.getPlaybackState();
            MediaMetadata m = controller.getMetadata();
            long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (s != null && dur > 0) {
                long pos = s.getPosition();
                if (s.getState() == PlaybackState.STATE_PLAYING) {
                    pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                }
                frac = Math.max(0f, Math.min(1f, pos / (float) dur));
            }
        }
        progress.setVisibility(controller == null ? INVISIBLE : VISIBLE);
        ViewGroupParams.setWidth(progressFill, Math.round(w * frac));
    }

    /** Small helper so a width change only re-lays out when it changed. */
    private static final class ViewGroupParams {
        static void setWidth(View v, int w) {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp.width != w) {
                lp.width = w;
                v.setLayoutParams(lp);
            }
        }
    }

    private void togglePlay() {
        if (controller == null) {
            openMusicApp.run();
            return;
        }
        PlaybackState s = controller.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    private void openApp() {
        if (controller == null) {
            if (!hasAccess()) {
                try {
                    getContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException ignored) {
                }
            } else {
                openMusicApp.run();
            }
            return;
        }
        PendingIntent pi = controller.getSessionActivity();
        try {
            if (pi != null) {
                pi.send();
                return;
            }
        } catch (PendingIntent.CanceledException ignored) {
        }
        Intent i = getContext().getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
        if (i != null) getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private CharSequence appLabel() {
        if (controller == null) return "";
        try {
            android.content.pm.PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0));
        } catch (Exception e) {
            return controller.getPackageName();
        }
    }

    private void noteArt() {
        art.setImageResource(R.drawable.ic_music);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = art.getLayoutParams().width / 4;
        art.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF375F, 0xFFBF5AF2});
        art.setBackground(g);
    }

    private void showEmpty() {
        title.setText(R.string.nothing_playing);
        artist.setText(R.string.tap_to_play);
        noteArt();
        playPause.setImageResource(R.drawable.ic_play);
        setControlsEnabled(true);
        progress.setVisibility(INVISIBLE);
    }

    private void showNoAccess() {
        title.setText(R.string.music_needs_access);
        artist.setText(R.string.music_access_hint);
        noteArt();
        setControlsEnabled(false);
        progress.setVisibility(INVISIBLE);
    }

    private void setControlsEnabled(boolean on) {
        for (int i = 0; i < buttonRow.getChildCount(); i++) {
            View v = buttonRow.getChildAt(i);
            if (v == art) continue;
            if (v instanceof ImageView) {
                v.setEnabled(on);
                v.setAlpha(on ? 1f : 0.35f);
            }
        }
    }
}
