package com.akemi.wristsocial;

import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class RedditView {
    private final MainActivity act;
    private final WatchUi ui;
    private final BridgeClient api;
    private final ExecutorService pool;

    public RedditView(MainActivity act, WatchUi ui, BridgeClient api, ExecutorService pool) {
        this.act = act; this.ui = ui; this.api = api; this.pool = pool;
    }

    public void show(final String sub, final String sort, final boolean force) {
        ui.clear();
        act.topBar("rd", () -> show(sub, sort, false), () -> show(sub, sort, true));

        LinearLayout sBar = ui.hBar(2, 4);
        Button bHome = ui.chip("Home", sub.isEmpty() && !"saved".equals(sort) ? "#EA580C" : "#181E29", "#C2410C", "#2D3748", "#FFFFFF");
        bHome.setOnClickListener(v -> show("", "hot", false));
        sBar.addView(bHome);

        Button bPop = ui.chip("Popolari", "popular".equalsIgnoreCase(sub) ? "#EA580C" : "#181E29", "#C2410C", "#2D3748", "#FFFFFF");
        bPop.setOnClickListener(v -> show("popular", "hot", false));
        act.addGapChip(sBar, bPop, 5);

        Button bSav = ui.chip("Salvati", "saved".equals(sort) ? "#EA580C" : "#181E29", "#C2410C", "#2D3748", "#FFFFFF");
        bSav.setOnClickListener(v -> show("", "saved", false));
        act.addGapChip(sBar, bSav, 5);

        Button bSrc = ui.chip("🔍 Cerca", "#181E29", "#263042", "#2D3748", "#38BDF8");
        bSrc.setOnClickListener(v -> act.nav(() -> show(sub, sort, false), this::showSearch));
        act.addGapChip(sBar, bSrc, 5);

        Button bTr = ui.chip(act.autoIt ? "🌐 IT: ON" : "🌐 IT: OFF", act.autoIt ? "#0284C7" : "#181E29", "#0369A1", "#2D3748", "#FFFFFF");
        bTr.setOnClickListener(v -> {
            act.autoIt = !act.autoIt;
            act.getSharedPreferences("ws_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("auto_it", act.autoIt).apply();
            show(sub, sort, false);
        });
        act.addGapChip(sBar, bTr, 5);

        if (!sub.isEmpty() && !"popular".equalsIgnoreCase(sub)) {
            renderSubHeader(sub, sort);
        }

        final TextView ld = ui.text(ui.box, "Caricamento Reddit...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                String ep = "saved".equals(sort) ? "/reddit/saved"
                        : "/reddit/feed?sub=" + URLEncoder.encode(sub, "UTF-8") + "&sort=" + sort;
                JSONObject data = new JSONObject(api.get(ep, force)).getJSONObject("data");
                final JSONArray ch = data.getJSONArray("children");
                final String after = data.optString("after", "");

                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (ch.length() == 0) { ui.text(ui.box, "Nessun post trovato.", 12.5f, "#E2E8F0", false); return; }
                    for (int i = 0; i < ch.length(); i++) {
                        JSONObject d = ch.optJSONObject(i).optJSONObject("data");
                        if (d != null) renderCard(d, () -> show(sub, sort, false));
                    }
                    if (!after.isEmpty() && !"saved".equals(sort)) {
                        addLoadMoreBtn(sub, sort, after, () -> show(sub, sort, false));
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore Reddit: " + e.getMessage())); }
        });
    }

    private void renderSubHeader(final String sub, final String sort) {
        final LinearLayout box = ui.card();
        ui.text(box, "r/" + sub, 15f, "#FB923C", true);
        final TextView infoTv = ui.text(box, "Caricamento info community...", 11.5f, "#94A3B8", false);

        LinearLayout sortBar = ui.hBar(2, 2);
        String[][] tabs = {{"Hot", "hot"}, {"Nuovi", "new"}, {"Top 24h", "day"}, {"Top 7gg", "week"}};
        for (int i = 0; i < tabs.length; i++) {
            final String sc = tabs[i][1];
            Button tb = ui.chip(tabs[i][0], sc.equals(sort) ? "#EA580C" : "#181E29", "#C2410C", "#2D3748", "#FFFFFF");
            tb.setOnClickListener(v -> show(sub, sc, false));
            if (i == 0) sortBar.addView(tb); else act.addGapChip(sortBar, tb, 4);
        }
        ui.box.addView(box);

        pool.execute(() -> {
            try {
                JSONObject j = new JSONObject(api.get("/reddit/sub_info?sub=" + URLEncoder.encode(sub, "UTF-8"), false));
                final int subs = j.optInt("subs", 0);
                final String desc = j.optString("desc", "");
                final boolean[] isSub = {j.optBoolean("subscribed", false)};
                act.runOnUiThread(() -> {
                    infoTv.setText(subs + " iscritti" + (desc.isEmpty() ? "" : "\n" + (desc.length() > 120 ? desc.substring(0, 120) + "..." : desc)));
                    final Button bSub = ui.pill(isSub[0] ? "✓ Iscritto" : "+ Iscriviti a r/" + sub, isSub[0] ? "#181E29" : "#EA580C", "#C2410C", "#EA580C", "#FFFFFF");
                    bSub.setOnClickListener(v -> {
                        isSub[0] = !isSub[0];
                        bSub.setText(isSub[0] ? "✓ Iscritto" : "+ Iscriviti a r/" + sub);
                        pool.execute(() -> { try { api.get("/reddit/sub_toggle?sub=" + URLEncoder.encode(sub, "UTF-8") + "&unsub=" + (isSub[0] ? "0" : "1"), true); } catch (Exception ignored) {} });
                    });
                    box.addView(bSub);
                });
            } catch (Exception ignored) {}
        });
    }

    private void addLoadMoreBtn(final String sub, final String sort, final String after, final Runnable back) {
        final Button bMore = ui.pill("⬇ Carica altri post", "#181E29", "#263042", "#EA580C", "#FB923C");
        bMore.setOnClickListener(v -> {
            bMore.setText("Caricamento..."); bMore.setEnabled(false);
            pool.execute(() -> {
                try {
                    String ep = "/reddit/feed?sub=" + URLEncoder.encode(sub, "UTF-8") + "&sort=" + sort + "&after=" + URLEncoder.encode(after, "UTF-8");
                    JSONObject data = new JSONObject(api.get(ep, true)).getJSONObject("data");
                    final JSONArray ch = data.getJSONArray("children");
                    final String nextAfter = data.optString("after", "");
                    act.runOnUiThread(() -> {
                        ui.box.removeView(bMore);
                        for (int i = 0; i < ch.length(); i++) {
                            JSONObject d = ch.optJSONObject(i).optJSONObject("data");
                            if (d != null) renderCard(d, back);
                        }
                        if (!nextAfter.isEmpty()) addLoadMoreBtn(sub, sort, nextAfter, back);
                    });
                } catch (Exception e) { act.runOnUiThread(() -> { bMore.setText("Riprova"); bMore.setEnabled(true); }); }
            });
        });
        ui.box.addView(bMore);
    }

    public void showUserProfile(final String username) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        final TextView ld = ui.text(ui.box, "Profilo u/" + username + "...", 12.5f, "#94A3B8", false);

        pool.execute(() -> {
            try {
                JSONObject j = new JSONObject(api.get("/reddit/user?u=" + URLEncoder.encode(username, "UTF-8"), false));
                final int karma = j.optInt("karma", 0);
                final String bio = j.optString("bio", "");
                final JSONArray items = j.optJSONArray("items");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    LinearLayout hdr = ui.card();
                    ui.text(hdr, "u/" + username, 15f, "#38BDF8", true);
                    ui.text(hdr, "Karma: " + karma, 11.5f, "#FB923C", true);
                    if (!bio.isEmpty()) ui.text(hdr, bio, 12f, "#CBD5E1", false);
                    ui.box.addView(hdr);

                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject d = items.optJSONObject(i).optJSONObject("data");
                            if (d != null) renderCard(d, () -> showUserProfile(username));
                        }
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore utente: " + e.getMessage())); }
        });
    }

    public void renderCard(JSONObject d, final Runnable back) {
        LinearLayout card = ui.card();
        final String name = d.optString("name", ""), sub = d.optString("subreddit", ""), author = d.optString("author", "");
        final String title = d.optString("title", ""), perm = d.optString("permalink", ""), url = d.optString("url", "");
        int score = d.optInt("score", 0);

        LinearLayout metaRow = new LinearLayout(act);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        if (!sub.isEmpty()) {
            Button bSub = ui.chip("r/" + sub, "#26150E", "#431F12", "#9A3412", "#FB923C");
            bSub.setOnClickListener(v -> act.nav(back, () -> show(sub, "hot", false)));
            metaRow.addView(bSub);
        }
        if (!author.isEmpty()) {
            Button bUsr = ui.chip("u/" + author, "#141923", "#222B3A", "#2D3748", "#38BDF8");
            bUsr.setOnClickListener(v -> act.nav(back, () -> showUserProfile(author)));
            act.addGapChip(metaRow, bUsr, 4);
        }
        card.addView(metaRow);

        final TextView tvT = ui.text(card, title, 13.5f, "#F8FAFC", true);
        if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(title); if (!tr.equals(title)) act.runOnUiThread(() -> tvT.setText(tr)); });
        final String selfPreview = d.optString("selftext", "").trim();
        if (!selfPreview.isEmpty()) {
            final String cut = selfPreview.length() > 160 ? selfPreview.substring(0, 160) + "..." : selfPreview;
            final TextView tvS = ui.text(card, cut, 12f, "#CBD5E1", false);
            if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(cut); if (!tr.equals(cut)) act.runOnUiThread(() -> tvS.setText(tr)); });
        }

        String vid = extractVideo(d);
        if (vid != null && !vid.isEmpty()) {
            final String fVid = vid;
            Button pv = ui.pill("▶ Guarda Video Reddit", "#EA580C", "#C2410C", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(fVid)));
            card.addView(pv);
        }

        final JSONArray gal = extractGallery(d);
        if (gal.length() > 1) {
            act.attachImage(card, gal.optString(0, ""), act.imagesOnTap);
            Button bGal = ui.pill("🖼 Vedi tutte le " + gal.length() + " foto", "#172033", "#1E293B", "#EA580C", "#FB923C");
            bGal.setOnClickListener(v -> act.nav(back, () -> act.showCarousel("r/" + sub, gal)));
            card.addView(bGal);
        } else if (url.matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) {
            act.attachImage(card, url, act.imagesOnTap);
        }

        LinearLayout actBar = new LinearLayout(act);
        actBar.setOrientation(LinearLayout.HORIZONTAL);
        final Button bUp = ui.chip("▲ " + score, "#181E29", "#263042", "#EA580C", "#FB923C");
        bUp.setOnClickListener(v -> {
            bUp.setText("▲ " + (score + 1));
            pool.execute(() -> { try { api.get("/reddit/vote?id=" + name + "&dir=1", true); } catch (Exception ignored) {} });
        });
        actBar.addView(bUp);

        final Button bSave = ui.chip("🔖 Salva", "#181E29", "#263042", "#2D3748", "#34D399");
        bSave.setOnClickListener(v -> {
            bSave.setText("✓ Salvato");
            pool.execute(() -> { try { api.get("/reddit/save?id=" + name, true); } catch (Exception ignored) {} });
        });
        act.addGapChip(actBar, bSave, 5);
        card.addView(actBar);

        Button comm = ui.pill("💬 Commenti (" + d.optInt("num_comments", 0) + ")", "#1B2332", "#263145", "#334155", "#38BDF8");
        comm.setOnClickListener(v -> act.nav(back, () -> showComments(perm, name)));
        card.addView(comm);
        ui.box.addView(card);
    }

    public void showSearch() {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);

        final EditText et = new EditText(act);
        et.setHint("Cerca subreddit o post Reddit...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#EA580C", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        final LinearLayout resBox = new LinearLayout(act);
        resBox.setOrientation(LinearLayout.VERTICAL);

        Button go = ui.pill("🔍 Cerca su Reddit", "#EA580C", "#C2410C", null, "#FFFFFF");
        go.setOnClickListener(v -> {
            final String q = et.getText().toString().trim();
            if (q.isEmpty()) return;
            resBox.removeAllViews();
            ui.text(resBox, "Ricerca in corso...", 12f, "#94A3B8", false);
            pool.execute(() -> {
                try {
                    JSONObject j = new JSONObject(api.get("/reddit/search?q=" + URLEncoder.encode(q, "UTF-8"), true));
                    final JSONArray subs = j.optJSONArray("subs"), posts = j.optJSONArray("posts");
                    act.runOnUiThread(() -> {
                        resBox.removeAllViews();
                        if (subs != null) {
                            for (int i = 0; i < subs.length(); i++) {
                                JSONObject s = subs.optJSONObject(i);
                                final String sn = s.optString("sub", "");
                                Button b = ui.pill("r/" + sn + " (" + s.optInt("subs", 0) + " iscritti)", "#26150E", "#431F12", "#EA580C", "#FB923C");
                                b.setOnClickListener(v2 -> act.nav(this::showSearch, () -> show(sn, "hot", false)));
                                resBox.addView(b);
                            }
                        }
                        if (posts != null) {
                            for (int i = 0; i < posts.length(); i++) {
                                JSONObject d = posts.optJSONObject(i).optJSONObject("data");
                                if (d != null) renderCard(d, this::showSearch);
                            }
                        }
                    });
                } catch (Exception e) { act.runOnUiThread(() -> ui.text(resBox, "Errore: " + e.getMessage(), 12f, "#F87171", false)); }
            });
        });
        ui.box.addView(go);
        ui.box.addView(resBox);
    }

    public void showComments(final String perm, final String thingId) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);

        final EditText et = new EditText(act);
        et.setHint("Scrivi un commento su Reddit...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
        et.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        ui.box.addView(et);

        final Button send = ui.pill("💬 Commenta", "#EA580C", "#C2410C", null, "#FFFFFF");
        send.setOnClickListener(v -> {
            final String txt = et.getText().toString().trim();
            if (txt.isEmpty() || thingId.isEmpty()) return;
            send.setText("Invio..."); send.setEnabled(false);
            pool.execute(() -> {
                try {
                    api.get("/reddit/comment_add?thing=" + URLEncoder.encode(thingId, "UTF-8") + "&text=" + URLEncoder.encode(txt, "UTF-8"), true);
                    act.runOnUiThread(() -> showComments(perm, thingId));
                } catch (Exception e) { act.runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); }); }
            });
        });
        ui.box.addView(send);

        final TextView ld = ui.text(ui.box, "Caricamento post e commenti...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                String raw = api.get("/reddit/comments?url=" + URLEncoder.encode(perm, "UTF-8"), true).trim();
                if (!raw.startsWith("[")) throw new Exception("Discussione non disponibile");
                JSONArray rootArr = new JSONArray(raw);
                if (rootArr.length() < 2) throw new Exception("Nessun dato");

                final JSONObject postObj = rootArr.getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
                final JSONArray arr = rootArr.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                String rawTitle = postObj.optString("title", ""), rawSelf = postObj.optString("selftext", "").trim();
                final String fTitle = (act.autoIt && !rawTitle.isEmpty()) ? api.translateIt(rawTitle) : rawTitle;
                final String fSelf = (act.autoIt && !rawSelf.isEmpty()) ? api.translateIt(rawSelf) : rawSelf;

                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (!fTitle.isEmpty()) ui.text(ui.box, fTitle, 14f, "#F8FAFC", true);
                    String pUrl = postObj.optString("url", "");
                    if (pUrl.matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) act.attachImage(ui.box, pUrl, false);
                    String pVid = extractVideo(postObj);
                    if (pVid != null && !pVid.isEmpty()) {
                        Button pv = ui.pill("▶ Guarda Video", "#EA580C", "#C2410C", null, "#FFFFFF");
                        pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(pVid)));
                        ui.box.addView(pv);
                    }
                    if (!fSelf.isEmpty()) ui.text(ui.box, fSelf, 13f, "#E2E8F0", false);
                    renderTree(arr, 0, () -> showComments(perm, thingId));
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore commenti: " + e.getMessage())); }
        });
    }

    private void renderTree(JSONArray children, int depth, final Runnable back) {
        if (children == null || depth > 4) return;
        for (int i = 0; i < children.length(); i++) {
            JSONObject item = children.optJSONObject(i);
            if (item == null || !"t1".equals(item.optString("kind"))) continue;
            JSONObject c = item.optJSONObject("data");
            if (c == null) continue;
            final String body = c.optString("body", "").trim(), author = c.optString("author", "?");
            if (body.isEmpty()) continue;

            LinearLayout box = ui.card();
            if (depth > 0) box.setPadding(ui.dp(12 + Math.min(depth * 10, 30)), ui.dp(10), ui.dp(12), ui.dp(10));
            Button bUsr = ui.chip((depth > 0 ? "↳ u/" : "u/") + author + " • ▲ " + c.optInt("score", 0), "#181E29", "#263042", "#2D3748", "#38BDF8");
            bUsr.setOnClickListener(v -> act.nav(back, () -> showUserProfile(author)));
            box.addView(bUsr);

            final TextView tv = ui.text(box, body, 12.5f, "#E2E8F0", false);
            if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(body); if (!tr.equals(body)) act.runOnUiThread(() -> tv.setText(tr)); });

            Matcher mImg = Pattern.compile("(https?://\\S+\\.(?:jpg|jpeg|png|webp|gif))", Pattern.CASE_INSENSITIVE).matcher(body);
            if (mImg.find()) act.attachImage(box, mImg.group(1).replace("&amp;", "&"), false);
            JSONObject mm = c.optJSONObject("media_metadata");
            if (mm != null) {
                Iterator<String> keys = mm.keys();
                if (keys.hasNext()) {
                    JSONObject m0 = mm.optJSONObject(keys.next());
                    if (m0 != null && m0.has("s")) {
                        String gu = m0.optJSONObject("s").optString("gif", m0.optJSONObject("s").optString("u", ""));
                        if (!gu.isEmpty()) act.attachImage(box, gu.replace("&amp;", "&"), false);
                    }
                }
            }
            ui.box.addView(box);

            Object rep = c.opt("replies");
            if (rep instanceof JSONObject) {
                renderTree(((JSONObject) rep).optJSONObject("data").optJSONArray("children"), depth + 1, back);
            }
        }
    }

    private String extractVideo(JSONObject post) {
        try {
            JSONObject m = post.optJSONObject("secure_media");
            if (m == null) m = post.optJSONObject("media");
            if (m != null && m.has("reddit_video")) return m.getJSONObject("reddit_video").optString("fallback_url", "");
        } catch (Exception ignored) {}
        String u = post.optString("url", "");
        return u.endsWith(".mp4") ? u : null;
    }

    private JSONArray extractGallery(JSONObject post) {
        JSONArray out = new JSONArray();
        try {
            JSONObject gd = post.optJSONObject("gallery_data"), mm = post.optJSONObject("media_metadata");
            if (gd != null && mm != null) {
                JSONArray items = gd.optJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject meta = mm.optJSONObject(items.getJSONObject(i).optString("media_id", ""));
                    if (meta != null && meta.has("s")) {
                        String u = meta.getJSONObject("s").optString("u", meta.getJSONObject("s").optString("gif", ""));
                        if (!u.isEmpty()) out.put(u.replace("&amp;", "&"));
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
