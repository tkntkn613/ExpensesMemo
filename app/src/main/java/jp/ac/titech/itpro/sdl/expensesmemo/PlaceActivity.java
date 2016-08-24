package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class PlaceActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener {
    private final static String TAG = "PlaceActivity";
    private MyDatabaseHelper db = new MyDatabaseHelper(this);

    private ListView placeList;
    private Button addPlace;
    private Places places = new Places();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place);

        placeList = (ListView)findViewById(R.id.place_list);
        if(placeList != null) {
            placeList.setOnItemClickListener(this);
        }
        addPlace = (Button)findViewById(R.id.add_place_button);
        if(addPlace != null) {
            addPlace.setOnClickListener(this);
        }

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        setPlaceList();
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.add_place_button:
                startActivity(new Intent(this, MapActivity.class)
                        .putExtra("place_id", -1L)
                );
                break;
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        Place place = places.get(pos);
        if(place == null) return;

        Log.d(TAG, "click " + place.getName());
        startActivity(new Intent(this, MapActivity.class)
                .putExtra("place_id", place.getId()))
        ;
    }

    private void setPlaceList() {
        clearList();
        places.fetch(db);
        List<String> pList = new ArrayList<>();
        for(int i=0; i<places.size(); ++i) {
            Place place = places.get(i);
            if(place.getName() != null) {
                pList.add(getString(R.string.place_list_element, place.getName(), place.getLatitude(), place.getLongitude()));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, pList);
        placeList.setAdapter(adapter);
    }

    private void clearList() {
        placeList.setAdapter(null);
    }
}
