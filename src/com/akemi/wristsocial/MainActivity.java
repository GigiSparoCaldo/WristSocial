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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WatchUi ui;
    private BridgeClient api;
    private DropNotifView dropNotif;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final List<Runnable> backStack = new ArrayList<>();
    private final List<String[]> geminiHistory = new ArrayList<>();
    private boolean imagesOnTap = true;
    private boolean autoIt = true;

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
        dropNotif = new DropNotifView(this, ui, api, pool);
        showInsta(false);
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

    private void nav(Runnable back, Runnable next) {
        if (back != null) backStack.add(back);
        next.run();
    }

    private void cycleFont(Runnable reload) {
        ui.fontScale = ui.fontScale < 0.95f ? 1.0f : ui.fontScale < 1.15f ? 1.2f : ui.fontScale < 1.35f ? 1.4f : 0.85f;
        getSharedPreferences("ws_prefs", MODE_PRIVATE).edit().putFloat("scale", ui.fontScale).apply();
        reload.run();
    }

    private void topBar(String tab, final Runnable reloadCache, final Runnable reloadForce) {
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER);

        Button bNotif = ui.chip("🔔", "#181E29", "#263042", "#2D3748", "#FBBF24");
        bNotif.setOnClickListener(v -> nav(reloadCache, () -> dropNotif.showNotifs(this::onBackPressed)));

        Button bDrop = ui.chip("📋", "#181E29", "#263042", "#2D3748", "#34D399");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = ui.dp(3); bDrop.setLayoutParams(dlp);
        bDrop.setOnClickListener(v -> nav(reloadCache, () -> dropNotif.showDrop(this::onBackPressed)));

        Button bDm = ui.chip("DM", "#181E29", "#263042", "#2D3748", "#F472B6");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = ui.dp(3); bDm.setLayoutParams(mlp);
        bDm.setOnClickListener(v -> nav(reloadCache, () -> showDm(false)));

        Button bAa = ui.chip("Aa", "#181E29", "#263042", "#2D3748", "#38BDF8");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = ui.dp(3); bAa.setLayoutParams(alp);
        bAa.setOnClickListener(v -> cycleFont(reloadCache));

        Button bSet = ui.chip("⚙", "#181E29", "#263042", "#2D3748", "#94A3B8");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = ui.dp(3); bSet.setLayoutParams(slp);
        bSet.setOnClickListener(v -> nav(reloadCache, this::showSettings));

        Button bRef = ui.chip("↻", "#181E29", "#263042", "#2D3748", "#34D399");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = ui.dp(3); bRef.setLayoutParams(rlp);
        bRef.setOnClickListener(v -> reloadForce.run());

        r1.addView(bNotif); r1.addView(bDrop); r1.addView(bDm); r1.addView(bAa); r1.addView(bSet); r1.addView(bRef);
        ui.box.addView(r1);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.setMargins(0, ui.dp(5), 0, ui.dp(5));
        r2.setLayoutParams(r2lp);

        boolean isIg = "ig".equals(tab), isRd = "rd".equals(tab), isNw = "nw".equals(tab), isGm = "gm".equals(tab);
        Button bIg = ui.chip("IG", isIg ? "#DB2777" : "#141923", "#BE185D", isIg ? null : "#2A3447", "#FFFFFF");
        Button bRd = ui.chip("Reddit", isRd ? "#EA580C" : "#141923", "#C2410C", isRd ? null : "#2A3447", "#FFFFFF");
        Button bNw = ui.chip("News", isNw ? "#0284C7" : "#141923", "#0369A1", isNw ? null : "#2A3447", "#FFFFFF");
        Button bGm = ui.chip("Gemini", isGm ? "#7C3AED" : "#141923", "#6D28D9", isGm ? null : "#2A3447", "#FFFFFF");

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = ui.dp(3);
        bIg.setLayoutParams(p1); bRd.setLayoutParams(p2); bNw.setLayoutParams(p2); bGm.setLayoutParams(p2);

        bIg.setOnClickListener(v -> showInsta(false));
        bRd.setOnClickListener(v -> showReddit("", "hot", false));
        bNw.setOnClickListener(v -> showNews(false));
        bGm.setOnClickListener(v -> showGemini());

        r2.addView(bIg); r2.addView(bRd); r2.addView(bNw); r2.addView(bGm);
        ui.box.addView(r2);
    }

    // ==================== GEMINI PERSONALE (Z FLIP) ====================

    private void showGemini() {
        ui.clear();
        topBar("gm", this::showGemini, this::showGemini);

        final EditText et = new EditText(this);
        et.setHint("Chiedi a Gemini (voce o tastiera)...");
        et.setHintTextColor(Color.parseColor("#64748B"));
        et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#7C3AED", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final Button askBtn = ui.pill("✨ Invia a Gemini", "#7C3AED", "#6D28D9", null, "#FFFFFF");
        askBtn.setOnClickListener(v -> {
            final String q = et.getText().toString().trim();
            if (q.isEmpty()) return;
            askBtn.setText("Gemini sta pensando...");
            askBtn.setEnabled(false);
            pool.execute(() -> {
                try {
                    String res = api.get("/gemini/ask?q=" + URLEncoder.encode(q, "UTF-8"), true);
                    String reply = new JSONObject(res).optString("reply", "Nessuna risposta.");
                    geminiHistory.add(0, new String[]{q, reply});
                    runOnUiThread(this::showGemini);
                } catch (Exception e) {
                    runOnUiThread(() -> { askBtn.setText("Errore: " + e.getMessage()); askBtn.setEnabled(true); });
                }
            });
        });
        ui.box.addView(askBtn);

        for (final String[] turn : geminiHistory) {
            LinearLayout card = ui.card();
            ui.text(card, "Tu: " + turn[0], 11.5f, "#C4B5FD", true);
            ui.text(card, turn[1], 13f, "#F8FAFC", false);
            Button bCopy = ui.pill("📋 Copia risposta", "#141923", "#222B3A", "#334155", "#38BDF8");
            bCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Gemini", turn[1]));
                Toast.makeText(this, "Copiato!", Toast.LENGTH_SHORT).show();
            });
            card.addView(bCopy);
            ui.box.addView(card);
        }
    }

    // ==================== GOOGLE NEWS ====================

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

    // ==================== INSTAGRAM (CAROSELLI, STORIE, VIDEO, COMMENTI) ====================

    private void showInsta(final boolean force) {
        ui.clear();
        topBar("ig", () -> showInsta(false), () -> showInsta(true));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        final LinearLayout storyRow = new LinearLayout(this);
        storyRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(storyRow);
        ui.box.addView(hsv);

        final TextView loading = ui.text(ui.box, "Caricamento Instagram...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                final JSONArray stories = new JSONObject(api.get("/ig/stories", force)).optJSONArray("stories");
                final JSONArray items = new JSONObject(api.get("/ig/feed", force)).optJSONArray("items");
                runOnUiThread(() -> {
                    ui.box.removeView(loading);
                    if (stories != null) {
                        for (int i = 0; i < stories.length(); i++) {
                            JSONObject st = stories.optJSONObject(i);
                            if (st != null) addStoryBubble(storyRow, st);
                        }
                    }
                    if (items == null || items.length() == 0) {
                        ui.text(ui.box, "Nessun post. Apri IG su SocialBridge (Z Flip).", 12f, "#E2E8F0", false);
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject m = items.optJSONObject(i);
                        if (m != null) renderIgCard(m);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> loading.setText("Errore Ponte: " + e.getMessage())); }
        });
    }

    private void addStoryBubble(LinearLayout row, JSONObject st) {
        final String id = st.optString("id", ""), user = st.optString("user", ""), av = st.optString("avatar", "");
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        item.setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4));
        final ImageView iv = new ImageView(this);
        int sz = ui.dp(42);
        iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        iv.setBackground(ui.roundBox("#1F2937", "#EC4899", 999));
        item.addView(iv);
        TextView tv = new TextView(this);
        tv.setText(user.length() > 9 ? user.substring(0, 9) : user);
        tv.setTextColor(Color.parseColor("#CBD5E1")); tv.setTextSize(ui.sp(9.5f));
        item.addView(tv);
        item.setOnClickListener(v -> nav(() -> showInsta(false), () -> openStory(id, user)));
        row.addView(item);
        if (!av.isEmpty()) {
            pool.execute(() -> {
                try {
                    Bitmap b = api.getProxiedImage(av);
                    if (b != null) { final Bitmap circ = ui.makeCircleAvatar(b, sz); runOnUiThread(() -> iv.setImageBitmap(circ)); }
                } catch (Exception ignored) {}
            });
        }
    }

    private void openStory(final String reelId, final String user) {
        ui.clear();
        Button back = ui.chip("← Torna al Feed", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Storia di " + user, 15f, "#F472B6", true);
        final TextView ld = ui.text(ui.box, "Caricamento storia...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray items = new JSONObject(api.get("/ig/story?id=" + URLEncoder.encode(reelId, "UTF-8"), true)).optJSONArray("items");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (items == null || items.length() == 0) { ui.text(ui.box, "Storia non disponibile.", 12.5f, "#E2E8F0", false); return; }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        String img = it.optString("img", "");
                        final String vid = it.optString("vid", "");
                        if (!img.isEmpty()) attachImage(ui.box, img, false);
                        if (!vid.isEmpty()) {
                            Button p = ui.pill("▶ Riproduci Storia Video", "#DB2777", "#BE185D", null, "#FFFFFF");
                            p.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(vid)));
                            ui.box.addView(p);
                        }
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore storia: " + e.getMessage())); }
        });
    }

    private void renderIgCard(JSONObject m) {
        LinearLayout card = ui.card();
        final String id = m.optString("id", ""), user = m.optString("user", ""), caption = m.optString("text", "");
        int likes = m.optInt("likes", 0), comm = m.optInt("comments", 0);
        String img = m.optString("img", "");
        final String vid = m.optString("vid", "");
        final JSONArray imgs = m.optJSONArray("imgs");

        ui.text(card, "@" + user + (imgs != null && imgs.length() > 1 ? "  •  🖼 1/" + imgs.length() : ""), 12f, "#F472B6", true);
        if (!img.isEmpty()) attachImage(card, img, imagesOnTap);
        if (imgs != null && imgs.length() > 1) {
            Button bAll = ui.pill("🖼 Vedi tutte le " + imgs.length() + " foto", "#172033", "#1E293B", "#EC4899", "#F472B6");
            bAll.setOnClickListener(v -> nav(() -> showInsta(false), () -> showCarousel(user, imgs)));
            card.addView(bAll);
        }
        if (!vid.isEmpty()) {
            Button pv = ui.pill("▶ Guarda Video / Reel", "#DB2777", "#BE185D", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(vid)));
            card.addView(pv);
        }
        if (!caption.isEmpty()) {
            final TextView tvCap = ui.text(card, caption, 13f, "#F8FAFC", false);
            if (autoIt) pool.execute(() -> { final String tr = api.translateIt(caption); if (!tr.equals(caption)) runOnUiThread(() -> tvCap.setText(tr)); });
        }

        final Button likeBtn = ui.pill("♥ Mi piace (" + likes + ")", "#1B2332", "#263145", "#334155", "#F472B6");
        likeBtn.setOnClickListener(v -> {
            likeBtn.setText("♥ Piaciuto (" + (likes + 1) + ")");
            pool.execute(() -> { try { api.get("/ig/like?id=" + URLEncoder.encode(id, "UTF-8"), true); } catch (Exception ignored) {} });
        });
        card.addView(likeBtn);

        Button commBtn = ui.pill("💬 Commenti e Risposte (" + comm + ")", "#141923", "#222B3A", "#334155", "#38BDF8");
        commBtn.setOnClickListener(v -> nav(() -> showInsta(false), () -> showIgComments(id, user)));
        card.addView(commBtn);

        ui.box.addView(card);
    }

    private void showCarousel(String user, JSONArray imgs) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Foto di @" + user + " (" + imgs.length() + ")", 14f, "#F472B6", true);
        for (int i = 0; i < imgs.length(); i++) {
            String u = imgs.optString(i, "");
            if (!u.isEmpty()) {
                ui.text(ui.box, "Foto " + (i + 1) + " di " + imgs.length(), 11f, "#94A3B8", true);
                attachImage(ui.box, u, false);
            }
        }
    }

    private void showIgComments(final String mediaId, String user) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Commenti @" + user, 14f, "#F472B6", true);
        final TextView ld = ui.text(ui.box, "Caricamento commenti...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray arr = new JSONObject(api.get("/ig/comments?id=" + URLEncoder.encode(mediaId, "UTF-8"), false)).optJSONArray("comments");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (arr == null || arr.length() == 0) { ui.text(ui.box, "Nessun commento.", 12.5f, "#E2E8F0", false); return; }
                    renderIgCommentList(arr, 0);
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore commenti: " + e.getMessage())); }
        });
    }

    private void renderIgCommentList(JSONArray arr, int depth) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            LinearLayout box = ui.card();
            if (depth > 0) box.setPadding(ui.dp(22), ui.dp(10), ui.dp(12), ui.dp(10));
            ui.text(box, (depth > 0 ? "↳ @" : "@") + c.optString("user", "") + " • ♥ " + c.optInt("likes", 0), 10.5f, "#38BDF8", true);
            final String txt = c.optString("text", "");
            if (!txt.isEmpty()) {
                final TextView tv = ui.text(box, txt, 12.5f, "#E2E8F0", false);
                if (autoIt) pool.execute(() -> { final String tr = api.translateIt(txt); if (!tr.equals(txt)) runOnUiThread(() -> tv.setText(tr)); });
            }
            String gif = c.optString("img", "");
            if (!gif.isEmpty()) attachImage(box, gif, false);
            ui.box.addView(box);
            renderIgCommentList(c.optJSONArray("replies"), depth + 1);
        }
    }

    // ==================== DM INSTAGRAM ====================

    private void showDm(final boolean force) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Direct Instagram", 15f, "#F472B6", true);
        final TextView ld = ui.text(ui.box, "Caricamento chat...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray threads = new JSONObject(api.get("/ig/dm", force)).optJSONArray("threads");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (threads == null || threads.length() == 0) { ui.text(ui.box, "Nessuna chat.", 12.5f, "#E2E8F0", false); return; }
                    for (int i = 0; i < threads.length(); i++) {
                        JSONObject t = threads.optJSONObject(i);
                        final String tid = t.optString("id", ""), title = t.optString("title", "Chat");
                        LinearLayout c = ui.card();
                        ui.text(c, title, 13f, "#38BDF8", true);
                        ui.text(c, t.optString("last", ""), 12f, "#CBD5E1", false);
                        Button rep = ui.pill("Rispondi", "#DB2777", "#BE185D", null, "#FFFFFF");
                        rep.setOnClickListener(v -> nav(() -> showDm(false), () -> replyDm(tid, title)));
                        c.addView(rep);
                        ui.box.addView(c);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore DM: " + e.getMessage())); }
        });
    }

    private void replyDm(final String threadId, String title) {
        ui.clear();
        Button back = ui.chip("← Annulla", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Rispondi a " + title, 14f, "#F472B6", true);
        final EditText et = new EditText(this);
        et.setHint("Scrivi o detta messaggio...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final Button send = ui.pill("Invia Messaggio", "#DB2777", "#BE185D", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String msg = et.getText().toString().trim();
            if (msg.isEmpty()) return;
            send.setText("Invio..."); send.setEnabled(false);
            pool.execute(() -> {
                try {
                    api.get("/ig/dm_send?id=" + URLEncoder.encode(threadId, "UTF-8") + "&text=" + URLEncoder.encode(msg, "UTF-8"), true);
                    runOnUiThread(this::onBackPressed);
                } catch (Exception e) { runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); }); }
            });
        });
        ui.box.addView(send);
    }

    // ==================== REDDIT (GALLERIE, VIDEO, COMMENTI ANNIDATI + GIF) ====================

    private void showReddit(final String sub, final String sort, final boolean force) {
        ui.clear();
        topBar("rd", () -> showReddit(sub, sort, false), () -> showReddit(sub, sort, true));
        final EditText subIn = new EditText(this);
        subIn.setHint(sub.isEmpty() ? "Home Reddit (o scrivi sub)" : "r/" + sub);
        subIn.setHintTextColor(Color.parseColor("#64748B")); subIn.setTextColor(Color.WHITE);
        subIn.setGravity(Gravity.CENTER); subIn.setSingleLine(true);
        subIn.setBackground(ui.roundBox("#141923", "#2A3447", 999));
        subIn.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        ui.box.addView(subIn);

        Button goSub = ui.pill("Vai al Subreddit / Home", "#EA580C", "#C2410C", null, "#FFFFFF");
        goSub.setOnClickListener(v -> showReddit(subIn.getText().toString().trim().replaceFirst("^/?r/", ""), "hot", false));
        ui.box.addView(goSub);

        final TextView ld = ui.text(ui.box, "Caricamento Reddit...", 13f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray ch = new JSONObject(api.get("/reddit/feed?sub=" + URLEncoder.encode(sub, "UTF-8") + "&sort=" + sort, force))
                        .getJSONObject("data").getJSONArray("children");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    for (int i = 0; i < ch.length(); i++) {
                        JSONObject d = ch.optJSONObject(i).optJSONObject("data");
                        if (d != null) renderRedditCard(sub, sort, d);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore Reddit: " + e.getMessage())); }
        });
    }

    private void renderRedditCard(final String sub, final String sort, JSONObject d) {
        LinearLayout card = ui.card();
        final String title = d.optString("title", ""), perm = d.optString("permalink", ""), url = d.optString("url", "");
        ui.text(card, "r/" + d.optString("subreddit", "") + " • ▲ " + d.optInt("score", 0), 10.5f, "#FB923C", true);
        final TextView tvT = ui.text(card, title, 13.5f, "#F8FAFC", true);
        if (autoIt) pool.execute(() -> { final String tr = api.translateIt(title); if (!tr.equals(title)) runOnUiThread(() -> tvT.setText(tr)); });

        String vid = extractRedditVideo(d);
        if (vid != null && !vid.isEmpty()) {
            final String fVid = vid;
            Button pv = ui.pill("▶ Guarda Video Reddit", "#EA580C", "#C2410C", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(fVid)));
            card.addView(pv);
        }

        final JSONArray gal = extractRedditGallery(d);
        if (gal.length() > 1) {
            attachImage(card, gal.optString(0, ""), imagesOnTap);
            Button bGal = ui.pill("🖼 Vedi tutte le " + gal.length() + " foto", "#172033", "#1E293B", "#EA580C", "#FB923C");
            bGal.setOnClickListener(v -> nav(() -> showReddit(sub, sort, false), () -> showCarousel("Reddit", gal)));
            card.addView(bGal);
        } else if (url.matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) {
            attachImage(card, url, imagesOnTap);
        }

        Button comm = ui.pill("💬 Commenti e Risposte (" + d.optInt("num_comments", 0) + ")", "#1B2332", "#263145", "#334155", "#38BDF8");
        comm.setOnClickListener(v -> nav(() -> showReddit(sub, sort, false), () -> showRedditComments(perm)));
        card.addView(comm);
        ui.box.addView(card);
    }

    private String extractRedditVideo(JSONObject post) {
        try {
            JSONObject m = post.optJSONObject("secure_media");
            if (m == null) m = post.optJSONObject("media");
            if (m != null && m.has("reddit_video")) return m.getJSONObject("reddit_video").optString("fallback_url", "");
        } catch (Exception ignored) {}
        String u = post.optString("url", "");
        return u.endsWith(".mp4") ? u : null;
    }

    private JSONArray extractRedditGallery(JSONObject post) {
        JSONArray out = new JSONArray();
        try {
            JSONObject gd = post.optJSONObject("gallery_data");
            JSONObject mm = post.optJSONObject("media_metadata");
            if (gd != null && mm != null) {
                JSONArray items = gd.optJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    String mid = items.getJSONObject(i).optString("media_id", "");
                    JSONObject meta = mm.optJSONObject(mid);
                    if (meta != null && meta.has("s")) {
                        String u = meta.getJSONObject("s").optString("u", meta.getJSONObject("s").optString("gif", ""));
                        if (!u.isEmpty()) out.put(u.replace("&amp;", "&"));
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void showRedditComments(final String perm) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        final TextView ld = ui.text(ui.box, "Caricamento post e commenti...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray rootArr = new JSONArray(api.get("/reddit/comments?url=" + URLEncoder.encode(perm, "UTF-8"), false));
                final JSONObject postObj = rootArr.getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
                final JSONArray arr = rootArr.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                String rawTitle = postObj.optString("title", "");
                String rawSelf = postObj.optString("selftext", "").trim();
                final String fTitle = (autoIt && !rawTitle.isEmpty()) ? api.translateIt(rawTitle) : rawTitle;
                final String fSelf = (autoIt && !rawSelf.isEmpty()) ? api.translateIt(rawSelf) : rawSelf;
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (!fTitle.isEmpty()) ui.text(ui.box, fTitle, 14f, "#F8FAFC", true);
                    String pUrl = postObj.optString("url", "");
                    if (pUrl.matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) attachImage(ui.box, pUrl, false);
                    String pVid = extractRedditVideo(postObj);
                    if (pVid != null && !pVid.isEmpty()) {
                        Button pv = ui.pill("▶ Guarda Video", "#EA580C", "#C2410C", null, "#FFFFFF");
                        pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(pVid)));
                        ui.box.addView(pv);
                    }
                    if (!fSelf.isEmpty()) ui.text(ui.box, fSelf, 13f, "#E2E8F0", false);
                    renderRedditTree(arr, 0);
                });
            } catch (Exception e) { runOnUiThread(() -> ld.setText("Errore commenti: " + e.getMessage())); }
        });
    }

    private void renderRedditTree(JSONArray children, int depth) {
        if (children == null || depth > 4) return;
        for (int i = 0; i < children.length(); i++) {
            JSONObject item = children.optJSONObject(i);
            if (item == null || !"t1".equals(item.optString("kind"))) continue;
            JSONObject c = item.optJSONObject("data");
            if (c == null) continue;
            final String body = c.optString("body", "").trim();
            if (body.isEmpty()) continue;

            LinearLayout box = ui.card();
            if (depth > 0) box.setPadding(ui.dp(12 + Math.min(depth * 10, 30)), ui.dp(10), ui.dp(12), ui.dp(10));
            ui.text(box, (depth > 0 ? "↳ u/" : "u/") + c.optString("author", "?") + " • ▲ " + c.optInt("score", 0), 10.5f, "#38BDF8", true);
            final TextView tv = ui.text(box, body, 12.5f, "#E2E8F0", false);
            if (autoIt) pool.execute(() -> { final String tr = api.translateIt(body); if (!tr.equals(body)) runOnUiThread(() -> tv.setText(tr)); });

            // Estrae GIF/foto nei commenti Reddit (link diretti o media_metadata)
            Matcher mImg = Pattern.compile("(https?://\\S+\\.(?:jpg|jpeg|png|webp|gif))", Pattern.CASE_INSENSITIVE).matcher(body);
            if (mImg.find()) attachImage(box, mImg.group(1).replace("&amp;", "&"), false);
            JSONObject mm = c.optJSONObject("media_metadata");
            if (mm != null) {
                Iterator<String> keys = mm.keys();
                if (keys.hasNext()) {
                    JSONObject m0 = mm.optJSONObject(keys.next());
                    if (m0 != null && m0.has("s")) {
                        String gu = m0.optJSONObject("s").optString("gif", m0.optJSONObject("s").optString("u", ""));
                        if (!gu.isEmpty()) attachImage(box, gu.replace("&amp;", "&"), false);
                    }
                }
            }
            ui.box.addView(box);

            Object rep = c.opt("replies");
            if (rep instanceof JSONObject) {
                renderRedditTree(((JSONObject) rep).optJSONObject("data").optJSONArray("children"), depth + 1);
            }
        }
    }

    // ==================== IMPOSTAZIONI & MEDIA ====================

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

    private void attachImage(final LinearLayout parent, final String url, boolean onTap) {
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
