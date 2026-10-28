package com.alabtal.portaltest;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.provider.MediaStore;
import android.os.Environment;
import android.net.Uri;
import android.content.Intent;
import android.content.IntentSender;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(7, 8, 10);
    private static final int PANEL = Color.rgb(17, 19, 23);
    private static final int ORANGE = Color.rgb(255, 121, 0);
    private static final int ORANGE_DEEP = Color.rgb(190, 75, 0);
    private static final int WHITE = Color.rgb(245, 246, 248);
    private static final int MUTED = Color.rgb(160, 166, 176);
    private static final int GREEN = Color.rgb(45, 210, 125);
    private static final int RED = Color.rgb(255, 82, 82);

    private static final String PORTAL_URL =
        "https://eg.me.logisticsbackoffice.com/dashboard/rooster/live-3pl";

    private static final String FORM_URL =
        "https://docs.google.com/forms/d/e/1FAIpQLSekxaUQPgiWq-Y_IPfsTx7wTANA324a2JklFJZ_Gxg9CGPaKA/viewform";

    private static final String SHEET_URL =
        "https://docs.google.com/spreadsheets/d/1gM2Mm0fUwJrPz3KZciXoNt9xatNB2j96ecsMPcog-uc/edit";

    private static final String RIDERS_URL =
        "/api/rider-live-operations/v1/external/city/214/riders?page=0&size=50";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private WebView formWebView;
    private LinearLayout formRoot;
    private TextView formTitle;
    private TextView status;
    private TextView banner;
    private TextView summary;
    private LinearLayout dashboardStats;
    private LinearLayout cards;
    private ScrollView riderCardsScroll;
    private WebView riderMapView;
    private WebView sheetView;
    private boolean mapTabActive = false;
    private boolean sheetTabActive = false;
    private boolean bookingMode = false;
    private Button inspectButton;
    private EditText riderSearch;
    private JSONArray currentRiders = new JSONArray();
    private String riderFilter = "الكل";
    private int riderLimit = 10;
    private boolean portalVisible = false;
    private boolean portalPageVisible = false;
    private LinearLayout portalRoot;
    private LinearLayout dashboardRoot;
    private boolean detailVisible = false;
    private SharedPreferences authPrefs;
    private SharedPreferences rosterCachePrefs;

    private boolean inspectionRunning = false;
    private boolean pageLoaded = false;
    private boolean destroyed = false;
    private int consecutiveSyncFailures = 0;
    private long nextSyncDelayMs = 30000L;
    private static final long SYNC_INTERVAL_MS = 10000L;
    private static final long SYNC_RETRY_MIN_MS = 5000L;
    private static final long SYNC_RETRY_MAX_MS = 60000L;

    private int bannerPosition = 0;
    private final String bannerMessage = "AL ABTAL  •  TALABAT  •  طنطا";

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            if (pageLoaded && !inspectionRunning) {
                inspectRoster();
            } else if (!inspectionRunning && pageLoaded) {
                scheduleNextSync();
            }
        }
    };

    private final Runnable resumeRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !pageLoaded || detailVisible) return;

            if (inspectionRunning) {
                handler.postDelayed(this, 500);
                return;
            }

            handler.removeCallbacks(refreshRunnable);
            inspectRoster();
        }
    };

    private void scheduleNextSync() {
        handler.removeCallbacks(refreshRunnable);
        if (!destroyed && pageLoaded) {
            handler.postDelayed(refreshRunnable, nextSyncDelayMs);
        }
    }

    private void recordSyncFailure() {
        consecutiveSyncFailures++;

        // Recover a stale portal connection automatically.
        if (consecutiveSyncFailures >= 3
                && webView != null && pageLoaded && !destroyed) {
            pageLoaded = false;
            handler.removeCallbacks(refreshRunnable);
            handler.removeCallbacks(resumeRefreshRunnable);
            status.setText("جاري إعادة الاتصال بالبوابة تلقائيًا...");
            handler.postDelayed(() -> {
                if (destroyed || webView == null) return;
                webView.loadUrl(PORTAL_URL);
            }, 1000L);
            return;
        }

        long delay = SYNC_RETRY_MIN_MS;
        for (int i = 1; i < consecutiveSyncFailures
                && delay < SYNC_RETRY_MAX_MS; i++) {
            delay = Math.min(delay * 2, SYNC_RETRY_MAX_MS);
        }
        nextSyncDelayMs = delay;
        scheduleNextSync();
    }

    private final Runnable bannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || banner == null) return;

            if (bannerPosition >= bannerMessage.length()) {
                banner.setText(bannerMessage);
                handler.postDelayed(() -> {
                    if (destroyed || banner == null) return;
                    bannerPosition = 0;
                    banner.setText("");
                    handler.postDelayed(this, 250);
                }, 1800);
                return;
            }

            bannerPosition++;
            banner.setText(bannerMessage.substring(0, bannerPosition));
            handler.postDelayed(this, 115);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authPrefs = getSharedPreferences("alabtal_local_auth", MODE_PRIVATE);
        rosterCachePrefs = getSharedPreferences(
                "alabtal_roster_cache", MODE_PRIVATE);
        String savedUser = authPrefs.getString("username", null);
        boolean loggedIn = authPrefs.getBoolean("logged_in", false);
        if (savedUser == null) {
            showLoginScreen(true);
        } else if (loggedIn) {
            buildDashboard();
        } else {
            showLoginScreen(false);
        }
    }

    private void buildDashboard() {
        detailVisible = false;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        dashboardRoot = new LinearLayout(this);
        LinearLayout root = dashboardRoot;
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable dashboardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(19, 23, 30), Color.rgb(5, 8, 13),
                          Color.rgb(18, 13, 10)}
        );
        root.setBackground(dashboardBg);
        root.setPadding(dp(6), dp(2), dp(6), 0);

        // Unified 3D operations header
        LinearLayout portalRow = new LinearLayout(this);
        portalRow.setOrientation(LinearLayout.HORIZONTAL);
        portalRow.setGravity(Gravity.CENTER_VERTICAL);
        portalRow.setPadding(dp(2), dp(3), dp(2), dp(4));

        Button portalButton = makeButton("🌐");
        portalButton.setTextSize(18);
        portalButton.setPadding(0, 0, 0, 0);
        portalButton.setContentDescription("فتح بوابة الشركة");
        portalButton.setBackground(round(Color.rgb(10, 23, 34), ORANGE, 2));
        portalButton.setElevation(dp(5));
        LinearLayout.LayoutParams portalParams =
                new LinearLayout.LayoutParams(dp(36), dp(36));
        portalParams.setMargins(0, 0, dp(7), 0);
        portalRow.addView(portalButton, portalParams);
        portalButton.setOnClickListener(v -> showPortalPage());

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(dp(4), dp(3), dp(4), dp(3));

        GradientDrawable brandBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(74, 38, 13), Color.rgb(15, 19, 26)}
        );
        brandBg.setCornerRadius(dp(14));
        brandBg.setStroke(dp(2), ORANGE);
        brand.setBackground(brandBg);
        brand.setElevation(dp(7));

        TextView title = makeText("AL ABTAL", 18, ORANGE, true);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.09f);
        title.setShadowLayer(dp(7), 0, 0, ORANGE);
        brand.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView subtitle = makeText("LIVE OPERATIONS", 9, WHITE, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.18f);
        brand.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(11)));

        LinearLayout.LayoutParams brandParams =
                new LinearLayout.LayoutParams(0, dp(39), 1f);
        brandParams.setMargins(0, 0, dp(6), 0);
        portalRow.addView(brand, brandParams);

        TextView liveBadge = makeText("● LIVE", 9, GREEN, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(5), dp(5), dp(5), dp(5));
        liveBadge.setBackground(round(Color.rgb(8, 30, 25), GREEN, 1));
        liveBadge.setElevation(dp(3));
        portalRow.addView(liveBadge,
                new LinearLayout.LayoutParams(-2, dp(34)));

        Button logoutButton = makeButton("⇥");
        logoutButton.setTextSize(17);
        logoutButton.setContentDescription("تسجيل الخروج من البوابة");
        logoutButton.setBackground(round(
                Color.rgb(35, 13, 13), Color.rgb(230, 75, 65), 1));
        logoutButton.setElevation(dp(4));
        LinearLayout.LayoutParams logoutParams =
                new LinearLayout.LayoutParams(dp(34), dp(34));
        logoutParams.setMargins(dp(5), 0, 0, 0);
        portalRow.addView(logoutButton, logoutParams);
        logoutButton.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("تسجيل الخروج")
                        .setMessage("سيتم تسجيل الخروج من بوابة الشركة. ستظل إحصائيات اليوم محفوظة.")
                        .setNegativeButton("إلغاء",
                                (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("تسجيل الخروج",
                                (dialog, which) -> logoutPortal())
                        .show());

        LinearLayout.LayoutParams portalRowParams =
                new LinearLayout.LayoutParams(-1, -2);
        portalRowParams.setMargins(dp(1), dp(2), dp(1), dp(3));
        root.addView(portalRow, portalRowParams);

        banner = makeText("", 14, ORANGE, true);
        banner.setGravity(Gravity.CENTER);
        banner.setSingleLine(true);
        banner.setLetterSpacing(0.025f);
        banner.setShadowLayer(dp(4), 0, 0, ORANGE);
        banner.setPadding(dp(4), dp(3), dp(4), dp(3));

        GradientDrawable ledBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(48, 25, 10), Color.rgb(15, 18, 24),
                          Color.rgb(48, 25, 10)}
        );
        ledBg.setCornerRadius(dp(8));
        ledBg.setStroke(dp(1), ORANGE);
        banner.setBackground(ledBg);

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(23));
        bp.setMargins(0, 0, 0, dp(1));
        root.addView(banner, bp);
        handler.post(bannerRunnable);


        summary = makeText(
            "في انتظار تحميل بيانات الروستر...",
            12, MUTED, false
        );
        summary.setTextSize(10);
        summary.setMaxLines(1);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setPadding(0, 0, 0, 0);
        root.addView(summary);

        TextView statsTitle = makeText(
                "◈  لوحة مؤشرات التشغيل", 11, ORANGE, true);
        statsTitle.setPadding(dp(3), dp(1), dp(3), dp(1));

        dashboardStats = new LinearLayout(this);
        dashboardStats.setOrientation(LinearLayout.VERTICAL);
        dashboardStats.setPadding(0, 0, 0, dp(1));
        dashboardStats.setVisibility(View.VISIBLE);
        statsTitle.setText("ملخص التشغيل المباشر");
        statsTitle.setClickable(false);

        // Compact refresh control beside the status line
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        status = makeText(
            "في انتظار بيانات الروستر...",
            10, ORANGE, false
        );
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(7), dp(4), dp(5), dp(4));
        status.setBackground(round(Color.rgb(14, 20, 28),
                Color.rgb(95, 55, 24), 1));
        controls.addView(
            status, new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button reportButton = makeButton("⇩ تقرير");
        reportButton.setTextSize(9);
        reportButton.setTextColor(ORANGE);
        reportButton.setPadding(dp(5), 0, dp(5), 0);
        reportButton.setBackground(round(Color.rgb(20, 17, 15), ORANGE, 1));
        reportButton.setElevation(dp(3));
        reportButton.setContentDescription("تصدير تقرير المشرف");
        LinearLayout.LayoutParams reportParams =
                new LinearLayout.LayoutParams(-2, dp(30));
        reportParams.setMargins(dp(3), 0, 0, 0);
        controls.addView(reportButton, reportParams);
        reportButton.setOnClickListener(v -> exportSupervisorReport());

        inspectButton = makeButton("↻");
        inspectButton.setTextSize(22);
        inspectButton.setTextColor(ORANGE);
        inspectButton.setPadding(0, 0, 0, 0);
        inspectButton.setBackground(round(Color.rgb(9, 28, 24), GREEN, 1));
        inspectButton.setElevation(dp(4));
        inspectButton.setContentDescription("تحديث البيانات الآن");
        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(dp(38), dp(30));
        refreshParams.setMargins(dp(3), 0, 0, 0);
        controls.addView(inspectButton, refreshParams);

        root.addView(controls);

        // Embedded portal: login and authenticated session
        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        webView.setVisibility(View.GONE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportMultipleWindows(false);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String finishedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);

                if (finishedUrl.contains("cloudflareaccess.com")
                        || finishedUrl.contains("/cdn-cgi/access/login")) {
                    pageLoaded = false;
                    inspectionRunning = false;
                    if (inspectButton != null) inspectButton.setEnabled(true);
                    status.setText("سجّل الدخول في Cloudflare للوصول إلى الروستر.");
                    return;
                }

                if (!finishedUrl.startsWith("https://eg.me.logisticsbackoffice.com/")) {
                    pageLoaded = false;
                    status.setText("في انتظار صفحة بوابة الشركة الموثّقة...");
                    return;
                }

                pageLoaded = true;
                status.setText("تم الاتصال بالبوابة؛ جاري تحديث بيانات المندوبين...");
                inspectRoster();
            }
        });

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onReport(String json) {
                runOnUiThread(() -> handleReport(json));
            }

            @JavascriptInterface
            public void onError(String message) {
                runOnUiThread(() -> {
                    inspectionRunning = false;
                    inspectButton.setEnabled(true);
                    status.setText("تعذر جلب البيانات: " + message);
                    recordSyncFailure();
                });
            }
        }, "AlabtalBridge");

        // Keep the portal accessible while leaving most space for rider cards.
        webView.setVisibility(View.GONE);
        root.addView(
            webView,
            new LinearLayout.LayoutParams(-1, dp(155))
        );

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, 0, 0, 0);

        String[] filters = {"الكل", "شغال", "بريك", "معاه أوردر", "فاضي"};
        for (String filter : filters) {
            Button chip = makeButton(filter);
            chip.setTextSize(9);
            chip.setTextColor(ORANGE);
            chip.setPadding(dp(7), 0, dp(7), 0);
            chip.setMinWidth(0);
            GradientDrawable chipBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(39, 27, 18), Color.rgb(10, 14, 21)}
            );
            chipBg.setCornerRadius(dp(18));
            chipBg.setStroke(dp(1), ORANGE);
            chip.setBackground(chipBg);
            chip.setElevation(dp(4));
            chip.setTranslationZ(dp(1));
            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-2, dp(28));
            cp.setMargins(0, 0, dp(3), 0);
            filterRow.addView(chip, cp);
            chip.setOnClickListener(v -> {
                riderFilter = filter;
                renderRiderCards();
            });
        }
        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        // تبويبات مضغوطة: المناديب والخريطة
        LinearLayout mapTabs = new LinearLayout(this);
        mapTabs.setOrientation(LinearLayout.HORIZONTAL);
        mapTabs.setPadding(0, dp(3), 0, dp(4));

        Button ridersTab = makeButton("☷ المناديب");
        Button mapTab = makeButton("⌖ الخريطة");
        Button sheetTab = makeButton("▤ ردود الشيت");
        Button bookingsTab = makeButton("📦 الحجوزات");
        ridersTab.setTextSize(10);
        mapTab.setTextSize(10);
        sheetTab.setTextSize(9);
        bookingsTab.setTextSize(9);
        ridersTab.setBackground(round(Color.rgb(30, 22, 16), ORANGE, 2));
        mapTab.setBackground(round(Color.rgb(15, 22, 31), ORANGE, 2));
        sheetTab.setBackground(round(Color.rgb(15, 22, 31), ORANGE, 2));
        bookingsTab.setBackground(round(Color.rgb(30, 22, 16), ORANGE, 2));
        ridersTab.setElevation(dp(7));
        mapTab.setElevation(dp(7));
        sheetTab.setElevation(dp(7));
        bookingsTab.setElevation(dp(7));
        ridersTab.setTranslationZ(dp(1));
        mapTab.setTranslationZ(dp(1));
        sheetTab.setTranslationZ(dp(1));
        bookingsTab.setTranslationZ(dp(1));

        LinearLayout.LayoutParams tabParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        tabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(ridersTab, tabParams);
        LinearLayout.LayoutParams mapTabParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        mapTabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(mapTab, mapTabParams);
        LinearLayout.LayoutParams sheetTabParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        sheetTabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(sheetTab, sheetTabParams);
        LinearLayout.LayoutParams bookingsParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        mapTabs.addView(bookingsTab, bookingsParams);
        root.addView(mapTabs);

        FrameLayout contentFrame = new FrameLayout(this);

        riderCardsScroll = new ScrollView(this);
        riderCardsScroll.setFillViewport(false);

        // الهيدر والكورة الأرضية والبانر ثابتون أعلى الشاشة.
        // الإحصائيات وكروت المندوبين داخل نفس منطقة التمرير.
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(statsTitle,
                new LinearLayout.LayoutParams(-1, -2));
        scrollContent.addView(dashboardStats,
                new LinearLayout.LayoutParams(-1, -2));
        scrollContent.addView(cards,
                new LinearLayout.LayoutParams(-1, -2));
        riderCardsScroll.addView(scrollContent);

        riderMapView = new WebView(this);
        riderMapView.setBackgroundColor(BG);
        riderMapView.getSettings().setJavaScriptEnabled(true);
        riderMapView.getSettings().setDomStorageEnabled(true);
        riderMapView.setVisibility(View.GONE);

        // شاشة مستقلة للشيت حتى لا تتأثر جلسة بوابة الشركة.
        sheetView = new WebView(this);
        sheetView.setBackgroundColor(BG);
        sheetView.getSettings().setJavaScriptEnabled(true);
        sheetView.getSettings().setDomStorageEnabled(true);
        sheetView.getSettings().setSupportMultipleWindows(false);
        sheetView.setWebChromeClient(new WebChromeClient());
        sheetView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (view != sheetView || url == null ||
                        !url.startsWith("https://docs.google.com/")) {
                    return;
                }

                String probe =
                    "(function() {" +
                    " var login = location.href.indexOf('accounts.google.com') >= 0" +
                    "   || !!document.querySelector('input[type=password]');" +
                    " var cells = document.querySelectorAll('[role=gridcell]').length;" +
                    " var headers = document.querySelectorAll('table th').length;" +
                    " if (login) return 'LOGIN_REQUIRED';" +
                    " if (cells > 0) return 'GRID_CELLS=' + cells;" +
                    " return 'NO_GRID;TABLE_HEADERS=' + headers;" +
                    "})()";

                view.evaluateJavascript(probe, result -> {
                    if (isFinishing() || isDestroyed()) return;

                    String outcome = result == null ? "" : result;
                    outcome = outcome.replace("\\\"", "")
                                     .replace("\"", "");

                    String message;
                    if (outcome.contains("LOGIN_REQUIRED")) {
                        message = "Google يطلب تسجيل الدخول.";
                    } else if (outcome.startsWith("GRID_CELLS=")) {
                        message = "ظهرت خلايا الشيت داخل الصفحة. " + outcome;
                    } else {
                        message = "الصفحة فتحت، لكن لم يظهر جدول قابل للفحص. "
                                + outcome;
                    }

                    android.widget.Toast.makeText(
                        MainActivity.this,
                        message,
                        android.widget.Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
        sheetView.setVisibility(View.GONE);

        contentFrame.addView(riderCardsScroll,
                new FrameLayout.LayoutParams(-1, -1));
        contentFrame.addView(riderMapView,
                new FrameLayout.LayoutParams(-1, -1));
        contentFrame.addView(sheetView,
                new FrameLayout.LayoutParams(-1, -1));

        root.addView(contentFrame,
                new LinearLayout.LayoutParams(-1, 0, 1));

        ridersTab.setOnClickListener(v -> {
            mapTabActive = false;
            sheetTabActive = false;
            riderCardsScroll.setVisibility(View.VISIBLE);
            riderMapView.setVisibility(View.GONE);
            sheetView.setVisibility(View.GONE);
            ridersTab.setTextColor(ORANGE);
            mapTab.setTextColor(WHITE);
            sheetTab.setTextColor(WHITE);
            renderRiderCards();
        });

        mapTab.setOnClickListener(v -> {
            mapTabActive = true;
            sheetTabActive = false;
            riderCardsScroll.setVisibility(View.GONE);
            riderMapView.setVisibility(View.VISIBLE);
            sheetView.setVisibility(View.GONE);
            mapTab.setTextColor(ORANGE);
            ridersTab.setTextColor(WHITE);
            sheetTab.setTextColor(WHITE);
            renderRiderMap();
        });

        bookingsTab.setOnClickListener(v -> {
            bookingMode = true;
            mapTabActive = false;
            sheetTabActive = false;
            riderCardsScroll.setVisibility(View.GONE);
            riderMapView.setVisibility(View.GONE);
            sheetView.setVisibility(View.VISIBLE);
            bookingsTab.setTextColor(ORANGE);
            ridersTab.setTextColor(WHITE);
            mapTab.setTextColor(WHITE);
            sheetTab.setTextColor(WHITE);
            requestSheetAccess();
        });

        sheetTab.setOnClickListener(v -> {
            bookingMode = false;
            mapTabActive = false;
            sheetTabActive = true;
            riderCardsScroll.setVisibility(View.GONE);
            riderMapView.setVisibility(View.GONE);
            sheetView.setVisibility(View.VISIBLE);
            sheetTab.setTextColor(ORANGE);
            ridersTab.setTextColor(WHITE);
            mapTab.setTextColor(WHITE);

            requestSheetAccess();
        });

        setContentView(root);

        inspectButton.setText("↻");
        inspectButton.setTextSize(22);
        inspectButton.setOnClickListener(v -> requestRosterRefresh());

        addEmptyCard(
            "بانتظار البيانات",
            "جاري استعادة آخر بيانات محفوظة..."
        );

        restoreCachedReport();

        // لا تفتح البوابة تلقائيًا؛ تُفتح فقط بطلب المستخدم.
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(
        String value, float size, int color, boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(
            Typeface.DEFAULT,
            bold ? Typeface.BOLD : Typeface.NORMAL
        );
        return t;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setBackground(round(ORANGE, ORANGE, 1));
        return b;
    }

    private GradientDrawable round(int color, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(13));
        d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private TextView tile(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackground(round(Color.rgb(25, 27, 32), Color.rgb(55, 58, 65), 1));

        TextView l = makeText(label, 9, MUTED, false);
        TextView v = makeText(value, 13, valueColor, true);

        box.addView(l);
        box.addView(v);

        return wrapView(box);
    }

    private TextView wrapView(View view) {
        TextView unused = new TextView(this);
        unused.setTag(view);
        return unused;
    }

    private void addTileRow(
        LinearLayout target,
        String label1, String value1,
        String label2, String value2
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(
            createTile(label1, value1),
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        LinearLayout.LayoutParams second =
            new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(4), 0, 0, 0);

        row.addView(createTile(label2, value2), second);

        LinearLayout.LayoutParams rp =
            new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dp(3), 0, 0);
        target.addView(row, rp);
    }


    private LinearLayout createTile(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setMinimumHeight(dp(42));
        box.setPadding(dp(6), dp(4), dp(6), dp(4));

        GradientDrawable tileBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(35, 25, 19), Color.rgb(12, 13, 17)});
        tileBg.setCornerRadius(dp(13));
        tileBg.setStroke(dp(1), Color.rgb(165, 78, 30));
        box.setBackground(tileBg);
        box.setElevation(dp(3));

        TextView l = makeText(label, 10, MUTED, false);
        l.setMaxLines(2);
        l.setGravity(Gravity.START);
        box.addView(l);

        TextView v = makeText(
                value == null || value.trim().isEmpty()
                        ? "—" : value,
                12, WHITE, true);
        v.setMaxLines(3);
        v.setGravity(Gravity.START);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(v);

        return box;
    }

    private void showFormPage(String targetTitle) {
        if (dashboardRoot == null) return;

        if (formRoot == null) {
            formRoot = new LinearLayout(this);
            formRoot.setOrientation(LinearLayout.VERTICAL);
            formRoot.setPadding(dp(8), dp(8), dp(8), dp(8));
            formRoot.setBackgroundColor(BG);

            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);

            Button backButton = makeButton("← رجوع");
            toolbar.addView(backButton,
                    new LinearLayout.LayoutParams(0, dp(44), 1));
            backButton.setOnClickListener(v -> {
                if (formWebView != null && formWebView.canGoBack()) {
                    formWebView.goBack();
                } else {
                    returnToDashboard();
                }
            });

            Button exitButton = makeButton("✕ خروج");
            LinearLayout.LayoutParams exitParams =
                    new LinearLayout.LayoutParams(0, dp(44), 1);
            exitParams.setMargins(dp(6), 0, 0, 0);
            toolbar.addView(exitButton, exitParams);
            exitButton.setOnClickListener(v -> returnToDashboard());
            formRoot.addView(toolbar);

            formTitle = makeText(
                    "نموذج التزويد والبريك", 15, ORANGE, true);
            formTitle.setPadding(dp(4), dp(8), dp(4), dp(8));
            formRoot.addView(formTitle);

            formWebView = new WebView(this);
            formWebView.setBackgroundColor(Color.WHITE);
            formWebView.getSettings().setJavaScriptEnabled(true);
            formWebView.getSettings().setDomStorageEnabled(true);
            formWebView.getSettings().setSupportMultipleWindows(false);
            formWebView.setWebChromeClient(new WebChromeClient());
            formWebView.setWebViewClient(new WebViewClient());

            formRoot.addView(
                    formWebView, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        formTitle.setText(targetTitle);
        setContentView(formRoot);
        formWebView.onResume();
        formWebView.loadUrl(FORM_URL);
    }

    private void showPortalPage() {
        if (webView == null || dashboardRoot == null) return;

        if (portalRoot == null) {
            portalRoot = new LinearLayout(this);
            portalRoot.setOrientation(LinearLayout.VERTICAL);
            portalRoot.setPadding(dp(10), dp(8), dp(10), dp(8));
            portalRoot.setBackgroundColor(BG);

            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);

            Button backButton = makeButton("← رجوع");
            toolbar.addView(backButton,
                    new LinearLayout.LayoutParams(0, dp(46), 1));
            backButton.setOnClickListener(v -> returnToDashboard());

            Button syncButton = makeButton("↻ تحديث");
            LinearLayout.LayoutParams syncParams =
                    new LinearLayout.LayoutParams(0, dp(46), 1);
            syncParams.setMargins(dp(6), 0, 0, 0);
            toolbar.addView(syncButton, syncParams);
            syncButton.setOnClickListener(v -> requestRosterRefresh());

            portalRoot.addView(toolbar);
            TextView portalTitle =
                    makeText("بوابة الروستر الحي", 17, ORANGE, true);
            portalTitle.setPadding(dp(3), dp(8), dp(3), dp(8));
            portalRoot.addView(portalTitle);
        }

        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }

        portalRoot.addView(
                webView, new LinearLayout.LayoutParams(-1, 0, 1));
        webView.setVisibility(View.VISIBLE);

        portalPageVisible = true;
        portalVisible = true;
        setContentView(portalRoot);
        webView.onResume();

        if (!pageLoaded) {
            openPortal();
        } else {
            inspectRoster();
        }
    }

    private void openPortal() {
        if (webView != null) {
            portalVisible = true;
            webView.setVisibility(View.VISIBLE);
            status.setText("جاري فتح بوابة الروستر...");
            webView.loadUrl(PORTAL_URL);
        }
    }

    private void logoutPortal() {
        if (webView == null) return;
        pageLoaded = false;
        inspectionRunning = false;
        inspectButton.setEnabled(true);
        webView.stopLoading();
        webView.clearHistory();
        webView.clearCache(true);
        webView.evaluateJavascript(
            "try{localStorage.clear();sessionStorage.clear();}catch(e){}", result -> {
                WebStorage.getInstance().deleteAllData();
                CookieManager cm = CookieManager.getInstance();
                cm.removeAllCookies(removed -> runOnUiThread(() -> {
                    if (destroyed || webView == null) return;
                    cm.flush();
                    portalVisible = true;
                    webView.setVisibility(View.VISIBLE);
                    webView.loadUrl(PORTAL_URL);
                    status.setText("تم مسح جلسة WebView المحلية؛ سجّل الدخول مجددًا.");
                }));
            }
        );
    }

    private void requestRosterRefresh() {
        if (inspectButton != null) {
            inspectButton.setTextColor(ORANGE);
        }

        if (destroyed || webView == null) return;

        if (inspectionRunning) {
            status.setText("المزامنة جارية بالفعل...");
            return;
        }

        if (!pageLoaded) {
            status.setText("جاري الاتصال بالبوابة لتحديث البيانات...");
            webView.loadUrl(PORTAL_URL);
            return;
        }

        inspectRoster();
    }

    private void inspectRoster() {
        if (inspectionRunning || destroyed) return;

        if (webView == null || !pageLoaded) {
            status.setText("اضغط تحديث للاتصال بالبوابة.");
            return;
        }

        inspectionRunning = true;
        inspectButton.setEnabled(false);
        status.setText("جاري جلب المندوبين والأوردرات من المصدر الحي...");

        String script =
            "(async function() {" +
            "try {" +
            "const base = location.origin;" +

            "async function getJson(url) {" +
            " const controller = new AbortController();" +
            " const timer = setTimeout(() => controller.abort(), 15000);" +
            " try {" +
            "  const response = await fetch(url, {" +
            "   credentials:'include'," +
            "   headers:{'Accept':'application/json'}," +
            "   signal:controller.signal" +
            "  });" +
            "  if (!response.ok) throw new Error('HTTP '+response.status+' - '+url);" +
            "  return await response.json();" +
            " } catch(e) {" +
            "  if (e && e.name === 'AbortError') throw new Error('انتهت مهلة الاتصال بعد 15 ثانية');" +
            "  throw e;" +
            " } finally {" +
            "  clearTimeout(timer);" +
            " }" +
            "}" +

            "const data = await getJson(base + " +
                JSONObject.quote(RIDERS_URL) + ");" +
            "const riders = data.content || data.riders || [];" +

            "function getShiftFromTable(r) {" +
            " const id = String(r.employee_id || '').trim();" +
            " const name = String(r.name || '').replace(/\\s+/g,' ').trim().toLowerCase();" +
            " const norm = s => String(s || '').replace(/\\s+/g,' ').trim().toLowerCase();" +
            " const timePattern = /(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})/;" +
            " const tables = Array.from(document.querySelectorAll('table.cape-table, table'));" +
            " for (const table of tables) {" +
            "  let headers = Array.from(table.querySelectorAll('thead th, thead td'));" +
            "  if (!headers.length) headers = Array.from(table.querySelectorAll('tr:first-child th, tr:first-child td'));" +
            "  const labels = headers.map(c => norm(c.innerText || c.textContent));" +
            "  let index = labels.findIndex(h => /current\\s*session|session|shift|الشيفت|الشفت|الوردية|مواعيد الشيفت/.test(h));" +
            "  if (index < 0) continue;" +
            "  const rows = Array.from(table.querySelectorAll('tbody tr')).filter(row => row.querySelectorAll('td').length);" +
            "  for (const row of rows) {" +
            "   const cells = Array.from(row.querySelectorAll('td'));" +
            "   const text = norm(row.innerText || row.textContent);" +
            "   const hasId = id && new RegExp('(?:^|\\\\D)' + id + '(?:\\\\D|$)').test(text);" +
            "   const hasName = name && text.includes(name);" +
            "   if (!hasId && !hasName) continue;" +
            "   if (cells[index]) {" +
            "    const match = String(cells[index].innerText || cells[index].textContent || '').match(timePattern);" +
            "    if (match) return {start:match[1],end:match[2]};" +
            "   }" +
            "   const match = text.match(timePattern);" +
            "   if (match) return {start:match[1],end:match[2]};" +
            "  }" +
            " }" +
            " return {start:'',end:''};" +
            "}" +

            "function getRosterRowFromTable(r) {" +
            " const id = String(r.employee_id || '').trim();" +
            " const name = String(r.name || '').replace(/\\s+/g,' ').trim().toLowerCase();" +
            " const norm = s => String(s || '').replace(/\\s+/g,' ').trim();" +
            " const tables = Array.from(document.querySelectorAll('table.cape-table, table'));" +
            " for (const table of tables) {" +
            "  let headers = Array.from(table.querySelectorAll('thead th,thead td')).map(x => norm(x.innerText || x.textContent).toLowerCase());" +
            "  if (!headers.length) {" +
            "   const first = table.querySelector('tr');" +
            "   if (first) headers = Array.from(first.querySelectorAll('th,td')).map(x => norm(x.innerText || x.textContent).toLowerCase());" +
            "  }" +
            "  if (!headers.length) continue;" +
            "  const idx = re => headers.findIndex(h => re.test(h));" +
            "  const sessionIdx = idx(/current\\s*session|session|shift|الشيفت|الشفت|الوردية/);" +
            "  const perfIdx = idx(/performance|\\butr\\b/);" +
            "  const workedIdx = idx(/time\\s*worked|worked\\s*time|وقت\\s*العمل/);" +
            "  const acceptanceIdx = idx(/acceptance\\s*rate|acceptance|نسبة\\s*القبول/);" +
            "  const walletIdx = idx(/wallet\\s*balance|wallet|المحفظة/);" +
            "  const ordersIdx = idx(/deliveries|orders|عدد\\s*الأوردرات|الأوردرات/);" +
            "  const deliveriesInPerf = perfText => { const m = String(perfText || '').match(/Deliveries\\s*:\\s*(\\d+)/i); return m ? Number(m[1]) : -1; };" +
            "  const rows = Array.from(table.querySelectorAll('tbody tr')).filter(tr => tr.querySelectorAll('td').length);" +
            "  for (const tr of rows) {" +
            "   const cells = Array.from(tr.querySelectorAll('td')).map(td => norm(td.innerText || td.textContent));" +
            "   const text = cells.join(' ').toLowerCase();" +
            "   const hasId = id && new RegExp('(?:^|\\\\D)' + id + '(?:\\\\D|$)').test(cells.join(' '));" +
            "   const hasName = name && text.includes(name);" +
            "   if (!hasId && !hasName) continue;" +
            "   const cell = i => i >= 0 && i < cells.length ? cells[i] : '';" +
            "   const session = cell(sessionIdx).match(/(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})/);" +
            "   const perfText = cell(perfIdx);" +
            "   const utr = perfText.match(/(?:UTR\\s*:\\s*)?([0-9]+(?:\\.[0-9]+)?)/i);" +
            "   const workedText = cell(workedIdx);" +
            "   const orderText = cell(ordersIdx);" +
            "   const orderMatch = orderText.match(/\\d+/);" +
            "   const deliveriesCount = deliveriesInPerf(perfText);" +
            "   return {" +
            "    start:session ? session[1] : '', end:session ? session[2] : ''," +
            "    performance:utr ? utr[1] : '', worked_time:workedText," +
            "    worked_seconds:(() => { const m = workedText.match(/^(\\d{1,3}):(\\d{2}):(\\d{2})$/); return m ? (+m[1]*3600 + +m[2]*60 + +m[3]) : -1; })()," +
            "    acceptance_rate:cell(acceptanceIdx), wallet_balance:cell(walletIdx)," +
            "    total_orders:deliveriesCount >= 0 ? deliveriesCount : (orderMatch ? Number(orderMatch[0]) : -1)" +
            "   };" +
            "  }" +
            " }" +
            " return {start:'',end:'',performance:'',worked_time:'',worked_seconds:-1,acceptance_rate:'',wallet_balance:'',total_orders:-1};" +
            "}" +

            "const output = await Promise.all(riders.map(async r => {" +
            " const info = r.deliveries_info || {};" +
            " const activeIds = (info.active_delivery_ids || []).map(x => {" +
            "  if (x && typeof x === 'object') {" +
            "   return String(x.delivery_id != null ? x.delivery_id :" +
            "    (x.order_id != null ? x.order_id : (x.id != null ? x.id : ''))).trim();" +
            "  }" +
            "  return String(x == null ? '' : x).trim();" +
            " }).filter(Boolean);" +
            " let detail = null;" +
            " let detailError = '';" +

            " {" +
            "  try {" +
            "   detail = await getJson(base +" +
            "    '/api/rider-live-operations/v2/external/rider/' +" +
            "    encodeURIComponent(String(r.employee_id)));" +
            "  } catch(e) { detailError = String(e.message || e); }" +
            " }" +

            " const deliveryInfo = (detail && detail.deliveries_info) || {};" +
            " const latest = deliveryInfo.latest_deliveries || [];" +
            " const tableShift = getShiftFromTable(r);" +
            " const tableData = getRosterRowFromTable(r);" +
            " const perf = (detail && detail.performance && typeof detail.performance === 'object') ? detail.performance : ((r.performance_info && typeof r.performance_info === 'object') ? r.performance_info : ((r.performance && typeof r.performance === 'object') ? r.performance : {}));" +
            " const timeSpent = (detail && detail.time_spent && typeof detail.time_spent === 'object') ? detail.time_spent : ((r.time_spent && typeof r.time_spent === 'object') ? r.time_spent : {});" +
            " const gpsPair = (() => {" +
            "  const seen = new Set();" +
            "  const find = (obj, depth) => {" +
            "   if (!obj || typeof obj !== 'object' || depth > 8 || seen.has(obj)) return null;" +
            "   seen.add(obj);" +
            "   const keys = Object.keys(obj);" +
            "   const latKey = keys.find(k => /^(lat|latitude|current_latitude|currentLatitude)$/i.test(k));" +
            "   const lonKey = keys.find(k => /^(lon|lng|longitude|current_longitude|currentLongitude)$/i.test(k));" +
            "   if (latKey && lonKey) {" +
            "    const lat = Number(obj[latKey]), lon = Number(obj[lonKey]);" +
            "    if (Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 && !(lat === 0 && lon === 0)) return {lat:lat, lon:lon};" +
            "   }" +
            "   for (const k of keys) { const found = find(obj[k], depth + 1); if (found) return found; }" +
            "   return null;" +
            "  };" +
            "  return find({rider:r, detail:detail}, 0);" +
            " })();" +
            " const perfRaw = (typeof r.performance === 'number' || typeof r.performance === 'string') ? r.performance : (perf.utilization_rate ?? perf.score ?? perf.value ?? r.performance_score ?? r.performanceScore ?? '');" +
            " const acceptanceRaw = r.acceptance_rate ?? r.acceptance_percentage ?? r.acceptance ?? r.acceptanceRate ?? perf.acceptance_rate ?? (r.acceptance_info || {}).rate ?? (r.acceptance_info || {}).percentage ?? (info || {}).acceptance_rate ?? '';" +
            " const perfValue = perfRaw == null ? '' : String(perfRaw);" +
            " const acceptanceValue = acceptanceRaw == null ? '' : String(acceptanceRaw);" +


            " const activeOrders = latest.filter(o => {" +
            "  const rawId = o.delivery_id != null ? o.delivery_id :" +
            "   (o.order_id != null ? o.order_id :" +
            "   (o.id != null ? o.id :" +
            "   (o.deliveryId != null ? o.deliveryId : o.orderId)));" +
            "  const id = String(rawId == null ? '' : rawId).trim();" +
            "  return id !== '' && activeIds.includes(id);" +
            " }).map(o => ({" +
            "  delivery_id:String(o.delivery_id != null ? o.delivery_id :" +
            "   (o.order_id != null ? o.order_id :" +
            "   (o.id != null ? o.id :" +
            "   (o.deliveryId != null ? o.deliveryId : o.orderId))))," +
            "  order_code:o.order_code || ''," +
            "  status:o.status || ''," +
            "  vendor_name:o.vendor_name || ''," +
            "  pickup_address:o.pickup_address || ''," +
            "  dropoff_address:o.dropoff_address || ''," +
            "  timeline:o.timeline || []," +
            "  created_at:o.created_at || o.createdAt || o.order_created_at || o.orderCreatedAt || o.created_at_utc || ''," +
            "  accepted_at:o.accepted_at || o.acceptedAt || o.accepted_time || o.acceptedTime || ''," +
            "  picked_up_at:o.picked_up_at || o.pickedUpAt || o.pickup_time || o.pickupTime || ''," +
            " }));" +

            " return {" +
            "  employee_id:String(r.employee_id || '')," +
            "  name:r.name || ''," +
            "  rider_status:r.status || ''," +
            "  has_active_deliveries:activeIds.length > 0," +
            "  active_delivery_ids:activeIds," +
            "  active_count:activeIds.length," +
            "  wallet_balance:((r.wallet_info || {}).balance != null ? (r.wallet_info || {}).balance : (tableData.wallet_balance || ''))," +
            "  zone:(r.zone || {}).name || ''," +
            "  starting_point:(r.starting_point || {}).name || ''," +
            "  shift_start:(r.shift_start || r.shiftStart || r.planned_shift_start || r.session_start || (r.shift_info || {}).start_time || (r.shift_info || {}).start || (r.shift || {}).start_time || (r.shift || {}).start || (r.session || {}).start_time || (r.session || {}).start || tableShift.start || '')," +
            "  shift_end:(r.shift_end || r.shiftEnd || r.planned_shift_end || r.session_end || (r.shift_info || {}).end_time || (r.shift_info || {}).end || (r.shift || {}).end_time || (r.shift || {}).end || (r.session || {}).end_time || (r.session || {}).end || tableShift.end || '')," +
            "  break_started_at:r.break_started_at || r.breakStartedAt || r.break_start_time || r.breakStartTime || r.on_break_since || r.onBreakSince || r.status_changed_at || r.statusChangedAt || ''," +
            "  performance:perfValue || tableData.performance || ''," +
            "  acceptance_rate:acceptanceValue || tableData.acceptance_rate || ''," +
            "  worked_time:r.worked_time || r.worked_hours || r.worked || tableData.worked_time || ''," +
            "  vehicle:(typeof r.vehicle === 'string' ? r.vehicle : ((r.vehicle || {}).name || (r.vehicle || {}).type || (r.vehicle || {}).vehicle_type || r.vehicle_type || r.vehicleType || (r.vehicle_info || {}).name || (r.vehicle_info || {}).type || ''))," +
            "  total_orders:(r.total_orders != null ? r.total_orders : (r.total_deliveries_count != null ? r.total_deliveries_count : (info.total_orders != null ? info.total_orders : (info.total_deliveries_count != null ? info.total_deliveries_count : (info.completed_deliveries_count != null ? info.completed_deliveries_count : (tableData.total_orders >= 0 ? tableData.total_orders : latest.length))))))," +
            "  total_orders_source:(r.total_orders != null || r.total_deliveries_count != null || info.total_orders != null || info.total_deliveries_count != null || info.completed_deliveries_count != null) ? 'counter' : (tableData.total_orders >= 0 ? 'roster_table' : 'latest_deliveries')," +
            "  worked_seconds:(timeSpent.worked_seconds != null && Number.isFinite(Number(timeSpent.worked_seconds))) ? Number(timeSpent.worked_seconds) : ((detail && detail.worked_seconds != null && Number.isFinite(Number(detail.worked_seconds))) ? Number(detail.worked_seconds) : tableData.worked_seconds)," +
            "  latitude:gpsPair ? gpsPair.lat : null," +
            "  longitude:gpsPair ? gpsPair.lon : null," +
            "  current_location:(r && r.current_location && typeof r.current_location === 'object') ? {" +
            "   latitude:typeof r.current_location.latitude === 'number' ? r.current_location.latitude : null," +
            "   longitude:typeof r.current_location.longitude === 'number' ? r.current_location.longitude : null," +
            "   accuracy:typeof r.current_location.accuracy === 'number' ? r.current_location.accuracy : null," +
            "   location_updated_at:r.current_location.location_updated_at || null" +
            "  } : null," +
            "  active_orders:activeOrders," +
            "  detail_error:detailError" +
            " };" +
            "}));" +

            "const report = {" +
            " url:location.href," +
            " pageLength:(document.body.innerText || '').length," +
            " cardCount:output.length," +
            " total_elements:data.total_elements || output.length," +
            " checked_at:new Date().toISOString()," +
            " riders:output" +
            "};" +

            "window.AlabtalBridge.onReport(JSON.stringify(report));" +
            "} catch(e) {" +
            "window.AlabtalBridge.onError(String(e.message || e));" +
            "}" +
            "})();";

        webView.evaluateJavascript(script, null);
    }

    private void restoreCachedReport() {
        if (rosterCachePrefs == null) return;

        String saved = rosterCachePrefs.getString(
                "last_successful_report", null);

        if (saved == null || saved.trim().isEmpty()) return;

        try {
            JSONObject cached = new JSONObject(saved);
            cached.put("_from_cache", true);
            handleReport(cached.toString());
        } catch (Exception e) {
            status.setText("تعذر استعادة البيانات المحفوظة؛ جاري محاولة التحديث.");
        }
    }

    private void handleReport(String json) {
        inspectionRunning = false;
        inspectButton.setEnabled(true);

        try {
            JSONObject report = new JSONObject(json);
            boolean fromCache = report.optBoolean("_from_cache", false);
            report.remove("_from_cache");

            JSONArray riders = report.optJSONArray("riders");

            if (riders == null) {
                throw new Exception("بيانات المندوبين غير موجودة");
            }

            currentRiders = riders;

            int ridersWithOrders = 0;
            int totalOrders = 0;

            for (int i = 0; i < riders.length(); i++) {
                JSONObject rider = riders.getJSONObject(i);
                JSONArray orders = rider.optJSONArray("active_orders");

                if (orders == null) orders = new JSONArray();

                boolean hasOrders = orders.length() > 0 || rider.optInt("active_count") > 0;
                if (hasOrders) ridersWithOrders++;
                totalOrders += rider.optInt("total_orders", orders.length());

            }

            renderRiderCards();
            if (mapTabActive) renderRiderMap();

            summary.setText("المندوبون: " + riders.length());

            renderDashboardStats(riders, ridersWithOrders, totalOrders);

            if (!fromCache) notifySmartAlerts(riders);

            if (fromCache) {
                status.setText(
                    "آخر بيانات محفوظة: " +
                    formatTime(report.optString("checked_at")) +
                    " • جاري التحقق من التحديث الحي"
                );
            } else {
                status.setText(
                    "آخر تحديث: " + formatTime(report.optString("checked_at")) +
                    " • تم تحديث البيانات بنجاح"
                );

                if (inspectButton != null) {
                    inspectButton.setTextColor(GREEN);
                }

                // لا تحفظ التقرير إلا بعد نجاح تحليله وعرض بياناته.
                rosterCachePrefs.edit()
                    .putString("last_successful_report", report.toString())
                    .apply();

                consecutiveSyncFailures = 0;
                nextSyncDelayMs = SYNC_INTERVAL_MS;
                scheduleNextSync();

            }

        } catch (Exception e) {
            status.setText("تعذر عرض التقرير: " + e.getMessage());
            Toast.makeText(this, "فشل قراءة بيانات الروستر", Toast.LENGTH_LONG).show();
            recordSyncFailure();
        }
    }

    private void renderDashboardStats(
            JSONArray riders, int ridersWithOrders, int totalOrders) {
        if (dashboardStats == null) return;
        dashboardStats.removeAllViews();

        int working = 0, onBreak = 0, offline = 0;
        int utrCount = 0, lowUtr = 0, walletOver2300 = 0;
        double utrSum = 0.0;
        long totalWorkedSeconds = 0;
        int workedTimeCount = 0;

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            String state = rider.optString("rider_status",
                    rider.optString("status", ""))
                    .trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');

            if (state.equals("working") || state.equals("active")
                    || state.equals("on_delivery")
                    || state.equals("on_delivery_order")) {
                working++;
            } else if (state.equals("break") || state.equals("on_break")
                    || state.equals("onbreak")) {
                onBreak++;
            } else if (state.equals("offline") || state.equals("inactive")) {
                offline++;
            }

            long seconds = rider.optLong("worked_seconds", -1);
            if (seconds >= 0) {
                totalWorkedSeconds += seconds;
                workedTimeCount++;
            }

            String performance = rider.optString("performance", "")
                    .trim().replace(',', '.');
            if (performance.matches("-?\\d+(?:\\.\\d+)?")) {
                try {
                    double value = Double.parseDouble(performance);
                    if (Double.isFinite(value) && value >= 0 && value <= 100) {
                        utrSum += value;
                        utrCount++;
                        if (value < 1.4) lowUtr++;
                    }
                } catch (NumberFormatException ignored) { }
            }

            String wallet = rider.optString("wallet_balance", "")
                    .trim().replace(" ", "").replace(",", "");
            java.util.regex.Matcher wm = java.util.regex.Pattern
                    .compile("-?\\d+(?:\\.\\d+)?").matcher(wallet);
            if (wm.find()) {
                try {
                    if (Double.parseDouble(wm.group()) > 2300) {
                        walletOver2300++;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        String hours = workedTimeCount > 0
                ? String.format(Locale.US, "%.1f", totalWorkedSeconds / 3600.0)
                : "—";
        String averageUtr = utrCount > 0
                ? String.format(Locale.US, "%.2f", utrSum / utrCount)
                : "—";

        addMicroStatSection(dashboardStats, "الأوردرات والتشغيل",
                new String[]{"معاه أوردر", "فاضي", "إجمالي الأوردرات",
                        "إجمالي الساعات"},
                new String[]{String.valueOf(ridersWithOrders),
                        String.valueOf(Math.max(0, riders.length() - ridersWithOrders)),
                        String.valueOf(totalOrders), hours},
                new int[]{GREEN, MUTED, ORANGE, Color.WHITE});

        addMicroStatSection(dashboardStats, "الأداء والمحافظ",
                new String[]{"UTR متوسط", "UTR أقل من 1.4",
                        "محفظة أعلى من 2300"},
                new String[]{averageUtr,
                        utrCount > 0 ? String.valueOf(lowUtr) : "—",
                        String.valueOf(walletOver2300)},
                new int[]{Color.WHITE, lowUtr > 0 ? Color.RED : GREEN, ORANGE});

        JSONObject daily = updateDailyStats(riders);
        long dailySeconds = daily.optLong("worked_seconds", 0);
        long dailyOrders = daily.optLong("orders", 0);
        addMicroStatSection(dashboardStats, "إجمالي اليوم المحفوظ",
                new String[]{"ساعات العمل اليوم", "أوردرات اليوم"},
                new String[]{
                        String.format(Locale.US, "%.1f س", dailySeconds / 3600.0),
                        String.valueOf(dailyOrders)
                },
                new int[]{Color.WHITE, ORANGE});

        addSmartRadarAndRanking(riders);
    }

    // رادار الحالات فقط؛ بدون ترتيب أفضل أداء
    private void addSmartRadarAndRanking(JSONArray riders) {
        if (dashboardStats == null || riders == null) return;

        int working = 0, onBreak = 0, withOrders = 0, empty = 0, unknown = 0;

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            String state = rider.optString("rider_status",
                    rider.optString("status", ""))
                    .trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');

            boolean hasOrder = false;
            JSONArray orders = rider.optJSONArray("active_orders");
            if ((orders != null && orders.length() > 0)
                    || rider.optInt("active_count") > 0
                    || rider.optBoolean("has_active_deliveries", false)) {
                hasOrder = true;
                withOrders++;
            }

            if (state.equals("working") || state.equals("active")
                    || state.equals("on_delivery") || state.equals("on_delivery_order")) {
                working++;
                if (!hasOrder) empty++;
            } else if (state.equals("break") || state.equals("on_break")
                    || state.equals("onbreak")) {
                onBreak++;
            } else if (!state.equals("offline") && !state.equals("inactive")
                    && !state.isEmpty() && !state.equals("null")) {
                unknown++;
            }
        }

        addInteractiveStatusSection(dashboardStats, riders,
                new String[]{"شغال", "بريك", "معاه أوردر", "فاضي", "غير محدد"},
                new String[]{
                        String.valueOf(working),
                        String.valueOf(onBreak),
                        String.valueOf(withOrders),
                        String.valueOf(empty),
                        String.valueOf(unknown)
                },
                new int[]{GREEN, RED, ORANGE, MUTED, Color.LTGRAY});
    }


    private void addInteractiveStatusSection(LinearLayout parent,
            JSONArray riders, String[] labels, String[] values, int[] colors) {
        TextView title = makeText(
                "◈  رادار الحالات  •  اضغط لعرض أسماء المندوبين",
                10, ORANGE, true);
        title.setPadding(dp(4), dp(5), dp(4), dp(4));
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int count = Math.min(labels.length,
                Math.min(values.length, colors.length));

        for (int i = 0; i < count; i++) {
            final String filter = labels[i];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(2), dp(5), dp(2), dp(5));

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(38, 35, 31), Color.rgb(13, 16, 21)});
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), colors[i]);
            tile.setBackground(bg);
            tile.setElevation(dp(3));
            tile.setClickable(true);
            tile.setFocusable(true);

            TextView value = makeText(values[i], 13, colors[i], true);
            value.setGravity(Gravity.CENTER);
            tile.addView(value, new LinearLayout.LayoutParams(-1, -2));

            TextView label = makeText(labels[i], 8, WHITE, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            tile.addView(label, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(4));
            row.addView(tile, lp);

            tile.setOnClickListener(v -> showRidersByStatus(riders, filter));
        }

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showRidersByStatus(JSONArray riders, String filter) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();

        if (riders != null) {
            for (int i = 0; i < riders.length(); i++) {
                JSONObject rider = riders.optJSONObject(i);
                if (rider == null) continue;

                String state = rider.optString("rider_status",
                        rider.optString("status", ""))
                        .trim().toLowerCase(Locale.ROOT)
                        .replace('-', '_').replace(' ', '_');

                boolean working = state.equals("working")
                        || state.equals("active")
                        || state.equals("on_delivery")
                        || state.equals("on_delivery_order");

                boolean onBreak = state.equals("break")
                        || state.equals("on_break")
                        || state.equals("onbreak");

                boolean hasOrder = false;
                JSONArray orders = rider.optJSONArray("active_orders");
                if (orders != null && orders.length() > 0) hasOrder = true;
                if (rider.optInt("active_count") > 0
                        || rider.optBoolean("has_active_deliveries", false)) {
                    hasOrder = true;
                }

                JSONObject deliveries = rider.optJSONObject("deliveries_info");
                if (deliveries != null) {
                    JSONArray ids = deliveries.optJSONArray("active_delivery_ids");
                    if (ids != null && ids.length() > 0) hasOrder = true;
                }

                boolean match;
                switch (filter) {
                    case "شغال": match = working; break;
                    case "بريك": match = onBreak; break;
                    case "معاه أوردر": match = hasOrder; break;
                    case "فاضي": match = working && !hasOrder; break;
                    default:
                        match = !working && !onBreak
                                && !state.equals("offline")
                                && !state.equals("inactive")
                                && !state.isEmpty() && !state.equals("null");
                }

                if (!match) continue;

                String name = rider.optString("name", "").trim();
                if (name.isEmpty()) name = rider.optString("rider_name", "").trim();
                if (name.isEmpty()) name = rider.optString("employee_name", "").trim();
                if (name.isEmpty()) name = rider.optString("employee_id", "").trim();
                if (name.isEmpty()) name = rider.optString("id", "مندوب بدون اسم");

                if (filter.equals("شغال") || filter.equals("معاه أوردر")
                        || filter.equals("فاضي")) {
                    names.add("● " + name + (hasOrder
                            ? "   |   🟠 معاه أوردر"
                            : "   |   ⚪ من غير أوردر"));
                } else {
                    names.add("● " + name);
                }
            }
        }

        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        String message = names.isEmpty()
                ? "مفيش مندوبين في الحالة دي حاليًا"
                : android.text.TextUtils.join("\n", names);

        new AlertDialog.Builder(this)
                .setTitle(filter + " (" + names.size() + ")")
                .setMessage(message)
                .setPositiveButton("إغلاق", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // تنبيهات مرة واحدة لكل حالة، مع مهلة 30 دقيقة قبل تكرارها
    private void notifySmartAlerts(JSONArray riders) {
        if (riders == null || rosterCachePrefs == null) return;

        long now = System.currentTimeMillis();
        long cooldown = 30L * 60L * 1000L;
        java.util.ArrayList<String> alerts = new java.util.ArrayList<>();

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            String name = rider.optString("name", "مندوب");
            String keyBase = firstValue(rider, "employee_id", "id", "rider_id", "name");
            if (keyBase.isEmpty()) keyBase = name;
            keyBase = keyBase.replaceAll("[^A-Za-z0-9_-]", "_");

            String state = rider.optString("rider_status",
                    rider.optString("status", ""))
                    .trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');

            boolean onBreak = state.equals("break")
                    || state.equals("on_break") || state.equals("onbreak");

            if (onBreak) {
                long started = parseTimestampMillis(firstValue(rider,
                        "break_started_at", "breakStartedAt", "break_start_time",
                        "breakStartTime", "on_break_since", "onBreakSince",
                        "status_changed_at", "statusChangedAt"));

                if (started > 0L && now >= started
                        && now - started >= 15L * 60L * 1000L) {
                    String key = "smart_alert_break_" + keyBase;
                    if (now - rosterCachePrefs.getLong(key, 0L) >= cooldown) {
                        alerts.add(name + ": بريك أكثر من 15 دقيقة");
                        rosterCachePrefs.edit().putLong(key, now).apply();
                    }
                }
            }

            JSONArray orders = rider.optJSONArray("active_orders");
            if (orders == null || orders.length() == 0) continue;

            JSONObject order = orders.optJSONObject(0);
            if (order == null) continue;

            long orderStarted = parseTimestampMillis(firstValue(order,
                    "accepted_at", "acceptedAt", "accepted_time", "acceptedTime",
                    "created_at", "createdAt", "order_created_at", "orderCreatedAt",
                    "created_at_utc", "timestamp"));

            if (orderStarted > 0L && now >= orderStarted
                    && now - orderStarted >= 10L * 60L * 1000L) {
                String orderId = firstValue(order, "order_code", "delivery_id", "id");
                orderId = orderId.replaceAll("[^A-Za-z0-9_-]", "_");
                String key = "smart_alert_order_" + keyBase + "_" + orderId;

                if (now - rosterCachePrefs.getLong(key, 0L) >= cooldown) {
                    alerts.add(name + ": أوردر متأخر أكثر من 10 دقائق");
                    rosterCachePrefs.edit().putLong(key, now).apply();
                }
            }
        }

        if (!alerts.isEmpty()) {
            StringBuilder message = new StringBuilder("تنبيه المشرف");
            int shown = Math.min(3, alerts.size());
            for (int i = 0; i < shown; i++) {
                message.append("\n• ").append(alerts.get(i));
            }
            if (alerts.size() > shown) {
                message.append("\nو ").append(alerts.size() - shown)
                        .append(" تنبيهات أخرى");
            }
            Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
        }
    }

    // حفظ آخر تقرير ناجح في مجلد Downloads
    private void exportSupervisorReport() {
        String report = rosterCachePrefs == null ? null
                : rosterCachePrefs.getString("last_successful_report", null);

        if (report == null || report.trim().isEmpty()) {
            Toast.makeText(this,
                    "لا يوجد تقرير ناجح للتصدير حتى الآن",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = saveInspection(report);
            Toast.makeText(this,
                    "تم حفظ التقرير في Downloads: " + fileName,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "تعذر حفظ التقرير: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject updateDailyStats(JSONArray riders) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US)
                .format(new Date());
        String prefKey = "daily_stats_" + day;
        JSONObject daily;

        try {
            daily = new JSONObject(
                    rosterCachePrefs.getString(prefKey, "{}"));
        } catch (Exception e) {
            daily = new JSONObject();
        }

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            String riderKey = firstValue(
                    rider, "employee_id", "id", "rider_id", "name");
            if (riderKey.isEmpty()
                    || "غير متاح".equals(riderKey)) continue;

            long seconds = rider.optLong("worked_seconds", -1);
            long orders = rider.optLong("total_orders", -1);

            if (seconds < 0) {
                String worked = rider.optString("worked_time", "").trim();
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile(
                                "^(\\d{1,3}):(\\d{2}):(\\d{2})$")
                                .matcher(worked);
                if (m.matches()) {
                    seconds = Long.parseLong(m.group(1)) * 3600L
                            + Long.parseLong(m.group(2)) * 60L
                            + Long.parseLong(m.group(3));
                }
            }

            String shiftStart = firstValue(
                    rider, "shift_start", "start",
                    "active_shift_started_at",
                    "active_shift_startedAt", "session_start");

            if (shiftStart.isEmpty()) shiftStart = "shift_unknown";
            shiftStart = shiftStart.trim()
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            String shiftKey = "shift_" + shiftStart;

            try {
                JSONObject saved = daily.optJSONObject(riderKey);
                if (saved == null) saved = new JSONObject();

                JSONObject shifts = saved.optJSONObject("shifts");
                if (shifts == null) {
                    shifts = new JSONObject();

                    // ترحيل السجل القديم بدل فقد أرقام اليوم.
                    if (saved.has("worked_seconds") || saved.has("orders")) {
                        JSONObject legacy = new JSONObject();
                        legacy.put("worked_seconds",
                                Math.max(0, saved.optLong("worked_seconds", 0)));
                        legacy.put("orders",
                                Math.max(0, saved.optLong("orders", 0)));
                        shifts.put(shiftKey, legacy);
                    }
                }

                JSONObject shift = shifts.optJSONObject(shiftKey);
                if (shift == null) shift = new JSONObject();

                if (seconds >= 0) {
                    shift.put("worked_seconds",
                            Math.max(seconds,
                                    shift.optLong("worked_seconds", 0)));
                }
                if (orders >= 0) {
                    shift.put("orders",
                            Math.max(orders, shift.optLong("orders", 0)));
                }

                shifts.put(shiftKey, shift);
                saved.put("shifts", shifts);
                saved.remove("worked_seconds");
                saved.remove("orders");
                daily.put(riderKey, saved);
            } catch (Exception ignored) { }
        }

        long totalSeconds = 0;
        long totalOrders = 0;
        java.util.Iterator<String> riderKeys = daily.keys();

        while (riderKeys.hasNext()) {
            JSONObject saved = daily.optJSONObject(riderKeys.next());
            if (saved == null) continue;

            JSONObject shifts = saved.optJSONObject("shifts");
            if (shifts == null) {
                totalSeconds += Math.max(
                        0, saved.optLong("worked_seconds", 0));
                totalOrders += Math.max(0, saved.optLong("orders", 0));
                continue;
            }

            java.util.Iterator<String> shiftKeys = shifts.keys();
            while (shiftKeys.hasNext()) {
                JSONObject shift =
                        shifts.optJSONObject(shiftKeys.next());
                if (shift == null) continue;
                totalSeconds += Math.max(
                        0, shift.optLong("worked_seconds", 0));
                totalOrders += Math.max(0, shift.optLong("orders", 0));
            }
        }

        rosterCachePrefs.edit()
                .putString(prefKey, daily.toString()).apply();

        JSONObject totals = new JSONObject();
        try {
            totals.put("worked_seconds", totalSeconds);
            totals.put("orders", totalOrders);
        } catch (Exception ignored) { }
        return totals;
    }

    private void addMicroStatSection(
            LinearLayout parent, String heading,
            String[] labels, String[] values, int[] colors) {
        TextView title = makeText("◈  " + heading, 10, ORANGE, true);
        title.setPadding(dp(4), dp(3), dp(4), dp(3));
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int count = Math.min(labels.length,
                Math.min(values.length, colors.length));

        for (int i = 0; i < count; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(2), dp(1), dp(2), dp(1));

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(31, 29, 27), Color.rgb(8, 10, 14)}
            );
            bg.setCornerRadius(dp(11));
            bg.setStroke(dp(1), Color.rgb(112, 66, 34));
            tile.setBackground(bg);
            tile.setElevation(dp(5));
            tile.setTranslationZ(dp(1));

            TextView icon = makeText(statIcon(labels[i]), 8, colors[i], true);
            icon.setGravity(Gravity.CENTER);
            icon.setShadowLayer(dp(3), 0, 0, colors[i]);
            tile.addView(icon, new LinearLayout.LayoutParams(-1, -2));

            TextView value = makeText(values[i], 11, colors[i], true);
            value.setGravity(Gravity.CENTER);
            value.setShadowLayer(dp(3), 0, 0, colors[i]);
            tile.addView(value, new LinearLayout.LayoutParams(-1, -2));

            TextView label = makeText(labels[i], 7, MUTED, false);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            tile.addView(label, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout.LayoutParams tileParams =
                    new LinearLayout.LayoutParams(0, dp(39), 1f);
            tileParams.setMargins(dp(2), dp(2), dp(2), dp(3));
            row.addView(tile, tileParams);
        }

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(2));
        parent.addView(row, rowParams);
    }

    private String statIcon(String label) {
        String key = label == null ? "" : label.trim();

        if (key.contains("معاه أوردر") || key.contains("أوردرات")) return "📦";
        if (key.contains("فاضي")) return "○";
        if (key.contains("ساعات") || key.contains("الساعة")) return "◷";
        if (key.contains("UTR")) return "⚡";
        if (key.contains("محفظة")) return "◈";
        if (key.contains("شغال")) return "●";
        if (key.contains("بريك")) return "☕";
        if (key.contains("غير محدد")) return "◇";
        return "✦";
    }

    private String formatWorkDuration(long seconds) {
        if (seconds < 0) return "غير متاح من المصدر";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + " س " + minutes + " د";
    }

    private String formatTime(String iso) {
        try {
            return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date());
        } catch (Exception e) {
            return iso;
        }
    }



    private long parseTimestampMillis(String raw) {
        if (raw == null) return -1L;
        String value = raw.trim();
        if (value.isEmpty() || value.equals("null") || value.equals("—")) return -1L;

        try {
            if (value.matches("^\\d{10,13}$")) {
                long number = Long.parseLong(value);
                return number < 100000000000L ? number * 1000L : number;
            }
        } catch (Exception ignored) { }

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                if (pattern.endsWith("'Z'")) {
                    parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                }
                Date parsed = parser.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) { }
        }
        return -1L;
    }

    private void addSmartAlerts(LinearLayout card, JSONObject rider,
            JSONArray orders, String state) {
        long now = System.currentTimeMillis();
        String normalized = state == null ? "" :
                state.trim().toLowerCase(Locale.ROOT)
                        .replace('-', '_').replace(' ', '_');

        boolean onBreak = normalized.equals("break")
                || normalized.equals("on_break")
                || normalized.equals("onbreak");

        if (onBreak) {
            long started = parseTimestampMillis(firstValue(rider,
                    "break_started_at", "breakStartedAt",
                    "break_start_time", "breakStartTime",
                    "on_break_since", "onBreakSince",
                    "status_changed_at", "statusChangedAt"));

            if (started > 0L && now >= started
                    && now - started >= 15L * 60L * 1000L) {
                addSmartAlertRow(card,
                        "⚠ بريك طويل • أكثر من 15 دقيقة",
                        Color.rgb(255, 82, 82));
            }
        }

        if (orders == null || orders.length() == 0) return;
        JSONObject order = orders.optJSONObject(0);
        if (order == null) return;

        long orderStarted = parseTimestampMillis(firstValue(order,
                "accepted_at", "acceptedAt", "accepted_time", "acceptedTime",
                "created_at", "createdAt", "order_created_at",
                "orderCreatedAt", "created_at_utc", "timestamp"));

        if (orderStarted > 0L && now >= orderStarted
                && now - orderStarted >= 10L * 60L * 1000L) {
            addSmartAlertRow(card,
                    "⚠ أوردر متأخر • أكثر من 10 دقائق",
                    Color.rgb(255, 181, 71));
        }
    }

    private void addSmartAlertRow(LinearLayout card, String message, int color) {
        TextView alert = makeText(message, 10, color, true);
        alert.setGravity(Gravity.CENTER_VERTICAL);
        alert.setPadding(dp(8), dp(6), dp(8), dp(6));
        alert.setBackground(round(Color.rgb(35, 22, 17), color, 1));
        alert.setShadowLayer(dp(2), 0, 0, color);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(2));
        card.addView(alert, params);
    }


    private double mapCoordinate(JSONObject rider, String... keys) {
        for (String key : keys) {
            double value = rider.optDouble(key, Double.NaN);
            if (Double.isFinite(value)) return value;
        }
        return Double.NaN;
    }

    private double nestedMapCoordinate(JSONObject rider, String objectKey,
                                       String... keys) {
        JSONObject nested = rider.optJSONObject(objectKey);
        if (nested == null) return Double.NaN;
        return mapCoordinate(nested, keys);
    }

    private void renderRiderMap() {
        if (riderMapView == null || !mapTabActive) return;

        try {
            JSONArray points = new JSONArray();

            for (int i = 0; i < currentRiders.length(); i++) {
                JSONObject rider = currentRiders.optJSONObject(i);
                if (rider == null) continue;

                double lat = mapCoordinate(rider,
                        "latitude", "lat", "current_latitude", "currentLatitude");
                double lon = mapCoordinate(rider,
                        "longitude", "lng", "lon", "current_longitude",
                        "currentLongitude");

                if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
                    lat = nestedMapCoordinate(rider, "location",
                            "latitude", "lat");
                    lon = nestedMapCoordinate(rider, "location",
                            "longitude", "lng", "lon");
                }

                if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
                    lat = nestedMapCoordinate(rider, "current_location",
                            "latitude", "lat");
                    lon = nestedMapCoordinate(rider, "current_location",
                            "longitude", "lng", "lon");
                }

                // لا نرسم علامات لمواقع غير حقيقية أو إحداثيات غير صالحة.
                if (!Double.isFinite(lat) || !Double.isFinite(lon)
                        || lat < -90 || lat > 90 || lon < -180 || lon > 180
                        || (lat == 0 && lon == 0)) {
                    continue;
                }

                JSONObject point = new JSONObject();
                point.put("lat", lat);
                point.put("lon", lon);
                point.put("name", firstValue(rider,
                        "name", "employee_name", "rider_name"));
                point.put("status", translateRiderStatus(firstValue(rider,
                        "rider_status", "status", "work_status")));
                JSONArray orders = rider.optJSONArray("active_orders");
                point.put("orders", orders == null ? rider.optInt("active_count")
                        : orders.length());
                points.put(point);
            }

            String html =
                "<!doctype html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<style>html,body,#map{height:100%;margin:0;background:#07080a;color:#fff}" +
                ".note{position:absolute;z-index:999;bottom:12px;left:12px;right:12px;" +
                "background:#111317;border:1px solid #ff7900;border-radius:10px;" +
                "padding:10px;text-align:center;font:14px sans-serif;color:#fff}" +
                ".leaflet-popup-content-wrapper{background:#111317;color:#fff}" +
                ".leaflet-popup-tip{background:#111317}</style></head><body>" +
                "<div id='map'></div><div id='note' class='note'>جاري تجهيز الخريطة...</div>" +
                "<script>" +
                "window.mapLibraryReady=false;" +
                "function loadLeafletFallback(){" +
                "if(window.mapLibraryReady)return;" +
                "var note=document.getElementById('note');" +
                "note.textContent='تعذر تحميل مكتبة الخريطة؛ جارٍ تجربة مصدر بديل...';" +
                "var css=document.createElement('link');css.rel='stylesheet';" +
                "css.href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css';" +
                "document.head.appendChild(css);" +
                "var js=document.createElement('script');" +
                "js.src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js';" +
                "js.onload=function(){window.mapLibraryReady=true;initRiderMap();};" +
                "js.onerror=function(){note.textContent='تعذر تحميل الخريطة. تحقق من الإنترنت ثم أعد فتح التبويب.';};" +
                "document.head.appendChild(js);" +
                "}" +
                "function initRiderMap(){" +
                "if(typeof L==='undefined'){loadLeafletFallback();return;}" +
                "const map=L.map('map').setView([30.7865,31.0004],12);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'," +
                "{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);" +
                "const safe=s=>String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');" +
                "const pts=" + points.toString() + ";" +
                "pts.forEach(p=>{const color=/بريك|استراحة/.test(p.status)?'#ff5252':" +
                "(/شغال|عمل|توصيل/.test(p.status)?'#2dd27d':'#ffb52e');" +
                "L.circleMarker([p.lat,p.lon],{radius:8,color:color,fillColor:color," +
                "fillOpacity:.9,weight:2}).addTo(map).bindPopup(" +
                "'<b>'+safe(p.name)+'</b><br>'+safe(p.status)+'<br>أوردرات نشطة: '+p.orders);});" +
                "const note=document.getElementById('note');" +
                "if(pts.length){note.textContent='المندوبون على الخريطة: '+pts.length;" +
                "if(pts.length>1)map.fitBounds(L.latLngBounds(pts.map(p=>[p.lat,p.lon]))," +
                "{padding:[25,25],maxZoom:15});else map.setView([pts[0].lat,pts[0].lon],15);" +
                "}else{note.textContent='لا توجد إحداثيات GPS حقيقية للمندوبين في البيانات الحالية.';}" +
                "}" +
                "var primary=document.createElement('script');" +
                "primary.src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';" +
                "primary.onload=function(){window.mapLibraryReady=true;initRiderMap();};" +
                "primary.onerror=loadLeafletFallback;" +
                "document.head.appendChild(primary);" +
                "</script></body></html>";

            riderMapView.loadDataWithBaseURL(
                    "https://local.alabtal.invalid/", html, "text/html", "UTF-8", null);

        } catch (Exception e) {
            Toast.makeText(this, "تعذر تجهيز الخريطة: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderRiderCards() {
        if (cards == null) return;
        cards.removeAllViews();

        String query = ""; // تم حذف البحث النصي من الواجهة.
        int shown = 0;
        int matched = 0;

        for (int i = 0; i < currentRiders.length(); i++) {
            JSONObject rider = currentRiders.optJSONObject(i);
            if (rider == null) continue;

            String name = firstValue(rider, "name", "employee_name", "rider_name");
            String id = firstValue(rider, "employee_id", "id", "rider_id");
            String state = rider.optString("rider_status",
                    rider.optString("status", "")).toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            JSONArray orders = rider.optJSONArray("active_orders");
            if (orders == null) orders = new JSONArray();
            boolean hasOrders = orders.length() > 0
                    || rider.optInt("active_count") > 0
                    || rider.optBoolean("has_active_deliveries", false);

            if (!query.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(query)
                    && !id.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            boolean working = state.equals("working") || state.equals("active")
                    || state.equals("on_delivery") || state.equals("on_delivery_order");
            boolean onBreak = state.equals("break") || state.equals("on_break")
                    || state.equals("onbreak");
            boolean offline = state.equals("offline") || state.equals("inactive");

            if (riderFilter.equals("شغال") && !working) continue;
            if (riderFilter.equals("بريك") && !onBreak) continue;
            if (riderFilter.equals("معاه أوردر") && !hasOrders) continue;
            if (riderFilter.equals("فاضي") && hasOrders) continue;

            matched++;
            if (shown >= riderLimit) continue;

            addRiderCard(rider, orders);
            shown++;
        }

        if (shown == 0) {
            addEmptyCard("لا توجد نتائج", "جرّب فلترًا آخر لعرض المندوبين.");
        }

        if (status != null && currentRiders.length() > 0) {
            status.setText("عرض " + shown + " من " + matched
                    + " نتيجة مطابقة • تحديث البيانات من زر ↻");
        }
    }

    private void addRiderCard(JSONObject rider, JSONArray orders) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(9), dp(9), dp(8));

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(48, 25, 10),
                        Color.rgb(17, 27, 39),
                        Color.rgb(5, 8, 13)
                }
        );
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(2), ORANGE);
        card.setBackground(cardBg);
        card.setElevation(dp(9));
        card.setTranslationZ(dp(2));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                -1, -2);
        cardParams.setMargins(dp(5), dp(3), dp(5), dp(4));
        cards.addView(card, cardParams);

        String name = firstValue(rider, "name", "employee_name", "rider_name");
        if (name.isEmpty()) name = "مندوب بدون اسم";
        String id = firstValue(rider, "employee_id", "id", "rider_id");
        String wallet = firstValue(rider, "wallet", "wallet_balance",
                "walletBalance", "balance");
        String totalOrders = firstValue(rider, "total_orders",
                "orders_count", "totalOrders");
        String state = firstValue(rider, "rider_status", "state", "status", "work_status");
        String stateLabel = translateRiderStatus(state);
        String batch = firstValue(rider, "batch", "batch_number", "patch", "patch_number", "rider_batch");

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        TextView nameView = makeText(name, 14, Color.WHITE, true);
        nameView.setShadowLayer(dp(5), 0, 0, ORANGE);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(nameView);

        TextView idView = makeText(
                id.isEmpty() ? "مندوب" : "ID: " + id, 10, Color.LTGRAY, false);
        identity.addView(idView);
        heading.addView(identity);

        JSONArray activeIds = rider.optJSONArray("active_delivery_ids");
        boolean hasOrders = (orders != null && orders.length() > 0)
                || rider.optInt("active_count") > 0
                || rider.optBoolean("has_active_deliveries", false)
                || (activeIds != null && activeIds.length() > 0);

        // لون شارة الحالة: الاستراحة أحمر، الشغال أخضر، والباقي أصفر.
        String normalizedState = state == null
                ? "" : state.trim().toLowerCase(Locale.ROOT);

        int stateColor;
        if (normalizedState.equals("break")
                || normalizedState.equals("on_break")
                || stateLabel.equals("استراحة")) {
            stateColor = RED;
        } else if (normalizedState.equals("working")
                || normalizedState.equals("active")
                || stateLabel.equals("شغال")) {
            stateColor = GREEN;
        } else {
            stateColor = Color.rgb(255, 193, 7);
        }

        TextView orderBadge = makeText(
                "● " + stateLabel,
                10, stateColor, true);
        orderBadge.setShadowLayer(dp(4), 0, 0, stateColor);
        orderBadge.setGravity(Gravity.CENTER);
        orderBadge.setPadding(dp(6), dp(4), dp(6), dp(4));

        android.graphics.drawable.GradientDrawable stateBadgeBg =
                new android.graphics.drawable.GradientDrawable();
        stateBadgeBg.setColor(Color.argb(
                28, Color.red(stateColor),
                Color.green(stateColor), Color.blue(stateColor)));
        stateBadgeBg.setCornerRadius(dp(12));
        stateBadgeBg.setStroke(dp(2), stateColor);
        orderBadge.setBackground(stateBadgeBg);
        orderBadge.setElevation(dp(4));
        heading.addView(orderBadge);

        if (!batch.isEmpty()) {
            TextView batchView = makeText(
                    "باتش " + batch,
                    10, ORANGE, true);
            batchView.setPadding(dp(6), 0, 0, 0);
            batchView.setShadowLayer(dp(4), 0, 0, ORANGE);
            heading.addView(batchView);
        }
        card.addView(heading);

        // ملخص صغير ظاهر دائمًا
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setWeightSum(4f);
        LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                -1, -2);
        metricParams.topMargin = dp(8);
        card.addView(metrics, metricParams);

        addCompactMetric(metrics, "💰 المحفظة",
                wallet.isEmpty() ? "—" : wallet);
        addCompactMetric(metrics, "📦 الأوردرات",
                totalOrders.isEmpty()
                        ? String.valueOf(orders == null ? 0 : orders.length())
                        : totalOrders);

        addCompactMetric(metrics, "◷ الساعة",
                firstValue(rider, "worked", "worked_time", "worked_hours",
                        "worked_duration", "work_time").isEmpty()
                        ? "—"
                        : firstValue(rider, "worked", "worked_time", "worked_hours",
                                "worked_duration", "work_time"));
        addCompactMetric(metrics, "☕ بريك",
                firstValue(rider, "break_time", "break_duration", "break_minutes",
                        "breaks", "breakTime").isEmpty()
                        ? "—"
                        : firstValue(rider, "break_time", "break_duration",
                                "break_minutes", "breaks", "breakTime"));

        LinearLayout shiftRow = new LinearLayout(this);
        shiftRow.setOrientation(LinearLayout.HORIZONTAL);
        shiftRow.setGravity(Gravity.CENTER_VERTICAL);
        shiftRow.setPadding(dp(7), dp(7), dp(7), dp(7));
        shiftRow.setBackground(round(Color.rgb(17, 18, 21), ORANGE, 1));
        TextView shiftStart = makeText("بداية الشيفت  " + displayValue(
                firstValue(rider, "shift_start", "shiftStart", "shift_start_time", "session_start", "planned_shift_start")),
                9, ORANGE, true);
        shiftStart.setShadowLayer(dp(3), 0, 0, ORANGE_DEEP);
        shiftStart.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        shiftRow.addView(shiftStart);
        TextView shiftEnd = makeText("النهاية  " + displayValue(
                firstValue(rider, "shift_end", "shiftEnd", "shift_end_time", "session_end", "planned_shift_end")),
                9, ORANGE, true);
        shiftEnd.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        shiftEnd.setShadowLayer(dp(3), 0, 0, ORANGE_DEEP);
        shiftEnd.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        shiftRow.addView(shiftEnd);
        LinearLayout.LayoutParams shiftParams = new LinearLayout.LayoutParams(-1, -2);
        shiftParams.topMargin = dp(2);
        card.addView(shiftRow, shiftParams);

        // لا تعرض تنبيهًا إلا عند توفر توقيت صالح.
        addSmartAlerts(card, rider, orders, state);

        if (hasOrders) {
            JSONObject activeOrder = orders == null
                    ? null : orders.optJSONObject(0);

            if (activeOrder != null) {
                TextView activeLabel = makeText(
                        "● معاه أوردر نشط", 11, GREEN, true);
                activeLabel.setPadding(dp(4), dp(6), dp(4), dp(4));
                activeLabel.setShadowLayer(dp(3), 0, 0,
                        Color.rgb(15, 100, 55));
                card.addView(activeLabel);

                LinearLayout progress = new LinearLayout(this);
                progress.setOrientation(LinearLayout.VERTICAL);
                addOrderProgressTrack(progress,
                        firstValue(activeOrder,
                                "status", "order_status", "stage", "delivery_status"),
                        firstValue(activeOrder,
                                "vendor_name", "restaurant_name", "merchant_name",
                                "vendorName", "restaurantName"));
                card.addView(progress);
            } else {
                TextView activeLabel = makeText(
                        "● معاه أوردر نشط • تفاصيل المرحلة غير متاحة حاليًا",
                        10, GREEN, true);
                activeLabel.setPadding(dp(3), dp(7), dp(3), dp(4));
                card.addView(activeLabel);
            }
        } else {
            TextView noActiveOrder = makeText(
                    "○ مفيش أوردر نشط", 10, Color.LTGRAY, true);
            noActiveOrder.setPadding(dp(3), dp(5), dp(3), dp(3));
            card.addView(noActiveOrder);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(4), 0, dp(3));
        addRiderAction(actions, rider, "📞", "اتصال", ORANGE);
        addRiderAction(actions, rider, "🔋", "تزويد", GREEN);
        addRiderAction(actions, rider, "☕", "بريك", ORANGE);
        addRiderAction(actions, rider, "✉", "رسالة", ORANGE);
        card.addView(actions);

        TextView toggle = makeText("⌄ تفاصيل المندوب", 11, ORANGE, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(8), dp(6), dp(8), dp(6));
        toggle.setBackground(round(Color.rgb(15, 23, 33), ORANGE, 1));
        card.addView(toggle);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(dp(2), dp(5), dp(2), 0);
        card.addView(details);

        addCompactDetail(details, "الحالة", state);
        addCompactDetail(details, "نقطة البداية",
                firstValue(rider, "starting_point", "startingPoint",
                        "start_point", "starting_point_name"));
        addCompactDetail(details, "المنطقة",
                firstValue(rider, "zone", "area", "city"));
        addCompactDetail(details, "الأداء",
                firstValue(rider, "performance", "performance_score"));
        addCompactDetail(details, "نسبة القبول",
                firstValue(rider, "acceptance", "acceptance_rate"));
        addCompactDetail(details, "بداية الشيفت",
                firstValue(rider, "shift_start", "shiftStart",
                        "shift_start_time", "session_start"));
        addCompactDetail(details, "نهاية الشيفت",
                firstValue(rider, "shift_end", "shiftEnd",
                        "shift_end_time", "session_end"));
        addCompactDetail(details, "وقت العمل",
                firstValue(rider, "worked", "worked_time",
                        "worked_duration", "work_time"));
        addCompactDetail(details, "المركبة",
                firstValue(rider, "vehicle", "vehicle_type"));

        if (hasOrders) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order == null) continue;
                TextView orderTitle = makeText(
                        "📦 أوردر " + (i + 1), 12, ORANGE, true);
                LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                        -1, -2);
                op.topMargin = dp(5);
                details.addView(orderTitle, op);

                addCompactDetail(details, "رقم الأوردر",
                        firstValue(order, "order_code", "order_id", "id"));
                addCompactDetail(details, "الحالة",
                        firstValue(order, "status", "order_status", "stage"));
                addCompactDetail(details, "الاستلام",
                        firstValue(order, "pickup_address", "pickup",
                                "pickupAddress"));
                addCompactDetail(details, "التسليم",
                        firstValue(order, "dropoff_address", "dropoff",
                                "dropoffAddress", "address"));
            }
        } else {
            TextView noOrder = makeText(
                    "لا يوجد أوردر ظاهر لهذا المندوب", 11, Color.LTGRAY, false);
            noOrder.setPadding(0, dp(4), 0, dp(4));
            details.addView(noOrder);
        }

        View.OnClickListener toggleDetails = v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "⌃ إخفاء التفاصيل" : "⌄ عرض التفاصيل");
        };
        card.setClickable(true);
        card.setFocusable(true);
        toggle.setOnClickListener(v -> showRiderDetails(rider, orders));
        card.setOnClickListener(v -> showRiderDetails(rider, orders));
    }

    private void addRiderAction(
            LinearLayout parent, JSONObject rider, String symbol, String label, int glow) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(2), dp(4), dp(2), dp(4));
        button.setBackground(round(Color.rgb(20, 20, 23), ORANGE_DEEP, 1));

        TextView icon = makeText(symbol, 16, glow, true);
        icon.setGravity(Gravity.CENTER);
        icon.setShadowLayer(dp(4), 0, 0, glow);
        button.addView(icon, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView caption = makeText(label, 8, Color.WHITE, true);
        caption.setGravity(Gravity.CENTER);
        button.addView(caption);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, lp);

        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            String phone = firstValue(rider, "phone", "phone_number", "mobile",
                    "rider_phone", "telephone", "contact_number");
            try {
                if ("تزويد".equals(label)) {
                    showFormPage("تزويد شيفتات الطيارين");
                } else if ("بريك".equals(label)) {
                    showFormPage("فك بريك");
                } else if ("اتصال".equals(label)) {
                    if (phone.isEmpty() || phone.equals("غير متاح")) {
                        Toast.makeText(this, "رقم هاتف المندوب غير متاح", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:" + phone.replace(" ", "")));
                    startActivity(intent);
                } else if ("رسالة".equals(label)) {
                    if (phone.isEmpty() || phone.equals("غير متاح")) {
                        Toast.makeText(this, "رقم هاتف المندوب غير متاح", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_SENDTO,
                            android.net.Uri.parse("smsto:" + phone.replace(" ", "")));
                    startActivity(intent);
                } else if ("بريك".equals(label)) {
                    Toast.makeText(this,
                            "زر البريك ظاهر؛ تفعيل الحالة يحتاج دعمًا من مصدر الروستر",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            "زر التزويد ظاهر؛ لا يوجد إجراء تزويد مؤكد في بيانات الروستر",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "تعذر فتح الإجراء على هذا الجهاز", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addCompactMetric(LinearLayout parent, String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(5), dp(4), dp(5), dp(4));

        int accent = ORANGE;
        String key = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (key.contains("محفظة")) accent = GREEN;
        else if (key.contains("أوردر")) accent = ORANGE;
        else if (key.contains("ساعة")) accent = Color.rgb(65, 165, 255);
        else if (key.contains("بريك")) accent = Color.rgb(185, 115, 255);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(25, 39, 53),
                        Color.rgb(8, 13, 21)
                }
        );
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), accent);
        item.setBackground(bg);
        item.setElevation(dp(5));
        item.setTranslationZ(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(item, lp);

        TextView metricLabel = makeText(label, 9, ORANGE, true);
        metricLabel.setShadowLayer(dp(3), 0, 0, ORANGE_DEEP);
        metricLabel.setGravity(Gravity.CENTER);
        metricLabel.setMaxLines(1);
        metricLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(metricLabel);
        TextView val = makeText(value, 12, Color.WHITE, true);
        val.setShadowLayer(dp(4), 0, 0, ORANGE_DEEP);
        val.setGravity(Gravity.CENTER);
        val.setMaxLines(1);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(val);
    }

    private void addCompactDetail(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(3), dp(2), dp(3));

        TextView key = makeText(label, 11, Color.LTGRAY, false);
        key.setLayoutParams(new LinearLayout.LayoutParams(dp(94), -2));
        row.addView(key);

        TextView val = makeText(value, 11, Color.WHITE, false);
        val.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(val);
        parent.addView(row);
    }


    private void addOrderProgressTrack(
            LinearLayout target, String rawStatus, String restaurantName) {
        String st = rawStatus == null
                ? "" : rawStatus.trim().toLowerCase(Locale.ROOT)
                    .replace('_', ' ').replace('-', ' ');

        String stage;
        int color;
        int background;

        if (st.equals("completed") || st.equals("delivered")) {
            stage = "✓ تم التسليم";
            color = GREEN;
            background = Color.rgb(15, 53, 37);
        } else if (st.equals("cancelled") || st.equals("canceled")) {
            stage = "✕ الأوردر ملغي";
            color = RED;
            background = Color.rgb(58, 20, 24);
        } else if (st.equals("in transit") || st.equals("on the way")
                || st.equals("near dropoff") || st.equals("picked up")
                || st.equals("left pickup")) {
            stage = "🚲 في الطريق للعميل";
            color = Color.rgb(190, 125, 255);
            background = Color.rgb(43, 27, 61);
        } else if (st.equals("at dropoff")
                || st.equals("arrived at dropoff")) {
            stage = "📍 وصل للعميل";
            color = Color.rgb(90, 165, 255);
            background = Color.rgb(20, 39, 65);
        } else if (st.equals("near pickup") || st.equals("at pickup")
                || st.equals("arrived at pickup")) {
            stage = "🏪 استلام من المطعم";
            color = Color.rgb(255, 174, 70);
            background = Color.rgb(65, 39, 18);
        } else {
            String restaurant = restaurantName == null
                    ? "" : restaurantName.trim();

            if (restaurant.length() > 14) {
                restaurant = restaurant.substring(0, 13) + "…";
            }

            stage = restaurant.isEmpty()
                    ? "معاه أوردر"
                    : "معاه أوردر • " + restaurant;

            color = Color.rgb(255, 154, 67);
            background = Color.rgb(65, 39, 18);
        }

        TextView badge = makeText(stage, 10, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        badge.setBackground(round(background, color, 1));
        badge.setShadowLayer(dp(3), 0, 0, color);
        badge.setElevation(dp(3));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(dp(3), dp(4), dp(3), dp(3));
        target.addView(badge, params);

        boolean animateOrderJourney =
                st.equals("near pickup")
                || st.equals("at pickup")
                || st.equals("arrived at pickup")
                || st.equals("in transit")
                || st.equals("on the way")
                || st.equals("picked up")
                || st.equals("left pickup")
                || st.equals("near dropoff")
                || st.equals("at dropoff")
                || st.equals("arrived at dropoff");

        if (animateOrderJourney) {
            ObjectAnimator slide = ObjectAnimator.ofFloat(
                    badge, View.TRANSLATION_X, dp(-3), dp(3));
            slide.setDuration(650);
            slide.setRepeatMode(ObjectAnimator.REVERSE);
            slide.setRepeatCount(ObjectAnimator.INFINITE);
            slide.start();
        }

        ObjectAnimator pulse = ObjectAnimator.ofFloat(
                badge, View.ALPHA, 0.78f, 1f);
        pulse.setDuration(850);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();

        ObjectAnimator breatheX = ObjectAnimator.ofFloat(
                badge, View.SCALE_X, 1f, 1.035f);
        breatheX.setDuration(850);
        breatheX.setRepeatMode(ObjectAnimator.REVERSE);
        breatheX.setRepeatCount(ObjectAnimator.INFINITE);
        breatheX.start();

        ObjectAnimator breatheY = ObjectAnimator.ofFloat(
                badge, View.SCALE_Y, 1f, 1.035f);
        breatheY.setDuration(850);
        breatheY.setRepeatMode(ObjectAnimator.REVERSE);
        breatheY.setRepeatCount(ObjectAnimator.INFINITE);
        breatheY.start();
    }

    private void addDetailPair(LinearLayout parent,
            String label1, String value1, String label2, String value2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams a =
                new LinearLayout.LayoutParams(0, -2, 1f);
        a.setMargins(0, 0, dp(4), dp(4));
        row.addView(createTile(label1, displayValue(value1)), a);

        LinearLayout.LayoutParams b =
                new LinearLayout.LayoutParams(0, -2, 1f);
        b.setMargins(dp(4), 0, 0, dp(4));
        row.addView(createTile(label2, displayValue(value2)), b);
        parent.addView(row);
    }

    private void showRiderDetails(JSONObject rider, JSONArray orders) {
        detailVisible = true;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(8), dp(7), dp(8), dp(10));
        page.setBackgroundColor(BG);
        scroll.addView(page);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = makeButton("← رجوع");
        back.setTextSize(11);
        back.setOnClickListener(v -> returnToDashboard());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(90), dp(36)));

        TextView title = makeText("تفاصيل المندوب", 15, ORANGE, true);
        title.setPadding(dp(8), 0, 0, 0);
        toolbar.addView(title);
        page.addView(toolbar);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(9), dp(7), dp(9), dp(7));
        identity.setBackground(round(PANEL, ORANGE, 1));

        identity.addView(makeText(
                "👤 " + rider.optString("name", "مندوب بدون اسم"),
                16, WHITE, true));
        identity.addView(makeText(
                "ID: " + rider.optString("employee_id", "غير متاح")
                        + "  •  " + translateRiderStatus(
                                rider.optString("rider_status", "")),
                10, GREEN, true));
        LinearLayout.LayoutParams ip =
                new LinearLayout.LayoutParams(-1, -2);
        ip.setMargins(0, dp(6), 0, dp(5));
        page.addView(identity, ip);

        addDetailPair(page, "المحفظة",
                firstValue(rider, "wallet_balance", "wallet", "balance"),
                "إجمالي الأوردرات", firstValue(rider, "total_orders"));
        addDetailPair(page, "بداية الشيفت",
                firstValue(rider, "shift_start", "planned_shift_start", "session_start"),
                "نهاية الشيفت",
                firstValue(rider, "shift_end", "planned_shift_end", "session_end"));
        addDetailPair(page, "الأداء",
                firstValue(rider, "performance", "performance_score"),
                "نسبة القبول",
                firstValue(rider, "acceptance_rate", "acceptance", "acceptance_percentage"));
        addDetailPair(page, "ساعات العمل",
                firstValue(rider, "worked_time", "worked_hours", "worked"),
                "المحطة", firstValue(rider, "starting_point"));
        addDetailPair(page, "المنطقة", firstValue(rider, "zone"),
                "وسيلة التوصيل", firstValue(rider, "vehicle", "vehicle_type"));

        TextView ordersTitle = makeText("الأوردرات النشطة", 13, ORANGE, true);
        ordersTitle.setPadding(dp(2), dp(5), dp(2), dp(4));
        page.addView(ordersTitle);

        if (orders == null || orders.length() == 0) {
            boolean activeConfirmed =
                    rider.optBoolean("has_active_deliveries", false)
                    || rider.optInt("active_count") > 0;
            page.addView(makeText(
                    activeConfirmed
                        ? "يوجد أوردر نشط لكن تفاصيله غير متاحة حاليًا."
                        : "لا يوجد أوردر نشط مؤكد.",
                    11, MUTED, false));
        } else {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order == null) continue;

                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(8), dp(7), dp(8), dp(7));
                box.setBackground(round(
                        Color.rgb(24, 20, 17), ORANGE_DEEP, 1));

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(0, 0, 0, dp(5));
                page.addView(box, params);

                String code = order.optString("order_code", "");
                if (code.isEmpty()) code = order.optString("delivery_id", "غير متاح");

                box.addView(makeText("📦 " + code, 12, ORANGE, true));
                box.addView(makeText("🚦 " +
                        arabicStatus(order.optString("status", "")),
                        11, GREEN, true));
                box.addView(makeText("🍔 " +
                        displayValue(order.optString("vendor_name", "")),
                        10, WHITE, false));
                addAddress(box, "الاستلام", order.optString("pickup_address", ""));
                addAddress(box, "التسليم", order.optString("dropoff_address", ""));

                JSONArray timeline = order.optJSONArray("timeline");
                if (timeline != null && timeline.length() > 0) {
                    box.addView(makeText(
                            "الأحداث المسجلة: " + timeline.length(),
                            9, MUTED, false));
                }
            }
        }
        setContentView(scroll);
    }

    private void addDetailLine(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(PANEL, Color.rgb(55, 58, 65), 1));
        TextView l = makeText(label, 11, MUTED, false);
        TextView v = makeText(value, 14, WHITE, true);
        row.addView(l);
        row.addView(v);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(7));
        parent.addView(row, params);
    }

    private void returnToDashboard() {
        detailVisible = false;

        if (portalPageVisible && webView != null
                && dashboardRoot != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }

            int index = Math.min(6, dashboardRoot.getChildCount());
            webView.setVisibility(View.GONE);
            dashboardRoot.addView(webView, index,
                    new LinearLayout.LayoutParams(-1, dp(155)));

            portalPageVisible = false;
            portalVisible = false;
        }

        if (dashboardRoot != null) setContentView(dashboardRoot);
    }

    private void showLoginScreen(boolean setup) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(12), dp(8), dp(12), dp(10));
        scroll.addView(page, new android.widget.ScrollView.LayoutParams(-1, -1));

        FrameLayout hero = new FrameLayout(this);
        hero.setClipChildren(false);
        hero.setClipToPadding(false);
        hero.setBackground(round(BG, ORANGE_DEEP, 1));
        hero.setElevation(dp(8));

        // شعاع برتقالي خلف الشعار، مرسوم داخل التطبيق دون صورة جديدة.
        View rayField = new View(this) {
            private final android.graphics.Paint rayPaint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float radius = Math.max(getWidth(), getHeight()) * 0.58f;

                rayPaint.setShader(new android.graphics.RadialGradient(
                        cx, cy, radius,
                        new int[]{0x66FF7900, 0x24FF7900, 0x00FF7900},
                        new float[]{0f, 0.48f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, radius, rayPaint);
                rayPaint.setShader(null);

                rayPaint.setColor(0x40FF8A1C);
                rayPaint.setStrokeWidth(dp(1));
                rayPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2d * i / 20d;
                    float inner = radius * 0.43f;
                    float outer = radius * (0.76f + (i % 4) * 0.045f);
                    canvas.drawLine(
                            cx + (float) Math.cos(a) * inner,
                            cy + (float) Math.sin(a) * inner,
                            cx + (float) Math.cos(a) * outer,
                            cy + (float) Math.sin(a) * outer,
                            rayPaint);
                }

                rayPaint.setStyle(android.graphics.Paint.Style.STROKE);
                rayPaint.setStrokeWidth(dp(1));
                rayPaint.setColor(0x55FF7900);
                canvas.drawCircle(cx, cy, radius * 0.47f, rayPaint);
                canvas.drawCircle(cx, cy, radius * 0.61f, rayPaint);
                rayPaint.setStyle(android.graphics.Paint.Style.FILL);
            }
        };
        rayField.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        hero.addView(rayField, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroContent = new LinearLayout(this);
        heroContent.setOrientation(LinearLayout.VERTICAL);
        heroContent.setGravity(Gravity.CENTER);
        heroContent.setPadding(dp(4), dp(4), dp(4), dp(4));
        heroContent.setElevation(dp(4));

        ImageView brand = new ImageView(this);
        // إزالة الخلفية الداكنة المتصلة بحواف الشعار مع الحفاظ على تفاصيله.
        android.graphics.Bitmap logoBitmap =
                android.graphics.BitmapFactory.decodeResource(
                        getResources(), com.alabtal.portaltest.R.drawable.alabtal_logo);

        if (logoBitmap != null) {
            android.graphics.Bitmap mutableLogo =
                    logoBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            if (mutableLogo != null) {
                int w = mutableLogo.getWidth();
                int h = mutableLogo.getHeight();
                int[] pixels = new int[w * h];
                mutableLogo.getPixels(pixels, 0, w, 0, 0, w, h);
                boolean[] seen = new boolean[pixels.length];
                int[] queue = new int[pixels.length];
                int head = 0, tail = 0;

                // البكسلات السوداء/شبه السوداء المتصلة بحافة الصورة فقط.
                for (int x = 0; x < w; x++) {
                    int top = x, bottom = (h - 1) * w + x;
                    if (!seen[top]) { seen[top] = true; queue[tail++] = top; }
                    if (!seen[bottom]) { seen[bottom] = true; queue[tail++] = bottom; }
                }
                for (int y = 0; y < h; y++) {
                    int left = y * w, right = y * w + w - 1;
                    if (!seen[left]) { seen[left] = true; queue[tail++] = left; }
                    if (!seen[right]) { seen[right] = true; queue[tail++] = right; }
                }

                while (head < tail) {
                    int idx = queue[head++];
                    int color = pixels[idx];
                    int red = android.graphics.Color.red(color);
                    int green = android.graphics.Color.green(color);
                    int blue = android.graphics.Color.blue(color);
                    int max = Math.max(red, Math.max(green, blue));
                    int min = Math.min(red, Math.min(green, blue));

                    if (max > 58 || max - min > 24) continue;

                    pixels[idx] = 0x00000000;
                    int x = idx % w, y = idx / w;
                    int n;

                    if (x > 0) {
                        n = idx - 1;
                        if (!seen[n]) { seen[n] = true; queue[tail++] = n; }
                    }
                    if (x + 1 < w) {
                        n = idx + 1;
                        if (!seen[n]) { seen[n] = true; queue[tail++] = n; }
                    }
                    if (y > 0) {
                        n = idx - w;
                        if (!seen[n]) { seen[n] = true; queue[tail++] = n; }
                    }
                    if (y + 1 < h) {
                        n = idx + w;
                        if (!seen[n]) { seen[n] = true; queue[tail++] = n; }
                    }
                }

                mutableLogo.setPixels(pixels, 0, w, 0, 0, w, h);
                brand.setImageBitmap(mutableLogo);
            } else {
                brand.setImageBitmap(logoBitmap);
            }
        }
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.setContentDescription("شعار الأبطال");

        brand.setAdjustViewBounds(true);
        heroContent.addView(brand, new LinearLayout.LayoutParams(-1, dp(164)));

        TextView brandTitle = makeText("AL ABTAL", 25, ORANGE, true);
        brandTitle.setGravity(Gravity.CENTER);
        brandTitle.setShadowLayer(dp(8), 0, 0, ORANGE);
        heroContent.addView(brandTitle, new LinearLayout.LayoutParams(-1, dp(36)));

        TextView tagline = makeText("OPERATIONS  •  TALABAT  •  TANTA", 11, MUTED, true);
        tagline.setGravity(Gravity.CENTER);
        heroContent.addView(tagline, new LinearLayout.LayoutParams(-1, dp(24)));

        hero.addView(heroContent, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // ABTAL_WINGS_OVERLAY: جناحان متناسقان مع ريش متداخل.
        View wingsOverlay = new View(this) {
            private final android.graphics.Paint paint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            private void feather(android.graphics.Canvas c, float x, float y,
                                 float tipX, float tipY, float width) {
                android.graphics.Path f = new android.graphics.Path();
                f.moveTo(x, y);
                f.quadTo(x + width * 0.40f, y - dp(4), tipX, tipY);
                f.quadTo(x + width * 0.10f, y + dp(5), x, y);
                f.close();

                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setShader(new android.graphics.LinearGradient(
                        x, y, tipX, tipY,
                        new int[]{0xFF2A211B, 0xFF8A430F, 0xFFFF9A2E},
                        null, android.graphics.Shader.TileMode.CLAMP));
                c.drawPath(f, paint);
                paint.setShader(null);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(0.8f));
                paint.setColor(0xFFFFA13A);
                c.drawPath(f, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
            }

            private void drawWing(android.graphics.Canvas c, float cx, float cy) {
                float root = cx + dp(38);
                float tip = Math.min(getWidth() - dp(3), cx + dp(174));

                android.graphics.Path base = new android.graphics.Path();
                base.moveTo(root, cy + dp(13));
                base.cubicTo(cx + dp(72), cy - dp(7),
                        cx + dp(119), cy - dp(69), tip, cy - dp(73));
                base.cubicTo(cx + dp(155), cy - dp(28),
                        cx + dp(133), cy + dp(1), cx + dp(113), cy + dp(8));
                base.cubicTo(cx + dp(139), cy + dp(17),
                        cx + dp(155), cy + dp(31), tip - dp(7), cy + dp(43));
                base.cubicTo(cx + dp(116), cy + dp(42),
                        cx + dp(87), cy + dp(30), root, cy + dp(13));
                base.close();

                paint.setShader(new android.graphics.LinearGradient(
                        root, cy - dp(35), tip, cy + dp(25),
                        new int[]{0xFF151719, 0xFF50270D, 0xFFB85B12},
                        null, android.graphics.Shader.TileMode.CLAMP));
                c.drawPath(base, paint);
                paint.setShader(null);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.4f));
                paint.setColor(0xFFFF8B1B);
                c.drawPath(base, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);

                // صفوف ريش متداخل؛ كل ريشة لها طرف مضيء.
                for (int i = 0; i < 7; i++) {
                    float x = cx + dp(52 + i * 12);
                    float y = cy + dp(7 - i * 8);
                    float featherTipX = Math.min(tip - dp(4), x + dp(43));
                    float featherTipY = cy - dp(35 + i * 5);
                    feather(c, x, y, featherTipX, featherTipY, dp(38));
                }

                for (int i = 0; i < 5; i++) {
                    float x = cx + dp(62 + i * 12);
                    feather(c, x, cy + dp(17 + i * 2),
                            Math.min(tip - dp(8), x + dp(34)),
                            cy + dp(25 + i * 3), dp(30));
                }

                paint.setColor(0xFFFFC078);
                paint.setStrokeWidth(dp(1));
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                c.drawLine(root, cy + dp(12), cx + dp(103), cy - dp(19), paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
            }

            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() * 0.39f;
                canvas.save();
                drawWing(canvas, cx, cy);
                canvas.scale(-1f, 1f, cx, cy);
                drawWing(canvas, cx, cy);
                canvas.restore();
            }
        };
        wingsOverlay.setClickable(false);
        wingsOverlay.setFocusable(false);
        hero.addView(wingsOverlay,
                new FrameLayout.LayoutParams(-1, -1));
        // الشعار في المقدمة والجناحان خلفه.
        heroContent.bringToFront();


        LinearLayout.LayoutParams heroParams =
                new LinearLayout.LayoutParams(-1, dp(232));
        heroParams.setMargins(0, 0, 0, dp(7));
        page.addView(hero, heroParams);

        brand.setAlpha(0f);
        brand.setScaleX(0.72f);
        brand.setScaleY(0.72f);
        brand.setTranslationY(dp(10));
        brand.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        ObjectAnimator glow = ObjectAnimator.ofFloat(
                hero, "alpha", 0.96f, 1f, 0.98f, 1f);
        glow.setDuration(2800);
        glow.setRepeatCount(ObjectAnimator.INFINITE);
        glow.start();

        // دوران بطيء للشعاع حتى يظل التأثير هادئاً.
        ObjectAnimator rayRotation = ObjectAnimator.ofFloat(
                rayField, View.ROTATION, 0f, 360f);
        rayRotation.setDuration(36000);
        rayRotation.setRepeatCount(ObjectAnimator.INFINITE);
        rayRotation.setInterpolator(
                new android.view.animation.LinearInterpolator());
        rayRotation.start();

        // حركة منظور وطفو ناعمة للنسر مع الحفاظ على ظهوره بالكامل.
        ObjectAnimator eagleMotion = ObjectAnimator.ofFloat(
                brand, View.ROTATION_Y, -5f, 5f);
        eagleMotion.setDuration(1900);
        eagleMotion.setRepeatMode(ObjectAnimator.REVERSE);
        eagleMotion.setRepeatCount(ObjectAnimator.INFINITE);
        eagleMotion.start();

        ObjectAnimator eagleFloat = ObjectAnimator.ofFloat(
                brand, View.TRANSLATION_Y, 0f, -dp(5), 0f);
        eagleFloat.setDuration(2300);
        eagleFloat.setRepeatCount(ObjectAnimator.INFINITE);
        eagleFloat.start();

        ObjectAnimator eaglePulse = ObjectAnimator.ofFloat(
                brand, View.ALPHA, 0.90f, 1f);
        eaglePulse.setDuration(1500);
        eaglePulse.setRepeatMode(ObjectAnimator.REVERSE);
        eaglePulse.setRepeatCount(ObjectAnimator.INFINITE);
        eaglePulse.start();

        TextView heading = makeText(
                setup ? "إنشاء حساب التطبيق" : "مرحباً بعودتك",
                22, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView description = makeText(
                setup ? "أنشئ بيانات الدخول المحلية للمتابعة"
                      : "سجّل الدخول لمتابعة عملياتك",
                13, MUTED, false);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(-1, dp(28));
        descParams.setMargins(0, 0, 0, dp(16));
        page.addView(description, descParams);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(14), dp(16), dp(13));
        form.setBackground(round(PANEL, ORANGE_DEEP, 1));
        form.setElevation(dp(10));
        page.addView(form, new LinearLayout.LayoutParams(-1, -2));

        TextView usernameLabel = makeText("♙   اسم المستخدم", 13, ORANGE, true);
        form.addView(usernameLabel, new LinearLayout.LayoutParams(-1, dp(24)));

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setTextColor(WHITE);
        username.setHintTextColor(MUTED);
        username.setHint("مثال: ramy_admin");
        username.setTextSize(15);
        username.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        username.setPadding(dp(12), dp(8), dp(12), dp(8));
        username.setBackground(round(BG, ORANGE_DEEP, 1));
        form.addView(username, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView passwordLabel = makeText("🔒   كلمة المرور", 13, ORANGE, true);
        LinearLayout.LayoutParams passwordLabelParams =
                new LinearLayout.LayoutParams(-1, dp(24));
        passwordLabelParams.setMargins(0, dp(12), 0, 0);
        form.addView(passwordLabel, passwordLabelParams);

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setTextColor(WHITE);
        password.setHintTextColor(MUTED);
        password.setHint("أدخل كلمة المرور");
        password.setTextSize(15);
        password.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(12), dp(8), dp(12), dp(8));
        password.setBackground(round(BG, ORANGE_DEEP, 1));
        LinearLayout.LayoutParams passwordParams =
                new LinearLayout.LayoutParams(-1, dp(46));
        passwordParams.setMargins(0, 0, 0, dp(4));
        form.addView(password, passwordParams);

        // زر عين لإظهار وإخفاء كلمة المرور دون تغيير طريقة التحقق.
        TextView passwordToggle = makeText("◉  إظهار كلمة المرور", 12, ORANGE, true);
        passwordToggle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        passwordToggle.setPadding(dp(4), dp(2), dp(4), dp(2));
        passwordToggle.setClickable(true);
        passwordToggle.setFocusable(true);
        form.addView(passwordToggle,
                new LinearLayout.LayoutParams(-1, dp(30)));

        passwordToggle.setOnClickListener(view -> {
            boolean showing = password.getInputType()
                    == (InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

            int cursor = password.getSelectionStart();
            if (showing) {
                password.setInputType(
                        InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordToggle.setText("◉  إظهار كلمة المرور");
            } else {
                password.setInputType(
                        InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordToggle.setText("◎  إخفاء كلمة المرور");
            }
            password.setTypeface(Typeface.DEFAULT);
            password.setSelection(Math.max(0,
                    Math.min(cursor, password.length())));
        });

        Button submit = makeButton(
                setup ? "إنشاء الحساب والمتابعة" : "دخول إلى التطبيق");
        form.addView(submit, new LinearLayout.LayoutParams(-1, dp(48)));
        submit.setElevation(dp(4));

        TextView note = makeText(
                "حساب محلي على هذا الجهاز، منفصل عن حساب بوابة الشركة.",
                11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(4), dp(12), dp(4), 0);
        form.addView(note, new LinearLayout.LayoutParams(-1, -2));

        submit.setOnClickListener(v -> {
            String u = username.getText().toString().trim().toLowerCase(Locale.US);
            String pw = password.getText().toString();

            if (u.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this,
                        "اكتب اسم المستخدم وكلمة المرور",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (setup) {
                if (pw.length() < 6) {
                    password.setError("6 أحرف على الأقل");
                    password.requestFocus();
                    return;
                }

                authPrefs.edit()
                        .putString("username", u)
                        .putString("password_hash", hashPassword(pw))
                        .putBoolean("logged_in", true)
                        .apply();

                Toast.makeText(this,
                        "تم إنشاء حساب الدخول المحلي",
                        Toast.LENGTH_SHORT).show();
                buildDashboard();
            } else {
                String savedUser = authPrefs.getString("username", "");
                String savedHash = authPrefs.getString("password_hash", "");

                if (u.equals(savedUser) && hashPassword(pw).equals(savedHash)) {
                    authPrefs.edit().putBoolean("logged_in", true).apply();
                    buildDashboard();
                } else {
                    Toast.makeText(this,
                            "اسم المستخدم أو كلمة المرور غير صحيحة",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        if (!setup) {
            Button resetLogin = makeButton("إعادة تعيين بيانات الدخول");
            LinearLayout.LayoutParams resetParams =
                    new LinearLayout.LayoutParams(-1, dp(42));
            resetParams.setMargins(0, dp(12), 0, 0);
            page.addView(resetLogin, resetParams);

            resetLogin.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                    .setTitle("إعادة تعيين الدخول")
                    .setMessage("سيتم حذف بيانات الدخول المحلية فقط. بيانات الروستر المحفوظة لن تُحذف.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("متابعة", (dialog, which) -> {
                        authPrefs.edit()
                                .remove("username")
                                .remove("password_hash")
                                .remove("logged_in")
                                .apply();
                        showLoginScreen(true);
                    })
                    .show()
            );
        }

        setContentView(scroll);
    }

    private String hashPassword(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return value;
        }
    }

    private static final int SHEET_AUTH_REQUEST = 7301;
    private static final String SHEET_ID =
        "1gM2Mm0fUwJrPz3KZciXoNt9xatNB2j96ecsMPcog-uc";
    private static final String BOOKINGS_SHEET_ID =
        "1qSs7DijUG2yIKPeD8bi4eq3gI1XdXsMpXM2MCiZKN5c";
    private static final String SHEETS_SCOPE =
        "https://www.googleapis.com/auth/spreadsheets.readonly";

    private void requestSheetAccess() {
        showSheetMessage("جاري الاتصال بحساب Google...");

        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(SHEETS_SCOPE)))
            .build();

        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(result -> {
                if (result.hasResolution()) {
                    try {
                        startIntentSenderForResult(
                            result.getPendingIntent().getIntentSender(),
                            SHEET_AUTH_REQUEST, null, 0, 0, 0
                        );
                    } catch (IntentSender.SendIntentException e) {
                        showSheetError("تعذر فتح نافذة إذن Google.");
                    }
                } else {
                    String token = result.getAccessToken();
                    if (token == null || token.isEmpty()) {
                        showSheetError("لم يستلم التطبيق رمز الوصول. حاول مرة أخرى.");
                    } else {
                        loadPrivateSheets(token);
                    }
                }
            })
            .addOnFailureListener(e ->
                showSheetError("تعذر طلب إذن Google. تأكد من الحساب وحاول مرة أخرى.")
            );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != SHEET_AUTH_REQUEST) return;

        if (resultCode != RESULT_OK || data == null) {
            showSheetError("لم يتم منح إذن قراءة الشيت.");
            return;
        }

        try {
            AuthorizationResult result =
                Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(data);

            String token = result.getAccessToken();
            if (token == null || token.isEmpty()) {
                showSheetError("رمز الوصول غير متاح. أعد المحاولة.");
            } else {
                loadPrivateSheets(token);
            }
        } catch (ApiException e) {
            showSheetError("تعذر إكمال تفويض Google. حاول مرة أخرى.");
        }
    }

    private void loadPrivateSheets(String token) {
        if (bookingMode) {
            loadBookingRows(token);
            return;
        }
        showSheetMessage("جاري تحميل ردود الشيت...");

        new Thread(() -> {
            try {
                JSONArray shifts = fetchSheetRows(
                    token, "'تزويد الشيفتات'!A:H"
                );
                JSONArray breaks = fetchSheetRows(
                    token, "'فك البريك'!A:F"
                );

                String html = buildSheetHtml(shifts, breaks);
                runOnUiThread(() -> {
                    if (sheetView != null) {
                        sheetView.loadDataWithBaseURL(
                            "https://alabtal.local/",
                            html, "text/html", "UTF-8", null
                        );
                    }
                });
            } catch (Exception e) {
                String detail = e.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = e.getClass().getSimpleName();
                }
                android.util.Log.e("ALABTAL_SHEETS",
                    "Failed to load private sheets", e);
                showSheetError("سبب الخطأ: " + detail);
            }
        }).start();
    }

    private void loadBookingRows(String token) {
        showSheetMessage("جاري تحميل أسماء الحجوزات...");
        new Thread(() -> {
            try {
                JSONArray rows = fetchSheetRows(
                    token, BOOKINGS_SHEET_ID, "'ان اصين المكتب'!A:B"
                );
                String html = buildBookingsHtml(rows);
                runOnUiThread(() -> {
                    if (sheetView != null && bookingMode) {
                        sheetView.loadDataWithBaseURL(
                            "https://alabtal.local/",
                            html, "text/html", "UTF-8", null
                        );
                    }
                });
            } catch (Exception e) {
                String detail = e.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = e.getClass().getSimpleName();
                }
                android.util.Log.e("ALABTAL_BOOKINGS",
                    "Failed to load bookings sheet", e);
                showSheetError("تعذر تحميل الحجوزات: " + detail);
            }
        }).start();
    }

    private String buildBookingsHtml(JSONArray rows) {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang='ar' dir='rtl'><head>")
         .append("<meta charset='UTF-8'><meta name='viewport' ")
         .append("content='width=device-width,initial-scale=1'>")
         .append("<style>")
         .append("*{box-sizing:border-box}")
         .append("body{margin:0;padding:10px;background:#07080a;color:#f5f6f8;font-family:Arial,sans-serif}")
         .append(".head{padding:13px;border:1px solid #6b3b18;border-right:4px solid #ff7900;border-radius:14px;background:linear-gradient(145deg,#25201b,#111317);margin-bottom:10px}")
         .append(".title{color:#ff9b3d;font-size:17px;font-weight:900}")
         .append(".sub{color:#a0a6b0;font-size:11px;margin-top:5px}")
         .append(".card{display:flex;align-items:center;gap:10px;padding:12px;margin-bottom:8px;border:1px solid #30323a;border-right:3px solid #ff7900;border-radius:12px;background:linear-gradient(145deg,#1c1e23,#101115);box-shadow:0 4px 10px #0008}")
         .append(".num{width:30px;height:30px;display:flex;align-items:center;justify-content:center;border-radius:9px;background:#38200f;color:#ff9b3d;font-weight:bold}")
         .append(".name{font-weight:bold;font-size:14px;overflow-wrap:anywhere}")
         .append(".id{font-size:10px;color:#9da3ae;margin-top:4px}")
         .append(".empty{padding:20px;text-align:center;color:#a0a6b0;border:1px dashed #383a40;border-radius:12px}")
         .append("</style></head><body>")
         .append("<div class='head'><div class='title'>📦 حجوزات المناديب</div>")
         .append("<div class='sub'>ورقة ان اصين المكتب • الأسماء من العمود B</div></div>");

        int shown = 0;
        for (int i = 1; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null) continue;
            String id = sheetCell(row, 0);
            String name = sheetCell(row, 1);
            if (name.isEmpty()) continue;

            h.append("<div class='card'><div class='num'>")
             .append(shown + 1)
             .append("</div><div><div class='name'>")
             .append(escapeSheetHtml(name))
             .append("</div>");
            if (!id.isEmpty()) {
                h.append("<div class='id'>Rider ID: ")
                 .append(escapeSheetHtml(id))
                 .append("</div>");
            }
            h.append("</div></div>");
            shown++;
        }

        if (shown == 0) {
            h.append("<div class='empty'>لا توجد أسماء في العمود B، أو أن الورقة فارغة.</div>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    private String fetchSheetTitles(String token) throws IOException {
        URL url = new URL(
            "https://sheets.googleapis.com/v4/spreadsheets/"
            + SHEET_ID + "?fields=sheets.properties.title"
        );
        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Authorization", "Bearer " + token);

        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (stream != null) {
                try (InputStream in = stream) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }
            }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IOException("Metadata API HTTP " + code + ": " + body);
            }
            JSONObject json = new JSONObject(body);
            JSONArray sheets = json.optJSONArray("sheets");
            StringBuilder titles = new StringBuilder();
            if (sheets != null) {
                for (int i = 0; i < sheets.length(); i++) {
                    JSONObject properties = sheets.getJSONObject(i)
                        .optJSONObject("properties");
                    if (properties != null) {
                        if (titles.length() > 0) titles.append(" | ");
                        titles.append(properties.optString("title", ""));
                    }
                }
            }
            return titles.toString();
        } catch (org.json.JSONException e) {
            throw new IOException("تعذر قراءة أسماء التبويبات.", e);
        } finally {
            connection.disconnect();
        }
    }

    private JSONArray fetchSheetRows(String token, String range)
            throws IOException {
        return fetchSheetRows(token, SHEET_ID, range);
    }

    private JSONArray fetchSheetRows(
            String token, String spreadsheetId, String range)
            throws IOException {
        String encoded = URLEncoder.encode(range, "UTF-8")
            .replace("+", "%20");

        URL url = new URL(
            "https://sheets.googleapis.com/v4/spreadsheets/"
            + spreadsheetId + "/values/" + encoded
            + "?valueRenderOption=FORMATTED_VALUE"
        );

        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");

        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();

            String body = "";
            if (stream != null) {
                try (InputStream in = stream;
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }

            if (code < 200 || code >= 300) {
                throw new IOException("Sheets API HTTP " + code + ": " + body);
            }

            JSONObject json = new JSONObject(body);
            JSONArray rows = json.optJSONArray("values");
            return rows == null ? new JSONArray() : rows;
        } catch (org.json.JSONException e) {
            throw new IOException("استجابة الشيت غير صالحة.", e);
        } finally {
            connection.disconnect();
        }
    }

    private void showSheetMessage(String message) {
        String html = "<html><meta charset='UTF-8'><body style='"
            + "background:#07080a;color:#fff;font-family:sans-serif;"
            + "text-align:center;padding:36px'>"
            + "<h3 style='color:#ff7900'>AL ABTAL</h3><p>"
            + escapeSheetHtml(message) + "</p></body></html>";

        runOnUiThread(() -> {
            if (sheetView != null) {
                sheetView.loadDataWithBaseURL(
                    "https://alabtal.local/", html,
                    "text/html", "UTF-8", null
                );
            }
        });
    }

    private void showSheetError(String message) {
        String html = "<html><meta charset='UTF-8'><body style='"
            + "background:#07080a;color:#fff;font-family:sans-serif;"
            + "text-align:center;padding:28px'>"
            + "<h3 style='color:#ff7900'>تعذر تحميل الردود</h3><p>"
            + escapeSheetHtml(message)
            + "</p><p>ارجع لتبويب ردود الشيت وحاول مرة أخرى.</p>"
            + "</body></html>";

        runOnUiThread(() -> {
            if (sheetView != null) {
                sheetView.loadDataWithBaseURL(
                    "https://alabtal.local/", html,
                    "text/html", "UTF-8", null
                );
            }
        });
    }

    private String buildSheetHtml(JSONArray shifts, JSONArray breaks) {
        StringBuilder h = new StringBuilder();

        h.append("<!doctype html><html lang='ar' dir='rtl'><head>")
         .append("<meta charset='UTF-8'><meta name='viewport' ")
         .append("content='width=device-width,initial-scale=1'>")
         .append("<style>")
         .append("*{box-sizing:border-box}")
         .append("body{margin:0;padding:9px;background:#07080a;color:#f5f6f8;font-family:Arial,sans-serif}")
         .append(".tabs{display:flex;gap:7px;position:sticky;top:0;background:#07080a;padding:5px 0 10px;z-index:2}")
         .append("button{flex:1;padding:11px 5px;border:1px solid #633719;border-radius:12px;background:linear-gradient(145deg,#202126,#111216);color:#eee;font-weight:bold;box-shadow:0 3px 8px #0008}")
         .append("button.active{background:linear-gradient(135deg,#ff9d3b,#ff7900);border-color:#ffb66c;color:#17100a;box-shadow:0 0 12px #ff790033}")
         .append(".card{position:relative;background:linear-gradient(145deg,#1c1e23,#101115 72%);border:1px solid #30323a;border-right:4px solid #ff7900;border-radius:15px;padding:11px;margin-bottom:9px;overflow-wrap:anywhere;box-shadow:0 5px 12px #0008}")
         .append(".card:before{content:'';display:block;height:1px;margin:-2px 0 7px;background:linear-gradient(90deg,transparent,#ff790077,transparent)}")
         .append(".line{display:flex;justify-content:space-between;align-items:flex-start;gap:9px;margin:0;padding:8px 1px;border-bottom:1px solid #292b31;font-size:12px;line-height:1.45}")
         .append(".line:last-child{border-bottom:0}")
         .append(".label{color:#969daa;flex-shrink:0}.pill{display:inline-block;padding:4px 8px;border-radius:8px;font-size:11px;font-weight:900;border:1px solid transparent}")
         .append(".red{background:#40191e;color:#ff777e;border-color:#77323a}")
         .append(".green{background:#123426;color:#55e39a;border-color:#235d40}")
         .append(".yellow{background:#3b2d13;color:#ffd166;border-color:#66501f}")
         .append(".empty{padding:22px 12px;text-align:center;color:#a0a6b0;border:1px dashed #383a40;border-radius:13px;background:#111317}")
         .append("</style></head><body>")
         .append("<div class='tabs'>")
         .append("<button id='b1' class='active' onclick='tab(1)'>")
         .append("تزويد شيفتات</button>")
         .append("<button id='b2' onclick='tab(2)'>فك بريك</button>")
         .append("</div><section id='s1'>");

        appendSheetRows(h, shifts, true);

        h.append("</section><section id='s2' style='display:none'>");
        appendSheetRows(h, breaks, false);

        h.append("</section><script>")
         .append("function tab(n){")
         .append("document.getElementById('s1').style.display=n===1?'block':'none';")
         .append("document.getElementById('s2').style.display=n===2?'block':'none';")
         .append("document.getElementById('b1').className=n===1?'active':'';")
         .append("document.getElementById('b2').className=n===2?'active':'';")
         .append("}</script></body></html>");

        return h.toString();
    }

    private void appendSheetRows(
            StringBuilder h, JSONArray rows, boolean isShift) {
        int shown = 0;

        // الصف الأول يحتوي أسماء الأعمدة.
        for (int i = 1; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null) continue;

            String timestamp = sheetCell(row, 0);
            String riderId = sheetCell(row, isShift ? 3 : 2);
            String reason = sheetCell(row, isShift ? 7 : 5);

            if (timestamp.isEmpty() && riderId.isEmpty()) continue;

            h.append("<article class='card'>");
            appendSheetField(h, "وقت الطلب", timestamp);
            appendSheetField(h, "Rider ID", riderId);

            if (isShift) {
                appendSheetField(h, "نوع الطلب", sheetCell(row, 1));
                appendSheetField(h, "ساعات التزويد", sheetCell(row, 2));
                appendSheetField(h, "المدينة", sheetCell(row, 4));
                appendSheetStatus(h, "حالة الطلب", sheetCell(row, 5));
                appendSheetStatus(h, "الرد على الطلب", sheetCell(row, 6));
            } else {
                appendSheetField(h, "نوع الطلب", sheetCell(row, 1));
                appendSheetStatus(h, "حالة الطلب", sheetCell(row, 3));
                appendSheetStatus(h, "القرار النهائي", sheetCell(row, 4));
            }

            if (!reason.isEmpty()) {
                appendSheetField(h, "سبب الرفض", reason);
            }

            h.append("</article>");
            shown++;
        }

        if (shown == 0) {
            h.append("<div class='empty'>لا توجد طلبات مسجلة حتى الآن.</div>");
        }
    }

    private void appendSheetField(
            StringBuilder h, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;

        h.append("<div class='line'><span class='label'>")
         .append(escapeSheetHtml(label))
         .append(":</span> ")
         .append(escapeSheetHtml(value))
         .append("</div>");
    }

    private void appendSheetStatus(
            StringBuilder h, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;

        String v = value.trim();
        String css = v.contains("مرفوض") ? "red"
            : (v.contains("مقبول") || v.contains("موافق")
               || v.contains("تم الرد")) ? "green" : "yellow";

        h.append("<div class='line'><span class='label'>")
         .append(escapeSheetHtml(label))
         .append(":</span> <span class='pill ")
         .append(css).append("'>")
         .append(escapeSheetHtml(v))
         .append("</span></div>");
    }

    private String sheetCell(JSONArray row, int index) {
        if (row == null || index < 0 || index >= row.length()
                || row.isNull(index)) {
            return "";
        }
        return row.optString(index, "").trim();
    }

    private String escapeSheetHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }

        if (!destroyed && pageLoaded && !detailVisible) {
            handler.removeCallbacks(refreshRunnable);
            handler.removeCallbacks(resumeRefreshRunnable);
            handler.postDelayed(resumeRefreshRunnable, 400);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(resumeRefreshRunnable);

        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    private void logoutApp() {
        detailVisible = false;
        authPrefs.edit().putBoolean("logged_in", false).apply();
        if (webView != null) webView.onPause();
        showLoginScreen(false);
    }

    private String firstValue(JSONObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) continue;
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return "غير متاح";
    }

    private void addAddress(LinearLayout parent, String label, String address) {
        TextView t = makeText(
            label + ": " + displayValue(address),
            11, MUTED, false
        );
        t.setPadding(0, dp(5), 0, 0);
        t.setMaxLines(4);
        parent.addView(t);
    }

    private String displayValue(String value) {
        if (value == null || value.trim().isEmpty()) return "غير متاح من المصدر";
        return value;
    }

    private String translateRiderStatus(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT);

        switch (s) {
            case "working":
            case "active":
                return "شغال";
            case "offline":
            case "inactive":
                return "غير متصل";
            case "break":
            case "on_break":
                return "استراحة";
            default:
                return displayValue(value);
        }
    }

    private String arabicStatus(String value) {
        if (value == null) return "غير معروف";

        switch (value.toLowerCase(Locale.ROOT)) {
            case "dispatched":
                return "تم إسناد الأوردر";
            case "courier_notified":
                return "تم إشعار المندوب";
            case "accepted":
                return "المندوب قبل الأوردر";
            case "near_pickup":
                return "وصل بالقرب من المطعم";
            case "at_pickup":
            case "arrived_at_pickup":
                return "وصل المطعم";
            case "picked_up":
                return "استلم الأوردر من المطعم";
            case "in_transit":
            case "on_the_way":
                return "في الطريق للعميل";
            case "at_dropoff":
            case "arrived_at_dropoff":
                return "وصل عند العميل";
            case "left_pickup":
                return "غادر المطعم وفي الطريق للعميل";
            case "near_dropoff":
                return "وصل بالقرب من العميل";
            case "completed":
                return "تم التسليم";
            case "cancelled":
                return "ملغي";
            default:
                return value.isEmpty() ? "غير معروف" : value;
        }
    }

    private void addEmptyCard(String heading, String message) {
        cards.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(PANEL, ORANGE, 1));

        TextView h = makeText(heading, 15, ORANGE, true);
        TextView m = makeText(message, 12, WHITE, false);
        m.setPadding(0, dp(8), 0, 0);

        card.addView(h);
        card.addView(m);
        cards.addView(card);
    }

    private String saveInspection(String report) throws Exception {
        String fileName = "roster-inspection-" +
            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + ".json";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/"
        );

        Uri uri = getContentResolver().insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        );

        if (uri == null) {
            throw new Exception("تعذر إنشاء التقرير");
        }

        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new Exception("تعذر فتح ملف التقرير");
            }

            out.write(report.getBytes(StandardCharsets.UTF_8));
            out.flush();

        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }

        return fileName;
    }

    @Override
    public void onBackPressed() {
        if (portalPageVisible || detailVisible) {
            returnToDashboard();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.removeJavascriptInterface("AlabtalBridge");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
