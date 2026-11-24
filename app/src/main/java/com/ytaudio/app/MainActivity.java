package com.ytaudio.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;

public class MainActivity extends Activity {

    // آدرس پایه https: یوتیوب بدون آن خطای ۱۵۳ می‌دهد
    private static final String BASE_URL = "https://ytaudio.app/";

    /**
     * این کد پیش از هر اسکریپت دیگری، در صفحه اصلی و داخل قاب یوتیوب اجرا می‌شود
     * و به سایت می‌گوید صفحه همیشه دیده می‌شود و در فوکوس است.
     */
    private static final String KEEP_VISIBLE_JS =
            "(function(){try{" +
            "var D=Document.prototype,def=function(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v},configurable:true})}catch(e){}};" +
            "def(D,'hidden',false);def(D,'webkitHidden',false);" +
            "def(D,'visibilityState','visible');def(D,'webkitVisibilityState','visible');" +
            "D.hasFocus=function(){return true};" +
            "var stop=function(e){e.stopImmediatePropagation()};" +
            "['visibilitychange','webkitvisibilitychange','pagehide','freeze','blur'].forEach(function(t){" +
            "window.addEventListener(t,stop,true);document.addEventListener(t,stop,true)});" +
            "}catch(e){}})();";

    private static WeakReference<MainActivity> current = new WeakReference<>(null);

    private WebView web;
    private boolean pageReady = false;
    private String pendingShare = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);

        web = new KeepAliveWebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, KEEP_VISIBLE_JS, Collections.singleton("*"));
        }

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingShare != null) {
                    sendToPage(pendingShare);
                    pendingShare = null;
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith(BASE_URL)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                } catch (Exception ignored) { }
                return true;
            }
        });

        web.loadDataWithBaseURL(BASE_URL, readAsset("index.html"), "text/html", "utf-8", null);

        askPermissionsOnce();
        handleIntent(getIntent());
    }

    /** اجازه اعلان و حذف از «بهینه‌سازی باتری» تا اندروید پخش را در قفل صفحه قطع نکند. */
    @SuppressLint("BatteryLife")
    private void askPermissionsOnce() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        SharedPreferences p = getSharedPreferences("app", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 23 && !p.getBoolean("askedBattery", false)) {
            p.edit().putBoolean("askedBattery", true).apply();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String text = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            text = intent.getData().toString();
        }
        if (text == null || text.trim().isEmpty()) return;
        if (pageReady) sendToPage(text); else pendingShare = text;
    }

    private void sendToPage(String text) {
        web.evaluateJavascript("window.playShared && window.playShared(" + JSONObject.quote(text) + ")", null);
    }

    private void runJs(final String js) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    static void toggleFromNotification() {
        MainActivity a = current.get();
        if (a != null) a.runJs("document.getElementById('pp').click()");
    }

    static void pauseFromNotification() {
        MainActivity a = current.get();
        if (a != null) a.runJs("window.pauseAudio && window.pauseAudio()");
    }

    // عمداً web.onPause() و pauseTimers() صدا زده نمی‌شوند تا پخش ادامه پیدا کند
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // دکمه برگشت، برنامه را به پس‌زمینه می‌برد تا پخش ادامه پیدا کند
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        sleepHandler.removeCallbacks(sleepTask);
        stopService(new Intent(this, PlaybackService.class));
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<p>هەڵە لە بارکردنی پەڕەکە</p>";
        }
    }

    // کاتژمێری خەو: لە بەشی ئەندرۆید هەژمار دەکرێت (سێرڤیسەکە بە wake lock CPU بەئاگا دەهێڵێتەوە)
    private final android.os.Handler sleepHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleepTask = () -> {
        if (web != null) web.evaluateJavascript("window.sleepFired && window.sleepFired()", null);
    };

    private class Bridge {
        @JavascriptInterface
        public void sleep(long ms) {
            sleepHandler.removeCallbacks(sleepTask);
            if (ms > 0) sleepHandler.postDelayed(sleepTask, ms);
        }

        @JavascriptInterface
        public void state(boolean playing, String title) {
            // پیش از اولین پخش سرویس را راه نینداز؛ بعد از آن فقط وضعیتش را به‌روز کن
            if (!playing && !PlaybackService.running) return;
            Intent i = new Intent(MainActivity.this, PlaybackService.class)
                    .setAction(PlaybackService.ACTION_STATE)
                    .putExtra("playing", playing)
                    .putExtra("title", title == null ? "" : title);
            try {
                if (PlaybackService.running) startService(i);
                else if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
            } catch (Exception ignored) { }
        }
    }
}
