package com.eerovil.babysheets;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static android.app.Notification.PRIORITY_MAX;
import static android.content.ContentValues.TAG;
import static android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES;
import static com.eerovil.babysheets.MainActivity.PREF;
import static com.eerovil.babysheets.MainActivity.PREF_FIREBASE_TOKEN;


public class MyService extends IntentService {
    private static final String TAG = "MyService";

    public static final String FEEDSTART = "com.eerovil.babysheets.FEEDSTART";
    public static final String FEEDEND = "com.eerovil.babysheets.FEEDEND";
    public static final String SLEEPSTART = "com.eerovil.babysheets.SLEEPSTART";
    public static final String SLEEPEND = "com.eerovil.babysheets.SLEEPEND";
    public static final String GETDATA = "com.eerovil.babysheets.GETDATA";
    public static final String CUSTOM = "com.eerovil.babysheets.CUSTOM";
    public static final String REFRESH = "com.eerovil.babysheets.REFRESH";

    public static final String BUNDLECREDENTIAL = "mCredential";

    private GoogleAccountCredential mCredential;

    private Date[] lastFeed = new Date[2];
    private Date[] lastSleep = new Date[2];

    private RemoteViews contentView;
    private NotificationCompat.Builder mBuilder;


    public static final String SPREADSHEET_ID = "19AkAs2dAkvHyvd3NR0Jpo5doqZBAJwEmK3hG5P-NE9A";
    public static final String SPREADSHEET_RANGE = "tietokanta!A1:F";
    public static final String SPREADSHEET_GETRANGE = "uusimmat!A2:F5";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS };

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String PREF = "pref";

    private Date date;

    private com.google.api.services.sheets.v4.Sheets mService;

    public MyService() {
        super("MyService");
    }

    public MyService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = null;
        try {
            action = intent.getAction();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        this.date = new Date();

        if (intent.hasExtra("hour") && intent.hasExtra("minute")) {
            Calendar calendar = Calendar.getInstance();
            calendar.set( Calendar.HOUR_OF_DAY, intent.getIntExtra("hour",0) );
            calendar.set( Calendar.MINUTE, intent.getIntExtra("minute",0) );
            this.date = calendar.getTime();
            if ((new Date()).before(this.date)) {
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                this.date = calendar.getTime();
            }
        }

        Log.d(TAG,"Service " + action);

        if (!REFRESH.equals(action)) {

            mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());

            String accountName = getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);

            Log.v("babysheets", "Using account " + accountName);

            if (accountName == null)
                return;

            mCredential.setSelectedAccountName(accountName);

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, mCredential)
                    .setApplicationName("Google Sheets API Android Quickstart")
                    .build();

            lastFeed[0] = null;
            createNotification();
        }

        if (FEEDSTART.equals(action) ||FEEDEND.equals(action) || SLEEPEND.equals(action) || SLEEPSTART.equals(action)) {
            sheetsAddData(action);
            refreshMainActivity();
            sendFirebase(this);
        }

        if (REFRESH.equals(action)) {
            loadData();
        } else {
            sheetsGetData();
        }

        createNotification();
    }

    private void refreshMainActivity() {
        Intent intent = new Intent();
        intent.setAction("REFRESH");
        sendBroadcast(intent);
    }

    public static void sendFirebase(Context context) {
        String token = context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
                .getString(PREF_FIREBASE_TOKEN, null);
        if (token != null) {
            DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference("updates");
            mDatabase.child(token).setValue("updated");
            Log.d(TAG, "Sent firebase with token " + token);

        } else {
            Log.e(TAG, "Error sending firebase: NO TOKEN");
        }
    }

    private String createNotificationHelper(boolean init, RemoteViews contentView, String type, Date[] dates) {
        DateFormat dateFormat = new SimpleDateFormat("HH:mm");
        boolean feed = type.equals("feed");
        int button = (feed ? R.id.b_feed : R.id.b_sleep);
        int text_ago = (feed ? R.id.feed_ago : R.id.sleep_ago);
        int textview = (feed ? R.id.title : R.id.text);
        String currentText = (feed ? "Syö " : "Nukkuu ");
        String endedText = (feed ? "Söi " : "Nukkui ");
        Intent tmpIntent = new Intent();
        String contentText;
        if (dates[0].before(dates[1])) {
            tmpIntent.setAction(feed ? FEEDSTART : SLEEPSTART);
            contentView.setImageViewResource(button, R.drawable.ic_play);

            contentText = endedText + dateFormat.format(dates[0])
                    + "-" + dateFormat.format(dates[1]);

            contentView.setTextViewText(text_ago, " ("
                    + timeDiff((feed ? dates[0] : dates[1])) + " sitten)");

        } else {
            tmpIntent.setAction(feed ? FEEDEND : SLEEPEND);
            contentView.setImageViewResource(button, R.drawable.ic_stop);
            contentText = currentText + dateFormat.format(dates[0]) +
                    "-...";

            contentView.setTextViewText(text_ago, " (" + timeDiff(dates[0]) + " sitten)");

        }

        tmpIntent.addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
        PendingIntent tmpPendingIntent = PendingIntent.getBroadcast(this, 12345, tmpIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (feed) {
            contentView.setOnClickPendingIntent(R.id.b_feed, tmpPendingIntent);
        } else {
            contentView.setOnClickPendingIntent(R.id.b_sleep, tmpPendingIntent);
        }
        contentView.setTextViewText(textview, contentText);
        return contentText;
    }

    protected PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context, getClass());
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }



    private void createNotification() {
        boolean init = false;
        if (contentView == null) {
            init = true;
            contentView = new RemoteViews(getPackageName(), R.layout.custom_push);
            contentView.setImageViewResource(R.id.b_refresh, R.drawable.ic_refresh);
        }
        Log.d(TAG, "createNotification, init = " + init);


        // Sets an ID for the notification
        int mNotificationId = 1;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (lastFeed[0] != null) {
            createNotificationHelper(init, contentView, "feed", lastFeed);
            createNotificationHelper(init, contentView, "sleep", lastSleep);
        } else {
            contentView.setTextViewText(R.id.title, "Loading...");
            contentView.setTextViewText(R.id.text, "");
            contentView.setTextViewText(R.id.feed_ago, "");
            contentView.setTextViewText(R.id.sleep_ago, "");
        }
        if (init) {
            Intent getDataReceive = new Intent();
            getDataReceive.setAction(GETDATA);
            getDataReceive.addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
            PendingIntent pendingIntentGetData = PendingIntent.getBroadcast(this, 12345, getDataReceive, PendingIntent.FLAG_UPDATE_CURRENT);
            contentView.setOnClickPendingIntent(R.id.b_refresh, pendingIntentGetData);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent intent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);
            mBuilder = new NotificationCompat.Builder(this)
                    .setAutoCancel(false)
                    .setPriority(PRIORITY_MAX)
                    .setContentIntent(intent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.n_icon)
                    .setContent(contentView);
        }


        mNotifyMgr.notify(mNotificationId, mBuilder.build());

    }

    private String timeDiff(Date d1) {
        Date now = new Date();
        long diff = now.getTime() - d1.getTime();//as given

        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) - 60*hours;
        String ret;
        ret = minutes + "min";
        if (hours != 0)
            ret = hours + "h " + ret;

        return ret;
    }

    private void sheetsGetData() {
        List<String> results = new ArrayList<>();
        try {
            ValueRange response = mService.spreadsheets().values()
                    .get(SPREADSHEET_ID, SPREADSHEET_GETRANGE)
                    .execute();
            List<List<Object>> values = response.getValues();
            if (values != null) {
                lastFeed[0] = parseDate(values.get(0));
                lastFeed[1] = parseDate(values.get(1));
                lastSleep[0] = parseDate(values.get(2));
                lastSleep[1] = parseDate(values.get(3));
                saveData();
                Log.v("babysheets","lastFeed[0 " + lastFeed[0]);
                Log.v("babysheets","lastFeed[1 " + lastFeed[1]);
                Log.v("babysheets","lastSleep[0 " + lastSleep[0]);
                Log.v("babysheets","lastSleep[1 " + lastSleep[1]);
            }
        }catch (IOException e) {
            Log.e("babysheets",e.toString());
        }
    }

    private void saveData() {
        SharedPreferences settings =
                getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong("lastFeedStart", lastFeed[0].getTime());
        editor.putLong("lastFeedEnd", lastFeed[1].getTime());
        editor.putLong("lastSleepStart", lastSleep[0].getTime());
        editor.putLong("lastSleepEnd", lastSleep[1].getTime());
        editor.apply();
    }

    private void loadData() {
        SharedPreferences p = getSharedPreferences(PREF,Context.MODE_PRIVATE);

        lastFeed[0] = new Date(p.getLong("lastFeedStart", 0));
        lastFeed[1] = new Date(p.getLong("lastFeedEnd", 0));
        lastSleep[0] = new Date(p.getLong("lastSleepStart", 0));
        lastSleep[1] = new Date(p.getLong("lastSleepEnd", 0));
    }

    public static Date parseDate(List<Object> obj) {
        String s = obj.get(0) + " " + obj.get(1);
        s = s.trim();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        try {
            return dateFormat.parse(s);
        } catch (ParseException err) {
            dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            try {
                return dateFormat.parse(s);
            } catch (ParseException err2) {
                dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
                try {
                    return dateFormat.parse(s);
                } catch (ParseException err3) {
                    Log.e("babysheets", err.getMessage() + ", "
                            + err2.getMessage() + ", " + err3.getMessage());
                    return null;
                }
            }
        }
    }

    private void sheetsAddData(String type) {

        try {

            DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            Object dateString = dateFormat.format(date);
            List<List<Object>> values = Arrays.asList(
                    Arrays.asList(
                            dateString,
                            "",
                            type,
                            "",
                            ""
                    )
            );
            ValueRange body = new ValueRange()
                    .setValues(values);
            AppendValuesResponse result = mService.spreadsheets()
                    .values().append(SPREADSHEET_ID, SPREADSHEET_RANGE, body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (IOException e) {
            Log.e("babysheets",e.getMessage());
        }
    }

}
