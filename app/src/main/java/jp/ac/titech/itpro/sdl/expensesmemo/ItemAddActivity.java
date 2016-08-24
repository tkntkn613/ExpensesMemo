package jp.ac.titech.itpro.sdl.expensesmemo;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ItemAddActivity extends AppCompatActivity
        implements View.OnClickListener, View.OnFocusChangeListener,
                    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static String TAG = "ItemAddActivity";

    private final static String[] PERMISSIONS = {
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
    };
    private final static int REQCODE_PERMISSIONS = 1111;

    private int curId = 0;
    // View.generateViewId代わり
    private int generateViewId() {
        while(findViewById(curId) != null) ++curId;
        Log.d(TAG, "generateId " + curId);
        return curId;
    }

    private class MenuItem {
        LinearLayout layout;
        EditText name, cost;
        Button removeButton;

        MenuItem(ItemAddActivity activity) {
            layout = new LinearLayout(activity);
            layout.setWeightSum(10.0f);

            // 商品名入力欄
            name = new EditText(activity);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    6.0f
            ));
            name.setHint(ItemAddActivity.this.getString(R.string.item_name));
            name.setInputType(InputType.TYPE_CLASS_TEXT);
            layout.addView(name);

            // 価格入力欄
            cost = new EditText(activity);
            cost.setId(activity.generateViewId());
            cost.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    3.0f
            ));
            cost.setHint(ItemAddActivity.this.getString(R.string.item_price));
            cost.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER);
            layout.addView(cost);

            // 商品名の次のフォーカスは価格
            name.setNextFocusDownId(cost.getId());

            // 削除ボタン
            removeButton = new Button(activity);
            removeButton.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f
            ));
            removeButton.setText(ItemAddActivity.this.getString(R.string.item_remove));
            removeButton.setOnClickListener(activity);
            layout.addView(removeButton);
        }

        public LinearLayout getLayout() {
            return layout;
        }

        public String getName() {
            return name.getText().toString();
        }

        public int getCost() {
            String str = cost.getText().toString();
            try {
                return Integer.valueOf(str);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        public int getRemoveButtonId() {
            return removeButton.getId();
        }
    }

    private Calendar calendar = Calendar.getInstance();
    private ArrayList<MenuItem> items = new ArrayList<>();

    private String dateFormat = "yy/MM/dd";
    private String timeFormat = "HH:mm";
    private SimpleDateFormat sdDate = new SimpleDateFormat(dateFormat);
    private SimpleDateFormat sdTime = new SimpleDateFormat(timeFormat);

    private MyAutoCompleteTextView placeName;
    private EditText dateText, timeText;
    private DatePickerDialog.OnDateSetListener date;
    private TimePickerDialog.OnTimeSetListener time;
    private boolean isDateFocus = true, isTimeFocus = true;

    private Button itemAddButton, rmAllButton;
    private Button compButton, cancelButton;

    private LinearLayout itemLayout;

    private MyDatabaseHelper db = new MyDatabaseHelper(this);

    private GoogleApiClient googleApiClient;
    double latitude, longitude;
    boolean gotLoc = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_add);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        placeName = (MyAutoCompleteTextView) findViewById(R.id.place_name);
        dateText = (EditText)findViewById(R.id.date_text);
        if(dateText != null) {
            dateText.setKeyListener(null);
            dateText.setOnClickListener(this);
            dateText.setOnFocusChangeListener(this);
        }
        timeText = (EditText)findViewById(R.id.time_text);
        if(timeText != null) {
            timeText.setKeyListener(null);
            timeText.setOnClickListener(this);
            timeText.setOnFocusChangeListener(this);
        }
        itemAddButton = (Button)findViewById(R.id.item_add);
        rmAllButton = (Button)findViewById(R.id.remove_all);
        compButton = (Button)findViewById(R.id.complete_button);
        cancelButton = (Button)findViewById(R.id.cancel_button);

        if(itemAddButton != null) itemAddButton.setOnClickListener(this);
        if(rmAllButton != null)   rmAllButton.setOnClickListener(this);
        if(compButton != null)    compButton.setOnClickListener(this);
        if(cancelButton != null)  cancelButton.setOnClickListener(this);

        date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, monthOfYear);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateLabel();
            }
        };
        time = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                updateDateLabel();
            }
        };
        itemLayout = (LinearLayout)findViewById(R.id.item_layout);

        Intent intent = getIntent();
        long place_id = intent.getLongExtra("place_id", -1);
        if(place_id != -1) {
            Places places = new Places();
            places.fetch(db);
            Place place = places.getById(place_id);
            if(place != null) {
                placeName.setText(place.getName());
            }
        }

        updateFocusInfo();

        updateDateLabel();
        addMenuItem();
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        showLastLocation(true);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.date_text:
                showDatePickerDialog();
                break;
            case R.id.time_text:
                showTimePickerDialog();
                break;
            case R.id.item_add:
                addMenuItem();
                break;
            case R.id.remove_all:
                clearMenuItem();
                break;
            case R.id.complete_button:
                completeAddItem();
                break;
            case R.id.cancel_button:
                cancelAddItem();
                break;
        }

        for(int i=0; i<items.size(); ++i) {
            if(items.get(i).getRemoveButtonId() == v.getId()) {
                items.remove(i);
                itemLayout.removeViewAt(i);
                break;
            }
        }
        if(items.isEmpty()) {
            addMenuItem();
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        Log.d(TAG, "onFocusChange " + hasFocus + " " + v.toString());
        switch (v.getId()) {
            case R.id.date_text:
                if (hasFocus && !isDateFocus) showDatePickerDialog();
                isDateFocus = hasFocus;
                break;
            case R.id.time_text:
                if (hasFocus && !isTimeFocus) showTimePickerDialog();
                isTimeFocus = hasFocus;
                break;
        }
    }

    private void updateDateLabel() {
        dateText.setText(sdDate.format(calendar.getTime()));
        timeText.setText(sdTime.format(calendar.getTime()));
    }

    private void updateFocusInfo() {
        isDateFocus = dateText.hasFocus();
        isTimeFocus = timeText.hasFocus();
    }

    private void showDatePickerDialog() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, date, year, month, dayOfMonth).show();
        updateDateLabel();
    }

    private void showTimePickerDialog() {
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        new TimePickerDialog(this, time, hourOfDay, minute, true).show();
        updateDateLabel();
    }

    // メニュー項目を追加
    private void addMenuItem() {
        MenuItem item = new MenuItem(this);
        items.add(item);
        LinearLayout layout = item.getLayout();

        itemLayout.addView(layout);
    }

    // 全削除
    private void clearMenuItem() {
        items.clear();
        itemLayout.removeAllViews();
        addMenuItem();
    }

    private void cancelAddItem() {
        finish();
    }

    // データのバリデーションチェックと保存処理
    private void completeAddItem() {
        String place_name = placeName.getText().toString();
        if(place_name.isEmpty()) {
            // お店の名前が入力されていない場合
            Toast.makeText(this, getString(R.string.toast_requires_place_name), Toast.LENGTH_LONG).show();
            return;
        }
        int count = 0;

        Places places = new Places();
        places.fetch(db);
        Place place = places.getByName(place_name);
        if(place == null) {
            // 店名が新規の場合
            place = new Place();
            place.setName(place_name);
            if(gotLoc) {
                place.setPos(latitude, longitude);
            }
            place.save(db);
        }

        for(MenuItem item : items) {
            if(item.getName().isEmpty()) {
                // 商品名が入力されていない場合は飛ばす
                continue;
            }
            Expense expense = new Expense();
            expense.setPlace(place)
                    .setCalendar(calendar)
                    .setName(item.getName())
                    .setCost(item.getCost());
            expense.save(db);
            ++count;
        }
        Log.d(TAG, "Count : " + count);
        if(count == 0) {
            // 商品が追加されていない場合はエラー
            Toast.makeText(this, getString(R.string.toast_requires_item), Toast.LENGTH_LONG).show();
            return;
        }
        finish();
    }

    private void showLastLocation(boolean reqPermission) {
        for(String permission : PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if(reqPermission) {
                    ActivityCompat.requestPermissions(this, PERMISSIONS, REQCODE_PERMISSIONS);
                } else {
                    Toast.makeText(this, getString(R.string.toast_requires_permission, permission),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        Location loc = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if(loc != null) {
            latitude = loc.getLatitude();
            longitude = loc.getLongitude();
            gotLoc = true;

            setAutocompleteSortedByLocation();
        }
        Log.d(TAG, "get position (" + latitude + ", " + longitude + ")");
    }

    private void setAutocompleteSortedByLocation() {
        Places places = new Places();
        places.fetch(db);
        List<Place> placeList = places.getPlaces();
        Collections.sort(placeList, new Comparator<Place>() {
            @Override
            public int compare(Place s, Place t) {
                double disS = s.getDistance(latitude, longitude);
                double disT = t.getDistance(latitude, longitude);
                if(disS < disT) return -1;
                if(disS > disT) return 1;
                return 0;
            }
        });
        List<String> list = new ArrayList<>();
        for(Place place : placeList) {
            Log.d(TAG, "distance " + place.getName() + " " + place.getDistance(latitude, longitude));
            list.add(place.getName());
        }

        ArrayAdapter adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        placeName.setAdapter(adapter);
        placeName.setCompletionHint(getString(R.string.place_name_autocomplete_hint, latitude, longitude));
        placeName.setThreshold(1);
    }
}
