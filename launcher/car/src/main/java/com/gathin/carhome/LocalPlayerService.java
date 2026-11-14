package com.gathin.carhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

/**
 * Glass Car's own music player, for the music files on the unit and USB sticks: plays them in order, keeps
 * playing while another app is on screen (it runs as a media service with a notification, as every music
 * player must), and publishes a media session, so the music widget, the steering-wheel buttons and the
 * unit's own media controls all work with it. Stops when the music stops, so it doesn't keep running.
 */
public final class LocalPlayerService extends Service {
    static final String ACTION_PLAY = "com.gathin.carhome.PLAY_LOCAL";
    private static final String TAG = "CarHome.Player";
    private static final String CHANNEL = "music";
    private static final int NOTE_ID = 7;

    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private List<LocalMusic.Song> songs;
    private int index;
    private boolean prepared;
    private SharedPreferences prefs;

    static void play(Context ctx) {
        Intent i = new Intent(ctx, LocalPlayerService.class).setAction(ACTION_PLAY);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("local_music", MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        session = new MediaSession(this, "GlassCarMusic");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                start(index + 1);
            }

            @Override
            public void onSkipToPrevious() {
                // Like most players: back to the start, or the song before if just started.
                if (player != null && prepared && player.getCurrentPosition() > 3_000) player.seekTo(0);
                else start(index - 1);
                publish();
            }

            @Override
            public void onStop() {
                stopAll();
            }

            @Override
            public void onSeekTo(long pos) {
                if (player != null && prepared) player.seekTo((int) pos);
                publish();
            }
        });
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) {
            session.setSessionActivity(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
        }
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTE_ID, notification(false));
        if (songs == null) songs = LocalMusic.songs(this);
        if (songs.isEmpty()) {
            Log.i(TAG, "no music files found");
            stopAll();
            return START_NOT_STICKY;
        }
        if (player == null) start(prefs.getInt("index", 0));
        else resume();
        return START_NOT_STICKY;
    }

    private void start(int i) {
        if (songs == null || songs.isEmpty()) return;
        index = ((i % songs.size()) + songs.size()) % songs.size();
        prefs.edit().putInt("index", index).apply();
        LocalMusic.Song s = songs.get(index);
        if (player != null) player.release();
        prepared = false;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        try {
            player.setDataSource(this, s.uri);
        } catch (Exception e) {
            Log.w(TAG, "can't play " + s.title + ": " + e);
            start(index + 1);
            return;
        }
        player.setOnPreparedListener(mp -> {
            prepared = true;
            if (requestFocus()) mp.start();
            publish();
        });
        player.setOnCompletionListener(mp -> start(index + 1));
        player.setOnErrorListener((mp, what, extra) -> {
            start(index + 1);
            return true;
        });
        player.prepareAsync();
        session.setMetadata(metadata(s));
        publish();
    }

    private void resume() {
        if (player == null) {
            start(index);
            return;
        }
        if (prepared && !player.isPlaying() && requestFocus()) player.start();
        publish();
    }

    private void pause() {
        if (player != null && prepared && player.isPlaying()) player.pause();
        publish();
    }

    private boolean requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (focus == null) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(change -> {
                            // Navigation voice, a call or the radio: pause; carry on when it is ours again.
                            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
                            else if (change == AudioManager.AUDIOFOCUS_GAIN) resume();
                        }).build();
            }
            return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return true;
    }

    private MediaMetadata metadata(LocalMusic.Song s) {
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs);
        Bitmap art = cover(s);
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        return b.build();
    }

    /** The cover picture inside the music file, kept small. */
    private Bitmap cover(LocalMusic.Song s) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, s.uri);
            byte[] pic = r.getEmbeddedPicture();
            if (pic == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            o.inSampleSize = 1;
            while (o.outWidth / o.inSampleSize > 300) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void publish() {
        boolean playing = player != null && prepared && player.isPlaying();
        long pos = player != null && prepared ? player.getCurrentPosition() : 0;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f)
                .build());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTE_ID, notification(playing));
        // Paused: the service no longer needs to stay in front, but keeps its place in the song.
        if (!playing) stopForeground(false);
        else startForeground(NOTE_ID, notification(true));
    }

    private Notification notification(boolean playing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.music_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        LocalMusic.Song s = songs != null && !songs.isEmpty() ? songs.get(index) : null;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(s != null ? s.title : getString(R.string.app_name))
                .setContentText(s != null ? s.artist : "")
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()));
        if (session.getController().getSessionActivity() != null) {
            b.setContentIntent(session.getController().getSessionActivity());
        }
        return b.build();
    }

    private void stopAll() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (focus != null && Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focus);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (player != null) player.release();
        session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
