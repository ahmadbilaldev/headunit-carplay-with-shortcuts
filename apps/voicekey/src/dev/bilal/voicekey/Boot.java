package dev.bilal.voicekey;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class Boot extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { c.startForegroundService(new Intent(c, KeyService.class)); }
}
