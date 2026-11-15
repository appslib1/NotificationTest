package com.notification.notification_test;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.json.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private TextInputEditText title, description;
    private String lastRows = "";
    private LinearLayout list;
    private Spinner icons;
    private View appearanceView;
    private BottomSheetDialog appearanceSheet, scheduleSheet;
    private TabLayout tabs;
    private TextView imageLabel, dateLabel, count;
    private FrameLayout adContainerView;
    private AdView adView;
    private String imageUri = "";
    private String repeatMode = "none";
    private long scheduledTime = System.currentTimeMillis() + 60000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { renderList(); handler.postDelayed(this, 2000); } };
    private Runnable pendingAction = null;
    private Runnable pendingExactAction = null;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> {
        Runnable action = pendingAction;
        pendingAction = null;
        if (!allowed) { toast(getString(R.string.permission_denied)); return; }
        if (action != null) action.run();
        else toast(getString(R.string.permission_granted));
    });
    private final ActivityResultLauncher<Intent> exactAlarmSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Runnable action = pendingExactAction;
        pendingExactAction = null;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && am.canScheduleExactAlarms() && action != null) action.run();
        }
    });
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            imageUri = uri.toString(); updateImage();
        } catch (SecurityException e) { toast(getString(R.string.image_error)); }
    });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationStore.channel(this);
        if (state != null) { imageUri = state.getString("image", ""); scheduledTime = state.getLong("time", scheduledTime); repeatMode = state.getString("repeat", "none"); }
        setContentView(R.layout.activity_main);
        title = findViewById(R.id.notification_title);
        description = findViewById(R.id.notification_description);
        appearanceSheet = new BottomSheetDialog(this);
        appearanceView = getLayoutInflater().inflate(R.layout.sheet_appearance, null);
        appearanceSheet.setContentView(appearanceView);
        scheduleSheet = new BottomSheetDialog(this);
        View scheduleView = getLayoutInflater().inflate(R.layout.sheet_schedule, null);
        scheduleSheet.setContentView(scheduleView);
        icons = appearanceView.findViewById(R.id.notification_icon);
        if (state != null) icons.setSelection(state.getInt("icon", 0));
        imageLabel = appearanceView.findViewById(R.id.image_status);
        dateLabel = scheduleView.findViewById(R.id.scheduled_time);
        count = findViewById(R.id.scheduled_count);
        list = findViewById(R.id.scheduled_list);
        appearanceView.findViewById(R.id.choose_image).setOnClickListener(v -> picker.launch(new String[]{"image/*"}));
        appearanceView.findViewById(R.id.remove_image).setOnClickListener(v -> { imageUri = ""; updateImage(); });
        findViewById(R.id.send_now).setOnClickListener(v -> submit(false));
        scheduleView.findViewById(R.id.choose_date).setOnClickListener(v -> chooseDate());
        scheduleView.findViewById(R.id.schedule).setOnClickListener(v -> submit(true));
        MaterialButtonToggleGroup repeatGroup = scheduleView.findViewById(R.id.repeat_group);
        repeatGroup.check("daily".equals(repeatMode) ? R.id.repeat_daily : "weekly".equals(repeatMode) ? R.id.repeat_weekly : R.id.repeat_none);
        repeatGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.repeat_daily) repeatMode = "daily";
            else if (checkedId == R.id.repeat_weekly) repeatMode = "weekly";
            else repeatMode = "none";
        });
        findViewById(R.id.notification_settings).setOnClickListener(v -> settings());
        findViewById(R.id.rate_button).setOnClickListener(v -> new RatingModal(this).openRatingDialog());
        findViewById(R.id.open_appearance).setOnClickListener(v -> appearanceSheet.show());
        findViewById(R.id.open_schedule).setOnClickListener(v -> scheduleSheet.show());
        appearanceSheet.setOnDismissListener(dialog -> updateImage());
        tabs = findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText(R.string.tab_create));
        tabs.addTab(tabs.newTab().setText(R.string.tab_scheduled));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                findViewById(R.id.compose_page).setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                findViewById(R.id.scheduled_page).setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        if (state != null) tabs.selectTab(tabs.getTabAt(state.getInt("tab", 0)));
        updateImage();
        updateDate();
        adContainerView = findViewById(R.id.ad_view_container);
        loadBanner();
    }
    private void loadBanner() {
        if (adContainerView == null) return;
        adView = new AdView(this);
        adView.setAdUnitId(getString(R.string.bannerAd));
        adView.setAdSize(getAdSize());
        adContainerView.removeAllViews();
        adContainerView.addView(adView);
        adView.loadAd(new AdRequest.Builder().build());
    }
    private AdSize getAdSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int adWidth = (int) (dm.widthPixels / dm.density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth);
    }
    private void updateImage() {
        boolean hasImage = !imageUri.isEmpty();
        appearanceView.findViewById(R.id.remove_image).setVisibility(hasImage ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.open_appearance)).setText(!hasImage && icons.getSelectedItemPosition() == 0 ? R.string.customize : R.string.customized);
        imageLabel.setText(hasImage ? getString(R.string.image_selected) : getString(R.string.no_image));
        ImageView preview = appearanceView.findViewById(R.id.image_preview);
        View placeholder = appearanceView.findViewById(R.id.image_placeholder);
        if (hasImage) {
            try { preview.setImageURI(Uri.parse(imageUri)); preview.setVisibility(View.VISIBLE); placeholder.setVisibility(View.GONE); }
            catch (Exception e) { preview.setVisibility(View.GONE); placeholder.setVisibility(View.VISIBLE); }
        } else {
            preview.setImageDrawable(null); preview.setVisibility(View.GONE); placeholder.setVisibility(View.VISIBLE);
        }
    }
    private String date(long time) { return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time)); }
    private void updateDate() { dateLabel.setText(date(scheduledTime)); }
    private void chooseDate() {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(Math.max(scheduledTime, System.currentTimeMillis()));
        DatePickerDialog dialog = new DatePickerDialog(this, (v,y,m,d) -> {
            new TimePickerDialog(this, (view,h,min) -> {
                Calendar selected = Calendar.getInstance(); selected.set(y,m,d,h,min,0); selected.set(Calendar.MILLISECOND,0);
                scheduledTime = selected.getTimeInMillis(); updateDate();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(this)).show();
        }, c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis()); dialog.show();
    }
    private boolean allowed(Runnable retry) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingAction = retry;
            permission.launch(Manifest.permission.POST_NOTIFICATIONS); return false;
        }
        if (!NotificationStore.enabled(this)) { toast(getString(R.string.enable_notifications)); settings(); return false; }
        return true;
    }
    private boolean exactAllowed(Runnable retry) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                toast(getString(R.string.exact_alarm_required));
                pendingExactAction = retry;
                try { exactAlarmSettings.launch(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (Exception ignored) { pendingExactAction = null; }
                return false;
            }
        }
        return true;
    }
    private void submit(boolean scheduled) {
        String t = title.getText().toString().trim(), d = description.getText().toString().trim();
        if (t.isEmpty()) { scheduleSheet.dismiss(); title.setError(getString(R.string.title_required)); title.requestFocus(); return; }
        if (d.isEmpty()) { scheduleSheet.dismiss(); description.setError(getString(R.string.description_required)); description.requestFocus(); return; }
        if (scheduled && scheduledTime <= System.currentTimeMillis()) { toast(getString(R.string.future_required)); return; }
        if (!allowed(() -> submit(scheduled))) return;
        if (scheduled && !exactAllowed(() -> submit(scheduled))) return;
        try {
            JSONObject row = new JSONObject().put("id",NotificationStore.nextId(this)).put("title",t).put("description",d).put("image",imageUri).put("icon",icons.getSelectedItemPosition()).put("time",scheduledTime).put("repeat", scheduled ? repeatMode : "none");
            if (scheduled) NotificationStore.schedule(this,row); else NotificationStore.show(this,row);
            if (scheduled) { scheduleSheet.dismiss(); tabs.selectTab(tabs.getTabAt(1)); }
            toast(scheduled ? getString(R.string.scheduled) : getString(R.string.sent)); renderList();
        } catch (Exception e) { toast(getString(R.string.create_error)); }
    }
    private void renderList() {
        if (list == null) return;
        List<JSONObject> rows = NotificationStore.all(this);
        String snapshot = rows.toString();
        if (snapshot.equals(lastRows)) return;
        lastRows = snapshot;
        list.removeAllViews();
        tabs.getTabAt(1).setText(getString(R.string.tab_scheduled_count, rows.size()));
        count.setText(getString(R.string.scheduled_count, rows.size()));
        findViewById(R.id.empty_list).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        for (JSONObject row : rows) {
            View card = getLayoutInflater().inflate(R.layout.item_scheduled_notification, list, false);
            ((TextView) card.findViewById(R.id.item_title)).setText(row.optString("title"));
            ((TextView) card.findViewById(R.id.item_description)).setText(row.optString("description"));
            String when = date(row.optLong("time"));
            String repeat = row.optString("repeat", "none");
            if ("daily".equals(repeat)) when += " · " + getString(R.string.repeat_daily);
            else if ("weekly".equals(repeat)) when += " · " + getString(R.string.repeat_weekly);
            ((TextView) card.findViewById(R.id.item_date)).setText(getString(
                    row.optBoolean("blocked") ? R.string.blocked_date : R.string.scheduled_date,
                    when));
            card.findViewById(R.id.item_image).setVisibility(row.optString("image").isEmpty() ? View.GONE : View.VISIBLE);
            View retry = card.findViewById(R.id.item_retry);
            retry.setVisibility(row.optBoolean("blocked") ? View.VISIBLE : View.GONE);
            Runnable retryAction = () -> {
                try {
                    NotificationStore.show(this, row);
                    NotificationStore.prefs(this).edit().remove(String.valueOf(row.optInt("id"))).commit();
                    renderList();
                } catch (SecurityException e) { toast(getString(R.string.disabled)); }
            };
            retry.setOnClickListener(v -> {
                if (!allowed(retryAction)) return;
                retryAction.run();
            });
            card.findViewById(R.id.item_cancel).setOnClickListener(v -> {
                NotificationStore.cancel(this, row.optInt("id"));
                renderList();
                toast(getString(R.string.cancelled));
            });
            list.addView(card);
        }
    }
    private void settings() {
        Intent i = Build.VERSION.SDK_INT >= 26 ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()) : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName()));
        startActivity(i);
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); if (adView != null) adView.resume(); }
    @Override protected void onDestroy() { appearanceSheet.dismiss(); scheduleSheet.dismiss(); if (adView != null) { adView.destroy(); adView = null; } super.onDestroy(); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); if (adView != null) adView.pause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("icon", icons.getSelectedItemPosition()); state.putInt("tab", tabs.getSelectedTabPosition()); state.putString("image",imageUri); state.putLong("time",scheduledTime); state.putString("repeat", repeatMode); }
}
