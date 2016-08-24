package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private MyDatabaseHelper db = new MyDatabaseHelper(this);
    private static String TAG = "MainActivity";

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE };

    private class TableElement {
        private Expense item;
        private TableRow row;
        private String placeText, datetimeText, nameText, costText, syncedText;

        private String dtFormat = "yyyy/MM/dd HH:mm";
        private SimpleDateFormat sdf = new SimpleDateFormat(dtFormat);

        TableElement(MainActivity activity, Expense item) {
            this.item = item;

            row = new TableRow(activity);
            row.setMinimumHeight(72);
            row.setOnClickListener(activity);

            if(item != null) {
                TextView placeView = new TextView(activity);
                // 店名
                Place place = item.getPlace();
                if(place != null && !place.getName().isEmpty()) {
                    placeText = place.getName();
                } else {
                    placeText = activity.getString(R.string.place_name_none);
                }
                placeView.setText(placeText);
                placeView.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
                row.addView(placeView);

                // 時刻
                TextView datetimeView = new TextView(activity);
                if(item.getCalendar() == null) {
                    datetimeText = activity.getString(R.string.time_none);
                } else {
                    datetimeText = sdf.format(item.getCalendar().getTime());
                }
                datetimeView.setText(datetimeText);
                datetimeView.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
                row.addView(datetimeView);

                // 商品名
                TextView nameView = new TextView(activity);
                nameText = item.getName();
                nameView.setText(nameText);
                nameView.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
                row.addView(nameView);

                // 価格
                TextView costView = new TextView(activity);
                costText = String.valueOf(item.getCost());
                costView.setText(costText);
                costView.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
                row.addView(costView);

                // 同期状況
                TextView syncedView = new TextView(activity);
                syncedText = activity.getString(item.getSynced() ? R.string.synced_true : R.string.synced_false);
                syncedView.setText(syncedText);
                syncedView.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
                row.addView(syncedView);
            }
        }

        public TableRow getTableRow() {
            return row;
        }
        public String getPlaceName() {
            return placeText;
        }
        public String getDatetime() {
            return datetimeText;
        }
        public String getName() {
            return nameText;
        }
        public String getCost() {
            return costText;
        }
        public String getSynced() {
            return syncedText;
        }
        public Expense getItem() { return item; }
    }

    private Button itemAddButton;
    private Expenses items = new Expenses();
    private ArrayList<TableElement> elements = new ArrayList<>();
    private TableLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        itemAddButton = (Button)findViewById(R.id.item_add);
        if(itemAddButton != null) itemAddButton.setOnClickListener(this);

        layout = (TableLayout)findViewById(R.id.table_layout);

        // 設定の読み込み
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(this);

        // 自動同期のためのBroadcastReceiver
        int syncFrequency = Integer.valueOf(sPref.getString("sync_frequency", "-1"));
        Log.d(TAG, "sync " + syncFrequency);
        if(syncFrequency != -1) {
            String accountName = sPref.getString(PREF_ACCOUNT_NAME, null);
            Log.d(TAG, "accountName " + accountName);
            if(accountName != null) {
                // 常に表示するNotificationの表示
                GoogleAccountCredential mCredential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(SCOPES))
                        .setBackOff(new ExponentialBackOff());
                mCredential.setSelectedAccountName(accountName);

                AlarmReceiver alarmReceiver = new AlarmReceiver(this);
                alarmReceiver.setRepeatAlarm(syncFrequency*60*1000, true);
            }
        }

        boolean notificationsAddItem = sPref.getBoolean("notifications_add_item", false);
        Log.d(TAG, "notifications_add_item " + notificationsAddItem);
        if(notificationsAddItem) {
            NotificationHelper notification = new NotificationHelper(this);
            notification.showAddItem();
        }

        Intent intent = getIntent();
        if(intent.getBooleanExtra("toItemAddActivity", false)) {
            startActivity(new Intent(this, ItemAddActivity.class).putExtra("place_id", -1));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        dropTable();
        setTable();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.expence_sync:
                SyncDataTask task = new SyncDataTask(this);
                task.setPostExecuteTask(new SyncDataTask.executeTask<MainActivity>(this) {
                    @Override
                    public void execute() {
                        activity.setTable();
                    }
                });
                task.execute();
                break;
            case R.id.app_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.about:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.about_title))
                        .setMessage(getString(R.string.app_name))
                        .setPositiveButton(getString(R.string.dialog_ok), null)
                        .show();
                break;
        }
        return true;
    }

    // 引数を渡せるOnClickListener
    private abstract class MyDialogCallback<T> implements DialogInterface.OnClickListener {
        protected T el;
        public MyDialogCallback(T el) { this.el = el; }
        @Override public abstract void onClick(DialogInterface dialog, int which);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.item_add:
                startActivity(new Intent(MainActivity.this, ItemAddActivity.class).putExtra("place_id", -1L));
                break;
        }
        TableElement selectElement = null;
        for(TableElement el : elements) {
            if(v == el.getTableRow()) {
                selectElement = el;
                break;
            }
        }
        if(selectElement != null) {
            // 詳細表示処理
            Log.d(TAG, " " + selectElement.getTableRow());
            String text = getString(R.string.place_name)  + " : " + selectElement.getPlaceName() +
                    "\n" + getString(R.string.datetime)   + " : " + selectElement.getDatetime() +
                    "\n" + getString(R.string.item_name)  + " : " + selectElement.getName() +
                    "\n" + getString(R.string.item_price) + " : " + selectElement.getCost() +
                    "\n" + getString(R.string.item_sync)  + " : " + selectElement.getSynced();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getString(R.string.detail_title))
                    .setMessage(text)
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .setNeutralButton(getString(R.string.copy_place), new MyDialogCallback<TableElement>(selectElement) {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            long place_id = el.getItem().getPlace().getId();
                            startActivity(new Intent(MainActivity.this, ItemAddActivity.class).putExtra("place_id", place_id));
                        }
                    })
                    .setNegativeButton(getString(R.string.remove), new MyDialogCallback<TableElement>(selectElement) {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 本当に削除していいですかダイアログ
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(getString(R.string.detail_title))
                                    .setMessage(getString(R.string.remove_verify_message))
                                    .setPositiveButton(getString(R.string.remove), new MyDialogCallback<TableElement>(el) {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Log.d(TAG, "データを削除するんじゃ^〜");
                                            removeElement(el);
                                        }
                                    })
                                    .setNegativeButton(getString(R.string.remove_verify_remain), null)
                                    .show();
                        }
                    })
                    .show();
        }
    }

    // Tableに情報をセット
    private void setTable() {
        dropTable();
        items.fetch(db, false);
        for(int i=items.size()-1; i>=0; --i) {
            Expense item = items.get(i);
            TableElement element = new TableElement(this, item);
            elements.add(element);

            layout.addView(element.getTableRow());
        }
    }

    // table初期化
    private void dropTable() {
        if(!elements.isEmpty()) {
            for(TableElement element : elements) {
                layout.removeView(element.getTableRow());
            }
            elements.clear();
        }
    }

    // 要素の削除
    private void removeElement(TableElement el) {
        Expense item = el.getItem();
        item.remove(db);
        setTable();
    }
}
