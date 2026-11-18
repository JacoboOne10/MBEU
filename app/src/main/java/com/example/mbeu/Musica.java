package com.example.mbeu;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Musica extends AppCompatActivity implements View.OnClickListener {
    private static final int RESTART_THRESHOLD_MS = 3000;   // "anterior" reinicia si lleva más de 3 s
    private static final int COLOR_ON = Color.parseColor("#3BD145");
    private static final int COLOR_OFF = Color.parseColor("#9E8AA8");
    private static final int COLOR_FAV = Color.parseColor("#FF6B8B");

    private MediaPlayer player;
    private int currentPosition = -1;
    private boolean looping = false;
    private boolean userSeeking = false;
    private final Set<String> favorites = new HashSet<>();
    private SongAdapter adapter;
    private List<SongItem> songs;
    private Button atras;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Panel del reproductor
    private View playerPanel;
    private TextView tvPlayerTitle, tvCurrent, tvTotal;
    private SeekBar seekBar;
    private ImageView btnPlayPause, btnPrev, btnNext, btnLoop, btnFav;

    // Mueve la barra y el tiempo actual cada medio segundo
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && !userSeeking) {
                int now = player.getCurrentPosition();
                seekBar.setProgress(now);
                tvCurrent.setText(formatTime(now));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musica);
        ListView list = findViewById(R.id.listMusica);
        atras = (Button) findViewById(R.id.musicaBackButton);
        atras.setOnClickListener(this);

        playerPanel = findViewById(R.id.playerPanel);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvTotal = findViewById(R.id.tvTotal);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnLoop = findViewById(R.id.btnLoop);
        btnFav = findViewById(R.id.btnFav);

        // Favoritos guardados en el perfil del usuario
        favorites.addAll(SleepDB.profile(this).getStringSet("favoritos", new HashSet<>()));

        // Para agregar pistas: copia el audio a res/raw y agrega una línea aquí
        songs = new ArrayList<>();
        songs.add(song("Lluvia suave", R.raw.lluvia_suave));
        songs.add(song("Olas del mar", R.raw.olas_del_mar));
        songs.add(song("Ruido blanco", R.raw.ruido_blanco));
        songs.add(song("Ruido café", R.raw.ruido_cafe));
        songs.add(song("Piano", R.raw.piano));

        adapter = new SongAdapter(this, songs, favorites);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onSongClicked(position));

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnPrev.setOnClickListener(v -> previous());
        btnNext.setOnClickListener(v -> next());
        btnLoop.setOnClickListener(v -> toggleLoop());
        btnFav.setOnClickListener(v -> toggleFavorite());
        updateLoopButton();
        updateFavoriteButton();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) tvCurrent.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) player.seekTo(sb.getProgress());
                userSeeking = false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(progressUpdater);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(progressUpdater);
    }

    /** Crea una pista leyendo su duración real del archivo. */
    private SongItem song(String title, int rawRes) {
        return new SongItem(title, realDuration(rawRes), R.drawable.ic_launcher_background, rawRes);
    }

    private String realDuration(int rawRes) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(rawRes);
            retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return formatTime(Integer.parseInt(ms));
        } catch (Exception e) {
            return "--:--";
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static String formatTime(int millis) {
        int total = millis / 1000;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private void onSongClicked(int position) {
        // Misma pista: pausar o reanudar. Otra: empezarla
        if (position == currentPosition && player != null) {
            togglePlayPause();
        } else {
            playSong(position);
        }
    }

    private void playSong(int position) {
        if (player != null) player.release();
        player = MediaPlayer.create(this, songs.get(position).rawRes);
        if (player == null) {
            Toast.makeText(this, "No se pudo reproducir", Toast.LENGTH_SHORT).show();
            currentPosition = -1;
            adapter.setState(-1, false);
            playerPanel.setVisibility(View.GONE);
            return;
        }
        currentPosition = position;
        updateFavoriteButton();
        player.setLooping(looping);
        // Sin bucle, al terminar la pista se detiene y queda lista para volver a empezar
        player.setOnCompletionListener(mp -> {
            mp.seekTo(0);
            seekBar.setProgress(0);
            tvCurrent.setText(formatTime(0));
            adapter.setState(position, false);
            showPlaying(false);
        });

        int duration = player.getDuration();
        seekBar.setMax(duration);
        seekBar.setProgress(0);
        tvCurrent.setText(formatTime(0));
        tvTotal.setText(formatTime(duration));
        tvPlayerTitle.setText(songs.get(position).title);
        playerPanel.setVisibility(View.VISIBLE);

        player.start();
        adapter.setState(position, true);
        showPlaying(true);
    }

    private void togglePlayPause() {
        if (player == null) return;
        boolean nowPlaying = !player.isPlaying();
        if (nowPlaying) player.start(); else player.pause();
        adapter.setState(currentPosition, nowPlaying);
        showPlaying(nowPlaying);
    }

    private void previous() {
        if (player == null) return;
        if (player.getCurrentPosition() > RESTART_THRESHOLD_MS) {
            player.seekTo(0);                       // lleva más de 3 s: reinicia la pista
            seekBar.setProgress(0);
            tvCurrent.setText(formatTime(0));
        } else {
            playSong((currentPosition - 1 + songs.size()) % songs.size());
        }
    }

    private void next() {
        if (player == null) return;
        playSong((currentPosition + 1) % songs.size());
    }

    private void toggleLoop() {
        looping = !looping;
        if (player != null) player.setLooping(looping);
        updateLoopButton();
    }

    private void updateLoopButton() {
        btnLoop.setColorFilter(looping ? COLOR_ON : COLOR_OFF);
    }

    private void toggleFavorite() {
        if (currentPosition < 0) return;
        String title = songs.get(currentPosition).title;
        if (!favorites.remove(title)) favorites.add(title);
        SleepDB.profile(this).edit()
                .putStringSet("favoritos", new HashSet<>(favorites)).apply();
        updateFavoriteButton();
        adapter.notifyDataSetChanged();
    }

    private void updateFavoriteButton() {
        boolean fav = currentPosition >= 0 && favorites.contains(songs.get(currentPosition).title);
        btnFav.setImageResource(fav ? R.drawable.ic_fav_on : R.drawable.ic_fav_off);
        btnFav.setColorFilter(fav ? COLOR_FAV : COLOR_OFF);
    }

    private void showPlaying(boolean playing) {
        btnPlayPause.setImageResource(playing ? R.drawable.pause : R.drawable.play);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressUpdater);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, Inicio.class);
        startActivity(intent);
        finish();
    }

    private static class SongItem {
        final String title;
        final String duration;
        final int thumbRes;
        final int rawRes;          // el archivo de audio en res/raw

        SongItem(String title, String duration, int thumbRes, int rawRes) {
            this.title = title;
            this.duration = duration;
            this.thumbRes = thumbRes;
            this.rawRes = rawRes;
        }
    }

    private static class SongAdapter extends ArrayAdapter<SongItem> {
        private final Set<String> favorites;
        private int playingPosition = -1;
        private boolean playing = false;

        SongAdapter(Context context, List<SongItem> items, Set<String> favorites) {
            super(context, 0, items);
            this.favorites = favorites;
        }

        /** Qué fila suena (o está en pausa) y vuelve a dibujar la lista */
        void setState(int position, boolean isPlaying) {
            playingPosition = position;
            playing = isPlaying;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.audio_item, parent, false);
            }
            SongItem item = getItem(position);
            ((TextView) convertView.findViewById(R.id.tvTitle)).setText(item.title);
            ((TextView) convertView.findViewById(R.id.tvDuration)).setText(item.duration);
            ((ImageView) convertView.findViewById(R.id.ivThumb)).setImageResource(item.thumbRes);

            boolean active = (position == playingPosition) && playing;
            ((ImageView) convertView.findViewById(R.id.ivPlay))
                    .setImageResource(active ? R.drawable.pause : R.drawable.play);

            convertView.findViewById(R.id.ivFav)
                    .setVisibility(favorites.contains(item.title) ? View.VISIBLE : View.GONE);
            // Sombrea la fila de la pista actual (suene o esté en pausa)
            convertView.setBackgroundColor(position == playingPosition
                    ? Color.parseColor("#33FFFFFF") : Color.TRANSPARENT);
            return convertView;
        }
    }
}