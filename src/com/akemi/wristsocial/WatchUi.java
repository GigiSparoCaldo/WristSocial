package com.akemi.wristsocial;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

public class WatchUi {
    public final Activity act;
    public final ScrollView scroll;
    public final LinearLayout box;
    public final FrameLayout overlay;
    private VideoView videoView;
    private TextView volHud;
    private final Runnable hideVolTask = () -> { if (volHud != null) volHud.setVisibility(View.GONE); };
    public float fontScale;
    public final float density;
    public final int widthPx;

    public WatchUi(Activity act, float scale) {
        this.act = act;
        this.fontScale = scale;
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        this.widthPx = dm.widthPixels;
        this.density = dm.density;

        FrameLayout root = new FrameLayout(act);
        root.setBackgroundColor(Color.BLACK);

        scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);

        int side = Math.max(dp(18), Math.round(widthPx * 0.11f));
        int top = Math.max(dp(32), Math.round(dm.heightPixels * 0.14f));
        int bot = Math.max(dp(48), Math.round(dm.heightPixels * 0.22f));

        box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(side, top, side, bot);
        scroll.addView(box);

        overlay = new FrameLayout(act);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setVisibility(View.GONE);

        root.addView(scroll);
        root.addView(overlay);
        act.setContentView(root);
        scroll.requestFocus();
    }

    public int dp(int v) { return Math.round(v * density); }
    public float sp(float v) { return v * fontScale; }

    public void clear() {
        box.removeAllViews();
        scroll.scrollTo(0, 0);
        scroll.requestFocus();
    }

    public LinearLayout hBar(int topDp, int botDp) {
        HorizontalScrollView hsv = new HorizontalScrollView(act);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, dp(topDp), 0, dp(botDp));
        hsv.setLayoutParams(hlp);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(row);
        box.addView(hsv);
        return row;
    }

    public boolean onRotary(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            float axis = ev.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (overlay.getVisibility() == View.VISIBLE) {
                if (videoView != null && axis != 0f) {
                    AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                axis > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                        if (volHud != null) {
                            volHud.setText("🔊 " + Math.round((cur * 100f) / max) + "%");
                            volHud.setVisibility(View.VISIBLE);
                            volHud.removeCallbacks(hideVolTask);
                            volHud.postDelayed(hideVolTask, 1200);
                        }
                    }
                }
                return true;
            }
            float d = -axis * ViewConfiguration.get(act).getScaledVerticalScrollFactor();
            scroll.scrollBy(0, Math.round(d));
            return true;
        }
        return false;
    }

    public boolean isOverlayOpen() { return overlay.getVisibility() == View.VISIBLE; }

    public void closeOverlay() {
        try { if (videoView != null) { videoView.stopPlayback(); videoView = null; } } catch (Exception ignored) {}
        volHud = null;
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        scroll.requestFocus();
    }

    public void showImage(final Bitmap bmp) {
        overlay.removeAllViews();
        ZoomImageView ziv = new ZoomImageView(act, bmp, this::closeOverlay);
        overlay.addView(ziv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button close = chip("✕ Chiudi", "#CC0F172A", "#1E293B", "#475569", "#FFFFFF");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(20);
        close.setOnClickListener(v -> closeOverlay());
        overlay.addView(close, lp);
        overlay.setVisibility(View.VISIBLE);
    }

    public void playVideo(String url) {
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        final TextView bufTv = new TextView(act);
        bufTv.setText("⏳ Avvio streaming...");
        bufTv.setTextColor(Color.parseColor("#38BDF8"));
        bufTv.setTextSize(sp(12f));
        bufTv.setTypeface(null, Typeface.BOLD);
        bufTv.setGravity(Gravity.CENTER);

        videoView = new VideoView(act);
        videoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            bufTv.setVisibility(View.GONE);
            mp.setOnInfoListener((m, what, extra) -> {
                if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) bufTv.setVisibility(View.VISIBLE);
                else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) bufTv.setVisibility(View.GONE);
                return false;
            });
        });
        videoView.setOnErrorListener((mp, w, e) -> { closeOverlay(); return true; });
        videoView.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.pause();
            else videoView.start();
        });

        Button close = chip("✕ Chiudi", "#CC0F172A", "#1E293B", "#475569", "#FFFFFF");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(20);
        close.setOnClickListener(v -> closeOverlay());

        volHud = new TextView(act);
        volHud.setTextColor(Color.WHITE);
        volHud.setTextSize(sp(12f));
        volHud.setTypeface(null, Typeface.BOLD);
        volHud.setBackground(roundBox("#CC0F172A", "#38BDF8", 999));
        volHud.setPadding(dp(12), dp(5), dp(12), dp(5));
        volHud.setVisibility(View.GONE);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        vlp.bottomMargin = dp(28);

        overlay.addView(videoView);
        overlay.addView(bufTv);
        overlay.addView(close, lp);
        overlay.addView(volHud, vlp);
        videoView.setVideoURI(Uri.parse(url));
        videoView.start();
    }

    private static class ZoomImageView extends ImageView {
        private final Matrix matrix = new Matrix();
        private final Matrix baseMatrix = new Matrix();
        private final float[] mVals = new float[9];
        private float curScale = 1f, lastX = 0f, lastY = 0f;
        private boolean dragging = false;
        private final ScaleGestureDetector scaleDet;
        private final GestureDetector gestDet;

        ZoomImageView(Context ctx, Bitmap bmp, final Runnable onSingleTapExit) {
            super(ctx);
            setScaleType(ScaleType.MATRIX);
            setImageBitmap(bmp);
            scaleDet = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float f = d.getScaleFactor();
                    float next = Math.max(1f, Math.min(4.5f, curScale * f));
                    float realF = next / curScale;
                    curScale = next;
                    if (curScale <= 1.01f) { curScale = 1f; matrix.set(baseMatrix); }
                    else { matrix.postScale(realF, realF, d.getFocusX(), d.getFocusY()); clampPan(); }
                    setImageMatrix(matrix);
                    return true;
                }
            });
            gestDet = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (curScale > 1.2f) {
                        curScale = 1f;
                        matrix.set(baseMatrix);
                    } else {
                        float target = 2.5f;
                        matrix.postScale(target / curScale, target / curScale, e.getX(), e.getY());
                        curScale = target;
                        clampPan();
                    }
                    setImageMatrix(matrix);
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (curScale <= 1.05f) onSingleTapExit.run();
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            Drawable d = getDrawable();
            if (d == null || w == 0 || h == 0) return;
            float bw = d.getIntrinsicWidth(), bh = d.getIntrinsicHeight();
            float s = Math.min(w / bw, h / bh);
            baseMatrix.reset();
            baseMatrix.postScale(s, s);
            baseMatrix.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
            curScale = 1f;
            matrix.set(baseMatrix);
            setImageMatrix(matrix);
        }

        private void clampPan() {
            Drawable d = getDrawable();
            if (d == null) return;
            matrix.getValues(mVals);
            float scaleX = mVals[Matrix.MSCALE_X], scaleY = mVals[Matrix.MSCALE_Y];
            float imgW = d.getIntrinsicWidth() * scaleX, imgH = d.getIntrinsicHeight() * scaleY;
            float tx = mVals[Matrix.MTRANS_X], ty = mVals[Matrix.MTRANS_Y];
            float minX = Math.min(0, getWidth() - imgW), maxX = imgW < getWidth() ? (getWidth() - imgW) / 2f : 0;
            float minY = Math.min(0, getHeight() - imgH), maxY = imgH < getHeight() ? (getHeight() - imgH) / 2f : 0;
            float fixX = Math.max(minX, Math.min(maxX, tx)) - tx;
            float fixY = Math.max(minY, Math.min(maxY, ty)) - ty;
            matrix.postTranslate(fixX, fixY);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            scaleDet.onTouchEvent(ev);
            gestDet.onTouchEvent(ev);
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                lastX = ev.getX(); lastY = ev.getY(); dragging = true;
            } else if (act == MotionEvent.ACTION_MOVE && dragging && !scaleDet.isInProgress() && ev.getPointerCount() == 1 && curScale > 1.05f) {
                matrix.postTranslate(ev.getX() - lastX, ev.getY() - lastY);
                clampPan();
                setImageMatrix(matrix);
                lastX = ev.getX(); lastY = ev.getY();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                dragging = false;
            }
            return true;
        }
    }

    public Bitmap makeCircleAvatar(Bitmap src, int sizePx) {
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = sizePx / 2f;
        c.drawCircle(r, r, r - dp(2), p);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));
        ring.setColor(Color.parseColor("#EC4899"));
        c.drawCircle(r, r, r - dp(1), ring);
        return out;
    }

    public Drawable roundBox(String fill, String stroke, int radDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fill));
        gd.setCornerRadius(dp(radDp));
        if (stroke != null) gd.setStroke(dp(1), Color.parseColor(stroke));
        return gd;
    }

    public Drawable pillBg(String norm, String press, String stroke) {
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed}, roundBox(press, stroke, 999));
        st.addState(new int[]{}, roundBox(norm, stroke, 999));
        return st;
    }

    public LinearLayout card() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundBox("#12161F", "#232B3B", 18));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    public Button chip(String text, String norm, String press, String stroke, String fg) {
        Button b = new Button(act);
        b.setText(text); b.setSingleLine(true); b.setAllCaps(false);
        b.setTextSize(sp(11.5f)); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(pillBg(norm, press, stroke));
        b.setPadding(dp(11), dp(6), dp(11), dp(6));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
    }

    public Button pill(String text, String norm, String press, String stroke, String fg) {
        Button b = new Button(act);
        b.setText(text); b.setAllCaps(false);
        b.setTextSize(sp(12.5f)); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(pillBg(norm, press, stroke));
        b.setPadding(dp(14), dp(9), dp(14), dp(9));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    public TextView text(LinearLayout parent, String t, float baseSp, String hex, boolean bold) {
        TextView tv = new TextView(act);
        tv.setText(t); tv.setTextColor(Color.parseColor(hex)); tv.setTextSize(sp(baseSp));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setLineSpacing(dp(2), 1.0f);
        tv.setPadding(0, dp(3), 0, dp(5));
        parent.addView(tv);
        return tv;
    }
}

    public void showImage(final Bitmap bmp) {
        overlay.removeAllViews();
        ZoomImageView ziv = new ZoomImageView(act, bmp, this::closeOverlay);
        overlay.addView(ziv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button close = chip("✕ Chiudi", "#CC0F172A", "#1E293B", "#475569", "#FFFFFF");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(20);
        close.setOnClickListener(v -> closeOverlay());
        overlay.addView(close, lp);
        overlay.setVisibility(View.VISIBLE);
    }

    public void playVideo(String url) {
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        videoView = new VideoView(act);
        videoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        videoView.setOnPreparedListener(mp -> mp.setLooping(true));
        videoView.setOnErrorListener((mp, w, e) -> { closeOverlay(); return true; });
        videoView.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.pause();
            else videoView.start();
        });

        Button close = chip("✕ Chiudi", "#CC0F172A", "#1E293B", "#475569", "#FFFFFF");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(20);
        close.setOnClickListener(v -> closeOverlay());

        volHud = new TextView(act);
        volHud.setTextColor(Color.WHITE);
        volHud.setTextSize(sp(12f));
        volHud.setTypeface(null, Typeface.BOLD);
        volHud.setBackground(roundBox("#CC0F172A", "#38BDF8", 999));
        volHud.setPadding(dp(12), dp(5), dp(12), dp(5));
        volHud.setVisibility(View.GONE);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        vlp.bottomMargin = dp(28);

        overlay.addView(videoView);
        overlay.addView(bufTv);
        overlay.addView(close, lp);
        overlay.addView(volHud, vlp);
        videoView.setVideoURI(Uri.parse(url));
        videoView.start();
    }

    private static class ZoomImageView extends ImageView {
        private final Matrix matrix = new Matrix();
        private final Matrix baseMatrix = new Matrix();
        private final float[] mVals = new float[9];
        private float curScale = 1f, lastX = 0f, lastY = 0f;
        private boolean dragging = false;
        private final ScaleGestureDetector scaleDet;
        private final GestureDetector gestDet;

        ZoomImageView(Context ctx, Bitmap bmp, final Runnable onSingleTapExit) {
            super(ctx);
            setScaleType(ScaleType.MATRIX);
            setImageBitmap(bmp);
            scaleDet = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float f = d.getScaleFactor();
                    float next = Math.max(1f, Math.min(4.5f, curScale * f));
                    float realF = next / curScale;
                    curScale = next;
                    if (curScale <= 1.01f) { curScale = 1f; matrix.set(baseMatrix); }
                    else { matrix.postScale(realF, realF, d.getFocusX(), d.getFocusY()); clampPan(); }
                    setImageMatrix(matrix);
                    return true;
                }
            });
            gestDet = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (curScale > 1.2f) {
                        curScale = 1f;
                        matrix.set(baseMatrix);
                    } else {
                        float target = 2.5f;
                        matrix.postScale(target / curScale, target / curScale, e.getX(), e.getY());
                        curScale = target;
                        clampPan();
                    }
                    setImageMatrix(matrix);
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (curScale <= 1.05f) onSingleTapExit.run();
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            Drawable d = getDrawable();
            if (d == null || w == 0 || h == 0) return;
            float bw = d.getIntrinsicWidth(), bh = d.getIntrinsicHeight();
            float s = Math.min(w / bw, h / bh);
            baseMatrix.reset();
            baseMatrix.postScale(s, s);
            baseMatrix.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
            curScale = 1f;
            matrix.set(baseMatrix);
            setImageMatrix(matrix);
        }

        private void clampPan() {
            Drawable d = getDrawable();
            if (d == null) return;
            matrix.getValues(mVals);
            float scaleX = mVals[Matrix.MSCALE_X], scaleY = mVals[Matrix.MSCALE_Y];
            float imgW = d.getIntrinsicWidth() * scaleX, imgH = d.getIntrinsicHeight() * scaleY;
            float tx = mVals[Matrix.MTRANS_X], ty = mVals[Matrix.MTRANS_Y];
            float minX = Math.min(0, getWidth() - imgW), maxX = imgW < getWidth() ? (getWidth() - imgW) / 2f : 0;
            float minY = Math.min(0, getHeight() - imgH), maxY = imgH < getHeight() ? (getHeight() - imgH) / 2f : 0;
            float fixX = Math.max(minX, Math.min(maxX, tx)) - tx;
            float fixY = Math.max(minY, Math.min(maxY, ty)) - ty;
            matrix.postTranslate(fixX, fixY);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            scaleDet.onTouchEvent(ev);
            gestDet.onTouchEvent(ev);
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                lastX = ev.getX(); lastY = ev.getY(); dragging = true;
            } else if (act == MotionEvent.ACTION_MOVE && dragging && !scaleDet.isInProgress() && ev.getPointerCount() == 1 && curScale > 1.05f) {
                matrix.postTranslate(ev.getX() - lastX, ev.getY() - lastY);
                clampPan();
                setImageMatrix(matrix);
                lastX = ev.getX(); lastY = ev.getY();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                dragging = false;
            }
            return true;
        }
    }

    public Bitmap makeCircleAvatar(Bitmap src, int sizePx) {
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = sizePx / 2f;
        c.drawCircle(r, r, r - dp(2), p);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));
        ring.setColor(Color.parseColor("#EC4899"));
        c.drawCircle(r, r, r - dp(1), ring);
        return out;
    }

    public Drawable roundBox(String fill, String stroke, int radDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fill));
        gd.setCornerRadius(dp(radDp));
        if (stroke != null) gd.setStroke(dp(1), Color.parseColor(stroke));
        return gd;
    }

    public Drawable pillBg(String norm, String press, String stroke) {
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed}, roundBox(press, stroke, 999));
        st.addState(new int[]{}, roundBox(norm, stroke, 999));
        return st;
    }

    public LinearLayout card() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundBox("#12161F", "#232B3B", 18));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    public Button chip(String text, String norm, String press, String stroke, String fg) {
        Button b = new Button(act);
        b.setText(text); b.setSingleLine(true); b.setAllCaps(false);
        b.setTextSize(sp(11.5f)); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(pillBg(norm, press, stroke));
        b.setPadding(dp(11), dp(6), dp(11), dp(6));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
    }

    public Button pill(String text, String norm, String press, String stroke, String fg) {
        Button b = new Button(act);
        b.setText(text); b.setAllCaps(false);
        b.setTextSize(sp(12.5f)); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(pillBg(norm, press, stroke));
        b.setPadding(dp(14), dp(9), dp(14), dp(9));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    public TextView text(LinearLayout parent, String t, float baseSp, String hex, boolean bold) {
        TextView tv = new TextView(act);
        tv.setText(t); tv.setTextColor(Color.parseColor(hex)); tv.setTextSize(sp(baseSp));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setLineSpacing(dp(2), 1.0f);
        tv.setPadding(0, dp(3), 0, dp(5));
        parent.addView(tv);
        return tv;
    }
}
