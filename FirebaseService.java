package com.eerovil.babysheets;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static android.content.ContentValues.TAG;
import static com.eerovil.babysheets.MainActivity.PREF;
import static com.eerovil.babysheets.MainActivity.PREF_FIREBASE_TOKEN;
import static com.eerovil.babysheets.MyService.GETDATA;

public class FirebaseService extends FirebaseMessagingService {
    public FirebaseService() {
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        String sender = null;
        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            sender = remoteMessage.getData().get("sender");
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
        String token = getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(PREF_FIREBASE_TOKEN, null);
        if (sender != null && token != null && !token.equals(sender)) {
            Intent serviceintent = new Intent(this, MyService.class);
            serviceintent.setAction(GETDATA);
            startService(serviceintent);
            Log.e(TAG, "Sent service GETDATA");
        } else {
            Log.d(TAG, "Received notification but sender was this device");
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
}
