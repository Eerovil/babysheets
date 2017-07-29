package com.eerovil.babysheets;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TimePicker;


import static com.eerovil.babysheets.MyService.FEEDEND;
import static com.eerovil.babysheets.MyService.FEEDSTART;
import static com.eerovil.babysheets.MyService.SLEEPEND;
import static com.eerovil.babysheets.MyService.SLEEPSTART;


public class CustomActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom);

        final TimePicker timePicker = (TimePicker) findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        Button bFeedStart = (Button) findViewById(R.id.bFeedStart);
        bFeedStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(view.getContext(), MyService.class);
                in.setAction(FEEDSTART);
                in.putExtra("hour", timePicker.getHour());
                in.putExtra("minute", timePicker.getMinute());
                view.getContext().startService(in);
                finish();
            }
        });

        Button bFeedEnd = (Button) findViewById(R.id.bFeedEnd);
        bFeedEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(view.getContext(), MyService.class);
                in.setAction(FEEDEND);
                in.putExtra("hour", timePicker.getHour());
                in.putExtra("minute", timePicker.getMinute());
                view.getContext().startService(in);
                finish();
            }
        });

        Button bSleepStart = (Button) findViewById(R.id.bSleepStart);
        bSleepStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(view.getContext(), MyService.class);
                in.setAction(SLEEPSTART);
                in.putExtra("hour", timePicker.getHour());
                in.putExtra("minute", timePicker.getMinute());
                view.getContext().startService(in);
                finish();
            }
        });

        Button bSleepEnd = (Button) findViewById(R.id.bSleepEnd);
        bSleepEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(view.getContext(), MyService.class);
                in.setAction(SLEEPEND);
                in.putExtra("hour", timePicker.getHour());
                in.putExtra("minute", timePicker.getMinute());
                view.getContext().startService(in);
                finish();
            }
        });

    }

}
