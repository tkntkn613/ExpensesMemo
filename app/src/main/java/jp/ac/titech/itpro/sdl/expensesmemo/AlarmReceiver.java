package jp.ac.titech.itpro.sdl.expensesmemo;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static PendingIntent sender;
    private static AlarmManager manager;

    public AlarmReceiver(Activity activity) {
        Intent intent = new Intent(activity, AlarmReceiver.class);
        sender = PendingIntent.getBroadcast(activity, 0, intent, 0);

        manager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
    }

    public AlarmReceiver() {}

    // 一度のみalarmを起動する
    public void setAlarm(int second) {
        if(sender != null) cancel();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, second);

        manager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), sender);
    }

    // 繰り返しalarmを起動する
    public void setRepeatAlarm(long term_second, boolean immediately) {
        if(sender != null) cancel();

        long firstTime = SystemClock.elapsedRealtime();
        if(!immediately) {
            firstTime += term_second;
        }
        manager.setRepeating(AlarmManager.ELAPSED_REALTIME, firstTime, term_second, sender);
        Log.d(TAG, "set repeating alarm " + String.valueOf(term_second));
    }

    public void cancel() {
        if(manager != null) {
            manager.cancel(sender);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received : " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0));

        SyncDataTask task = new SyncDataTask(context);
        task.execute();
    }
}
