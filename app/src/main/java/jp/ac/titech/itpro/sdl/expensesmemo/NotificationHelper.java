package jp.ac.titech.itpro.sdl.expensesmemo;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

public class NotificationHelper {
    private NotificationManagerCompat manager;
    private Context context;

    public NotificationHelper(Context context) {
        this.context = context;
        manager = NotificationManagerCompat.from(context);
    }

    public void showAddItem() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("toItemAddActivity", true);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 613613, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.common_full_open_on_phone)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notifications_item_add_text))
                .setOngoing(true)
                .setContentIntent(contentIntent);
        manager.notify(0, builder.build());
    }

    public void showNotification(String str) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 613614, intent, PendingIntent.FLAG_ONE_SHOT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.ic_info_black_24dp)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(str)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        manager.notify(1, builder.build());
    }

    public void cancel() {
        manager.cancel(0);
    }
    public void cancelSync() { manager.cancel(1); }
}
