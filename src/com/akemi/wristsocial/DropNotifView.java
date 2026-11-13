package com.akemi.wristsocial;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

public class DropNotifView {
    public static final int REQ_WATCH_FILE = 77;
    private final MainActivity act;
    private final WatchUi ui;
    private final BridgeClient api;
    private final ExecutorService pool;

    public DropNotifView(MainActivity act, WatchUi ui, BridgeClient api, ExecutorService pool) {
        this.act = act;
        this.ui = ui;
        this.api = api;
        this.pool = pool;
    }

    // ==================== SPECCHIO NOTIFICHE Z FLIP ====================

    public void showNotifs(final Runnable backToMain) {
        ui.clear();
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        Button bBack = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        bBack.setOnClickListener(v -> backToMain.run());
        Button bRef = ui.chip("↻ Aggiorna", "#181E29", "#263042", "#2D3748", "#34D399");
        bRef.setOnClickListener(v -> showNotifs(backToMain));
        top.addView(bBack);
        top.addView(bRef);
        ui.box.addView(top);

        ui.text(ui.box, "Notifiche Z Flip", 15f, "#FBBF24", true);
        final TextView ld = ui.text(ui.box, "Lettura notifiche recenti...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONArray arr = new JSONObject(api.get("/notif", true)).optJSONArray("items");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (arr == null || arr.length() == 0) {
                        ui.text(ui.box, "Nessuna notifica in memoria.\nVerifica 'Permesso' in SocialBridge sullo Z Flip.", 12f, "#CBD5E1", false);
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject n = arr.optJSONObject(i);
                        if (n == null) continue;
                        final String key = n.optString("key", "");
                        final String app = n.optString("app", "");
                        final String title = n.optString("title", "");
                        final String text = n.optString("text", "");
                        String time = n.optString("time", "");
                        boolean canReply = n.optBoolean("canReply", false);

                        LinearLayout card = ui.card();
                        ui.text(card, app + " • " + time, 10.5f, "#FBBF24", true);
                        if (!title.isEmpty()) ui.text(card, title, 13f, "#F8FAFC", true);
                        if (!text.isEmpty()) ui.text(card, text, 12.5f, "#E2E8F0", false);

                        Button bCopy = ui.pill("📋 Copia (es. OTP)", "#141923", "#222B3A", "#334155", "#38BDF8");
                        bCopy.setOnClickListener(v -> {
                            copyToWatchClipboard(text.isEmpty() ? title : text);
                            Toast.makeText(act, "Copiato negli appunti Watch!", Toast.LENGTH_SHORT).show();
                        });
                        card.addView(bCopy);

                        if (canReply) {
                            Button bRep = ui.pill("↩ Rispondi dal polso", "#0284C7", "#0369A1", null, "#FFFFFF");
                            bRep.setOnClickListener(v -> openReplyScreen(key, title.isEmpty() ? app : title, () -> showNotifs(backToMain)));
                            card.addView(bRep);
                        }
                        ui.box.addView(card);
                    }
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> ld.setText("Errore Notifiche: " + e.getMessage()));
            }
        });
    }

    private void openReplyScreen(final String key, String target, final Runnable back) {
        ui.clear();
        Button bBack = ui.chip("← Annulla", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        bBack.setOnClickListener(v -> back.run());
        ui.box.addView(bBack);
        ui.text(ui.box, "Rispondi a " + target, 14f, "#38BDF8", true);

        final EditText et = new EditText(act);
        et.setHint("Scrivi o detta risposta...");
        et.setHintTextColor(Color.parseColor("#64748B"));
        et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final Button send = ui.pill("Invia Risposta", "#0284C7", "#0369A1", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String msg = et.getText().toString().trim();
            if (msg.isEmpty()) return;
            send.setText("Invio...");
            send.setEnabled(false);
            pool.execute(() -> {
                try {
                    String res = api.get("/notif/reply?key=" + URLEncoder.encode(key, "UTF-8") + "&text=" + URLEncoder.encode(msg, "UTF-8"), true);
                    boolean ok = new JSONObject(res).optBoolean("ok", false);
                    act.runOnUiThread(() -> {
                        Toast.makeText(act, ok ? "Risposta inviata!" : "Impossibile rispondere", Toast.LENGTH_SHORT).show();
                        back.run();
                    });
                } catch (Exception e) {
                    act.runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); });
                }
            });
        });
        ui.box.addView(send);
    }

    // ==================== WRISTDROP (APPUNTI & FILE) ====================

    public void showDrop(final Runnable backToMain) {
        ui.clear();
        Button bBack = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        bBack.setOnClickListener(v -> backToMain.run());
        ui.box.addView(bBack);

        ui.text(ui.box, "WristDrop", 16f, "#38BDF8", true);
        ui.text(ui.box, "Appunti & File tra Z Flip e Watch", 11f, "#94A3B8", false);

        // Sezione 1: Ricevi Appunti dal telefono
        LinearLayout c1 = ui.card();
        ui.text(c1, "APPUNTI Z FLIP → WATCH", 11f, "#34D399", true);
        final TextView clipTv = ui.text(c1, "Tocca per leggere gli appunti copiati sul telefono...", 12.5f, "#E2E8F0", false);

        Button bPull = ui.pill("📥 Leggi e Copia dal Telefono", "#059669", "#047857", null, "#FFFFFF");
        bPull.setOnClickListener(v -> {
            clipTv.setText("Lettura dal ponte...");
            pool.execute(() -> {
                try {
                    final String txt = new JSONObject(api.get("/clip", true)).optString("text", "");
                    act.runOnUiThread(() -> {
                        if (txt.isEmpty()) { clipTv.setText("Appunti telefono vuoti."); return; }
                        clipTv.setText(txt);
                        copyToWatchClipboard(txt);
                        Toast.makeText(act, "Copiato negli appunti dell'orologio!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    act.runOnUiThread(() -> clipTv.setText("Errore: " + e.getMessage()));
                }
            });
        });
        c1.addView(bPull);
        ui.box.addView(c1);

        // Sezione 2: Invia testo al telefono
        LinearLayout c2 = ui.card();
        ui.text(c2, "TESTO WATCH → APPUNTI Z FLIP", 11f, "#38BDF8", true);
        final EditText etPush = new EditText(act);
        etPush.setHint("Testo o nota da inviare al telefono...");
        etPush.setHintTextColor(Color.parseColor("#64748B"));
        etPush.setTextColor(Color.WHITE);
        etPush.setBackground(ui.roundBox("#141923", "#2A3447", 14));
        etPush.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        c2.addView(etPush);

        Button bPush = ui.pill("📤 Invia agli Appunti Z Flip", "#0284C7", "#0369A1", null, "#FFFFFF");
        bPush.setOnClickListener(v -> {
            final String t = etPush.getText().toString();
            if (t.isEmpty()) return;
            pool.execute(() -> {
                try {
                    api.postData("/clip", t.getBytes(StandardCharsets.UTF_8), null);
                    act.runOnUiThread(() -> Toast.makeText(act, "Inviato agli appunti Z Flip!", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    act.runOnUiThread(() -> Toast.makeText(act, "Errore: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });
        c2.addView(bPush);
        ui.box.addView(c2);

        // Sezione 3: Trasferimento File bidirezionale
        LinearLayout c3 = ui.card();
        ui.text(c3, "TRASFERIMENTO FILE", 11f, "#F472B6", true);

        Button bGetFile = ui.pill("📥 Scarica File dallo Z Flip", "#DB2777", "#BE185D", null, "#FFFFFF");
        bGetFile.setOnClickListener(v -> pool.execute(() -> {
            try {
                BridgeClient.DownloadedFile df = api.downloadFile();
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File outF = new File(dir, df.name);
                try (FileOutputStream fos = new FileOutputStream(outF)) { fos.write(df.data); }
                act.runOnUiThread(() -> Toast.makeText(act, "Salvato in Download: " + df.name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                act.runOnUiThread(() -> Toast.makeText(act, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }));
        c3.addView(bGetFile);

        Button bSendFile = ui.pill("📤 Invia File Watch → Z Flip", "#141923", "#222B3A", "#334155", "#E2E8F0");
        bSendFile.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            act.startActivityForResult(pick, REQ_WATCH_FILE);
        });
        c3.addView(bSendFile);
        ui.box.addView(c3);
    }

    public void handleWatchFilePicked(Uri uri) {
        pool.execute(() -> {
            try (InputStream is = act.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                String name = getFileName(uri);
                api.postData("/file", out.toByteArray(), name);
                act.runOnUiThread(() -> Toast.makeText(act, "Inviato in Download Z Flip: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                act.runOnUiThread(() -> Toast.makeText(act, "Errore invio: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String getFileName(Uri uri) {
        try (Cursor c = act.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "watch_file.bin";
    }

    private void copyToWatchClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WristDrop", text));
    }
}
