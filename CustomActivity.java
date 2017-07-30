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


public class CustomActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "CustomActivity";
    private Button bFeedStart;
    private Button bFeedEnd;
    private Button bSleepStart;
    private Button bSleepEnd;
    private TimePicker timePicker;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom);

        timePicker = (TimePicker) findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        bFeedStart = (Button) findViewById(R.id.bFeedStart);
        bFeedStart.setOnClickListener(this);

        bFeedEnd = (Button) findViewById(R.id.bFeedEnd);
        bFeedEnd.setOnClickListener(this);

        bSleepStart = (Button) findViewById(R.id.bSleepStart);
        bSleepStart.setOnClickListener(this);

        bSleepEnd = (Button) findViewById(R.id.bSleepEnd);
        bSleepEnd.setOnClickListener(this);

    }

    public void onClick(View view) {
        timePicker.clearFocus();

        Intent in = new Intent(view.getContext(), MyService.class);

        if (view == bFeedStart) in.setAction(FEEDSTART);
        if (view == bFeedEnd) in.setAction(FEEDEND);
        if (view == bSleepStart) in.setAction(SLEEPSTART);
        if (view == bSleepEnd) in.setAction(SLEEPEND);

        in.putExtra("hour", timePicker.getHour());
        in.putExtra("minute", timePicker.getMinute());
        view.getContext().startService(in);
        finish();
    }

}
