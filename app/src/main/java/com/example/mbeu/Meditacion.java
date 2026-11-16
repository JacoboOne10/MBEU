package com.example.mbeu;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

public class Meditacion extends AppCompatActivity implements View.OnClickListener {
    Button back;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meditacion);
        ListView list = findViewById(R.id.listMeditations);
        back = (Button) findViewById(R.id.meditacionBackButton);
        back.setOnClickListener(this);

        List<VideoItem> videos = new ArrayList<>();
        videos.add(new VideoItem("Respiración para dormir", "5:00", R.drawable.ic_launcher_background));
        videos.add(new VideoItem("Mindfulness 30 minutos", "30:04", R.drawable.ic_launcher_background));
        videos.add(new VideoItem("Meditación de 10 minutos", "10:00", R.drawable.ic_launcher_background));
        videos.add(new VideoItem("Meditación en la naturaleza", "18:24", R.drawable.ic_launcher_background));

        list.setAdapter(new VideoAdapter(this, videos));

        list.setOnItemClickListener((parent, view, position, id) -> {
            VideoItem video = videos.get(position);
            Intent intent = new Intent(Meditacion.this, Video.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, Inicio.class);
        startActivity(intent);
        finish();
    }

    private static class VideoItem {
        final String title;
        final String duration;
        final int thumbRes;

        VideoItem(String title, String duration, int thumbRes) {
            this.title = title;
            this.duration = duration;
            this.thumbRes = thumbRes;
        }
    }

    private static class VideoAdapter extends ArrayAdapter<VideoItem> {
        VideoAdapter(Context context, List<VideoItem> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.video_item, parent, false);
            }
            VideoItem item = getItem(position);
            ((TextView) convertView.findViewById(R.id.tvTitle)).setText(item.title);
            ((TextView) convertView.findViewById(R.id.tvDuration)).setText(item.duration);
            ((ImageView) convertView.findViewById(R.id.ivThumb)).setImageResource(item.thumbRes);
            return convertView;
        }
    }

}
