package jp.ac.titech.itpro.sdl.expensesmemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.api.Result;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendCellsRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class SyncDataTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "SynDataTask";
    private static final String PREF_DRIVE_FILE_ID = "GoogleDriveFileId";
    private Drive mService = null;
    private Sheets msService = null;
    private Context context;
    private MyDatabaseHelper db;
    private Expenses expenses;

    private HttpTransport transport = AndroidHttp.newCompatibleTransport();
    private JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private GoogleAccountCredential mCredential;

    private String dtFormat = "yyyy/MM/dd HH:mm";
    private SimpleDateFormat sdf = new SimpleDateFormat(dtFormat);

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE, "https://spreadsheets.google.com/feeds" };

    private static final String folderType = "application/vnd.google-apps.folder";
    private static final String spreadsheetType = "application/vnd.google-apps.spreadsheet";
    // 決め打ちの名前
    private static final String folderName = "ExpensesMemo"; // フォルダ名
    private static final String fileName = "ExpensesMemo";   // spreadsheetのファイル名
    private static final String sheetName = "report";        // シート名

    public static abstract class executeTask<T extends Activity> {
        protected T activity;
        public executeTask(T activity) { this.activity = activity; }
        public abstract void execute();
    }
    private executeTask postExecuteTask = null;

    public SyncDataTask(Context context) {
        this.context = context;
        this.db = new MyDatabaseHelper(context);
        mCredential = GoogleAccountCredential.usingOAuth2(context, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }
    @Override
    public Void doInBackground(Void... params) {
        if(mCredential.getSelectedAccountName() == null) {
            // アカウントが選択されていない場合、一度取りに行ってみる
            if(!getCredential()) {
                Log.d(TAG, "No Credential");
                return null;
            }
        }
        if(!hasUnsyncedData()) {
            Log.d(TAG, "No unsynced data");
            return null;
        }
        if(!checkValidFile()) {
            // 新しいファイルを作成する
            if(!createFile()) {
                // ファイル作成に失敗
                Log.e(TAG, "No file created");
                showSyncFailed();
                return null;
            }
        }
        Log.d(TAG, "Spreadsheet is ready");

        int sheetId = createSheet();
        if(sheetId != -1) {
            // sheetが準備できた
            if(syncData(sheetId)) {
                Log.d(TAG, "Data Sync Success!");
            } else {
                Log.d(TAG, "Data Sync Failed...");
                showSyncFailed();
            }
        }
        return null;
    }
    @Override
    public void onPostExecute(Void result) {
        super.onPostExecute(result);
        if(postExecuteTask != null) {
            // 実行後タスクを実行
            postExecuteTask.execute();
        }
    }

    private String getNoticeResultOption() {
        String result = getSharedPreference().getString("notifications_sync_result", null);
        Log.d(TAG, "getNoticeResultOption " + result);
        return result;
    }
    private void showSyncFailed() {
        String option = getNoticeResultOption();
        if(option != null && (option.equals("All") || option.equals("Failed"))) {
            NotificationHelper notification = new NotificationHelper(context);
            notification.showNotification(context.getString(R.string.sync_failed_text));
        }
    }

    private SharedPreferences getSharedPreference() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private boolean getCredential() {
        String accountName = getSharedPreference().getString(PREF_ACCOUNT_NAME, null);
        Log.d(TAG, "getCredential " + accountName);
        // アカウントが選択されている場合
        if(accountName != null) {
            mCredential.setSelectedAccountName(accountName);
            mService = new Drive.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(context.getString(R.string.app_name))
                    .build();
            msService = new Sheets.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(context.getString(R.string.app_name))
                    .build();
            return true;
        }
        return false;
    }

    // 有効なファイルIDが存在するか
    private boolean checkValidFile() {
        Log.d(TAG, "checkValidFile");
        String fileId = getSharedPreference().getString(PREF_DRIVE_FILE_ID, null);
        if(fileId == null) {
            Log.d(TAG, "No Valid File ID");
            return false;
        }
        try {
            File file = mService.files().get(fileId).setFields("trashed").execute();
            return !file.getTrashed();
        } catch(IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
    }

    // ファイルの作成処理
    private boolean createFile() {
        File file = null, folder = null;
        // フォルダの作成前にフォルダチェック
        try {
            String pageToken = null;
            boolean exist_dir = false;
            Log.d(TAG, "Folder Check");
            do {
                FileList list = mService.files().list()
                        .setQ("mimeType='"+ folderType +"'" +
                                " and trashed=false" +
                                " and name='" + folderName + "'" +
                                " and 'root' in parents")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();
                exist_dir |= (list.getFiles().size() > 0);
                Log.d(TAG, "count " + list.getFiles().size());
                for(File f : list.getFiles()) {
                    Log.d(TAG, "id : " + f.getId() + " : " + f.getName());
                    folder = f;
                }
                pageToken = list.getNextPageToken();
            } while(pageToken != null);
            if(!exist_dir) {
                // フォルダ作成
                Log.d(TAG, "No Folder Exists");
                File fileMetadata = new File();
                fileMetadata.setName(folderName);
                fileMetadata.setMimeType(folderType);
                folder = mService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
                Log.d(TAG, "Folder created " + folder.getId());

            }
            if(folder == null) return false;
            pageToken = null;
            boolean exist_file = false;
            Log.d(TAG, "File Check");
            do {
                FileList list = mService.files().list()
                        .setQ("mimeType='"+ spreadsheetType +"'" +
                                " and trashed=false" +
                                " and name='" + fileName + "'" +
                                " and '" + folder.getId() + "' in parents")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();
                exist_file |= (list.getFiles().size() > 1);
                Log.d(TAG, "count " + list.getFiles().size());
                for(File f : list.getFiles()) {
                    Log.d(TAG, "id : " + f.getId() + " : " + f.getName());
                    file = f;
                }
                pageToken = list.getNextPageToken();
            } while(pageToken != null);

            if(!exist_file) {
                // ファイル作成
                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setMimeType(spreadsheetType);
                fileMetadata.setParents(Collections.singletonList(folder.getId()));
                file = mService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
                Log.d(TAG, "File created " + file.getId());
            }
        } catch(IOException e) {
            Log.e(TAG, e.toString());
            return false;
        }
        if(file == null) return false;
        Log.d(TAG, "File Id is " + file.getId());
        SharedPreferences settings = getSharedPreference();
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_DRIVE_FILE_ID, file.getId());
        editor.apply();
        return true;
    }

    @Contract("null -> null")
    private Sheet findSheet(String fileId) {
        if(fileId == null) return null;
        Sheet ret = null;
        try {
            Spreadsheet spreadsheet = msService.spreadsheets().get(fileId).execute();
            List<Sheet> sheets = spreadsheet.getSheets();
            for(Sheet sheet : sheets) {
                SheetProperties prop = sheet.getProperties();
                if(prop.getTitle().equals(sheetName)) {
                    ret = sheet;
                    break;
                }
            }
        } catch(IOException e) {
            Log.e(TAG, e.toString());
        }
        if(ret != null) {
            Log.d(TAG, "sheet found! " + ret);
        }
        return ret;
    }

    // Sheetのチェック・作成
    private int createSheet() {
        String fileId = getSharedPreference().getString(PREF_DRIVE_FILE_ID, null);
        if(fileId == null) return -1;
        int sheetId = -1;
        Sheet sheet = findSheet(fileId);
        if(sheet != null) {
            Log.d(TAG, "sheet '" + sheetName + "' exists!");
            SheetProperties prop = sheet.getProperties();
            return prop.getSheetId();
        }

        // sheetが存在しない場合は新規に作成
        try {
            SheetProperties properties = new SheetProperties()
                    .setTitle(sheetName);
            AddSheetRequest sRequest = new AddSheetRequest()
                    .setProperties(properties);
            Request request = new Request()
                    .setAddSheet(sRequest);
            BatchUpdateSpreadsheetRequest ssRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(request));

            BatchUpdateSpreadsheetResponse response = msService.spreadsheets().batchUpdate(fileId, ssRequest).execute();
            Log.d(TAG, "" + response);
            Log.d(TAG, "create sheet");

            for(Response res : response.getReplies()) {
                SheetProperties prop = res.getAddSheet().getProperties();
                sheetId = prop.getSheetId();
            }

            // sheetに初期値を追加
            // 一行目にそれぞれのデータ名を書き込み
            ValueRange range = new ValueRange();
            List<List<Object>> values = Collections.singletonList(Arrays.asList(
                    (Object) context.getString(R.string.id),
                    context.getString(R.string.place_name),
                    context.getString(R.string.datetime),
                    context.getString(R.string.item_name),
                    context.getString(R.string.item_price
                    )));
            range.setValues(values);
            UpdateValuesResponse vResponse = msService.spreadsheets().values()
                    .update(fileId, sheetName +"!1:1", range).setValueInputOption("RAW").execute();
            Log.d(TAG, "" + vResponse);
        } catch(IOException e) {
            Log.e(TAG, e.toString());
            return -1;
        }
        return sheetId;
    }

    // 同期処理が必要か判定
    private boolean hasUnsyncedData() {
        expenses = new Expenses();
        expenses.fetch(db, true);
        return expenses.size() > 0;
    }

    // データの同期
    private boolean syncData(int sheetId) {
        String fileId = getSharedPreference().getString(PREF_DRIVE_FILE_ID, null);
        if(fileId == null) return false;

        if(expenses == null) {
            expenses = new Expenses();
            expenses.fetch(db, true);
        }

        List<RowData> rows = new ArrayList<>();
        for(int i=0; i<expenses.size(); ++i) {
            Expense expense = expenses.get(i);
            Place place = expense.getPlace();
            Calendar calendar = expense.getCalendar();

            List<CellData> values = new ArrayList<>();
            // id
            values.add(new CellData().setUserEnteredValue(
                    new ExtendedValue().setNumberValue((double)expense.getId())
            ));
            // 店名
            values.add(new CellData().setUserEnteredValue(
                    new ExtendedValue().setStringValue(place == null ? "" : place.getName())
            ));
            // 時刻
            values.add(new CellData().setUserEnteredValue(
                    new ExtendedValue().setStringValue(calendar == null ? "" : sdf.format(calendar.getTime()))
            ));
            // 商品名
            values.add(new CellData().setUserEnteredValue(
                    new ExtendedValue().setStringValue(expense.getName())
            ));
            // 価格
            values.add(new CellData().setUserEnteredValue(
                    new ExtendedValue().setNumberValue((double)expense.getCost())
            ));

            Log.d(TAG, "add " + expense.getName());
            rows.add(new RowData().setValues(values));
        }

        AppendCellsRequest cRequest = new AppendCellsRequest()
                .setRows(rows).setFields("*").setSheetId(sheetId);
        Request request = new Request().setAppendCells(cRequest);
        BatchUpdateSpreadsheetRequest ssRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(request));

        try {
            BatchUpdateSpreadsheetResponse response = msService.spreadsheets()
                    .batchUpdate(fileId, ssRequest).execute();
            Log.d(TAG, "" + response);
            expenses.syncComplete(db);

            String option = getNoticeResultOption();
            if(option != null && (option.equals("All") || option.equals("Success"))) {
                // Notificationで同期したことを通知する
                NotificationHelper notification = new NotificationHelper(context);
                notification.showNotification(context.getString(R.string.sync_success_text, expenses.size()));
            }
        } catch(IOException e) {
            Log.e(TAG, e.toString());
            return false;
        }
        return true;
    }

    public void setPostExecuteTask(executeTask task) {
        postExecuteTask = task;
    }
}
