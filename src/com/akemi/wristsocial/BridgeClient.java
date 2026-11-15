package com.akemi.wristsocial;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;

public class BridgeClient {
    public String host = "http://192.168.1.12:8765";
    public String pin = "7827";

    private final LruCache<String, String> cache = new LruCache<>(40);
    private final LruCache<String, String> trCache = new LruCache<>(250);
    private final LruCache<String, Bitmap> imgCache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bmp) { return bmp.getByteCount(); }
    };
    private static final Pattern IT_WORDS = Pattern.compile(
            "\\b(che|non|per|con|sono|della|delle|degli|questo|questa|anche|come|più|perché|cosa|fare|quando|sempre|tutto|molto|essere|stato|anni|oggi|solo|così|ciao|grazie)\\b",
            Pattern.CASE_INSENSITIVE);

    public static class DownloadedFile {
        public String name;
        public byte[] data;
    }

    public String getProxiedVideoUrl(String rawUrl) {
        try {
            return host + "/vid?pin=" + URLEncoder.encode(pin, "UTF-8") + "&url=" + URLEncoder.encode(rawUrl, "UTF-8");
        } catch (Exception e) { return rawUrl; }
    }

    public String discoverHost() {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("192.168.43.1");
        candidates.add("172.20.10.1");
        for (int i = 1; i <= 254; i++) candidates.add("192.168.1." + i);
        for (int i = 1; i <= 254; i++) candidates.add("192.168.0." + i);
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.contains(".") && !ip.startsWith("10.0.0.")) {
                            String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
                            for (int i = 1; i <= 254; i++) candidates.add(prefix + i);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        final AtomicReference<String> found = new AtomicReference<>(null);
        ExecutorService scanPool = Executors.newFixedThreadPool(32);
        for (final String ip : candidates) {
            scanPool.execute(() -> {
                if (found.get() != null) return;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + ":8765/ping").openConnection();
                    c.setConnectTimeout(300); c.setReadTimeout(400);
                    c.setRequestProperty("X-Pin", pin);
                    int code = c.getResponseCode(); c.disconnect();
                    if (code == 200 || code == 401) found.compareAndSet(null, "http://" + ip + ":8765");
                } catch (Exception ignored) {}
            });
        }
        scanPool.shutdown();
        try { scanPool.awaitTermination(4, TimeUnit.SECONDS); } catch (Exception ignored) {}
        scanPool.shutdownNow();
        return found.get();
    }

    public String get(String pathAndQuery, boolean force) throws Exception {
        String key = host + pathAndQuery;
        if (!force && cache.get(key) != null) return cache.get(key);
        HttpURLConnection c = (HttpURLConnection) new URL(key).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(26000);
        c.setRequestProperty("X-Pin", pin);
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        c.disconnect();
        String res = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (code == 401) throw new Exception("PIN errato (tocca ⚙)");
        if (code >= 400) throw new Exception("Errore HTTP " + code);
        cache.put(key, res);
        return res;
    }

    public String postData(String path, byte[] payload, String filename) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(host + path).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setConnectTimeout(5000); c.setReadTimeout(12000);
        c.setRequestProperty("X-Pin", pin);
        if (filename != null) c.setRequestProperty("X-Filename", URLEncoder.encode(filename, "UTF-8"));
        try (OutputStream os = c.getOutputStream()) { os.write(payload); }
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        c.disconnect();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public DownloadedFile downloadFile() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(host + "/file").openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(12000);
        c.setRequestProperty("X-Pin", pin);
        if (c.getResponseCode() >= 400) { c.disconnect(); throw new Exception("Nessun file pronto sullo Z Flip"); }
        String hdrName = c.getHeaderField("X-Filename");
        DownloadedFile df = new DownloadedFile();
        df.name = (hdrName != null && !hdrName.isEmpty()) ? URLDecoder.decode(hdrName, "UTF-8") : "file_zflip.bin";
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            df.data = out.toByteArray();
        } finally { c.disconnect(); }
        return df;
    }

    public Bitmap getProxiedImage(String rawUrl) throws Exception {
        Bitmap cached = imgCache.get(rawUrl);
        if (cached != null && !cached.isRecycled()) return cached;
        String req = host + "/img?url=" + URLEncoder.encode(rawUrl, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(req).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(9000);
        c.setRequestProperty("X-Pin", pin);
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            byte[] b = out.toByteArray();
            Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
            if (bmp != null) imgCache.put(rawUrl, bmp);
            return bmp;
        } finally { c.disconnect(); }
    }

    public String translateIt(String txt) {
        if (txt == null || txt.trim().isEmpty()) return txt;
        if (trCache.get(txt) != null) return trCache.get(txt);
        Matcher m = IT_WORDS.matcher(txt.toLowerCase(Locale.ROOT));
        int hits = 0;
        while (m.find() && hits < 2) hits++;
        if (hits >= 2) { trCache.put(txt, txt); return txt; }
        try {
            String cut = txt.length() > 1000 ? txt.substring(0, 1000) : txt;
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=it&dt=t").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setConnectTimeout(4500); c.setReadTimeout(6500);
            try (OutputStream os = c.getOutputStream()) {
                os.write(("q=" + URLEncoder.encode(cut, "UTF-8")).getBytes(StandardCharsets.UTF_8));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = c.getInputStream()) {
                byte[] buf = new byte[4096]; int n;
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
