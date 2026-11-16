package dev.bilal.voicekey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Asks the head unit's car service (com.bw.car) to deliver the steering-wheel voice key to this app,
 * the same way the vendor voice app registers for it, and opens the camera when it is pressed.
 * Talks to the service's binder interfaces directly (bw.car.ICar, bw.car.input.ICarInput).
 */
public class KeyService extends Service implements ServiceConnection {
    static final String TAG = "VoiceKey";
    static final int KEYCODE_CAR_VR = 1050;
    static final int PRIORITY_HIGH = 3;
    static final String ICAR = "bw.car.ICar", IINPUT = "bw.car.input.ICarInput", ILISTENER = "bw.car.input.ICarInputListener";

    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("vk", "Voice key", NotificationManager.IMPORTANCE_MIN));
        startForeground(7, new Notification.Builder(this, "vk").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Voice key opens camera").build());
        listener = new Listener(this);
        bind();
    }

    void bind() {
        Intent i = new Intent(ICAR).setComponent(new ComponentName("com.bw.car", "com.bw.car.CarService"));
        boolean ok = bindService(i, this, Context.BIND_AUTO_CREATE);
        Log.e(TAG, "bind car service -> " + ok);
    }

    @Override public void onServiceConnected(ComponentName n, IBinder car) {
        try {
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            d.writeInterfaceToken(ICAR); d.writeString("car_input");
            car.transact(2, d, r, 0); r.readException();          // ICar.getCarService
            IBinder input = r.readStrongBinder();
            d.recycle(); r.recycle();
            if (input == null) { Log.e(TAG, "no car_input service"); return; }
            d = Parcel.obtain(); r = Parcel.obtain();
            d.writeInterfaceToken(IINPUT);
            d.writeStrongBinder(listener);
            d.writeIntArray(new int[]{KEYCODE_CAR_VR});
            d.writeIntArray(new int[]{PRIORITY_HIGH});
            input.transact(2, d, r, 0); r.readException();         // ICarInput.registerInputListener
            d.recycle(); r.recycle();
            Log.e(TAG, "registered for voice key");
        } catch (Exception e) { Log.e(TAG, "register failed", e); }
    }

    @Override public void onServiceDisconnected(ComponentName n) { Log.e(TAG, "car service gone"); }

    static final long MULTI_PRESS_GAP_MS = 350;
    volatile int presses;
    private Fire fire;

    static final long LONG_PRESS_MS = 600;
    private Hold hold;
    volatile boolean held, isDown;

    void down() {
        if (isDown) return;                      // ignore key repeats
        isDown = true; held = false;
        if (hold == null) hold = new Hold(this);
        h.removeCallbacks(hold);
        h.postDelayed(hold, LONG_PRESS_MS);
    }

    void up() {
        isDown = false;
        if (hold != null) h.removeCallbacks(hold);
        if (held) { held = false; return; }      // the long press already fired
        press();
    }

    void onLongPress() {
        Log.e(TAG, "long press -> home");
        held = true; presses = 0; FrontTap.armedUntil = 0;
        if (fire != null) h.removeCallbacks(fire);
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { Log.e(TAG, "home start failed", e); }
    }

    void press() {
        presses++;
        if (fire == null) fire = new Fire(this);
        h.removeCallbacks(fire);
        h.postDelayed(fire, MULTI_PRESS_GAP_MS);
    }

    void onDoublePress() {
        Log.e(TAG, "double press -> CarPlay");
        FrontTap.armedUntil = 0;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName("app.carplayscreen", "app.carplayscreen.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { Log.e(TAG, "carplay start failed", e); }
    }

    void onVoiceKey() {
        Log.e(TAG, "voice key -> front camera");
        FrontTap.arm();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName("com.bw.adas", "com.bw.adas.reverse.CameraActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { Log.e(TAG, "camera start failed", e); }
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    /** bw.car.input.ICarInputListener: transaction 2 = onKeyEvent(KeyEvent). */
    static class Listener extends Binder {
        final KeyService s;
        Listener(KeyService s) { this.s = s; attachInterface(null, ILISTENER); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code == 2) {
                data.enforceInterface(ILISTENER);
                KeyEvent ev = data.readInt() != 0 ? KeyEvent.CREATOR.createFromParcel(data) : null;
                if (reply != null) reply.writeNoException();
                Log.e(TAG, "key " + ev);
                if (ev != null && ev.getKeyCode() == KEYCODE_CAR_VR) {
                    if (ev.getAction() == KeyEvent.ACTION_DOWN) s.down(); else if (ev.getAction() == KeyEvent.ACTION_UP) s.up();
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
    /** Counts presses; fires once no further press arrives within the gap. */
    static class Fire implements Runnable {
        final KeyService s; Fire(KeyService s) { this.s = s; }
        public void run() { int n = s.presses; s.presses = 0; if (n >= 2) s.onDoublePress(); else s.onVoiceKey(); }
    }
    /** Fires while the key is still held down. */
    static class Hold implements Runnable {
        final KeyService s; Hold(KeyService s) { this.s = s; }
        public void run() { if (s.isDown) s.onLongPress(); }
    }
}
