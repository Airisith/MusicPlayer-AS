package com.airisith.ksmusic;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;

import com.airisith.util.AppGlobalValues;

public class Splash extends Activity {

    private final int SPLASH_DISPLAY_LENGHT = 2000; //延迟启动

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        AppGlobalValues appGlobalValues = (AppGlobalValues) getApplication();
        if (appGlobalValues.getStartanim()) { // 第一次启动应用
            appGlobalValues.setStartanim(false);
            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    Intent mainIntent = new Intent(Splash.this, HomeActivity.class);
                    Splash.this.startActivity(mainIntent);
                    Splash.this.finish();
                    overridePendingTransition(R.anim.zoom_trans_in, R.anim.zoom_trans_out);
                }

            }, SPLASH_DISPLAY_LENGHT);
        } else {
            Intent mainIntent = new Intent(Splash.this, HomeActivity.class);
            Splash.this.startActivity(mainIntent);
            Splash.this.finish();
            overridePendingTransition(R.anim.zoom_trans_in, R.anim.zoom_trans_out);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        startActivity(intent);
        Splash.this.finish();
        overridePendingTransition(R.anim.zoom_trans_in, R.anim.zoom_trans_out);
    }

}
