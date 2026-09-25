package com.example.musicplayer;

import android.net.Uri;

public class Track {
    private final long id;
    private final String title;
    private final String artist;
    private final Uri uri;
    private boolean isFavorite;

    public Track(long id, String title, String artist, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.uri = uri;
        this.isFavorite = false;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public Uri getUri() { return uri; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}
