package dev.bilal.voicekey;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * The car camera app always opens on the reverse camera. After the voice key opens it, this does the
 * two taps a person would: tap the picture to reveal the camera icons, then press the front one.
 * Scoped to com.bw.adas and only active for a few seconds after a voice-key press.
 */
public class FrontTap extends AccessibilityService implements Runnable {
    static volatile long armedUntil;            // set by KeyService
    static volatile FrontTap self;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastReveal, lastClick;
    static volatile long notBefore;

    static void arm() { long t = System.currentTimeMillis(); armedUntil = t + 15000; notBefore = t + 1500; FrontTap s = self; if (s != null) s.h.postDelayed(s, 1600); }

    @Override protected void onServiceConnected() { self = this; Log.e(KeyService.TAG, "front-tap ready"); }
    @Override public boolean onUnbind(android.content.Intent i) { self = null; return super.onUnbind(i); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { step(); }
    @Override public void onInterrupt() {}
    @Override public void run() { step(); if (System.currentTimeMillis() < armedUntil) h.postDelayed(this, 500); }

    private void step() {
        long now = System.currentTimeMillis();
        if (now > armedUntil || now < notBefore) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !"com.bw.adas".contentEquals(root.getPackageName())) return;
        AccessibilityNodeInfo front = first(root, "com.bw.adas:id/camera_front");
        if (front != null && front.isVisibleToUser()) {
            // The app disables the icon of the camera that is showing: disabled front icon = done.
            if (!front.isEnabled()) { Log.e(KeyService.TAG, "front camera is showing"); armedUntil = 0; return; }
            if (now - lastClick < 1800) return;   // a switch takes a moment; too-early clicks are dropped by the app
            lastClick = now;
            Log.e(KeyService.TAG, "front camera click -> " + front.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            return;
        }
        if (now - lastReveal < 1200) return;
        AccessibilityNodeInfo view = first(root, "com.bw.adas:id/reverse_view");
        if (view != null) { lastReveal = now; Log.e(KeyService.TAG, "reveal icons -> " + view.performAction(AccessibilityNodeInfo.ACTION_CLICK)); }
    }

    private static AccessibilityNodeInfo first(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
        return l == null || l.isEmpty() ? null : l.get(0);
    }
}
