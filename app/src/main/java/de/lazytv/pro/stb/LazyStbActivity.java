package de.lazytv.pro.stb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Dedicated LazySTB shell. This module deliberately owns its profile/session UI
 * instead of reusing LazyTV playlist/player screens. Portal transport and the
 * dedicated STB player are attached here in follow-up steps.
 */
public final class LazyStbActivity extends Activity {
    private static final String PREFS = "lazy_stb_profiles";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_PORTAL = "portal_url";
    private static final String KEY_MAC = "mac_address";

    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView status;
    private Button connect;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildScreen();
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackground(gradient(Color.rgb(2, 8, 26), Color.rgb(7, 43, 73), 0));

        TextView title = label("LazySTB", 28, Color.WHITE, true);
        TextView subtitle = label("MAG / Stalker Portal • profil i pavarur nga LazyTV", 14, Color.rgb(145, 205, 235), false);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(gradient(Color.rgb(7, 30, 52), Color.rgb(3, 18, 34), dp(18)));

        TextView profile = label(profileSummary(), 18, Color.WHITE, true);
        profile.setId(View.generateViewId());
        card.addView(profile, new LinearLayout.LayoutParams(-1, dp(74)));

        Button edit = action("PROFILE • URL • MAC");
        connect = action("CONNECT TO PORTAL");
        card.addView(edit, buttonParams());
        card.addView(connect, buttonParams());

        status = label("Gati për lidhje", 14, Color.rgb(255, 201, 92), true);
        status.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(status, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        edit.setOnClickListener(v -> editProfile(profile));
        connect.setOnClickListener(v -> beginPortalConnection());
        edit.requestFocus();
    }

    private void editProfile(TextView profileView) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        form.setPadding(pad, pad, pad, 0);

        EditText name = field("Profile name", prefs.getString(KEY_NAME, "LazySTB Profile"));
        EditText portal = field("Portal URL", prefs.getString(KEY_PORTAL, ""));
        portal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText mac = field("MAC address", prefs.getString(KEY_MAC, ""));
        mac.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(name); form.addView(portal); form.addView(mac);

        new AlertDialog.Builder(this)
                .setTitle("LazySTB Profile")
                .setView(form)
                .setPositiveButton("SAVE", (d, which) -> {
                    prefs.edit()
                            .putString(KEY_NAME, name.getText().toString().trim())
                            .putString(KEY_PORTAL, normalizePortal(portal.getText().toString()))
                            .putString(KEY_MAC, mac.getText().toString().trim().toUpperCase())
                            .apply();
                    profileView.setText(profileSummary());
                    status.setText("Profili u ruajt");
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void beginPortalConnection() {
        String portal = prefs.getString(KEY_PORTAL, "").trim();
        String mac = prefs.getString(KEY_MAC, "").trim();
        if (portal.isEmpty() || mac.isEmpty()) {
            status.setText("Plotëso Portal URL dhe MAC Address");
            return;
        }
        connect.setEnabled(false);
        connect.setText("LOADING PORTAL…");
        status.setText("Connecting • handshake • " + safeHost(portal));
        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        root.addView(loading, new LinearLayout.LayoutParams(-1, dp(48)));

        // Intentionally stop at the dedicated transport boundary. The next
        // step wires MAG/Stalker handshake/profile/categories into this module;
        // it must never silently fall back to the LazyTV player/session.
        root.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            root.removeView(loading);
            connect.setEnabled(true);
            connect.setText("CONNECT TO PORTAL");
            status.setText("LazySTB transport po ndërtohet • profili është gati");
        }, 700);
    }

    private String profileSummary() {
        String name = prefs.getString(KEY_NAME, "LazySTB Profile");
        String portal = prefs.getString(KEY_PORTAL, "");
        String mac = prefs.getString(KEY_MAC, "");
        if (name.trim().isEmpty()) name = "LazySTB Profile";
        return name + "\n" + (portal.isEmpty() ? "Portal URL nuk është vendosur" : safeHost(portal))
                + (mac.isEmpty() ? "" : "  •  " + mac);
    }

    private static String normalizePortal(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String safeHost(String portal) {
        try {
            java.net.URI u = java.net.URI.create(portal);
            return u.getHost() == null ? portal : u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception ignored) { return portal; }
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setSingleLine(true);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(125, 165, 190));
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(gradient(Color.rgb(4, 23, 40), Color.rgb(4, 23, 40), dp(10)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(0, 0, 0, dp(10)); e.setLayoutParams(p);
        return e;
    }

    private Button action(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setTextSize(15); b.setFocusable(true);
        b.setBackground(gradient(Color.rgb(0, 118, 190), Color.rgb(4, 57, 93), dp(14)));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(0, dp(6), 0, dp(6)); return p;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable gradient(int a, int b, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(radius); g.setStroke(dp(1), Color.rgb(34, 79, 112)); return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
