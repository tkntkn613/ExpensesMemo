package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 権限認証のために実行するテストタスク
 */
class SyncTestTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "SyncTestTask";
    private static final String PREF_DRIVE_FILE_ID = "GoogleDriveFileId";
    private Drive mService = null;
    private Sheets msService = null;
    private Exception mLastError = null;
    private SettingsActivity activity;

    private static final String folderType = "application/vnd.google-apps.folder";
    private static final String spreadsheetType = "application/vnd.google-apps.spreadsheet";
    // 決め打ちの名前
    private static final String folderName = "ExpensesMemo"; // フォルダ名
    private static final String fileName = "ExpensesMemo";   // spreadsheetのファイル名
    private static final String sheetName = "report";        // シート名

    public static abstract class executeTask {
        protected SettingsActivity activity;
        public executeTask(SettingsActivity activity) { this.activity = activity; }
        public abstract void execute();
    }
    private executeTask postExecuteTask = null;

    public SyncTestTask(SettingsActivity activity, GoogleAccountCredential credential) {
        this.activity = activity;
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.drive.Drive.Builder(
                transport, jsonFactory, credential)
                .setApplicationName(activity.getString(R.string.app_name))
                .build();
        msService = new Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName(activity.getString(R.string.app_name))
                .build();
    }
    public SyncTestTask setPostExecuteTask(executeTask task) {
        postExecuteTask = task;
        return this;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            if(!createFile()) {
                // ファイル作成に失敗
                Log.e(TAG, "No file created");
                return null;
            }
            Log.d(TAG, "Spreadsheet is ready");

            createSheet();
        } catch (Exception e) {
            mLastError = e;
            cancel(true);
        }
        return null;
    }

    private SharedPreferences getSharedPreference() {
        return PreferenceManager.getDefaultSharedPreferences(activity);
    }

    // ファイルの作成処理
    private boolean createFile() throws IOException {
        File file = null, folder = null;
        // フォルダの作成前にフォルダチェック
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

        if(file == null) return false;
        Log.d(TAG, "File Id is " + file.getId());
        SharedPreferences settings = getSharedPreference();
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_DRIVE_FILE_ID, file.getId());
        editor.apply();
        return true;
    }

    @Contract("null -> null")
    private Sheet findSheet(String fileId) throws IOException {
        if(fileId == null) return null;
        Sheet ret = null;
        Spreadsheet spreadsheet = msService.spreadsheets().get(fileId).execute();
        List<Sheet> sheets = spreadsheet.getSheets();
        for(Sheet sheet : sheets) {
            SheetProperties prop = sheet.getProperties();
            if (prop.getTitle().equals(sheetName)) {
                ret = sheet;
                break;
            }
        }
        if(ret != null) {
            Log.d(TAG, "sheet found! " + ret);
        }
        return ret;
    }

    // Sheetのチェック・作成
    private int createSheet() throws IOException {
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
                (Object) activity.getString(R.string.id),
                activity.getString(R.string.place_name),
                activity.getString(R.string.datetime),
                activity.getString(R.string.item_name),
                activity.getString(R.string.item_price
                )));
        range.setValues(values);
        UpdateValuesResponse vResponse = msService.spreadsheets().values()
                .update(fileId, sheetName +"!1:1", range).setValueInputOption("RAW").execute();
        Log.d(TAG, "" + vResponse);
        return sheetId;
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onCancelled() {
        if (mLastError != null) {
            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                activity.showGooglePlayServicesAvailabilityErrorDialog(
                        ((GooglePlayServicesAvailabilityIOException) mLastError)
                                .getConnectionStatusCode());
            } else if (mLastError instanceof UserRecoverableAuthIOException) {
                activity.startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        SettingsActivity.REQUEST_AUTHORIZATION);
            } else {
                Log.d(TAG, "The following error occured:\n" + mLastError.getMessage());
            }
        } else {
            Log.d(TAG, "Request Cancelled");
        }
    }

    @Override
    public void onPostExecute(Void result) {
        super.onPostExecute(result);
        if(postExecuteTask != null) {
            // 実行後タスクを実行
            postExecuteTask.execute();
        }
    }
}
