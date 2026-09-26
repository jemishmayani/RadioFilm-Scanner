package com.filmscan.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays chips out left to right, wrapping onto new lines. */
public final class ChipFlow extends ViewGroup {
    private final int hGap, vGap;

    public ChipFlow(Context c) {
        super(c);
        hGap = Ui.dp(c, 8);
        vGap = Ui.dp(c, 8);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + v.getMeasuredWidth() > max) { x = 0; y += lineH + vGap; lineH = 0; }
            x += v.getMeasuredWidth() + hGap;
            lineH = Math.max(lineH, v.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(ws), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > max) { x = 0; y += lineH + vGap; lineH = 0; }
            v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
    }
}
