package com.eerovil.babysheets;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static com.eerovil.babysheets.MyService.CUSTOM;

public class MainReceiver extends BroadcastReceiver {



    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v("babysheets","I Arrived!!!!");
        String action = intent.getAction();
        if (action.equals(CUSTOM)) {
            Intent c = new Intent(context, CustomActivity.class);
            context.startActivity(c);
        } else {
            Intent in = new Intent(context, MyService.class);
            in.setAction(action);
            context.startService(in);
        }


    }



}
