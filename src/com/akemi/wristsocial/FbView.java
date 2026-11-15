package com.akemi.wristsocial;

import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

public class FbView {
    private final MainActivity act;
    private final WatchUi ui;
    private final BridgeClient api;
    private final ExecutorService pool;

    public FbView(MainActivity act, WatchUi ui, BridgeClient api, ExecutorService pool) {
        this.act = act; this.ui = ui; this.api = api; this.pool = pool;
    }

    public void show(final boolean force) {
        ui.clear();
        act.topBar("fb", () -> show(false), () -> show(true));

        LinearLayout bar = ui.hBar(2, 4);
        Button bFeed = ui.chip("Home", "#1D4ED8", "#1E40AF", null, "#FFFFFF");
        bFeed.setOnClickListener(v -> show(true));
        bar.addView(bFeed);

        Button bMsg = ui.chip("💬 Messaggi", "#181E29", "#263042", "#2D3748", "#60A5FA");
        bMsg.setOnClickListener(v -> act.nav(() -> show(false), () -> openFbPage("Messaggi Facebook", "https://m.facebook.com/messages/", false)));
        act.addGapChip(bar, bMsg, 5);

        Button bSrc = ui.chip("🔍 Cerca", "#181E29", "#263042", "#2D3748", "#38BDF8");
        bSrc.setOnClickListener(v -> act.nav(() -> show(false), this::showSearch));
        act.addGapChip(bar, bSrc, 5);

        final TextView ld = ui.text(ui.box, "Caricamento Facebook...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONArray items = new JSONObject(api.get("/fb/feed", force)).optJSONArray("items");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (items == null || items.length() == 0) {
                        ui.text(ui.box, "Nessun post. Apri la scheda FB in SocialBridge sullo Z Flip e accedi.", 12f, "#E2E8F0", false);
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it != null) renderCard(it, () -> show(false));
                    }
                    addLoadMoreBtn();
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore FB: " + e.getMessage())); }
        });
    }

    private void addLoadMoreBtn() {
        final Button bMore = ui.pill("⬇ Carica altri post", "#181E29", "#263042", "#2563EB", "#60A5FA");
        bMore.setOnClickListener(v -> {
            bMore.setText("Caricamento..."); bMore.setEnabled(false);
            pool.execute(() -> {
                try {
                    final JSONArray items = new JSONObject(api.get("/fb/more", true)).optJSONArray("items");
                    act.runOnUiThread(() -> {
                        ui.box.removeView(bMore);
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject it = items.optJSONObject(i);
                                if (it != null) renderCard(it, () -> show(false));
                            }
                        }
                        addLoadMoreBtn();
                    });
                } catch (Exception e) { act.runOnUiThread(() -> { bMore.setText("Riprova"); bMore.setEnabled(true); }); }
            });
        });
        ui.box.addView(bMore);
    }

    private void renderCard(JSONObject it, final Runnable back) {
        LinearLayout card = ui.card();
        final int idx = it.optInt("idx", 0);
        final String author = it.optString("author", "Facebook"), text = it.optString("text", "");
        final String purl = it.optString("purl", ""), curl = it.optString("curl", ""), vid = it.optString("vid", "");
        String img = it.optString("img", "");

        Button bAuth = ui.chip("👤 " + author, "#141D33", "#1E2C4D", "#2563EB", "#93C5FD");
        bAuth.setOnClickListener(v -> {
            if (!purl.isEmpty()) act.nav(back, () -> openFbPage(author, purl, false));
        });
        card.addView(bAuth);

        if (!text.isEmpty()) {
            final TextView tv = ui.text(card, text, 13f, "#F8FAFC", false);
            if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(text); if (!tr.equals(text)) act.runOnUiThread(() -> tv.setText(tr)); });
        }
        if (!img.isEmpty()) act.attachImage(card, img, act.imagesOnTap);
        if (!vid.isEmpty()) {
            Button pv = ui.pill("▶ Guarda Video Facebook", "#1D4ED8", "#1E40AF", null, "#FFFFFF");
            pv.setOnClickListener(v -> ui.playVideo(api.getProxiedVideoUrl(vid)));
            card.addView(pv);
        }

        final Button bLike = ui.pill("👍 Mi piace", "#1B2332", "#263145", "#334155", "#60A5FA");
        bLike.setOnClickListener(v -> {
            bLike.setText("👍 Piaciuto");
            pool.execute(() -> { try { api.get("/fb/like?id=" + idx, true); } catch (Exception ignored) {} });
        });
        card.addView(bLike);

        if (!curl.isEmpty()) {
            Button bComm = ui.pill("💬 Apri Post / Commenta", "#141923", "#222B3A", "#334155", "#38BDF8");
            bComm.setOnClickListener(v -> act.nav(back, () -> openFbPage("Post di " + author, curl, true)));
            card.addView(bComm);
        }
        ui.box.addView(card);
    }

    public void showSearch() {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);

        final EditText et = new EditText(act);
        et.setHint("Cerca su Facebook...");
        et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
        et.setBackground(ui.roundBox("#141923", "#2563EB", 18));
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        ui.box.addView(et);

        Button go = ui.pill("🔍 Cerca su Facebook", "#1D4ED8", "#1E40AF", null, "#FFFFFF");
        go.setOnClickListener(v -> {
            String q = et.getText().toString().trim();
            if (q.isEmpty()) return;
            try {
                String url = "https://m.facebook.com/search/top/?q=" + URLEncoder.encode(q, "UTF-8");
                act.nav(this::showSearch, () -> openFbPage("Ricerca: " + q, url, false));
            } catch (Exception ignored) {}
        });
        ui.box.addView(go);
    }

    public void openFbPage(final String label, final String url, final boolean allowReply) {
        ui.clear();
        Button back = ui.chip("← Indietro", "#181E29", "#263042", "#2D3748", "#FFFFFF");
        back.setOnClickListener(v -> act.onBackPressed());
        ui.box.addView(back);
        ui.text(ui.box, label, 14f, "#60A5FA", true);

        if (allowReply) {
            final EditText et = new EditText(act);
            et.setHint("Scrivi un commento...");
            et.setHintTextColor(Color.parseColor("#64748B")); et.setTextColor(Color.WHITE);
            et.setBackground(ui.roundBox("#141923", "#2A3447", 18));
            et.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
            ui.box.addView(et);

            final Button send = ui.pill("💬 Pubblica Commento", "#1D4ED8", "#1E40AF", null, "#FFFFFF");
            send.setOnClickListener(v -> {
                final String msg = et.getText().toString().trim();
                if (msg.isEmpty()) return;
                send.setText("Invio..."); send.setEnabled(false);
                pool.execute(() -> {
                    try {
                        api.get("/fb/comment_add?text=" + URLEncoder.encode(msg, "UTF-8"), true);
                        act.runOnUiThread(() -> { Toast.makeText(act, "Inviato!", Toast.LENGTH_SHORT).show(); openFbPage(label, url, true); });
                    } catch (Exception e) { act.runOnUiThread(() -> { send.setText("Errore"); send.setEnabled(true); }); }
                });
            });
            ui.box.addView(send);
        }

        final TextView ld = ui.text(ui.box, "Caricamento pagina...", 12.5f, "#94A3B8", false);
        pool.execute(() -> {
            try {
                JSONObject j = new JSONObject(api.get("/fb/open?url=" + URLEncoder.encode(url, "UTF-8"), true));
                final JSONArray items = j.optJSONArray("items");
                act.runOnUiThread(() -> {
                    ui.box.removeView(ld);
                    if (items == null || items.length() == 0) { ui.text(ui.box, "Nessun contenuto disponibile.", 12f, "#E2E8F0", false); return; }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        LinearLayout c = ui.card();
                        final String txt = it.optString("text", "");
                        if (!txt.isEmpty()) {
                            final TextView tv = ui.text(c, txt, 12.5f, "#F8FAFC", false);
                            if (act.autoIt) pool.execute(() -> { final String tr = api.translateIt(txt); if (!tr.equals(txt)) act.runOnUiThread(() -> tv.setText(tr)); });
                        }
                        String im = it.optString("img", "");
                        if (!im.isEmpty()) act.attachImage(c, im, act.imagesOnTap);
                        ui.box.addView(c);
                    }
                });
            } catch (Exception e) { act.runOnUiThread(() -> ld.setText("Errore: " + e.getMessage())); }
        });
    }
}
