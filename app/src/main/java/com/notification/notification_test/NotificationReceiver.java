package com.notification.notification_test;

import android.content.*;
import org.json.*;

public class NotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            long now = System.currentTimeMillis();
            for (JSONObject row : NotificationStore.all(context)) {
                if (row.optBoolean("blocked")) continue;
                long time = row.optLong("time");
                String repeat = row.optString("repeat", "none");
                int id = row.optInt("id");
                try {
                    if (time < now) {
                        if ("none".equals(repeat)) {
                            row.put("blocked", true);
                            NotificationStore.prefs(context).edit().putString(String.valueOf(id), row.toString()).commit();
                            continue;
                        }
                        row.put("time", NotificationStore.nextOccurrence(time, repeat));
                    }
                    NotificationStore.schedule(context, row);
                } catch (JSONException ignored) { }
            }
            return;
        }
        int id = intent.getIntExtra("id", -1);
        String saved = NotificationStore.prefs(context).getString(String.valueOf(id), null);
        if (saved == null) return;
        try {
            JSONObject row = new JSONObject(saved);
            try {
                NotificationStore.show(context, row);
                String repeat = row.optString("repeat", "none");
                if ("none".equals(repeat)) {
                    NotificationStore.prefs(context).edit().remove(String.valueOf(id)).commit();
                } else {
                    row.put("time", NotificationStore.nextOccurrence(row.optLong("time"), repeat));
                    NotificationStore.schedule(context, row);
                }
            } catch (SecurityException e) {
                row.put("blocked", true);
                NotificationStore.prefs(context).edit().putString(String.valueOf(id), row.toString()).commit();
            }
        } catch (JSONException ignored) { }
    }
}
