package com.eerovil.babysheets;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.eerovil.babysheets.MyService.GETDATA;
import static com.eerovil.babysheets.MyService.SPREADSHEET_ID;
import static com.eerovil.babysheets.MyService.SPREADSHEET_RANGE;
import static com.eerovil.babysheets.MyService.STARTSERVICE;
import static com.eerovil.babysheets.MyService.parseDate;
import static com.eerovil.babysheets.MyService.sendFirebase;
import static com.eerovil.babysheets.MyService.timeDiff;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {

    private GoogleAccountCredential mCredential;
    private com.google.api.services.sheets.v4.Sheets mService;
    private Button mButton;
    private Notification nStatus;
    private ProgressDialog mProgress;
    ArrayAdapter mAdapter;

    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String TAG = "MainActivity";

    private static final String BUTTON_TEXT = "Call Google Sheets API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    public static final String PREF_FIREBASE_TOKEN = "firebasetoken";
    public static final String PREF = "pref";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS };

    private ListView listMain;
    private final ArrayList<MyListItem> listItems = new ArrayList<>();
    private MyListItemAdapter listAdapter;

    private BroadcastReceiver receiver;

    private Context context;


    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        context = this;


        Log.v(TAG,"Log start");



        listAdapter = new MyListItemAdapter(this, listItems);

        ListView listView = (ListView) findViewById(R.id.listMain);
        listView.setAdapter(listAdapter);
        registerForContextMenu(listView);

        mProgress = new ProgressDialog(this);



        FloatingActionButton fab_refresh = (FloatingActionButton) findViewById(R.id.fab_refresh);

        fab_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getResultsFromApi();
            }
        });

        FloatingActionButton fab_add = (FloatingActionButton) findViewById(R.id.fab_add);

        fab_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), CustomActivity.class);
                view.getContext().startActivity(intent);
            }
        });

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        getResultsFromApi();

        startService();

        /*AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, MyService.class);
        intent.setAction(REFRESH);
        long frequency= 60 * 1000; // in ms
        PendingIntent piLoop = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), frequency, piLoop);
        Log.d(TAG, "Set repeating alarm every " + frequency + " ms");*/

        IntentFilter filter = new IntentFilter();
        filter.addAction("REFRESH");
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                getResultsFromApi();
            }
        };
        registerReceiver(receiver, filter);


        FirebaseMessaging.getInstance().subscribeToTopic("updates");
        String token = getSharedPreferences(PREF,Context.MODE_PRIVATE)
                .getString(PREF_FIREBASE_TOKEN, null);
        if (token == null) {
            token = FirebaseInstanceId.getInstance().getToken();
            SharedPreferences settings =
                    getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PREF_FIREBASE_TOKEN, token);
            editor.apply();
        }
        Log.d(TAG, "Firebase token is " + token);


    }

    protected void onResume() {
        super.onResume();

        getResultsFromApi();
        refreshNotification();

    }


    private void showData() {

    }
    private void refreshNotification() {
        Intent serviceintent = new Intent(this, MyService.class);
        serviceintent.setAction(GETDATA);
        startService(serviceintent);
        Log.e(TAG,"Sent service GETDATA");
    }
    private void startService() {
        Intent serviceintent = new Intent(this, MyService.class);
        serviceintent.setAction(STARTSERVICE);
        startService(serviceintent);
        Log.e(TAG,"Sent service STARTSERVICE");
    }


    private void showLoading(String message) {
        mProgress.setMessage(message);
        mProgress.show();
    }
    private void showLoading() {
        showLoading("Loading ...");
    }
    private void hideLoading() {
        mProgress.hide();
    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        showLoading();
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            Log.e(TAG,"No network connection available.");
        } else {
            tryGet();
        }
    }


    private void tryGet() {
        AsyncTask<Void, Void, ValueRange> task = new AsyncTask<Void, Void, ValueRange>() {

            @Override
            protected ValueRange doInBackground(Void... params) {

                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                        transport, jsonFactory, mCredential)
                        .setApplicationName("Google Sheets API Android Quickstart")
                        .build();

                try {
                    return mService.spreadsheets().values()
                            .get(SPREADSHEET_ID, SPREADSHEET_RANGE)
                            .execute();

                } catch (UserRecoverableAuthIOException a) {
                    startActivity(a.getIntent());
                } catch (IOException e) {
                    Log.e(TAG,e.toString());
                }
                return null;
            }

            @Override
            protected void onPostExecute(ValueRange v) {
                List<List<Object>> values = v.getValues();
                listItems.clear();
                for (int i=0; i<values.size(); i++){
                    List<Object> row = values.get(i);
                    if (row.size() <= 2) continue;
                    Date date = parseDate(row);
                    String type = parseType(row.get(2));
                    if (date != null && type != null)
                        listItems.add(new MyListItem(i,date,type));

                }
                Collections.sort(listItems, new ListCompare());
                Date lastFeed = null;
                Date lastSleep = null;
                for (MyListItem item : listItems) {
                    if (item.type.contains("feed")){
                        if (lastFeed != null)
                            item.setTimeSince(lastFeed);
                        lastFeed = item.date;
                    }
                    if (item.type.contains("sleep")){
                        if (lastSleep != null)
                            item.setTimeSince(lastSleep);
                        lastSleep = item.date;
                    }
                }
                listAdapter.notifyDataSetChanged();
                hideLoading();
            }
        };
        task.execute();
    }

    private class ListCompare implements Comparator<MyListItem> {
        @Override
        public int compare(MyListItem o1, MyListItem o2) {
            return (o1.date).compareTo(o2.date);
        }
    }

    private String parseType(Object o) {
        String s = (String) o;
        String[] types = {"feedstart", "feedend", "feed",  "sleepstart", "sleepend"};
        for (String t : types){
            if (s.toLowerCase().contains(t))
                return t;
        }
        return null;
    }

    public class MyListItem {
        public final String type;
        public Date date;
        public final int position;
        public String timesince;

        public MyListItem(int position, Date date, String type) {
            this.date = date;
            this.type = type;
            this.position = position;
        }
        public void setTimeSince(Date last) {
            this.timesince = timeDiff(last, this.date);
        }
    }

    private class MyListItemAdapter extends ArrayAdapter<MyListItem> {
        public MyListItemAdapter(Context context, ArrayList<MyListItem> items) {
            super(context, 0, items);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            MyListItem item = getItem(position);
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM HH:mm");
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.activity_listview, parent, false);
            }
            // Lookup view for data population
            TextView tvDate = (TextView) convertView.findViewById(R.id.date);
            TextView tvType = (TextView) convertView.findViewById(R.id.type);
            TextView tvSince = (TextView) convertView.findViewById(R.id.timesince);
            // Populate the data into the template view using the data object
            tvDate.setText(dateFormat.format(item.date));
            String typeText = item.type;
            if (typeText.contains("feed"))
                tvType.setTextColor(Color.parseColor("#BA0000"));
            if (typeText.contains("sleep"))
                tvType.setTextColor(Color.parseColor("#00BA00"));
            typeText = typeText.replace("feedstart","Syöttö Alku");
            typeText = typeText.replace("feedend", "Syöttö Loppu");
            typeText = typeText.replace("feed", "Syöttö");
            typeText = typeText.replace("sleepstart", "Nukahti");
            typeText = typeText.replace("sleepend", "Heräsi");
            tvType.setText(typeText);
            tvSince.setText(item.timesince);
            // Return the completed view to render on screen
            return convertView;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.edit:
                editItem(info.id);
                return true;
            case R.id.delete:
                deleteItem(info.id);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
    private void editItem(final Long id) {
        final MyListItem item = listItems.get(id.intValue());

        final Calendar itemDate = Calendar.getInstance();
        itemDate.setTime(item.date);
        int mYear = itemDate.get(Calendar.YEAR);
        int mMonth = itemDate.get(Calendar.MONTH);
        int mDay = itemDate.get(Calendar.DAY_OF_MONTH);
        int mHour = itemDate.get(Calendar.HOUR_OF_DAY);
        int mMinute = itemDate.get(Calendar.MINUTE);

        final AsyncTask<Integer, Void, Void> task = new AsyncTask<Integer, Void, Void>() {
            Integer index;
            int listIndex;
            @Override
            protected Void doInBackground(Integer... params) {
                index = params[0];
                listIndex = params[1];

                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                        transport, jsonFactory, mCredential)
                        .setApplicationName("Google Sheets API Android Quickstart")
                        .build();

                try {
                    ValueRange range = new ValueRange();
                    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    Object dateString = dateFormat.format(itemDate.getTime());
                    List<List<Object>> values = Collections.singletonList(
                            Collections.singletonList(
                                    dateString
                            )
                    );
                    range.setValues(values);
                    UpdateValuesResponse response = mService.spreadsheets()
                            .values()
                            .update(SPREADSHEET_ID, "tietokanta!A" + (index + 1), range)
                            .setValueInputOption("RAW")
                            .execute();
                    Log.d(TAG,response.toString());


                } catch (UserRecoverableAuthIOException a) {
                    startActivity(a.getIntent());
                } catch (IOException e) {
                    Log.e(TAG,e.toString());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                getResultsFromApi();
                sendFirebase(context);
            }
        };

        final TimePickerDialog timeDialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                itemDate.set(Calendar.HOUR_OF_DAY, selectedHour);
                itemDate.set(Calendar.MINUTE, selectedMinute);
                Log.d(TAG, "selected new date " + itemDate.toString());

                Integer[] row = {listItems.get(id.intValue()).position, id.intValue()};
                showLoading("Muokataan...");
                item.date = itemDate.getTime();
                task.execute(row);
            }
        }, mHour, mMinute, true);
        DatePickerDialog dateDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int newYear, int newMonth, int newDay) {
                itemDate.set(Calendar.YEAR, newYear);
                itemDate.set(Calendar.MONTH, newMonth);
                itemDate.set(Calendar.DAY_OF_MONTH, newDay);
                timeDialog.show();
            }
        }, mYear, mMonth, mDay);



        dateDialog.show();
    }

    private void deleteItem(Long id) {
        AsyncTask<Integer, Void, Void> task = new AsyncTask<Integer, Void, Void>() {
            Integer index;
            int listIndex;
            @Override
            protected Void doInBackground(Integer... params) {
                index = params[0];
                listIndex = params[1];

                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                        transport, jsonFactory, mCredential)
                        .setApplicationName("Google Sheets API Android Quickstart")
                        .build();

                try {
                    BatchUpdateSpreadsheetRequest content = new BatchUpdateSpreadsheetRequest();
                    content.setRequests(Collections.singletonList(
                            new Request().setDeleteDimension(new DeleteDimensionRequest()
                                    .setRange(new DimensionRange()
                                            .setDimension("ROWS")
                                            .setStartIndex(index)
                                            .setEndIndex(index + 1)
                                            .setSheetId(0)))
                    ));
                    BatchUpdateSpreadsheetResponse response = mService.spreadsheets()
                            .batchUpdate(SPREADSHEET_ID,content)
                            .execute();
                    Log.d(TAG,response.toString());


                } catch (UserRecoverableAuthIOException a) {
                    startActivity(a.getIntent());
                } catch (IOException e) {
                    Log.e(TAG,e.toString());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                listItems.remove(listIndex);
                listAdapter.notifyDataSetChanged();
                refreshNotification();
                hideLoading();
                sendFirebase(context);
            }
        };
        Integer[] row = {listItems.get(id.intValue()).position, id.intValue()};
        showLoading("Poistetaan...");
        task.execute(row);
    }




    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getSharedPreferences(PREF,Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Log.e(TAG,
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getSharedPreferences(PREF, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);

                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    private void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
        if (mProgress != null) {
            mProgress.hide();
            mProgress.cancel();
        }
        super.onDestroy();
    }
}