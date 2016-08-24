package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.ContentValues;
import android.location.Location;
import android.util.Log;

public class Place {
    private final static String TAG = "Place";
    private final static String tableName = "places";
    private long id = -1;
    private String name;
    private boolean hasPos = false;
    private double latitude, longitude;

    // setter
    public Place setId(long id) { this.id = id; return this; }
    public Place setName(String name) { this.name = name; return this; }
    public Place setPos(double latitude, double longitude) {
        hasPos = true;
        this.latitude = latitude; this.longitude = longitude; return this;
    }

    // save (INSERT)
    public boolean save(MyDatabaseHelper db) {
        ContentValues values = new ContentValues();
        if(name != null) {
            values.put("name", name);
        }
        if(hasPos) {
            values.put("latitude", latitude);
            values.put("longitude", longitude);
        }
        if(id != -1) {
            return update(db, values);
        }
        long id = db.getWritableDatabase().insert(tableName, null, values);
        if(id == -1) return false;
        setId(id);
        return true;
    }

    // 要素の更新(UPDATE)
    private boolean update(MyDatabaseHelper db, ContentValues values) {
        int count = db.getWritableDatabase()
                .update(tableName, values, "id = ?", new String[] {String.valueOf(id)});
        return count > 0;
    }

    // 要素の削除(DELETE)
    public boolean remove(MyDatabaseHelper db) {
        if(id == -1) return false;

        Expenses.removeByPlaceId(db, id);
        int result = db.getWritableDatabase()
                .delete(tableName, "id = ?", new String[] {String.valueOf(id)});
        if(result > 0) {
            id = -1;
            return true;
        }
        return false;
    }

    // getter
    public long getId() { return id; }
    public String getName() { return name; }
    public boolean isSetPos() { return hasPos; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    // 2つの緯度経度から距離を求める
    public double getDistance(double latitude, double longitude) {
        if(!isSetPos()) return Double.MAX_VALUE;
        float[] distance = new float[3];
        Location.distanceBetween(this.latitude, this.longitude, latitude, longitude, distance);

        return distance[0];
    }
}
