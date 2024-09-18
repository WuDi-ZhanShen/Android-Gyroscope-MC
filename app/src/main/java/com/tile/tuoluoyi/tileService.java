package com.tile.tuoluoyi;


import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class tileService extends TileService {


    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        String set = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        tile.setState(set != null && set.contains(getPackageName()) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
        super.onStartListening();
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (tile.getState() == Tile.STATE_INACTIVE) {
            final String serviceName = new ComponentName(getPackageName(), tuoluoyiService.class.getName()).flattenToString();
            final String oldSetting = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            final String newSetting = oldSetting == null ? serviceName : serviceName + ":" + oldSetting;
            try {
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newSetting);
            } catch (Exception e) {
                Bundle bundle = new Bundle();
                bundle.putString(":settings:fragment_args_key", serviceName);
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(":settings:fragment_args_key", serviceName).putExtra(":settings:show_fragment_args", bundle));
            }
        } else {
            sendBroadcast(new Intent("intent.tuoluoyi.exit"));
        }

        tile.setState(tile.getState() == Tile.STATE_ACTIVE ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.updateTile();
        startActivityAndCollapse(new Intent(this, serviceStop.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        super.onClick();


    }

}
