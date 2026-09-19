package de.lazytv.pro;
import de.lazytv.pro.activation.ActivationGuard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import de.lazytv.pro.playlist.PlaylistManagerActivity;

public class MainActivity extends Activity {
    // LazyTV Player Core home. Debug builds may reach this through the development-only activation gate.
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);if(!ActivationGuard.enforce(this))return;setContentView(R.layout.activity_main);findViewById(R.id.manage_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));}
}
