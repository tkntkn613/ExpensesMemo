package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public class MapActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {
    private final static String TAG = "MapActivity";
    private MyDatabaseHelper db = new MyDatabaseHelper(this);

    private GoogleApiClient googleApiClient;
    private boolean needUpdate = false;

    private EditText placeName;
    private TextView placePosText, thirdPartyText;
    private Button setPlaceButton, removePlaceButton, selectPlaceButton;
    private Places places = new Places();
    private long place_id = -1;
    private boolean firstTime = true;

    private double latitude = 0.0, longitude = 0.0;

    private final static String[] PERMISSIONS = {
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
    };
    private final static int REQCODE_PERMISSIONS = 1111;

    // 引数を渡せるOnClickListener
    private abstract class MyDialogCallback<T> implements DialogInterface.OnClickListener {
        protected T el;
        public MyDialogCallback(T el) { this.el = el; }
        @Override public abstract void onClick(DialogInterface dialog, int which);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(com.google.android.gms.location.places.Places.GEO_DATA_API)
                .addApi(com.google.android.gms.location.places.Places.PLACE_DETECTION_API)
                .addApi(LocationServices.API)
                .build();

        setPlaceButton = (Button)findViewById(R.id.place_set_button);
        if(setPlaceButton != null) {
            setPlaceButton.setOnClickListener(this);
        }
        removePlaceButton = (Button)findViewById(R.id.place_remove_button);
        if(removePlaceButton != null) {
            removePlaceButton.setOnClickListener(this);
        }
        selectPlaceButton = (Button) findViewById(R.id.place_selection_button);
        if(selectPlaceButton != null) {
            selectPlaceButton.setOnClickListener(this);
        }
        placeName = (EditText)findViewById(R.id.map_place_name);
        placePosText = (TextView)findViewById(R.id.place_pos_text);
        thirdPartyText = (TextView)findViewById(R.id.third_party_text);

        Intent intent = getIntent();
        place_id = intent.getLongExtra("place_id", -1);

        places.fetch(db);

        if(place_id != -1) {
            Place place = places.getById(place_id);
            if (place != null) {
                placeName.setText(place.getName());
                latitude = place.getLatitude();
                longitude = place.getLongitude();
            }
        } else {
            // 登録/キャンセルボタンに変更
            setPlaceButton.setText(getString(R.string.place_registration));
            removePlaceButton.setText(R.string.place_cancel);
        }

    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected");
        startLocationUpdate(true);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspented");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    private void updateLocation() {
        Log.d(TAG, "updateLocation (" + latitude + ", " + longitude + ")");
        placePosText.setText(getString(R.string.place_position_text, latitude, longitude));
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] permissions, @NonNull int[] grants) {
        Log.d(TAG, "onRequestPermissionsResult");
        switch (reqCode) {
            case REQCODE_PERMISSIONS:
                startLocationUpdate(false);
                break;
        }
    }

    private void startLocationUpdate(boolean reqPermission) {
        Log.d(TAG, "startLocationUpdate: " + reqPermission);
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                if (reqPermission)
                    ActivityCompat.requestPermissions(this, PERMISSIONS, REQCODE_PERMISSIONS);
                else
                    Toast.makeText(this, getString(R.string.toast_requires_permission, permission),
                            Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if(place_id == -1 && firstTime) {
            Location loc = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if(loc != null) {
                latitude = loc.getLatitude();
                longitude = loc.getLongitude();
            }
            firstTime = false;
        }
        updateLocation();
    }


    private static int PLACE_PICKER_REQUEST = 1;
    private static double AROUND = 0.001f;
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.place_set_button:
                if(place_id == -1) {
                    if (saveNewPlace()) {
                        Log.d(TAG, "New place saved");
                        finish();
                    }
                } else {
                    if(!isNeedUpdate()) {
                        Log.d(TAG, "No save due to no modified data");
                        finish();
                    }
                    if(updatePlace()) {
                        Log.d(TAG, "update place data");
                        finish();
                    }
                }
                break;
            case R.id.place_remove_button:
                if(place_id == -1) {
                    Log.d(TAG, "cancel addition");
                    finish();
                } else {
                    Log.d(TAG, "remove place data");
                    new AlertDialog.Builder(MapActivity.this)
                            .setTitle(getString(R.string.detail_title))
                            .setMessage(getString(R.string.place_remove_verify_message))
                            .setPositiveButton(getString(R.string.remove),
                                    new MyDialogCallback<MapActivity>(this) {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if(el.removePlace()) {
                                                el.finish();
                                            }
                                        }
                                    })
                            .setNegativeButton(getString(R.string.remove_verify_remain), null)
                            .show();
                }
                break;
            case R.id.place_selection_button:
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
                try {
                    LatLng tlLatlng = new LatLng(latitude - AROUND, longitude - AROUND);
                    LatLng brLatlng = new LatLng(latitude + AROUND, longitude + AROUND);
                    Intent intent = builder
                            .setLatLngBounds(new LatLngBounds(tlLatlng, brLatlng))
                            .build(this);
                    startActivityForResult(intent, PLACE_PICKER_REQUEST);
                } catch(GooglePlayServicesRepairableException e) {
                    Log.e(TAG, e.toString());
                } catch(GooglePlayServicesNotAvailableException e) {
                    Log.e(TAG, e.toString());
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PLACE_PICKER_REQUEST) {
            if(resultCode == RESULT_OK) {
                com.google.android.gms.location.places.Place place = PlacePicker.getPlace(this, data);
                Log.d(TAG, "" + place.getName());
                LatLng latlng = place.getLatLng();
                latitude = latlng.latitude;
                longitude = latlng.longitude;
                placeName.setText(place.getName());

                CharSequence thirdPartyAttributions = place.getAttributions();
                Log.d(TAG, "thirdPartyAttributions " + thirdPartyAttributions);
                thirdPartyText.setText(thirdPartyAttributions);

                needUpdate = true;
                updateLocation();
            }
        }
    }

    // 更新が必要かを判定
    private boolean isNeedUpdate() {
        String name = placeName.getText().toString();
        Place place = places.getById(place_id);
        return place == null || !name.equals(place.getName()) || needUpdate;
    }

    // 新規で登録
    // バリデーションチェックして登録
    private boolean saveNewPlace() {
        String name = placeName.getText().toString();
        if(places.getByName(name) != null) {
            // 既に登録されている場合
            Toast.makeText(this, getString(R.string.toast_duplicate_place_name), Toast.LENGTH_LONG).show();
            return false;
        }
        if(name.isEmpty()) {
            // 未入力
            Toast.makeText(this, getString(R.string.toast_requires_place_name), Toast.LENGTH_LONG).show();
            return false;
        }
        Place place = new Place();
        place.setName(name);
        place.setPos(latitude, longitude);
        return place.save(db);
    }

    // 更新処理
    private boolean updatePlace() {
        String name = placeName.getText().toString();
        if(name.isEmpty()) {
            // 未入力
            Toast.makeText(this, getString(R.string.toast_requires_place_name), Toast.LENGTH_LONG).show();
            return false;
        }
        Place place = places.getById(place_id);
        if(place == null) {
            Toast.makeText(this, getString(R.string.toast_place_error), Toast.LENGTH_LONG).show();
            return false;
        }

        if(!name.equals(place.getName())) {
            // 店名が異なる => かぶりチェック
            if(places.getByName(name) != null) {
                // 名前かぶり
                Toast.makeText(this, getString(R.string.toast_duplicate_place_name), Toast.LENGTH_LONG).show();
                return false;
            }
            place.setName(name);
        }
        place.setPos(latitude, longitude);
        return place.save(db);
    }

    private boolean removePlace() {
        Place place = places.getById(place_id);
        if(place == null) {
            Toast.makeText(this, getString(R.string.toast_place_error), Toast.LENGTH_LONG).show();
            return false;
        }

        return place.remove(db);
    }
}
