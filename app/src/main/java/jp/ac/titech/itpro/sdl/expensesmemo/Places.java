package jp.ac.titech.itpro.sdl.expensesmemo;

import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Places {
    private final static String TAG = "Places";
    private final static String tableName = "places";
    private ArrayList<Place> array = new ArrayList<>();
    private boolean synced = false;

    private String read(Cursor cur, String name) {
        return cur.getString(cur.getColumnIndex(name));
    }
    public void fetch(MyDatabaseHelper db) {
        Cursor cur = db.getReadableDatabase().rawQuery("SELECT * FROM " + tableName, null);
        array.clear();
        try {
            while(cur.moveToNext()) {
                Place item = new Place();

                item.setId(Long.valueOf(read(cur, "id")))
                        .setName(read(cur, "name"));
                String strLat = read(cur, "latitude"), strLong = read(cur, "longitude");
                if(strLat != null && strLong != null) {
                    item.setPos(Double.valueOf(strLat), Double.valueOf(strLong));
                }
                array.add(item);
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            cur.close();
        }
        synced = true;
        Log.d(TAG, "Fetched : count = " + array.size());
    }

    public Place get(int i) {
        return array.get(i);
    }

    public Place getById(long id) {
        for(Place place : array) {
            if(place.getId() == id) {
                return place;
            }
        }
        return null;
    }

    public Place getByName(String name) {
        if(name == null) return null;
        for(Place place : array) {
            if(name.equals(place.getName())) {
                return place;
            }
        }
        return null;
    }

    public List<Place> getPlaces() {
        return (List<Place>)array.clone();
    }

    public int size() {
        return array.size();
    }

    public boolean isFetched() { return synced; }
}
