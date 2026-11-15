package com.akemi.wristsocial;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    public WatchUi ui;
    public BridgeClient api;
    private InstaView insta;
    private RedditView reddit;
    private DropNotifView dropNotif;
    public final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final List<Runnable> backStack = new ArrayList<>();
    private final List<String[]> geminiHistory = new ArrayList<>();
    public boolean imagesOnTap = true;
    public boolean autoIt = true;
    private String geminiModel = "Auto";
    private String geminiThink = "medio";
    private boolean geminiBusy = false;
    private int geminiReqSeq = 0;
    private int editingIdx = -1;
    private int editingFromEnd = -1;
    private String editingDraft = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getSharedPreferences("ws_prefs", MODE_PRIVATE);
        ui = new WatchUi(this, p.getFloat("scale", 1.0f));
        api = new BridgeClient();
        api.host = p.getString("host", "http://192.168.1.12:8765");
        api.pin = p.getString("pin", "7827");
        imagesOnTap = p.getBoolean("img_tap", true);
        autoIt = p.getBoolean("auto_it", true);
        geminiModel = p.getString("gm_model", "Auto");
        geminiThink = p.getString("gm_think", "medio");

        insta = new InstaView(this, ui, api, pool);
        reddit = new RedditView(this, ui, api, pool);
        dropNotif = new DropNotifView(this, ui, api, pool);
        insta.show("home", false);
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == DropNotifView.REQ_WATCH_FILE && resCode == RESULT_OK && data != null && data.getData() != null) {
            dropNotif.handleWatchFilePicked(data.getData());
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (ui != null && ui.onRotary(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (ui != null && ui.isOverlayOpen()) { ui.closeOverlay(); return; }
        if (!backStack.isEmpty()) { backStack.remove(backStack.size() - 1).run(); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (ui != null) ui.closeOverlay();
        pool.shutdownNow();
        super.onDestroy();
    }

    public void nav(Runnable back, Runnable next) {
        if (back != null) backStack.add(back);
        next.run();
    }

    private void cycleFont(Runnable reload) {
        ui.fontScale = ui.fontScale < 0.95f ? 1.0f : ui.fontScale < 1.15f ? 1.2f : ui.fontScale < 1.35f ? 1.4f : 0.85f;
        getSharedPreferences("ws_prefs", MODE_PRIVATE).edit().putFloat("scale", ui.fontScale).apply();
        reload.run();
    }

    public void addGapChip(LinearLayout row, Button b, int leftDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(leftDp);
        b.setLayoutParams(lp);
        row.addView(b);
    }

    public void topBar(String tab, final Runnable reloadCache, final Runnable reloadForce) {
        LinearLayout r1 = ui.hBar(0, 4);
        Button bNotif = ui.chip("🔔 Notif", "#181E29", "#263042", "#2D3748", "#FBBF24");
        bNotif.setOnClickListener(v -> nav(reloadCache, () -> dropNotif.showNotifs(this::onBackPressed)));
        r1.addView(bNotif);

        Button bDrop = ui.chip("📋 Drop", "#181E29", "#263042", "#2D3748", "#34D399");
        bDrop.setOnClickListener(v -> nav(reloadCache, () -> dropNotif.showDrop(this::onBackPressed)));
        addGapChip(r1, bDrop, 5);

        Button bDm = ui.chip("💬 DM", "#181E29", "#263042", "#2D3748", "#F472B6");
        bDm.setOnClickListener(v -> nav(reloadCache, () -> insta.showDm(false)));
        addGapChip(r1, bDm, 5);

        Button bAa = ui.chip("Aa", "#181E29", "#263042", "#2D3748", "#38BDF8");
        bAa.setOnClickListener(v -> cycleFont(reloadCache));
        addGapChip(r1, bAa, 5);

        Button bRef = ui.chip("↻", "#181E29", "#263042", "#2D3748", "#34D399");
        bRef.setOnClickListener(v -> reloadForce.run());
        addGapChip(r1, bRef, 5);

        Button bSet = ui.chip("⚙", "#181E29", "#263042", "#2D3748", "#94A3B8");
        bSet.setOnClickListener(v -> nav(reloadCache, this::showSettings));
        addGapChip(r1, bSet, 5);

        LinearLayout r2 = ui.hBar(2, 6);
        boolean isIg = "ig".equals(tab), isRd = "rd".equals(tab), isNw = "nw".equals(tab), isGm = "gm".equals(tab);

        Button bIg = ui.chip("Instagram", isIg ? "#DB2777" : "#141923", "#BE185D", isIg ? null : "#2A3447", "#FFFFFF");
        bIg.setOnClickListener(v -> insta.show("home", false));
        r2.addView(bIg);

        Button bRd = ui.chip("Reddit", isRd ? "#EA580C" : "#141923", "#C2410C", isRd ? null : "#2A3447", "#FFFFFF");
        bRd.setOnClickListener(v -> reddit.show("", "hot", false));
        addGapChip(r2, bRd, 5);

        Button bNw = ui.chip("Google News", isNw ? "#0284C7" : "#141923", "#0369A1", isNw ? null : "#2A3447", "#FFFFFF");
        bNw.setOnClickListener(v -> showNews(false));
        addGapChip(r2, bNw, 5);

        Button bGm = ui.chip("Gemini AI", isGm ? "#7C3AED" : "#141923", "#6D28D9", isGm ? null : "#2A3447", "#FFFFFF");
        bGm.setOnClickListener(v -> showGemini());
        addGapChip(r2, bGm, 5);
    }

    // ==================== GEMINI AI CON STOP E MODIFICA PROMPT ====================

    private void showGemini() {
        ui.clear();
        topBar("gm", this::showGemini, this::showGemini);

        LinearLayout gBar = ui.hBar(2, 4);
        Button bNew = ui.chip("➕ Nuova", "#1E1B4B", "#312E81", "#6366F1", "#C7D2FE");
        bNew.setOnClickListener(v -> {
            geminiHistory.clear(); editingIdx = -1; editingDraft = ""; geminiBusy = false; geminiReqSeq++;
            pool.execute(() -> { try { api.get("/gemini/new", true); } catch (Exception ignored) {} });
            showGemini();
        });
        gBar.addView(bNew);

        Button bHist = ui.chip("🗂 Chat", "#1E1B4B", "#312E81", "#6366F1", "#C7D2FE");
        bHist.setOnClickListener(v -> nav(this::showGemini, this::showGeminiChats));
        addGapChip(gBar, bHist, 5);

        Button bMod = ui.chip("🧠 " + geminiModel, "#2E1065", "#4C1D95", "#8B5CF6", "#DDD6FE");
        bMod.setOnClickListener(v -> nav(this::showGemini, this::showGeminiModelPicker));
        addGapChip(gBar, bMod, 5);

        LinearLayout tBar = ui.hBar(2, 6);
        String[][] levels = {{"Pensiero: Basso", "basso"}, {"Pensiero: Medio", "medio"}, {"Pensiero: Alto", "alto"}};
        for (int i = 0; i < levels.length; i++) {
            final String lv = levels[i][1];
            boolean act = lv.equals(geminiThink);
            Button tb = ui.chip(levels[i][0], act ? "#7C3AED" : "#141923", "#6D28D9", act ? null : "#2A3447", act ? "#FFFFFF" : "#94A3B8");
            tb.setOnClickListener(v -> {
                geminiThink = lv;
                getSharedPreferences("ws_prefs", MODE_PRIVATE).edit().putString("gm_think", geminiThink).apply();
                showGemini();
            });
            if (i == 0) tBar.addView(tb); else addGapChip(tBar, tb, 5);
        }

        final EditText et = new EditText(this);
        et.setHint(editingIdx >= 0 ? "Modifica il tuo prompt..." : "Chiedi a Gemini...");
        if (!editingDraft.isEmpty()) et.setText(editingDraft);
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", editingIdx >= 0 ? "#F59E0B" : "#7C3AED", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        if (geminiBusy) {
            Button stopBtn = ui.pill("⏹ Ferma generazione", "#DC2626", "#B91C1C", "#F87171", "#FFFFFF");
            stopBtn.setOnClickListener(v -> {
                geminiBusy = false;
                geminiReqSeq++;
                pool.execute(() -> { try { api.get("/gemini/stop", true); } catch (Exception ignored) {} });
                Toast.makeText(this, "Richiesta bloccata", Toast.LENGTH_SHORT).show();
                showGemini();
            });
            ui.box.addView(stopBtn);
        } else {
            String btnLabel = editingIdx >= 0 ? "✨ Aggiorna risposta" : "✨ Invia a Gemini";
            Button askBtn = ui.pill(btnLabel, editingIdx >= 0 ? "#D97706" : "#7C3AED", "#6D28D9", null, "#FFFFFF");
            askBtn.setOnClickListener(v -> {
                final String q = et.getText().toString().trim();
                if (q.isEmpty()) return;
                final int editPos = editingIdx;
                final int editOff = editingFromEnd;
                geminiBusy = true;
                final int mySeq = ++geminiReqSeq;
                showGemini();

                pool.execute(() -> {
                    try {
                        String u = editPos >= 0
                                ? "/gemini/edit?idx=" + editOff + "&q=" + URLEncoder.encode(q, "UTF-8")
                                  + "&model=" + URLEncoder.encode(geminiModel, "UTF-8") + "&think=" + geminiThink
                                : "/gemini/ask?q=" + URLEncoder.encode(q, "UTF-8")
                                  + "&model=" + URLEncoder.encode(geminiModel, "UTF-8") + "&think=" + geminiThink;
                        String reply = new JSONObject(api.get(u, true)).optString("reply", "Nessuna risposta.");
                        runOnUiThread(() -> {
                            if (mySeq != geminiReqSeq) return;
                            geminiBusy = false;
                            if (editPos >= 0 && editPos < geminiHistory.size()) {
                                geminiHistory.subList(editPos, geminiHistory.size()).clear();
                            }
                            geminiHistory.add(new String[]{"Tu", q});
                            geminiHistory.add(new String[]{"Gemini", reply});
                            editingIdx = -1; editingFromEnd = -1; editingDraft = "";
                            showGemini();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (mySeq != geminiReqSeq) return;
                            geminiBusy = false;
                            Toast.makeText(this, "Errore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            showGemini();
                        });
                    }
                });
            });
            ui.box.addView(askBtn);

            if (editingIdx >= 0) {
                Button cancelEdit = ui.pill("✕ Annulla modifica", "#181E29", "#263042", "#475569", "#CBD5E1");
                cancelEdit.setOnClickListener(v -> { editingIdx = -1; editingFromEnd = -1; editingDraft = ""; showGemini(); });
                ui.box.addView(cancelEdit);
            }
        }

        // Mostra i turni dal più recente in alto mantenendo ogni domanda sopra la sua risposta
        int i = geminiHistory.size() - 1;
        while (i >= 0) {
            int qIdx = i;
            int aIdx = -1;
            if (i > 0 && "Gemini".equals(geminiHistory.get(i)[0]) && "Tu".equals(geminiHistory.get(i - 1)[0])) {
                qIdx = i - 1;
                aIdx = i;
                i -= 2;
            } else {
                i -= 1;
            }
            renderGeminiTurnCard(qIdx, false);
            if (aIdx >= 0) renderGeminiTurnCard(aIdx, true);
        }
    }

    private void renderGeminiTurnCard(final int idx, boolean isAnswer) {
        final String[] turn = geminiHistory.get(idx);
        LinearLayout card = ui.card();
        ui.text(card, turn[0], 11f, isAnswer ? "#38BDF8" : "#C4B5FD", true);
        ui.text(card, turn[1], 13f, "#F8FAFC", false);

        if (!isAnswer) {
            int uAfter = 0;
            for (int k = idx + 1; k < geminiHistory.size(); k++) {
                if ("Tu".equals(geminiHistory.get(k)[0])) uAfter++;
            }
            final int fromEnd = uAfter;
            Button bEdit = ui.pill("✏️ Modifica", "#141923", "#222B3A", "#F59E0B", "#FBBF24");
            bEdit.setOnClickListener(v -> {
                editingIdx = idx;
                editingFromEnd = fromEnd;
                editingDraft = turn[1];
                showGemini();
            });
            card.addView(bEdit);
        } else {
            Button bCopy = ui.pill("📋 Copia", "#141923", "#222B3A", "#334155", "#38BDF8");
            bCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Gemini", turn[1]));
                Toast.makeText(this, "Copiato!", Toast.LENGTH_SHORT).show();
            });
            card.addView(bCopy);
        }
        ui.box.addView(card);
    }

    private void showGeminiModelPicker() {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Scegli Modello Gemini", 15f, "#C4B5FD", true);
        final TextView ld = ui.text(ui.box, "Lettura modelli disponibili dal tuo account...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONArray arr = new JSONObject(api.get("/gemini/models", true)).optJSONArray("models");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    Button bAuto = ui.pill("Auto (Predefinito)", "#141923", "#1E2634", "#7C3AED", "#FFFFFF");
                    bAuto.setOnClickListener(v -> {
                        geminiModel = "Auto";
                        getSharedPreferences("ws_prefs", MODE_PRIVATE).edit().putString("gm_model", geminiModel).apply();
                        onBackPressed();
                    });
                    ui.box.addView(bAuto);

                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            final String mName = arr.optString(i, "");
                            if (mName.isEmpty()) continue;
                            Button bm = ui.pill("🧠 " + mName, "#141923", "#1E2634", "#8B5CF6", "#F8FAFC");
                            bm.setOnClickListener(v -> {
                                geminiModel = mName;
                                getSharedPreferences("ws_prefs", MODE_PRIVATE).edit().putString("gm_model", geminiModel).apply();
                                onBackPressed();
                            });
                            ui.box.addView(bm);
                        }
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore modelli: " + e.getMessage())); }
        });
    }

    private void showGeminiChats() {
        ui.clear();
        Button back = ui.chip("← Torna a Gemini", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Vecchie Chat Gemini", 15f, "#C4B5FD", true);
        final TextView ld = ui.text(ui.box, "Lettura cronologia dal telefono...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONArray chats = new JSONObject(api.get("/gemini/chats", true)).optJSONArray("chats");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (chats == null || chats.length() == 0) { ui.text(ui.box, "Nessuna chat trovata.", 12f, "#E2E8F0", false); return; }
                    for (int i = 0; i < chats.length(); i++) {
                        JSONObject c = chats.optJSONObject(i);
                        if (c == null) continue;
                        final String title = c.optString("title", "Conversazione"), url = c.optString("url", "");
                        Button bChat = ui.pill("💬 " + title, "#141923", "#1E2634", "#4C1D95", "#F8FAFC");
                        bChat.setOnClickListener(v -> openOldGeminiChat(title, url));
                        ui.box.addView(bChat);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore chat: " + e.getMessage())); }
        });
    }

    private void openOldGeminiChat(final String title, final String url) {
        ui.clear();
        ui.text(ui.box, "Apertura: " + title + "...", 13f, "#C4B5FD", true);
        pool.execute(() -> {
            try {
                JSONArray turns = new JSONObject(api.get("/gemini/open?url=" + URLEncoder.encode(url, "UTF-8"), true)).optJSONArray("turns");
                geminiHistory.clear();
                editingIdx = -1; editingDraft = "";
                if (turns != null) {
                    for (int i = 0; i < turns.length(); i++) {
                        JSONObject t = turns.optJSONObject(i);
                        if (t != null) geminiHistory.add(new String[]{t.optString("role", "Gemini"), t.optString("text", "")});
                    }
                }
                runOnUiThread(() -> { backStack.clear(); showGemini(); });
            } catch (Exception e) { runOnUiThread(() -> ui.text(ui.box, "Errore: " + e.getMessage(), 12.5f, "#F87171", false)); }
        });
    }

    private void showNews(final boolean force) {
        ui.clear();
        topBar("nw", () -> showNews(false), () -> showNews(true));
        final TextView ld = ui.text(ui.box, "Caricamento Google News...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray items = new JSONObject(api.get("/news/feed", force)).optJSONArray("items");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (items == null || items.length() == 0) { ui.text(ui.box, "Nessuna notizia.", 12f, "#E2E8F0", false); return; }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        final String title = it.optString("title", ""), url = it.optString("url", "");
                        LinearLayout card = ui.card();
                        ui.text(card, it.optString("src", "Google News"), 10.5f, "#38BDF8", true);
                        ui.text(card, title, 13.5f, "#F8FAFC", true);
                        String img = it.optString("img", "");
                        if (!img.isEmpty()) attachImage(card, img, imagesOnTap);
                        if (!url.isEmpty()) {
                            Button bRead = ui.pill("Leggi Articolo", "#0284C7", "#0369A1", null, "#FFFFFF");
                            bRead.setOnClickListener(v -> nav(() -> showNews(false), () -> openNewsArticle(url, title)));
                            card.addView(bRead);
                        }
                        ui.box.addView(card);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore Ponte: " + e.getMessage())); }
        });
    }

    private void openNewsArticle(final String url, final String title) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, title, 15f, "#38BDF8", true);
        final TextView ld = ui.text(ui.box, "Estrazione articolo...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                String txt = new JSONObject(api.get("/news/article?url=" + URLEncoder.encode(url, "UTF-8"), false)).optString("text", "");
                runOnUiThread(() -> { ui.box.removeView(ld); ui.text(ui.box, txt, 13f, "#E2E8F0", false); });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore: " + e.getMessage())); }
        });
    }

    public void showCarousel(String title, JSONArray imgs) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Foto " + title + " (" + imgs.length() + ")", 14f, "#F472B6", true);
        for (int i = 0; i < imgs.length(); i++) {
            String u = imgs.optString(i, "");
            if (!u.isEmpty()) {
                ui.text(ui.box, "Foto " + (i + 1) + " di " + imgs.length(), 11f, "#94A3B8", true);
                attachImage(ui.box, u, false);
            }
        }
    }

    private void showSettings() {
        ui.clear();
        Button back = ui.chip("← Salva e Chiudi", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        ui.box.addView(back);
        ui.text(ui.box, "Configura Ponte Z Flip", 15f, "#38BDF8", true);

        final EditText pinEt = new EditText(this);
        pinEt.setText(api.pin); pinEt.setHint("PIN a 4 cifre");
        pinEt.setTextColor(Color.WHITE); pinEt.setGravity(Gravity.CENTER);
        pinEt.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        pinEt.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(pinEt);

        final EditText hostEt = new EditText(this);
        hostEt.setText(api.host); hostEt.setTextColor(Color.WHITE); hostEt.setGravity(Gravity.CENTER);
        hostEt.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        hostEt.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(hostEt);

        final Button scanBtn = ui.pill("🔍 Trova Z Flip Automaticamente", "#0284C7", "#0369A1", null, "#FFFFFF");
        scanBtn.setOnClickListener(v -> {
            scanBtn.setText("Ricerca in corso..."); scanBtn.setEnabled(false);
            pool.execute(() -> {
                final String found = api.discoverHost();
                runOnUiThread(() -> {
                    scanBtn.setEnabled(true);
                    if (found != null) { hostEt.setText(found); scanBtn.setText("✓ Trovato: " + found); }
                    else scanBtn.setText("Non trovato (copia IP da SocialBridge)");
                });
            });
        });
        ui.box.addView(scanBtn);

        final Button imgBtn = ui.pill(imagesOnTap ? "🖼 Foto: Solo al tocco" : "🖼 Foto: Automatiche", "#141923", "#222B3A", "#059669", "#34D399");
        imgBtn.setOnClickListener(v -> { imagesOnTap = !imagesOnTap; imgBtn.setText(imagesOnTap ? "🖼 Foto: Solo al tocco" : "🖼 Foto: Automatiche"); });
        ui.box.addView(imgBtn);

        final Button trBtn = ui.pill(autoIt ? "🌐 Traduzione IT: ON" : "🌐 Traduzione IT: OFF", "#141923", "#222B3A", "#0284C7", "#38BDF8");
        trBtn.setOnClickListener(v -> { autoIt = !autoIt; trBtn.setText(autoIt ? "🌐 Traduzione IT: ON" : "🌐 Traduzione IT: OFF"); });
        ui.box.addView(trBtn);

        back.setOnClickListener(v -> {
            api.pin = pinEt.getText().toString().trim();
            api.host = hostEt.getText().toString().trim();
            getSharedPreferences("ws_prefs", MODE_PRIVATE).edit()
                    .putString("pin", api.pin).putString("host", api.host)
                    .putBoolean("img_tap", imagesOnTap).putBoolean("auto_it", autoIt).apply();
            Toast.makeText(this, "Salvato!", Toast.LENGTH_SHORT).show();
            onBackPressed();
        });
    }

    public void attachImage(final LinearLayout parent, final String url, boolean onTap) {
        if (!onTap) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true); iv.setMaxHeight(ui.dp(170));
            parent.addView(iv); loadImg(url, iv); return;
        }
        final Button btn = ui.pill("🖼 Carica foto", "#0E1726", "#16243B", "#1D4ED8", "#60A5FA");
        btn.setOnClickListener(v -> {
            btn.setText("Caricamento..."); btn.setEnabled(false);
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true); iv.setMaxHeight(ui.dp(170));
            int idx = parent.indexOfChild(btn);
            if (idx >= 0) { parent.removeView(btn); parent.addView(iv, idx); } else parent.addView(iv);
            loadImg(url, iv);
        });
        parent.addView(btn);
    }

    private void loadImg(final String url, final ImageView target) {
        pool.execute(() -> {
            try {
                final Bitmap bmp = api.getProxiedImage(url);
                if (bmp != null) runOnUiThread(() -> { target.setImageBitmap(bmp); target.setOnClickListener(v -> ui.showImage(bmp)); });
            } catch (Exception ignored) {}
        });
    }
}
