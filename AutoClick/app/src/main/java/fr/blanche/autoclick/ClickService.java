package fr.blanche.autoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Service d'accessibilité : affiche la bulle de contrôle + les cibles,
 * et injecte les appuis avec dispatchGesture().
 *
 * Astuce vitesse : au lieu d'envoyer un geste par clic (aller-retour système
 * de plusieurs ms à chaque fois), on regroupe jusqu'à 10 appuis dans UN seul
 * geste, chacun décalé dans le temps. Ça permet de vraiment tenir des écarts
 * de 1 ms à l'intérieur d'un lot.
 */
public class ClickService extends AccessibilityService {

    private static ClickService instance;

    static ClickService get() { return instance; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private Prefs prefs;

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView playBtn;
    private TextView counter;

    private final List<TextView> targets = new ArrayList<>();
    private final List<WindowManager.LayoutParams> targetLps = new ArrayList<>();

    // État du clic auto
    private boolean running;
    private float[][] points;      // centres des cibles en coordonnées écran
    private int nextTarget;
    private long clicksDone;
    private long startedAt;
    private int interval, press, limit;

    // ------------------------------------------------------------ cycle de vie

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = new Prefs(this);
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* rien à lire */ }

    @Override
    public void onInterrupt() { stopClicking(); }

    @Override
    public boolean onUnbind(Intent intent) {
        stopClicking();
        hideOverlay();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopClicking();
        hideOverlay();
        instance = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------ overlay

    boolean isOverlayShown() { return panel != null; }

    void showOverlay() {
        if (panel != null) return;
        buildPanel();
        List<int[]> saved = prefs.targets();
        if (saved.isEmpty()) {
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            addTarget(w / 2 - dp(24), h / 2 - dp(24));
        } else {
            for (int[] p : saved) addTarget(p[0], p[1]);
        }
    }

    void hideOverlay() {
        stopClicking();
        saveTargets();
        for (View t : targets) safeRemove(t);
        targets.clear();
        targetLps.clear();
        if (panel != null) safeRemove(panel);
        panel = null;
    }

    private void safeRemove(View v) {
        try { wm.removeView(v); } catch (Exception ignored) { }
    }

    private WindowManager.LayoutParams overlayParams(int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61E1B2E);
        bg.setCornerRadius(dp(14));
        panel.setBackground(bg);

        TextView handle = panelButton("✥");
        handle.setTextColor(0xFF9A96B5);
        playBtn = panelButton("▶");
        playBtn.setTextColor(0xFF4ADE80);
        TextView plus = panelButton("+");
        TextView minus = panelButton("−");
        TextView settings = panelButton("⚙");
        TextView close = panelButton("✕");
        counter = new TextView(this);
        counter.setTextColor(0xFFCCCCDD);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        counter.setGravity(Gravity.CENTER);
        counter.setText("0");

        panel.addView(handle);
        panel.addView(playBtn);
        panel.addView(counter);
        panel.addView(plus);
        panel.addView(minus);
        panel.addView(settings);
        panel.addView(close);

        playBtn.setOnClickListener(v -> { if (running) stopClicking(); else startClicking(); });
        plus.setOnClickListener(v -> {
            if (running) return;
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            int off = targets.size() * dp(20);
            addTarget(w / 2 - dp(24) + off, h / 2 - dp(24) + off);
            saveTargets();
        });
        minus.setOnClickListener(v -> {
            if (running || targets.size() <= 1) return;
            int last = targets.size() - 1;
            safeRemove(targets.remove(last));
            targetLps.remove(last);
            saveTargets();
        });
        settings.setOnClickListener(v -> {
            stopClicking();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        close.setOnClickListener(v -> hideOverlay());

        panelLp = overlayParams(prefs.panelX(), prefs.panelY());
        handle.setOnTouchListener(new DragListener(panel, panelLp, () ->
                prefs.savePanel(panelLp.x, panelLp.y)));
        wm.addView(panel, panelLp);
    }

    private TextView panelButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addTarget(int x, int y) {
        TextView t = new TextView(this);
        t.setText(String.valueOf(targets.size() + 1));
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x88FF4D6D);
        circle.setStroke(dp(2), 0xFFFF4D6D);
        t.setBackground(circle);
        WindowManager.LayoutParams lp = overlayParams(x, y);
        lp.width = dp(48);
        lp.height = dp(48);
        t.setOnTouchListener(new DragListener(t, lp, this::saveTargets));
        wm.addView(t, lp);
        targets.add(t);
        targetLps.add(lp);
    }

    private void saveTargets() {
        List<int[]> pts = new ArrayList<>();
        for (WindowManager.LayoutParams lp : targetLps) pts.add(new int[]{lp.x, lp.y});
        if (!pts.isEmpty()) prefs.saveTargets(pts);
    }

    /** Laisse passer (ou non) les appuis à travers les cibles. */
    private void setTargetsPassThrough(boolean passThrough) {
        for (int i = 0; i < targets.size(); i++) {
            WindowManager.LayoutParams lp = targetLps.get(i);
            if (passThrough) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            View v = targets.get(i);
            v.setAlpha(passThrough ? 0.45f : 1f);
            try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------ moteur de clic

    void startClicking() {
        if (running || targets.isEmpty()) return;
        interval = prefs.interval();
        press = prefs.press();
        limit = prefs.count();

        points = new float[targets.size()][2];
        int[] loc = new int[2];
        for (int i = 0; i < targets.size(); i++) {
            View v = targets.get(i);
            v.getLocationOnScreen(loc);
            points[i][0] = loc[0] + v.getWidth() / 2f;
            points[i][1] = loc[1] + v.getHeight() / 2f;
        }

        setTargetsPassThrough(true);
        running = true;
        nextTarget = 0;
        clicksDone = 0;
        startedAt = SystemClock.uptimeMillis();
        playBtn.setText("■");
        playBtn.setTextColor(0xFFFF4D6D);
        main.post(counterTick);
        dispatchBatch();
    }

    void stopClicking() {
        if (!running) return;
        running = false;
        main.removeCallbacksAndMessages(null);
        if (playBtn != null) {
            playBtn.setText("▶");
            playBtn.setTextColor(0xFF4ADE80);
        }
        updateCounter();
        setTargetsPassThrough(false);
    }

    private final Runnable dispatchRunnable = this::dispatchBatch;

    private void dispatchBatch() {
        if (!running) return;

        long period = (long) press + interval;   // temps entre deux appuis
        int maxStrokes = GestureDescription.getMaxStrokeCount(); // 10 en général
        // Lots courts (≈ 100 ms max) pour que le bouton stop reste réactif.
        int n = (int) Math.max(1, Math.min(maxStrokes, 100 / period));
        if (limit > 0) n = (int) Math.min(n, limit - clicksDone);
        if (n <= 0) { stopClicking(); return; }

        GestureDescription.Builder gb = new GestureDescription.Builder();
        for (int k = 0; k < n; k++) {
            float[] p = points[nextTarget];
            nextTarget = (nextTarget + 1) % points.length;
            Path path = new Path();
            path.moveTo(p[0], p[1]);
            gb.addStroke(new GestureDescription.StrokeDescription(path, k * period, press));
        }
        final int batch = n;

        boolean ok = dispatchGesture(gb.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                clicksDone += batch;
                if (!running) return;
                if (limit > 0 && clicksDone >= limit) { stopClicking(); return; }
                if (interval <= 2) dispatchBatch();          // enchaîne direct
                else main.postDelayed(dispatchRunnable, interval);
            }

            @Override
            public void onCancelled(GestureDescription g) {
                // Annulé (ex : tu as touché l'écran) → on reprend un peu après.
                if (running) main.postDelayed(dispatchRunnable, 30);
            }
        }, main);

        if (!ok) {
            stopClicking();
            Toast.makeText(this, "Le système a refusé le geste", Toast.LENGTH_SHORT).show();
        }
    }

    private final Runnable counterTick = new Runnable() {
        @Override
        public void run() {
            updateCounter();
            if (running) main.postDelayed(this, 250);
        }
    };

    private void updateCounter() {
        if (counter == null) return;
        long elapsed = Math.max(1, SystemClock.uptimeMillis() - startedAt);
        long cps = running ? clicksDone * 1000 / elapsed : 0;
        counter.setText(running ? clicksDone + "\n" + cps + "/s" : String.valueOf(clicksDone));
    }

    // ------------------------------------------------------------ utilitaires

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    /** Déplacement d'une vue overlay au doigt. */
    private final class DragListener implements View.OnTouchListener {
        private final View view;
        private final WindowManager.LayoutParams lp;
        private final Runnable onDrop;
        private final int slop = ViewConfiguration.get(ClickService.this).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean dragging;

        DragListener(View view, WindowManager.LayoutParams lp, Runnable onDrop) {
            this.view = view;
            this.lp = lp;
            this.onDrop = onDrop;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        lp.x = startX + Math.round(dx);
                        lp.y = startY + Math.round(dy);
                        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging && onDrop != null) onDrop.run();
                    return true;
            }
            return false;
        }
    }
}
