package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class Expenses {
    private final static String tableName = "expenses";
    private static String TAG = "Expenses";
    private ArrayList<Expense> array = new ArrayList<>();
    private boolean synced = false;

    private String read(Cursor cur, String name) {
        return cur.getString(cur.getColumnIndex(name));
    }

    private Calendar getCalendar(String str) {
        SimpleDateFormat format = new SimpleDateFormat("yy/MM/dd HH:mm");
        Calendar calendar;
        try {
            Date date = format.parse(str);
            calendar = Calendar.getInstance();
            calendar.setTime(date);
        } catch(Exception e) {
            e.printStackTrace();
            calendar = null;
        }
        return calendar;
    }

    public void fetch(MyDatabaseHelper db, boolean unsynced) {
        Cursor cur;
        if(unsynced) {
            cur = db.getReadableDatabase().rawQuery("SELECT * FROM " + tableName + " WHERE synced = 0", null);
        } else {
            cur = db.getReadableDatabase().rawQuery("SELECT * FROM " + tableName, null);
        }
        array.clear();
        Places places = new Places();
        places.fetch(db);
        try {
            while(cur.moveToNext()) {
                Expense item = new Expense();

                item.setId(Long.valueOf(read(cur, "id")))
                        .setName(read(cur, "name"))
                        .setCalendar(getCalendar(read(cur, "datetime")))
                        .setCost(Integer.valueOf(read(cur, "cost")))
                        .setSynced(!read(cur, "synced").equals("0"));

                String place_id = read(cur, "place_id");
                if(place_id != null) {
                    Place place = places.getById(Integer.valueOf(place_id));
                    item.setPlace(place);
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

    public boolean syncComplete(MyDatabaseHelper db) {
        Log.d(TAG, "syncComplete");
        StringBuilder builder = new StringBuilder();
        for(Expense expense : array) {
            if(builder.length() > 0) {
                builder.append(",");
            }
            builder.append(expense.getId());
        }
        ContentValues values = new ContentValues();
        values.put("synced", true);
        int count = db.getWritableDatabase()
                .update(tableName, values, "id IN (" +builder.toString() + ")", null);
        return count > 0;
    }

    public static boolean removeByPlaceId(MyDatabaseHelper db, long place_id) {
        db.getWritableDatabase()
                .delete(tableName, "place_id = ?", new String[] {String.valueOf(place_id)});
        return true;
    }

    public Expense get(int i) {
        return array.get(i);
    }

    public int size() {
        return array.size();
    }

    public boolean isFetched() { return synced; }
}
