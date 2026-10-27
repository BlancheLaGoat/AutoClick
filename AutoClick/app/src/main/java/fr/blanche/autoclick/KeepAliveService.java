package fr.blanche.autoclick;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Service au premier plan (notification permanente) :
 *  - HyperOS / MIUI tuent beaucoup moins un processus qui en a un ;
 *  - chien de garde toutes les 5 s : si le service d'accessibilité a été décoché
 *    ou ne répond plus, on le relance (nécessite WRITE_SECURE_SETTINGS via ADB).
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL = "keepalive";
    private static final int NOTIF_ID = 1;
    private static final long CHECK_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int deadChecks;

    /** Démarre le service si possible, sans jamais planter. */
    static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Démarrage refusé en arrière-plan : on réessaiera au prochain lancement.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "AutoClick actif",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification n = b
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("AutoClick est actif")
                .setContentText("Garde le service d'accessibilité en vie")
                .setContentIntent(open)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return;
        }
        handler.post(watchdog);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;   // relancé par le système s'il est tué
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    private void check() {
        Context c = this;
        if (!ClickService.canSelfEnable(c)) return;

        if (!ClickService.isEnabled(c)) {
            // Décoché (par HyperOS) → on recoche.
            ClickService.tryReenable(c);
            deadChecks = 0;
            return;
        }
        if (ClickService.get() != null) {
            deadChecks = 0;
            return;
        }
        // Coché mais le système ne l'a pas relié depuis ~15 s → on le décoche
        // puis le recoche pour forcer Android à le redémarrer.
        if (++deadChecks >= 3) {
            deadChecks = 0;
            forceRebind(c);
        }
    }

    private static void forceRebind(Context c) {
        try {
            String me = new ComponentName(c, ClickService.class).flattenToString();
            String list = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) list = "";
            StringBuilder others = new StringBuilder();
            for (String s : list.split(":")) {
                ComponentName cn = ComponentName.unflattenFromString(s);
                if (s.isEmpty() || (cn != null && cn.flattenToString().equals(me))) continue;
                if (others.length() > 0) others.append(':');
                others.append(s);
            }
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.toString());
            ClickService.tryReenable(c);
        } catch (Exception ignored) {
        }
    }
}
