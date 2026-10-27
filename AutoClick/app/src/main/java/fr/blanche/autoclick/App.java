package fr.blanche.autoclick;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Dès que le processus d'AutoClick démarre (lancement, redémarrage par HyperOS,
 * démarrage de la tablette), on recoche le service s'il a été décoché.
 * Nécessite la permission WRITE_SECURE_SETTINGS accordée une fois par ADB.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ClickService.tryReenable(this);
        KeepAliveService.start(this);
    }

    /** Réveille l'appli au démarrage de la tablette. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ClickService.tryReenable(context);
            KeepAliveService.start(context);
        }
    }
}
