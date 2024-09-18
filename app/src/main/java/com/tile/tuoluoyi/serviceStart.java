package com.tile.tuoluoyi;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class serviceStart extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_NoDisplay);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled() && getSharedPreferences("data", 0).getBoolean("foreground", true)) return;

        final String serviceName = new ComponentName(getPackageName(), tuoluoyiService.class.getName()).flattenToString();
        final String oldSetting = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        final String newSetting = oldSetting == null ? serviceName : serviceName + ":" + oldSetting;
        try {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newSetting);
        } catch (Exception ignored) {
        }
    }


    @Override
    protected void onResume() {
        finish();
        super.onResume();
    }
}