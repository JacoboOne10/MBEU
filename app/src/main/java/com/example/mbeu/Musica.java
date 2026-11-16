package com.example.mbeu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class Musica extends AppCompatActivity implements View.OnClickListener {
    private MediaPlayer player;
    private int currentPosition = -1;
    private SongAdapter adapter;
    private List<SongItem> songs;
    private Button atras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musica);
        ListView list = findViewById(R.id.listMusica);
        atras = (Button) findViewById(R.id.musicaBackButton);
        atras.setOnClickListener(this);

        songs = new ArrayList<>();
        songs.add(new SongItem("Lluvia suave", "30:00", R.drawable.ic_launcher_background, R.raw.ruido_blanco));
        songs.add(new SongItem("Olas del mar", "45:00", R.drawable.ic_launcher_background, R.raw.ruido_blanco));
        songs.add(new SongItem("Ruido Blanco", "50:00", R.drawable.ic_launcher_background, R.raw.ruido_blanco));
        songs.add(new SongItem("Ruido Café", "25:00", R.drawable.ic_launcher_background, R.raw.ruido_blanco));
        songs.add(new SongItem("Música a baja frecuencia", "44:00", R.drawable.ic_launcher_background, R.raw.ruido_blanco));

        adapter = new SongAdapter(this, songs);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onSongClicked(position));
    }

    private void onSongClicked(int position) {
        // Misma canción: pausar o reanudar
        if (position == currentPosition && player != null) {
            if (player.isPlaying()) {
                player.pause();
                adapter.setState(position, false);
            } else {
                player.start();
                adapter.setState(position, true);
            }
            return;
        }

        // Otra canción: soltar la anterior y empezar la nueva
        if (player != null) player.release();
        player = MediaPlayer.create(this, songs.get(position).rawRes);
        if (player == null) {
            Toast.makeText(this, "No se pudo reproducir", Toast.LENGTH_SHORT).show();
            currentPosition = -1;
            adapter.setState(-1, false);
            return;
        }
        player.setOnCompletionListener(mp -> adapter.setState(position, false));
        player.start();
        currentPosition = position;
        adapter.setState(position, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        private int playingPosition = -1;
        private boolean playing = false;

        SongAdapter(Context context, List<SongItem> items) {
            super(context, 0, items);
        }

        /**qué fila suena (o está en pausa) y vuelve a dibujar la lista */
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
            return convertView;
        }
    }
}

