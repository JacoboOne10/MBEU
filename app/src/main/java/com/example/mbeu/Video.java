package com.example.mbeu;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class Video extends AppCompatActivity implements View.OnClickListener {
    private VideoView video;
    private Button atras;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        atras = (Button) findViewById(R.id.FinalVideoBackButton);
        atras.setOnClickListener(this);
        video = (VideoView) findViewById(R.id.videoMeditacion);
        String videopath = "android.resource://" + getPackageName() + "/" +  R.raw.video;
        Uri uri = Uri.parse(videopath);
        video.setVideoURI(uri);
        MediaController mediaController = new MediaController(this);
        video.setMediaController(mediaController);
        mediaController.setAnchorView(video);
        video.start();
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, Meditacion.class);
        startActivity(intent);
        finish();
    }
}
