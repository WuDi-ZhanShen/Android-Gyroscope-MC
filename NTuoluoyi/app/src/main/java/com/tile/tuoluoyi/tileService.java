package com.tile.tuoluoyi;


import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class tileService extends TileService {


    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(tuoluoyiService.isServiceOK ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
        super.onStartListening();
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (tile.getState() == Tile.STATE_ACTIVE) {
            startActivityAndCollapse(new Intent(this, serviceStop.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            startActivityAndCollapse(new Intent(this, serviceStart.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            tile.setState(Tile.STATE_ACTIVE);
        }
        tile.updateTile();
        super.onClick();


    }

}
