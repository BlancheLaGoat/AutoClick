package fr.blanche.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

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

        // --- Réglages
        root.addView(section("2. Réglages"));
        intervalField = field(root, "Pause entre deux clics (ms, min 1)", prefs.interval());
        pressField = field(root, "Durée d'appui (ms, min 1)", prefs.press());
        countField = field(root, "Nombre de clics (0 = infini)", prefs.count());
        root.addView(note("Vitesse max théorique avec 1 ms / 1 ms : ~500 clics/s. En pratique "
                + "ça dépend du téléphone et du jeu (beaucoup d'applis ne lisent les appuis "
                + "qu'une fois par image, soit 60 à 120 fois par seconde). Le compteur sur la "
                + "bulle affiche la vitesse réelle."));

        // --- Lancer
        root.addView(section("3. Utilisation"));
        root.addView(button("Afficher la bulle", v -> {
            save();
            ClickService s = ClickService.get();
            if (s == null) {
                Toast.makeText(this, "Active d'abord le service d'accessibilité",
                        Toast.LENGTH_LONG).show();
                return;
            }
            s.showOverlay();
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
        boolean on = ClickService.get() != null;
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
