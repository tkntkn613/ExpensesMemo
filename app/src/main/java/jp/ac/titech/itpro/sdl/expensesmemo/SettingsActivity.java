package jp.ac.titech.itpro.sdl.expensesmemo;


import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "SettingsActivity";
    private static SettingsActivity activity;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            Log.d(TAG, "changeListener " + stringValue);
            if(preference == null) return true;
            Log.d(TAG, "preference " + preference.getKey());

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                CharSequence result = index >= 0 ? listPreference.getEntries()[index] : null;

                // Set the summary to reflect the new value.
                preference.setSummary(result);

                if(stringValue != null && preference.getKey().equals("sync_frequency")) {
                    int syncFrequency = Integer.valueOf(stringValue);
                    activity.alarmExecute(syncFrequency);
                }
            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                Log.d(TAG, "setSummary " + stringValue);
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        activity = this;
        mCredential = GoogleAccountCredential.usingOAuth2(activity, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || DataSyncPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
        private final static String TAG = "GeneralPrefFrag";
        private Preference auth_pref, select_pref, place_manage_pref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            auth_pref = findPreference("account_enable_switch");
            if(auth_pref != null) {
                auth_pref.setOnPreferenceClickListener(this);
            }
            /*
            select_pref = findPreference("folder_select");
            if(select_pref != null) {
                select_pref.setOnPreferenceClickListener(this);
            }
            */
            place_manage_pref = findPreference("place_management");
            if(place_manage_pref != null) {
                place_manage_pref.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            Log.d(TAG, "onStart");
        }

        @Override
        public void onResume() {
            super.onResume();
            Log.d(TAG, "onResume");
            updatePreferenceStatus();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if(preference.getKey().equals(auth_pref.getKey())) {
                String accountName = activity.getSharedPreferences().getString(PREF_ACCOUNT_NAME, null);
                if(accountName == null) {
                    // select Google Account
                    Log.d(TAG, "click - auth_pref");
                    activity.getResultsFromApi();
                } else {
                    Log.d(TAG, "reset account choose");
                    // 同期するアカウント情報を削除
                    SharedPreferences settings = activity.getSharedPreferences();
                    SharedPreferences.Editor editor = settings.edit();
                    editor.remove(PREF_ACCOUNT_NAME);
                    editor.apply();
                }
            } else if(preference.getKey().equals(place_manage_pref.getKey())) {
                startActivity(new Intent(activity, PlaceActivity.class));
            }
            Log.d(TAG, "click end");
            updatePreferenceStatus();
            return true;
        }

        private void updatePreferenceStatus() {
            String accountName = activity.getSharedPreferences().getString(PREF_ACCOUNT_NAME, null);
            if(accountName != null) {
                //select_pref.setEnabled(true);
                auth_pref.setSummary(activity.getString(R.string.pref_auth_account_summary, accountName));
            } else {
                //select_pref.setEnabled(false);
                auth_pref.setSummary(activity.getString(R.string.pref_account_select_summary));
            }
        }

    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {
        private Preference notificationAddItem;
        private Preference.OnPreferenceChangeListener pChanged =
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Log.d(TAG, newValue.toString());
                        if(preference.getKey().equals(notificationAddItem.getKey())) {
                            NotificationHelper helper = new NotificationHelper(getActivity());
                            if(newValue instanceof Boolean) {
                                boolean val = (boolean) newValue;
                                if (val) {
                                    helper.showAddItem();
                                } else {
                                    helper.cancel();
                                }
                            }
                        }
                        return true;
                    }
                };
        private void listenChanged(Preference preference) {
            preference.setOnPreferenceChangeListener(pChanged);
        }
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("notifications_sync_result"));
            notificationAddItem = findPreference("notifications_add_item");
            if(notificationAddItem != null) {
                listenChanged(notificationAddItem);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataSyncPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_data_sync);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("sync_frequency"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    // 以下、Googleアカウント関連

    private GoogleAccountCredential mCredential;

    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    public static final int REQUEST_AUTHORIZATION = 1001;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE, "https://spreadsheets.google.com/feeds" };

    private SharedPreferences getSharedPreferences() {
        //return getPreferences(MODE_PRIVATE);
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void alarmExecute(int syncFrequency) {
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(this);
        if(syncFrequency == -1) {
            syncFrequency = Integer.valueOf(sPref.getString("sync_frequency", "-1"));
        }
        Log.d(TAG, "alarmExecute " + syncFrequency);
        AlarmReceiver alarmReceiver = new AlarmReceiver(this);
        if(syncFrequency != -1) {
            alarmReceiver.setRepeatAlarm(syncFrequency*60*1000, false);
        } else {
            alarmReceiver.cancel();
        }
    }

    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Log.d(TAG, "No network connection available.");
            Toast.makeText(this, getString(R.string.toast_no_network_available), Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "Execute Task");
            new SyncTestTask(this, mCredential)
                    .setPostExecuteTask(new SyncTestTask.executeTask(this) {
                        @Override
                        public void execute() {
                            alarmExecute(-1);
                        }
                    })
                    .execute();
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        Log.d(TAG, "chooseAccount");
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getSharedPreferences().getString(PREF_ACCOUNT_NAME, null);
            Log.d(TAG, "accountName " + accountName);
            if (accountName != null) {
                // ここでaccountNameを入れる
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // pickerでアカウントを選択させる
                startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            Log.d(TAG, "This app needs to access your Google account (via Contacts).");
            EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.request_permission),
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Log.d(TAG, "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                    Toast.makeText(this, getString(R.string.toast_require_google_play_service), Toast.LENGTH_LONG).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    Log.d(TAG, "REQUEST_ACCOUNT_PICKER " + accountName);
                    if (accountName != null) {
                        SharedPreferences settings = getSharedPreferences();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        Log.d(TAG, "acquireGooglePlayServices");
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    public void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(this, connectionStatusCode, REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }
}
