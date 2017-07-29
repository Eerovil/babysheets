package com.eerovil.babysheets;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.iid.zzj;

import static android.content.ContentValues.TAG;
import static com.eerovil.babysheets.MainActivity.PREF;
import static com.eerovil.babysheets.MainActivity.PREF_FIREBASE_TOKEN;

/**
 * Created by eero on 28/07/2017.
 */

public class FirebaseInstance extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);
        SharedPreferences settings =
                getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_FIREBASE_TOKEN, refreshedToken);
        editor.apply();
    }
}
