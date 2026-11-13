package com.akemi.wristsocial;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;

public class BridgeClient {
    public String host = "http://127.0.0.1:8765";
    public String pin = "1234";

    private final LruCache<String, String> cache = new LruCache<>(25);
    private final LruCache<String, String> trCache = new LruCache<>(200);

    public String get(String pathAndQuery, boolean force) throws Exception {
        String key = host + pathAndQuery;
        if (!force && cache.get(key) != null) return cache.get(key);
        HttpURLConnection c = (HttpURLConnection) new URL(key).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(10000);
        c.setRequestProperty("X-Pin", pin);
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        c.disconnect();
        String res = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (code == 401) throw new Exception("PIN Bridge errato (tocca ⚙)");
        if (code >= 400) throw new Exception("Errore Bridge HTTP " + code);
        cache.put(key, res);
        return res;
    }

    public Bitmap getProxiedImage(String rawUrl) throws Exception {
        String req = host + "/img?url=" + URLEncoder.encode(rawUrl, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(req).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(10000);
        c.setRequestProperty("X-Pin", pin);
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            byte[] b = out.toByteArray();
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } finally { c.disconnect(); }
    }

    public String translateIt(String txt) {
        if (txt == null || txt.trim().isEmpty()) return txt;
        if (trCache.get(txt) != null) return trCache.get(txt);
        try {
            String cut = txt.length() > 1200 ? txt.substring(0, 1200) : txt;
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=it&dt=t").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            try (OutputStream os = c.getOutputStream()) {
                os.write(("q=" + URLEncoder.encode(cut, "UTF-8")).getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = c.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            }
            JSONArray root = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if ("it".equalsIgnoreCase(root.optString(2, ""))) { trCache.put(txt, txt); return txt; }
            JSONArray segs = root.getJSONArray(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segs.length(); i++) {
                JSONArray s = segs.optJSONArray(i);
                if (s != null && !s.isNull(0)) sb.append(s.getString(0));
            }
            String tr = sb.toString().trim();
            if (!tr.isEmpty()) { trCache.put(txt, tr); return tr; }
        } catch (Exception ignored) {}
        return txt;
    }
}
