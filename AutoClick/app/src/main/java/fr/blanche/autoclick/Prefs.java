package fr.blanche.autoclick;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Réglages persistants de l'appli. */
final class Prefs {
    private static final String FILE = "autoclick";
    private static final String K_INTERVAL = "interval_ms";
    private static final String K_PRESS = "press_ms";
    private static final String K_COUNT = "count";
    private static final String K_TARGETS = "targets";
    private static final String K_PANEL_X = "panel_x";
    private static final String K_PANEL_Y = "panel_y";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Pause entre le relâchement d'un clic et l'appui suivant (ms, min 1). */
    int interval() { return Math.max(1, sp.getInt(K_INTERVAL, 1)); }

    /** Durée pendant laquelle le doigt virtuel reste appuyé (ms, min 1). */
    int press() { return Math.max(1, sp.getInt(K_PRESS, 1)); }

    /** Nombre total de clics avant arrêt auto (0 = infini). */
    int count() { return Math.max(0, sp.getInt(K_COUNT, 0)); }

    void save(int interval, int press, int count) {
        sp.edit()
                .putInt(K_INTERVAL, Math.max(1, interval))
                .putInt(K_PRESS, Math.max(1, press))
                .putInt(K_COUNT, Math.max(0, count))
                .apply();
    }

    int panelX() { return sp.getInt(K_PANEL_X, 0); }
    int panelY() { return sp.getInt(K_PANEL_Y, 300); }

    void savePanel(int x, int y) {
        sp.edit().putInt(K_PANEL_X, x).putInt(K_PANEL_Y, y).apply();
    }

    /** Positions (x,y) des cibles, en coordonnées de fenêtre. */
    List<int[]> targets() {
        List<int[]> out = new ArrayList<>();
        String raw = sp.getString(K_TARGETS, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            String[] xy = part.split(",");
            if (xy.length != 2) continue;
            try {
                out.add(new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    void saveTargets(List<int[]> pts) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : pts) {
            if (sb.length() > 0) sb.append(';');
            sb.append(p[0]).append(',').append(p[1]);
        }
        sp.edit().putString(K_TARGETS, sb.toString()).apply();
    }
}
