package dev.bilal.voicekey;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public class Start extends Activity {
    @Override protected void onCreate(Bundle b) { super.onCreate(b); startForegroundService(new Intent(this, KeyService.class)); finish(); }
}
