package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.ContentValues;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Expense {
    private static String tableName = "expenses";
    String datetimeFormat = "yy/MM/dd HH:mm";
    SimpleDateFormat dtFormat = new SimpleDateFormat(datetimeFormat);

    private long id = -1;
    private Place place;
    private Calendar calendar;
    private String name;
    private int cost = 0;
    private boolean synced = false;

    public Expense setId(long id) { this.id = id; return this; }
    public Expense setPlace(Place place) { this.place = place; return this; }
    public Expense setCalendar(Calendar calendar) { this.calendar = (Calendar)calendar.clone(); return this; }
    public Expense setName(String name) { this.name = name; return this; }
    public Expense setCost(int cost) { this.cost = cost; return this; }
    public Expense setSynced(boolean synced) { this.synced = synced; return this; }

    // 要素の保存(INSERT)
    public boolean save(MyDatabaseHelper db) {
        ContentValues values = new ContentValues();
        if(place != null && place.getId() != -1) {
            values.put("place_id", place.getId());
        }
        if(calendar != null) {
            values.put("datetime", dtFormat.format(calendar.getTime()));
        }
        if(name != null) values.put("name", name);
        values.put("cost", cost);
        values.put("synced", synced);
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

        int result = db.getWritableDatabase()
                .delete(tableName, "id = ?", new String[] {String.valueOf(id)});
        if(result > 0) {
            id = -1;
            return true;
        }
        return false;
    }

    public long getId() { return id; }
    public Place getPlace() { return place; }
    public Calendar getCalendar() { return calendar; }
    public String getName() { return name; }
    public int getCost() { return cost; }
    public boolean getSynced() { return synced; }
}
