package com.ytaudio.app;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;

/**
 * WebView که همیشه به موتور کروم می‌گوید «پنجره دیده می‌شود».
 * با قفل شدن صفحه یا رفتن به برنامه دیگر، صفحه وب خودش را «پنهان» حساب نمی‌کند
 * و پخش متوقف نمی‌شود.
 */
public class KeepAliveWebView extends WebView {

    public KeepAliveWebView(Context context) {
        super(context);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, View.VISIBLE);
    }
}
