package com.akemi.wristsocial;

import android.app.Activity;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WatchUi ui;
    private BridgeClient api;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final List<Runnable> backStack = new ArrayList<>();
    private boolean imagesOnTap = true;
    private boolean autoIt = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getSharedPreferences("ws_prefs", MODE_PRIVATE);
        ui = new WatchUi(this, p.getFloat("scale", 1.0f));
        api = new BridgeClient();
        api.host = p.getString("host", "http://127.0.0.1:8765");
        api.pin = p.getString("pin", "1234");
        imagesOnTap = p.getBoolean("img_tap", true);
        autoIt = p.getBoolean("auto_it", true);
        showInsta(false);
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

    private void topBar(String tab, Runnable reloadCache, Runnable reloadForce) {
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER);

        Button bAa = ui.chip("Aa", "#181E29", "#263042", "#2D3748", "#38BDF8");
        bAa.setOnClickListener(v -> cycleFont(reloadCache));

        Button bDm = ui.chip("💬 DM", "#181E29", "#263042", "#2D3748", "#F472B6");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = ui.dp(5);
        mlp.rightMargin = ui.dp(5);
        bDm.setLayoutParams(mlp);
        bDm.setOnClickListener(v -> nav(reloadCache, () -> showDm(false)));

        Button bRef = ui.chip("↻", "#181E29", "#263042", "#2D3748", "#34D399");
        bRef.setOnClickListener(v -> reloadForce.run());

        Button bSet = ui.chip("⚙", "#181E29", "#263042", "#2D3748", "#94A3B8");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = ui.dp(5);
        bSet.setLayoutParams(slp);
        bSet.setOnClickListener(v -> nav(reloadCache, this::showSettings));

        r1.addView(bAa);
        r1.addView(bDm);
        r1.addView(bRef);
        r1.addView(bSet);
        ui.box.addView(r1);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.setMargins(0, ui.dp(6), 0, ui.dp(6));
        r2.setLayoutParams(r2lp);

        boolean isIg = "ig".equals(tab);
        Button bIg = ui.chip("Instagram", isIg ? "#DB2777" : "#141923", "#BE185D", isIg ? null : "#2A3447", "#FFFFFF");
        Button bRd = ui.chip("Reddit", !isIg ? "#EA580C" : "#141923", "#C2410C", !isIg ? null : "#2A3447", "#FFFFFF");
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = ui.dp(5);
        bIg.setLayoutParams(p1);
        bRd.setLayoutParams(p2);
        bIg.setOnClickListener(v -> showInsta(false));
        bRd.setOnClickListener(v -> showReddit("", "hot", false));
        r2.addView(bIg);
        r2.addView(bRd);
        ui.box.addView(r2);
    }

    // ==================== INSTAGRAM ====================

    private void showInsta(final boolean force) {
        ui.clear();
        topBar("ig", () -> showInsta(false), () -> showInsta(true));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        final LinearLayout storyRow = new LinearLayout(this);
        storyRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(storyRow);
        ui.box.addView(hsv);

        final TextView loading = ui.text(ui.box, "Connessione a SocialBridge...", 13f, "#94A3B8", false);

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
                        ui.text(ui.box, "Nessun post. Verifica il login IG su SocialBridge (Z Flip).", 12.5f, "#E2E8F0", false);
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject m = items.optJSONObject(i);
                        if (m != null) renderIgCard(m);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("Errore Ponte: " + e.getMessage() + "\nApri SocialBridge sullo Z Flip."));
            }
        });
    }

    private void addStoryBubble(LinearLayout row, JSONObject st) {
        final String id = st.optString("id", "");
        final String user = st.optString("user", "");
        final String av = st.optString("avatar", "");

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4));

        final ImageView iv = new ImageView(this);
        int sz = ui.dp(44);
        iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        iv.setBackground(ui.roundBox("#1F2937", "#EC4899", 999));
        item.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(user.length() > 9 ? user.substring(0, 9) : user);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(ui.sp(9.5f));
        item.addView(tv);

        item.setOnClickListener(v -> nav(() -> showInsta(false), () -> openStory(id, user)));
        row.addView(item);

        if (!av.isEmpty()) {
            pool.execute(() -> {
                try {
                    Bitmap b = api.getProxiedImage(av);
                    if (b != null) {
                        final Bitmap circ = ui.makeCircleAvatar(b, sz);
                        runOnUiThread(() -> iv.setImageBitmap(circ));
                    }
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
                        String img = it.optString("img", ""), vid = it.optString("vid", "");
                        if (!img.isEmpty()) attachImage(ui.box, img, false);
                        if (!vid.isEmpty()) {
                            Button p = ui.pill("▶ Riproduci Storia Video", "#DB2777", "#BE185D", null, "#FFFFFF");
                            p.setOnClickListener(v -> ui.playVideo(vid));
                            ui.box.addView(p);
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> ld.setText("Errore storia: " + e.getMessage()));
            }
        });
    }

    private void renderIgCard(JSONObject m) {
        LinearLayout card = ui.card();
        final String id = m.optString("id", "");
        String user = m.optString("user", "");
        final String caption = m.optString("text", "");
        int likes = m.optInt("likes", 0);
        int comm = m.optInt("comments", 0);
        String img = m.optString("img", "");
        final String vid = m.optString("vid", "");

        ui.text(card, "@" + user, 12f, "#F472B6", true);
        if (!img.isEmpty()) attachImage(card, img, imagesOnTap);
        if (!vid.isEmpty()) {
            Button pv = ui.pill("▶ Riproduci Reel / Video", "#DB2777", "#BE185D", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(vid));
            card.addView(pv);
        }
        if (!caption.isEmpty()) {
            final TextView tvCap = ui.text(card, caption, 13f, "#F8FAFC", false);
            if (autoIt) {
                pool.execute(() -> {
                    final String tr = api.translateIt(caption);
                    if (!tr.equals(caption)) runOnUiThread(() -> tvCap.setText(tr));
                });
            }
        }

        final Button likeBtn = ui.pill("♥ " + likes + "  •  💬 " + comm, "#1B2332", "#263145", "#334155", "#E2E8F0");
        likeBtn.setOnClickListener(v -> {
            likeBtn.setText("♥ " + (likes + 1) + " (Piaciuto)");
            pool.execute(() -> { try { api.get("/ig/like?id=" + URLEncoder.encode(id, "UTF-8"), true); } catch (Exception ignored) {} });
        });
        card.addView(likeBtn);
        ui.box.addView(card);
    }

    // ==================== INSTAGRAM DIRECT (DM) ====================

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
                    if (threads == null || threads.length() == 0) { ui.text(ui.box, "Nessuna chat trovata.", 12.5f, "#E2E8F0", false); return; }
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
            } catch (Exception e) {
                runOnUiThread(() -> ld.setText("Errore DM: " + e.getMessage()));
            }
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
        et.setHintTextColor(Color.parseColor("#64748B"));
        et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final Button send = ui.pill("Invia Messaggio", "#DB2777", "#BE185D", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String msg = et.getText().toString().trim();
            if (msg.isEmpty()) return;
            send.setText("Invio...");
            send.setEnabled(false);
            pool.execute(() -> {
                try {
                    api.get("/ig/dm_send?id=" + URLEncoder.encode(threadId, "UTF-8") + "&text=" + URLEncoder.encode(msg, "UTF-8"), true);
                    runOnUiThread(this::onBackPressed);
                } catch (Exception e) {
                    runOnUiThread(() -> { send.setText("Errore invio"); send.setEnabled(true); });
                }
            });
        });
        ui.box.addView(send);
    }

    // ==================== REDDIT (LOGGATO SUL PONTE) ====================

    private void showReddit(final String sub, final String sort, final boolean force) {
        ui.clear();
        topBar("rd", () -> showReddit(sub, sort, false), () -> showReddit(sub, sort, true));

        final EditText subIn = new EditText(this);
        subIn.setHint(sub.isEmpty() ? "Home Reddit (o scrivi sub)" : "r/" + sub);
        subIn.setHintTextColor(Color.parseColor("#64748B"));
        subIn.setTextColor(Color.WHITE);
        subIn.setGravity(Gravity.CENTER);
        subIn.setSingleLine(true);
        subIn.setBackground(ui.roundBox("#141923", "#2A3447", 999));
        subIn.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        ui.box.addView(subIn);

        Button goSub = ui.pill("Vai al Subreddit / Home", "#EA580C", "#C2410C", null, "#FFFFFF");
        goSub.setOnClickListener(v -> showReddit(subIn.getText().toString().trim().replaceFirst("^/?r/", ""), "hot", false));
        ui.box.addView(goSub);

        final TextView ld = ui.text(ui.box, "Caricamento Reddit...", 13f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                String q = "/reddit/feed?sub=" + URLEncoder.encode(sub, "UTF-8") + "&sort=" + sort;
                JSONArray ch = new JSONObject(api.get(q, force)).getJSONObject("data").getJSONArray("children");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    for (int i = 0; i < ch.length(); i++) {
                        JSONObject d = ch.optJSONObject(i).optJSONObject("data");
                        if (d != null) renderRedditCard(sub, sort, d);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> ld.setText("Errore Reddit: " + e.getMessage()));
            }
        });
    }

    private void renderRedditCard(final String sub, final String sort, JSONObject d) {
        LinearLayout card = ui.card();
        final String title = d.optString("title", "");
        final String perm = d.optString("permalink", "");
        String url = d.optString("url", "");
        ui.text(card, "r/" + d.optString("subreddit", "") + " • ▲ " + d.optInt("score", 0), 10.5f, "#FB923C", true);
        final TextView tvT = ui.text(card, title, 13.5f, "#F8FAFC", true);
        if (autoIt) {
            pool.execute(() -> {
                final String tr = api.translateIt(title);
                if (!tr.equals(title)) runOnUiThread(() -> tvT.setText(tr));
            });
        }
        if (url.matches(".*\\.(jpg|jpeg|png|webp)(\\?.*)?$")) attachImage(card, url, imagesOnTap);
        Button comm = ui.pill("Commenti (" + d.optInt("num_comments", 0) + ")", "#1B2332", "#263145", "#334155", "#38BDF8");
        comm.setOnClickListener(v -> nav(() -> showReddit(sub, sort, false), () -> showRedditComments(perm)));
        card.addView(comm);
        ui.box.addView(card);
    }

    private void showRedditComments(final String perm) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> onBackPressed());
        ui.box.addView(back);
        final TextView ld = ui.text(ui.box, "Caricamento commenti...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONArray arr = new JSONArray(api.get("/reddit/comments?url=" + URLEncoder.encode(perm, "UTF-8"), false));
                final JSONArray comms = arr.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    for (int i = 0; i < comms.length(); i++) {
                        JSONObject item = comms.optJSONObject(i);
                        if (item == null || !"t1".equals(item.optString("kind"))) continue;
                        JSONObject c = item.optJSONObject("data");
                        final String body = c.optString("body", "").trim();
                        if (body.isEmpty()) continue;
                        LinearLayout box = ui.card();
                        ui.text(box, "u/" + c.optString("author", "?") + " • ▲ " + c.optInt("score", 0), 10.5f, "#38BDF8", true);
                        final TextView tv = ui.text(box, body, 12.5f, "#E2E8F0", false);
                        if (autoIt) {
                            pool.execute(() -> {
                                final String tr = api.translateIt(body);
                                if (!tr.equals(body)) runOnUiThread(() -> tv.setText(tr));
                            });
                        }
                        ui.box.addView(box);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> ld.setText("Errore commenti: " + e.getMessage()));
            }
        });
    }

    // ==================== IMPOSTAZIONI & MEDIA ====================

    private void showSettings() {
        ui.clear();
        Button back = ui.chip("← Salva e Chiudi", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        ui.box.addView(back);
        ui.text(ui.box, "Configura Ponte Z Flip", 15f, "#38BDF8", true);

        final EditText pinEt = new EditText(this);
        pinEt.setText(api.pin);
        pinEt.setHint("PIN a 4 cifre");
        pinEt.setTextColor(Color.WHITE);
        pinEt.setGravity(Gravity.CENTER);
        pinEt.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        pinEt.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(pinEt);

        final EditText hostEt = new EditText(this);
        hostEt.setText(api.host);
        hostEt.setTextColor(Color.WHITE);
        hostEt.setGravity(Gravity.CENTER);
        hostEt.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        hostEt.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(hostEt);

        final Button imgBtn = ui.pill(imagesOnTap ? "🖼 Foto: Solo al tocco" : "🖼 Foto: Automatiche", "#141923", "#222B3A", "#059669", "#34D399");
        imgBtn.setOnClickListener(v -> {
            imagesOnTap = !imagesOnTap;
            imgBtn.setText(imagesOnTap ? "🖼 Foto: Solo al tocco" : "🖼 Foto: Automatiche");
        });
        ui.box.addView(imgBtn);

        final Button trBtn = ui.pill(autoIt ? "🌐 Traduzione IT: ON" : "🌐 Traduzione IT: OFF", "#141923", "#222B3A", "#0284C7", "#38BDF8");
        trBtn.setOnClickListener(v -> {
            autoIt = !autoIt;
            trBtn.setText(autoIt ? "🌐 Traduzione IT: ON" : "🌐 Traduzione IT: OFF");
        });
        ui.box.addView(trBtn);

        back.setOnClickListener(v -> {
            api.pin = pinEt.getText().toString().trim();
            api.host = hostEt.getText().toString().trim();
            getSharedPreferences("ws_prefs", MODE_PRIVATE).edit()
                    .putString("pin", api.pin).putString("host", api.host)
                    .putBoolean("img_tap", imagesOnTap).putBoolean("auto_it", autoIt).apply();
            onBackPressed();
        });
    }

    private void attachImage(final LinearLayout parent, final String url, boolean onTap) {
        if (!onTap) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(ui.dp(170));
            parent.addView(iv);
            loadImg(url, iv);
            return;
        }
        final Button btn = ui.pill("🖼 Carica foto", "#0E1726", "#16243B", "#1D4ED8", "#60A5FA");
        btn.setOnClickListener(v -> {
            btn.setText("Caricamento...");
            btn.setEnabled(false);
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(ui.dp(170));
            int idx = parent.indexOfChild(btn);
            if (idx >= 0) { parent.removeView(btn); parent.addView(iv, idx); }
            else parent.addView(iv);
            loadImg(url, iv);
        });
        parent.addView(btn);
    }

    private void loadImg(final String url, final ImageView target) {
        pool.execute(() -> {
            try {
                final Bitmap bmp = api.getProxiedImage(url);
                if (bmp != null) runOnUiThread(() -> {
                    target.setImageBitmap(bmp);
                    target.setOnClickListener(v -> ui.showImage(bmp));
                });
            } catch (Exception ignored) {}
        });
    }
}
