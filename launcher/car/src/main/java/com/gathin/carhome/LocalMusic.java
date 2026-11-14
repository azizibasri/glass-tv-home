package com.gathin.carhome;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** The music files on the unit and plugged-in USB sticks or SD cards, as Android's media library lists them. */
final class LocalMusic {
    static final class Song {
        final long id, albumId, durationMs;
        final String title, artist, album;
        final Uri uri;

        Song(long id, String title, String artist, String album, long albumId, long durationMs) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.albumId = albumId;
            this.durationMs = durationMs;
            this.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
    }

    private LocalMusic() {
    }

    /** All songs (not ringtones or voice notes), by title. Empty when there are none or no permission. */
    static List<Song> songs(Context ctx) {
        List<Song> out = new ArrayList<>();
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION};
        try (Cursor c = ctx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (c == null) return out;
            while (c.moveToNext()) {
                String artist = c.getString(2);
                if (artist == null || artist.equals("<unknown>")) artist = "";
                out.add(new Song(c.getLong(0), c.getString(1), artist, c.getString(3), c.getLong(4), c.getLong(5)));
            }
        } catch (RuntimeException e) {
            // No permission yet, or no media library: no songs.
        }
        return out;
    }
}
