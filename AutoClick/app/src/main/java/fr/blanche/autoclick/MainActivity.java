package fr.blanche.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Écran de réglages (construit en code, pas de layout XML). */
public class MainActivity extends Activity {

    private Prefs prefs;
    private TextView status;
    private EditText intervalField, pressField, countField;
    private Button batteryBtn;
    private TextView selfEnableStatus;
    private Switch humanSwitch;
    private CheckBox pausesBox;
    private EditText jitterField, varianceField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        // Notification permanente (Android 13+ demande l'autorisation d'afficher des notifs).
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        KeepAliveService.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("AutoClick");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status);

        // --- Permissions
        root.addView(section("1. Autorisations"));
        root.addView(button("Activer le service d'accessibilité", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(note("Si l'option est grisée (« paramètre restreint ») : ouvre les infos de "
                + "l'appli ci-dessous → menu ⋮ en haut à droite → « Autoriser les paramètres "
                + "restreints », puis réessaie."));
        root.addView(button("Ouvrir les infos de l'appli", v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }));
        batteryBtn = button("Désactiver l'optimisation de batterie", v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            try { startActivity(i); } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        root.addView(batteryBtn);
        root.addView(note("Évite qu'Android coupe le service en arrière-plan (c'est souvent pour "
                + "ça que les autoclickers « arrêtent de marcher »)."));
        selfEnableStatus = new TextView(this);
        selfEnableStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        selfEnableStatus.setPadding(0, dp(10), 0, dp(2));
        root.addView(selfEnableStatus);
        root.addView(note("Optionnel, une seule fois depuis un PC (ADB) : permet à AutoClick de "
                + "recocher tout seul son service quand HyperOS le décoche.\n"
                + "adb shell pm grant fr.blanche.autoclick "
                + "android.permission.WRITE_SECURE_SETTINGS"));

        // --- Réglages
        root.addView(section("2. Réglages"));
        intervalField = field(root, "Pause entre deux clics (ms, min 1)", prefs.interval());
        pressField = field(root, "Durée d'appui (ms, min 1)", prefs.press());
        countField = field(root, "Nombre de clics (0 = infini)", prefs.count());
        root.addView(note("Vitesse max théorique avec 1 ms / 1 ms : ~500 clics/s. En pratique "
                + "ça dépend du téléphone et du jeu (beaucoup d'applis ne lisent les appuis "
                + "qu'une fois par image, soit 60 à 120 fois par seconde). Le compteur sur la "
                + "bulle affiche la vitesse réelle."));

        // --- Anti-détection
        root.addView(section("3. Mode anti-détection"));
        humanSwitch = new Switch(this);
        humanSwitch.setText("Clics « humains »");
        humanSwitch.setChecked(prefs.human());
        root.addView(humanSwitch);
        jitterField = field(root, "Dispersion autour de la cible (dp)", prefs.jitter());
        varianceField = field(root, "Irrégularité du rythme (%, 0-90)", prefs.variance());
        pausesBox = new CheckBox(this);
        pausesBox.setText("Petites pauses aléatoires de temps en temps");
        pausesBox.setChecked(prefs.pauses());
        root.addView(pausesBox);
        root.addView(note("Chaque clic tombe à un endroit un peu différent, avec un rythme et une "
                + "durée d'appui irréguliers et un léger mouvement du doigt. Attention : à 1 ms, "
                + "la vitesse elle-même est surhumaine. Si une appli bloque malgré tout, monte "
                + "la pause vers 40–100 ms (10 à 25 clics/s, rythme d'un humain rapide)."));

        // --- Lancer
        root.addView(section("4. Utilisation"));
        root.addView(button("Afficher la bulle", v -> {
            save();
            ClickService s = ClickService.get();
            if (s == null || !s.showOverlay()) {
                if (ClickService.tryReenable(this)) {
                    Toast.makeText(this, "Service réactivé, la bulle va apparaître",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, ClickService.isEnabled(this)
                        ? "Le service ne répond plus : désactive puis réactive AutoClick"
                        : "Active le service AutoClick", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            moveTaskToBack(true);
        }));
        root.addView(note("Bulle : ✥ déplacer · ▶/■ lancer/arrêter · + / − ajouter/retirer une "
                + "cible · ⚙ réglages · ✕ masquer. Glisse les ronds rouges sur les endroits à "
                + "cliquer ; avec plusieurs cibles, elles sont cliquées à tour de rôle."));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ClickService.tryReenable(this);
        boolean auto = ClickService.canSelfEnable(this);
        selfEnableStatus.setText(auto ? "Réactivation automatique : active ✓"
                : "Réactivation automatique : non configurée");
        boolean on = ClickService.get() != null || ClickService.isEnabled(this);
        status.setText(on ? "● Service actif" : "○ Service inactif — active-le à l'étape 1");
        status.setTextColor(on ? 0xFF4ADE80 : 0xFFFF4D6D);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBtn.setText(ignoring ? "Optimisation de batterie : désactivée ✓"
                : "Désactiver l'optimisation de batterie");
    }

    @Override
    protected void onPause() {
        save();
        super.onPause();
    }

    private void save() {
        prefs.save(parse(intervalField, 1), parse(pressField, 1), parse(countField, 0));
        prefs.saveHuman(humanSwitch.isChecked(), parse(jitterField, 6),
                parse(varianceField, 30), pausesBox.isChecked());
    }

    private static int parse(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (NumberFormatException ex) { return def; }
    }

    // ------------------------------------------------------------ petits helpers UI

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setAlpha(0.7f);
        t.setPadding(0, dp(2), 0, dp(8));
        return t;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private EditText field(LinearLayout root, String label, int value) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setPadding(0, dp(6), 0, 0);
        root.addView(l);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setGravity(Gravity.START);
        e.setSelectAllOnFocus(true);
        root.addView(e);
        return e;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
