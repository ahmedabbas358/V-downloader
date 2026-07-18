package com.junkfood.seal

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickDownloadTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.quick_download_tile_label)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickDownloadActivity::class.java).apply {
            action = "com.junkfood.seal.action.QUICK_DOWNLOAD"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
