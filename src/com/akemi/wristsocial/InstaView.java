package com.akemi.wristsocial;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

public class InstaView {
    private final MainActivity act;
    private final WatchUi ui;
    private final BridgeClient api;
    private final ExecutorService pool;

    public InstaView(MainActivity act, WatchUi ui, BridgeClient api, ExecutorService pool) {
        this.act = act; this.ui = ui; this.api = api; this.pool = pool;
    }

    public void show(final String mode, final boolean force) {
        ui.clear();
        act.topBar("ig", () -> show(mode, false), () -> show(mode, true));

        LinearLayout subBar = ui.hBar(2, 4);
        Button bHome = ui.chip("Home", "home".equals(mode) ? "#DB2777" : "#181E29", "#BE185D", "#2D3748", "#FFFFFF");
        bHome.setOnClickListener(v -> show("home", false));
        subBar.addView(bHome);

        Button bExp = ui.chip("Esplora", "explore".equals(mode) ? "#DB2777" : "#181E29", "#BE185D", "#2D3748", "#FFFFFF");
        bExp.setOnClickListener(v -> show("explore", false));
        act.addGapChip(subBar, bExp, 5);

        Button bSav = ui.chip("Salvati", "saved".equals(mode) ? "#DB2777" : "#181E29", "#BE185D", "#2D3748", "#FFFFFF");
        bSav.setOnClickListener(v -> show("saved", false));
        act.addGapChip(subBar, bSav, 5);

        Button bSrc = ui.chip("🔍 Cerca", "#181E29", "#263042", "#2D3748", "#38BDF8");
        bSrc.setOnClickListener(v -> act.nav(() -> show(mode, false), this::showSearch));
        act.addGapChip(subBar, bSrc, 5);

        final LinearLayout storyRow = "home".equals(mode) ? ui.hBar(2, 4) : null;
        final TextView ld = ui.text(ui.box, "Caricamento Instagram...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                final JSONArray stories = "home".equals(mode)
                        ? new JSONObject(api.get("/ig/stories", force)).optJSONArray("stories") : null;
                String ep = "explore".equals(mode) ? "/ig/explore" : "saved".equals(mode) ? "/ig/saved" : "/ig/feed";
                JSONObject jFeed = new JSONObject(api.get(ep, force));
                final JSONArray items = jFeed.optJSONArray("items");
                final String nextMax = jFeed.optString("next", "");

                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (stories != null && storyRow != null) {
                        for (int i = 0; i < stories.length(); i++) {
                            JSONObject st = stories.optJSONObject(i);
                            if (st != null) addStoryBubble(storyRow, st, () -> show(mode, false));
                        }
                    }
                    if (items == null || items.length() == 0) {
                        ui.text(ui.box, "Nessun post trovato.", 12f, "#E2E8F0", false);
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject m = items.optJSONObject(i);
                        if (m != null) renderCard(m, () -> show(mode, false));
                    }
                    if (!nextMax.isEmpty() && "home".equals(mode)) {
                        addLoadMoreBtn(nextMax, () -> show(mode, false));
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore IG: " + e.getMessage())); }
        });
    }

    private void addLoadMoreBtn(final String maxId, final Runnable back) {
        final Button bMore = ui.pill("⬇ Carica altri post", "#181E29", "#263042", "#DB2777", "#F472B6");
        bMore.setOnClickListener(v -> {
            bMore.setText("Caricamento..."); bMore.setEnabled(false);
            pool.execute(() -> {
                try {
                    JSONObject j = new JSONObject(api.get("/ig/feed?max_id=" + URLEncoder.encode(maxId, "UTF-8"), true));
                    final JSONArray more = j.optJSONArray("items");
                    final String next2 = j.optString("next", "");
                    act.runOnUiThread(() -> {
                        ui.box.removeView(bMore);
                        if (more != null) {
                            for (int i = 0; i < more.length(); i++) {
                                JSONObject m = more.optJSONObject(i);
                                if (m != null) renderCard(m, back);
                            }
                        }
                        if (!next2.isEmpty()) addLoadMoreBtn(next2, back);
                    });
                } catch (Exception e) { act.runOnUiThread(() -> { bMore.setText("Riprova"); bMore.setEnabled(true); }); }
            });
        });
        ui.box.addView(bMore);
    }

    public void addStoryBubble(LinearLayout row, JSONObject st, final Runnable back) {
        final String id = st.optString("id", ""), user = st.optString("user", ""), av = st.optString("avatar", "");
        LinearLayout item = new LinearLayout(act);
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        item.setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4));
        final ImageView iv = new ImageView(act);
        int sz = ui.dp(42);
        iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        iv.setBackground(ui.roundBox("#1F2937", "#EC4899", 999));
        item.addView(iv);
        TextView tv = new TextView(act);
        tv.setText(user.length() > 9 ? user.substring(0, 9) : user);
        tv.setTextColor(Color.parseColor("#CBD5E1")); tv.setTextSize(ui.sp(9.5f));
        item.addView(tv);
        item.setOnClickListener(v -> act.nav(back, () -> openStory(id, user)));
        row.addView(item);
        if (!av.isEmpty()) {
            pool.execute(() -> {
                try {
                    Bitmap b = api.getProxiedImage(av);
                    if (b != null) { final Bitmap circ = ui.makeCircleAvatar(b, sz); act.runOnUiThread(() -> iv.setImageBitmap(circ)); }
                } catch (Exception ignored) {}
            });
        }
    }

    public void openStory(final String reelId, final String user) {
        ui.clear();
        LinearLayout top = ui.hBar(0, 6);
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        top.addView(back);
        Button bProf = ui.chip("👤 @" + user, "#181E29", "#263042", "#EC4899", "#F472B6");
        bProf.setOnClickListener(v -> act.nav(() -> openStory(reelId, user), () -> showProfile(user)));
        act.addGapChip(top, bProf, 5);

        final TextView ld = ui.text(ui.box, "Caricamento storia di @" + user + "...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray items = new JSONObject(api.get("/ig/story?id=" + URLEncoder.encode(reelId, "UTF-8"), true)).optJSONArray("items");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (items == null || items.length() == 0) { ui.text(ui.box, "Nessuna storia attiva trovata.", 12.5f, "#E2E8F0", false); return; }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        String img = it.optString("img", "");
                        final String vid = it.optString("vid", "");
                        if (!img.isEmpty()) act.attachImage(ui.box, img, false);
                        if (!vid.isEmpty()) {
                            Button p = ui.pill("▶ Riproduci Storia Video", "#DB2777", "#BE185D", null, "#FFFFFF");
                            p.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(vid)));
                            ui.box.addView(p);
                        }
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore storia: " + e.getMessage())); }
        });
    }

    public void showProfile(final String username) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        final TextView ld = ui.text(ui.box, "Caricamento profilo @" + username + "...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONObject p = new JSONObject(api.get("/ig/profile?user=" + URLEncoder.encode(username, "UTF-8"), false));
                final String uid = p.optString("id", ""), name = p.optString("name", ""), bio = p.optString("bio", ""), av = p.optString("avatar", "");
                final int postsCnt = p.optInt("posts_count", 0), followers = p.optInt("followers", 0), following = p.optInt("following", 0);
                final boolean followed = p.optBoolean("followed", false);
                final JSONArray items = p.optJSONArray("items");

                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    LinearLayout hdr = ui.card();
                    hdr.setGravity(Gravity.CENTER);
                    final ImageView iv = new ImageView(act);
                    int sz = ui.dp(56);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                    iv.setBackground(ui.roundBox("#1F2937", "#EC4899", 999));
                    hdr.addView(iv);
                    if (!av.isEmpty()) pool.execute(() -> {
                        try { Bitmap b = api.getProxiedImage(av); if (b != null) { final Bitmap c = ui.makeCircleAvatar(b, sz); act.runOnUiThread(() -> iv.setImageBitmap(c)); } } catch (Exception ignored) {}
                    });

                    ui.text(hdr, "@" + username + (name.isEmpty() ? "" : "\n" + name), 14f, "#F8FAFC", true);
                    ui.text(hdr, postsCnt + " Post  •  " + followers + " Follower  •  " + following + " Seguiti", 11f, "#38BDF8", true);
                    if (!bio.isEmpty()) ui.text(hdr, bio, 12f, "#CBD5E1", false);

                    if (!uid.isEmpty()) {
                        final boolean[] isFol = {followed};
                        final Button bFol = ui.pill(isFol[0] ? "✓ Seguito" : "+ Segui", isFol[0] ? "#181E29" : "#DB2777", "#BE185D", "#EC4899", "#FFFFFF");
                        bFol.setOnClickListener(v -> {
                            isFol[0] = !isFol[0];
                            bFol.setText(isFol[0] ? "✓ Seguito" : "+ Segui");
                            pool.execute(() -> { try { api.get("/ig/follow?id=" + uid + "&unfollow=" + (isFol[0] ? "0" : "1"), true); } catch (Exception ignored) {} });
                        });
                        hdr.addView(bFol);

                        Button bSt = ui.pill("⭕ Guarda Storie", "#172033", "#1E293B", "#EC4899", "#F472B6");
                        bSt.setOnClickListener(v -> act.nav(() -> showProfile(username), () -> openStory(uid, username)));
                        hdr.addView(bSt);
                    }
                    ui.box.addView(hdr);

                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject m = items.optJSONObject(i);
                            if (m != null) renderCard(m, () -> showProfile(username));
                        }
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore profilo: " + e.getMessage())); }
        });
    }

    public void renderCard(JSONObject m, final Runnable back) {
        LinearLayout card = ui.card();
        final String id = m.optString("id", ""), user = m.optString("user", ""), caption = m.optString("text", "");
        int likes = m.optInt("likes", 0), comm = m.optInt("comments", 0);
        String img = m.optString("img", "");
        final String vid = m.optString("vid", "");
        final JSONArray imgs = m.optJSONArray("imgs");

        Button uBtn = ui.chip("👤 @" + user + (imgs != null && imgs.length() > 1 ? " • 1/" + imgs.length() : ""), "#1B1523", "#2D1F3D", "#EC4899", "#F472B6");
        uBtn.setOnClickListener(v -> act.nav(back, () -> showProfile(user)));
        card.addView(uBtn);

        if (!img.isEmpty()) act.attachImage(card, img, act.imagesOnTap);
        if (imgs != null && imgs.length() > 1) {
            Button bAll = ui.pill("🖼 Vedi tutte le " + imgs.length() + " foto", "#172033", "#1E293B", "#EC4899", "#F472B6");
            bAll.setOnClickListener(v -> act.nav(back, () -> act.showCarousel(user, imgs)));
            card.addView(bAll);
        }
        if (!vid.isEmpty()) {
            Button pv = ui.pill("▶ Guarda Video / Reel", "#DB2777", "#BE185D", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(vid)));
            card.addView(pv);
        }
        if (!caption.isEmpty()) {
            final TextView tvCap = ui.text(card, caption, 13f, "#F8FAFC", false);
            if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(caption); if (!tr.equals(caption)) act.runOnUiThread(() -> tvCap.setText(tr)); });
        }

        final Button likeBtn = ui.pill("♥ Mi piace (" + likes + ")", "#1B2332", "#263145", "#334155", "#F472B6");
        likeBtn.setOnClickListener(v -> {
            likeBtn.setText("♥ Piaciuto (" + (likes + 1) + ")");
            pool.execute(() -> { try { api.get("/ig/like?id=" + URLEncoder.encode(id, "UTF-8"), true); } catch (Exception ignored) {} });
        });
        card.addView(likeBtn);

        Button commBtn = ui.pill("💬 Commenti (" + comm + ")", "#141923", "#222B3A", "#334155", "#38BDF8");
        commBtn.setOnClickListener(v -> act.nav(back, () -> showComments(id, user)));
        card.addView(commBtn);
        ui.box.addView(card);
    }

    public void showSearch() {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);

        final EditText et = new EditText(act);
        et.setHint("Cerca utenti o #hashtag IG...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#EC4899", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final LinearLayout resBox = new LinearLayout(act);
        resBox.setOrientation(LinearLayout.VERTICAL);

        Button go = ui.pill("🔍 Cerca su Instagram", "#DB2777", "#BE185D", null, "#FFFFFF");
        go.setOnClickListener(v -> {
            final String q = et.getText().toString().trim();
            if (q.isEmpty()) return;
            resBox.removeAllViews();
            ui.text(resBox, "Ricerca in corso...", 12f, "#94A3B8", false);
            pool.execute(() -> {
                try {
                    JSONObject j = new JSONObject(api.get("/ig/search?q=" + URLEncoder.encode(q, "UTF-8"), true));
                    final JSONArray users = j.optJSONArray("users"), tags = j.optJSONArray("tags");
                    act.runOnUiThread(() -> {
                        resBox.removeAllViews();
                        if (users != null) {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject u = users.optJSONObject(i);
                                final String un = u.optString("user", "");
                                Button b = ui.pill("👤 @" + un + " (" + u.optString("name", "") + ")", "#141923", "#1E2634", "#EC4899", "#F8FAFC");
                                b.setOnClickListener(v2 -> act.nav(this::showSearch, () -> showProfile(un)));
                                resBox.addView(b);
                            }
                        }
                        if (tags != null) {
                            for (int i = 0; i < tags.length(); i++) {
                                JSONObject t = tags.optJSONObject(i);
                                ui.text(resBox, "#" + t.optString("name", "") + " (" + t.optInt("count", 0) + " post)", 12f, "#38BDF8", true);
                            }
                        }
                    });
                } catch (Exception e) { act.runOnUiThread(() -> ui.text(resBox, "Errore: " + e.getMessage(), 12f, "#F87171", false)); }
            });
        });
        ui.box.addView(go);
        ui.box.addView(resBox);
    }

    public void showComments(final String mediaId, final String user) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Commenti @" + user, 14f, "#F472B6", true);

        final EditText et = new EditText(act);
        et.setHint("Aggiungi un commento...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        ui.box.addView(et);

        final Button send = ui.pill("💬 Pubblica Commento", "#DB2777", "#BE185D", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String txt = et.getText().toString().trim();
            if (txt.isEmpty()) return;
            send.setText("Invio..."); send.setEnabled(false);
            pool.execute(() -> {
                try {
                    api.get("/ig/comment_add?id=" + URLEncoder.encode(mediaId, "UTF-8") + "&text=" + URLEncoder.encode(txt, "UTF-8"), true);
                    act.runOnUiThread(() -> showComments(mediaId, user));
                } catch (Exception e) { act.runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); }); }
            });
        });
        ui.box.addView(send);

        final TextView ld = ui.text(ui.box, "Caricamento commenti...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray arr = new JSONObject(api.get("/ig/comments?id=" + URLEncoder.encode(mediaId, "UTF-8"), true)).optJSONArray("comments");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (arr == null || arr.length() == 0) { ui.text(ui.box, "Nessun commento.", 12.5f, "#E2E8F0", false); return; }
                    renderCommentList(arr, 0, () -> showComments(mediaId, user));
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore: " + e.getMessage())); }
        });
    }

    private void renderCommentList(JSONArray arr, int depth, final Runnable back) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            final String cu = c.optString("user", "");
            LinearLayout box = ui.card();
            if (depth > 0) box.setPadding(ui.dp(22), ui.dp(10), ui.dp(12), ui.dp(10));
            Button uBtn = ui.chip((depth > 0 ? "↳ @" : "@") + cu + " • ♥ " + c.optInt("likes", 0), "#181E29", "#263042", "#2D3748", "#38BDF8");
            uBtn.setOnClickListener(v -> act.nav(back, () -> showProfile(cu)));
            box.addView(uBtn);

            final String txt = c.optString("text", "");
            if (!txt.isEmpty()) {
                final TextView tv = ui.text(box, txt, 12.5f, "#E2E8F0", false);
                if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(txt); if (!tr.equals(txt)) act.runOnUiThread(() -> tv.setText(tr)); });
            }
            String gif = c.optString("img", "");
            if (!gif.isEmpty()) act.attachImage(box, gif, false);
            ui.box.addView(box);
            renderCommentList(c.optJSONArray("replies"), depth + 1, back);
        }
    }

    public void showDm(final boolean force) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, "Direct Instagram", 15f, "#F472B6", true);
        final TextView ld = ui.text(ui.box, "Caricamento chat...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray threads = new JSONObject(api.get("/ig/dm", force)).optJSONArray("threads");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (threads == null || threads.length() == 0) { ui.text(ui.box, "Nessuna chat.", 12.5f, "#E2E8F0", false); return; }
                    for (int i = 0; i < threads.length(); i++) {
                        JSONObject t = threads.optJSONObject(i);
                        final String tid = t.optString("id", ""), title = t.optString("title", "Chat");
                        LinearLayout c = ui.card();
                        ui.text(c, title, 13f, "#38BDF8", true);
                        ui.text(c, t.optString("last", ""), 12f, "#CBD5E1", false);
                        Button open = ui.pill("Apri Chat e Rispondi", "#DB2777", "#BE185D", null, "#FFFFFF");
                        open.setOnClickListener(v -> act.nav(() -> showDm(false), () -> openDmThread(tid, title)));
                        c.addView(open);
                        ui.box.addView(c);
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore DM: " + e.getMessage())); }
        });
    }

    private void openDmThread(final String tid, final String title) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, title, 14f, "#F472B6", true);

        final EditText et = new EditText(act);
        et.setHint("Scrivi messaggio...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        ui.box.addView(et);

        final Button send = ui.pill("Invia", "#DB2777", "#BE185D", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String msg = et.getText().toString().trim();
            if (msg.isEmpty()) return;
            send.setText("Invio..."); send.setEnabled(false);
            pool.execute(() -> {
                try {
                    api.get("/ig/dm_send?id=" + URLEncoder.encode(tid, "UTF-8") + "&text=" + URLEncoder.encode(msg, "UTF-8"), true);
                    act.runOnUiThread(() -> openDmThread(tid, title));
                } catch (Exception e) { act.runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); }); }
            });
        });
        ui.box.addView(send);

        pool.execute(() -> {
            try {
                JSONArray msgs = new JSONObject(api.get("/ig/dm_thread?id=" + URLEncoder.encode(tid, "UTF-8"), true)).optJSONArray("messages");
                act.runOnUiThread(() -> {
                    if (msgs == null) return;
                    for (int i = 0; i < msgs.length(); i++) {
                        JSONObject m = msgs.optJSONObject(i);
                        LinearLayout b = ui.card();
                        ui.text(b, m.optString("sender", ""), 10.5f, m.optBoolean("isMe") ? "#34D399" : "#38BDF8", true);
                        ui.text(b, m.optString("text", ""), 12.5f, "#F8FAFC", false);
                        ui.box.addView(b);
                    }
                });
            } catch (Exception ignored) {}
        });
    }
}
