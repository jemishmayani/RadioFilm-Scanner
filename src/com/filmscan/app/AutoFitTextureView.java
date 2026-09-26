package com.filmscan.app;

import android.content.Context;
import android.view.TextureView;

/** TextureView that keeps the camera's aspect ratio (fits inside its parent, never crops). */
public final class AutoFitTextureView extends TextureView {
    private int ratioW, ratioH;

    public AutoFitTextureView(Context c) { super(c); }

    public void setAspectRatio(int w, int h) {
        if (w <= 0 || h <= 0) return;
        ratioW = w; ratioH = h;
        requestLayout();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        super.onMeasure(ws, hs);
        int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
        if (ratioW == 0 || ratioH == 0) { setMeasuredDimension(w, h); return; }
        if ((long) w * ratioH <= (long) h * ratioW) setMeasuredDimension(w, (int) ((long) w * ratioH / ratioW));
        else setMeasuredDimension((int) ((long) h * ratioW / ratioH), h);
    }
}
