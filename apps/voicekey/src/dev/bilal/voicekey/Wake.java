package dev.bilal.voicekey;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Boot anchor. This firmware force-stops third-party apps at shutdown, and a force-stopped app gets no
 * BOOT_COMPLETED. The system does re-bind enabled notification listeners at boot, so this listener
 * (which ignores notifications) is what starts the key service again.
 */
public class Wake extends NotificationListenerService {
    @Override public void onListenerConnected() {
        Log.e(KeyService.TAG, "woken by the system");
        try { startForegroundService(new Intent(this, KeyService.class)); } catch (Exception e) { Log.e(KeyService.TAG, "start failed", e); }
    }
}
