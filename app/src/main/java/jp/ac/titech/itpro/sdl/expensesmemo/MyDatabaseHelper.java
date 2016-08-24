package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MyDatabaseHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "appdata.db";
    private static final String TAG = "MyDatabaseHelper";

    public MyDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        Log.d(TAG, "DB Constructor");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // expensesテーブル
        db.execSQL(
                "CREATE TABLE expenses (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "place_id INTEGER NOT NULL," +
                        "datetime TEXT," +
                        "name TEXT," +
                        "cost INTEGER," +
                        "synced INTEGER," +
                        "latitude REAL, " +
                        "longitude REAL," +
                        "FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE" +
                        ")"
        );
        // placesテーブル
        db.execSQL(
                "CREATE TABLE places (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT, " +
                        "latitude REAL, " +
                        "longitude REAL" +
                        ")"
        );
        Log.d(TAG, "DB Create");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        drop(db);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    private void drop(SQLiteDatabase db) {
        Log.d(TAG, "Table Drop");
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS places");
    }
}
