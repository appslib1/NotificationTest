package com.notification.notification_test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.*;
import java.util.*;

final class NotificationStore {
    static final String CHANNEL = "notification_tests";
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("scheduled", Context.MODE_PRIVATE); }
    static List<JSONObject> all(Context c) {
        List<JSONObject> rows = new ArrayList<>();
        for (Object value : prefs(c).getAll().values()) {
            if (value instanceof String) try { rows.add(new JSONObject((String) value)); } catch (JSONException ignored) { }
        }
        Collections.sort(rows, (a,b) -> Long.compare(a.optLong("time"), b.optLong("time")));
        return rows;
    }
    static int nextId(Context c) {
        int id = prefs(c).getInt("next_id", 1);
        if (!prefs(c).edit().putInt("next_id", id + 1).commit()) throw new IllegalStateException(c.getString(R.string.save_error));
        return id;
    }
    static PendingIntent intent(Context c, int id) {
        return PendingIntent.getBroadcast(c, id, new Intent(c, NotificationReceiver.class).setAction("deliver").putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c, JSONObject row) {
        int id = row.optInt("id");
        if (!prefs(c).edit().putString(String.valueOf(id), row.toString()).commit()) throw new IllegalStateException(c.getString(R.string.save_error));
        AlarmManager alarms = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        long time = Math.max(System.currentTimeMillis() + 1000, row.optLong("time"));
        PendingIntent pending = intent(c, id);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                // Exact-alarm permission revoked on Android 12/13 — fall back to inexact.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, time, pending);
            }
        } catch (SecurityException e) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
        } catch (RuntimeException e) { prefs(c).edit().remove(String.valueOf(id)).commit(); throw e; }
    }

    static long nextOccurrence(long time, String repeat) {
        long now = System.currentTimeMillis();
        long step = "daily".equals(repeat) ? 86400000L
                  : "weekly".equals(repeat) ? 604800000L
                  : 0L;
        if (step == 0L) return 0L;
        long next = time + step;
        while (next <= now) next += step;
        return next;
    }
    static void cancel(Context c, int id) {
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(intent(c,id));
        prefs(c).edit().remove(String.valueOf(id)).commit();
        NotificationManagerCompat.from(c).cancel(id);
    }
    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH));
    }
    static boolean enabled(Context c) {
        channel(c);
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 26 || ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static void show(Context c, JSONObject row) {
        if (!enabled(c)) throw new SecurityException(c.getString(R.string.disabled));
        int icon = row.optInt("icon") == 1 ? R.drawable.ic_star : R.drawable.ic_notification;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(icon).setContentTitle(row.optString("title")).setContentText(row.optString("description"))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(row.optString("description")))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        String uri = row.optString("image");
        if (!uri.isEmpty()) try (java.io.InputStream input = c.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 4;
            // Determine dimensions before allocating memory for user-selected images.
            options.inJustDecodeBounds = true; BitmapFactory.decodeStream(input, null, options);
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1024 || options.outHeight / options.inSampleSize > 1024) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (java.io.InputStream data = c.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
                Bitmap bitmap = BitmapFactory.decodeStream(data, null, options);
                if (bitmap != null) b.setLargeIcon(bitmap).setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap).setSummaryText(row.optString("description")));
            }
        } catch (Exception ignored) { /* Text notification remains available if the document was removed. */ }
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) throw new SecurityException(c.getString(R.string.disabled));
        NotificationManagerCompat.from(c).notify(row.optInt("id"), b.build());
    }
}
