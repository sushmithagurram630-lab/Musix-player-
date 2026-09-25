package com.example.musicplayer;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TrackAdapter.OnTrackClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private List<Track> allTracks = new ArrayList<>();
    private List<Track> displayedTracks = new ArrayList<>();
    private List<Track> favoriteTracks = new ArrayList<>();
    
    private RecyclerView recyclerView;
    private TrackAdapter adapter;
    private TabLayout tabLayout;
    private EditText searchEditText;

    // Media Controls
    private ImageButton playPauseButton;
    private ImageButton prevButton;
    private ImageButton nextButton;
    private ImageButton mainFavoriteButton;
    private SeekBar progressBar;
    private SeekBar volumeBar;
    private TextView currentTrackTitle;

    private MediaPlayer mediaPlayer;
    private int currentTrackIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFavoritesTab = false;

    @Override
    protected void InsideOnCreate(Bundle savedInstanceState) {
        // Wrapper for content initialization
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view_tracks);
        tabLayout = findViewById(R.id.tab_layout);
        searchEditText = findViewById(R.id.edit_text_search);
        playPauseButton = findViewById(R.id.button_play_pause);
        prevButton = findViewById(R.id.button_prev);
        nextButton = findViewById(R.id.button_next);
        mainFavoriteButton = findViewById(R.id.button_favorite_main);
        progressBar = findViewById(R.id.progress_bar);
        volumeBar = findViewById(R.id.volume_bar);
        currentTrackTitle = findViewById(R.id.text_view_current_track);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(displayedTracks, this);
        recyclerView.setAdapter(adapter);

        setupMediaPlayer();
        setupListeners();
        checkPermissions();
    }

    private void setupMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );

        mediaPlayer.setOnCompletionListener(mp -> playNextTrack());
    }

    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        prevButton.setOnClickListener(v -> playPreviousTrack());
        nextButton.setOnClickListener(v -> playNextTrack());
        
        mainFavoriteButton.setOnClickListener(v -> {
            if (currentTrackIndex >= 0 && currentTrackIndex < displayedTracks.size()) {
                Track track = displayedTracks.get(currentTrackIndex);
                toggleFavorite(track);
                updateMainFavoriteButtonIcon(track);
                adapter.notifyDataSetChanged();
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTracks(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isFavoritesTab = tab.getPosition() == 1;
                updateDisplayList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        volumeBar.setMax(100);
        volumeBar.setProgress(70);
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float logVolume = (float) (1 - (Math.log(100 - progress) / Math.log(100)));
                if (progress == 100) logVolume = 1.0f;
                if (progress == 0) logVolume = 0.0f;
                mediaPlayer.setVolume(logVolume, logVolume);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateProgressRunnable();
    }

    private void checkPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU 
                ? Manifest.permission.READ_MEDIA_AUDIO 
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        } else {
            loadWavTracks();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadWavTracks();
        } else {
            Toast.makeText(this, "Permission Denied! Cannot read local audio files.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadWavTracks() {
        allTracks.clear();
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        
        String selection = MediaStore.Audio.Media.MIME_TYPE + "=?";
        String[] selectionArgs = new String[]{"audio/x-wav"};
        
        // Fallback filter matching extension if mime-type extraction fails on some devices
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = contentResolver.query(uri, null, selection, selectionArgs, sortOrder);

        if (cursor != null && cursor.moveToFirst()) {
            int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);

            do {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                allTracks.add(new Track(id, title, artist, trackUri));
            } while (cursor.moveToNext());
            cursor.close();
        }
        
        // If query returned nothing, check all audio files for .wav suffix manually
        if (allTracks.isEmpty()) {
            Cursor fallbackCursor = contentResolver.query(uri, null, null, null, sortOrder);
            if (fallbackCursor != null && fallbackCursor.moveToFirst()) {
                int idColumn = fallbackCursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleColumn = fallbackCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistColumn = fallbackCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int dataColumn = fallbackCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                do {
                    String data = fallbackCursor.getString(dataColumn);
                    if (data != null && data.toLowerCase().endsWith(".wav")) {
                        long id = fallbackCursor.getLong(idColumn);
                        String title = fallbackCursor.getString(titleColumn);
                        String artist = fallbackCursor.getString(artistColumn);
                        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        allTracks.add(new Track(id, title, artist, trackUri));
                    }
                } while (fallbackCursor.moveToNext());
                fallbackCursor.close();
            }
        }

        updateDisplayList();
    }

    private void updateDisplayList() {
        displayedTracks.clear();
        if (isFavoritesTab) {
            displayedTracks.addAll(favoriteTracks);
        } else {
            displayedTracks.addAll(allTracks);
        }
        filterTracks(searchEditText.getText().toString());
    }

    private void filterTracks(String query) {
        List<Track> baseList = isFavoritesTab ? favoriteTracks : allTracks;
        displayedTracks.clear();
        if (query.isEmpty()) {
            displayedTracks.addAll(baseList);
        } else {
            for (Track track : baseList) {
                if (track.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                    track.getArtist().toLowerCase().contains(query.toLowerCase())) {
                    displayedTracks.add(track);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onTrackClick(Track track, int position) {
        currentTrackIndex = position;
        playTrack(track);
    }

    @Override
    public void onFavoriteClick(Track track, int position) {
        toggleFavorite(track);
        if (isFavoritesTab) {
            updateDisplayList();
        } else {
            adapter.notifyItemChanged(position);
        }
        if (currentTrackIndex >= 0 && currentTrackIndex < displayedTracks.size() && 
            displayedTracks.get(currentTrackIndex).getId() == track.getId()) {
            updateMainFavoriteButtonIcon(track);
        }
    }

    private void toggleFavorite(Track track) {
        track.setFavorite(!track.isFavorite());
        if (track.isFavorite()) {
            if (!favoriteTracks.contains(track)) favoriteTracks.add(track);
        } else {
            favoriteTracks.remove(track);
        }
    }

    private void playTrack(Track track) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this, track.getUri());
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            currentTrackTitle.setText(track.getTitle());
            progressBar.setMax(mediaPlayer.getDuration());
            updateMainFavoriteButtonIcon(track);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to play WAV file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        } else {
            if (currentTrackIndex == -1 && !displayedTracks.isEmpty()) {
                currentTrackIndex = 0;
                playTrack(displayedTracks.get(0));
            } else if (currentTrackIndex != -1) {
                mediaPlayer.start();
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            }
        }
    }

    private void playNextTrack() {
        if (!displayedTracks.isEmpty()) {
            currentTrackIndex = (currentTrackIndex + 1) % displayedTracks.size();
            playTrack(displayedTracks.get(currentTrackIndex));
        }
    }

    private void playPreviousTrack() {
        if (!displayedTracks.isEmpty()) {
            currentTrackIndex = (currentTrackIndex - 1 + displayedTracks.size()) % displayedTracks.size();
            playTrack(displayedTracks.get(currentTrackIndex));
        }
    }

    private void updateMainFavoriteButtonIcon(Track track) {
        if (track.isFavorite()) {
            mainFavoriteButton.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            mainFavoriteButton.setImageResource(android.R.drawable.btn_star_big_off);
        }
    }

    private void updateProgressRunnable() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    progressBar.setProgress(mediaPlayer.getCurrentPosition());
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
