package com.alabtal.portaltest;

import android.widget.PopupWindow;
import android.accounts.Account;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
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
import androidx.core.content.FileProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.provider.MediaStore;
import android.provider.Settings;
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
import java.io.File;
import java.io.FileOutputStream;
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
    private LinearLayout movementAlertsPanel;
    private LinearLayout movementAlertsList;
    private TextView movementAlertsCount;
    private TextView topOrderAlertBanner;
    private PopupWindow topOrderAlertPopup;

    private final Runnable topOrderBannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (topOrderAlertPopup != null && topOrderAlertPopup.isShowing()) {
                topOrderAlertPopup.dismiss();
            }
            if (topOrderAlertBanner != null) {
                topOrderAlertBanner.setVisibility(View.GONE);
            }
        }
    };
    private TextView movementAlertsToggle;
    private boolean movementAlertsExpanded = false;
    private LinearLayout cards;
    private ScrollView riderCardsScroll;
    private WebView riderMapView;
    private WebView sheetView;
    private boolean mapTabActive = false;
    private boolean sheetTabActive = false;
    private boolean bookingMode = false;
    private boolean sheetAuthBookingMode = false;
    private Button inspectButton;
    private EditText riderSearch;
    private JSONArray currentRiders = new JSONArray();
    private LinearLayout followUpPanel, followUpList;
    private TextView followUpCount;
    private volatile JSONArray cachedBookingRows = new JSONArray();
    private volatile JSONArray cachedShiftRequestRows = new JSONArray();
    private volatile JSONArray cachedBreakRequestRows = new JSONArray();
    private String riderFilter = "الكل";
    private int riderLimit = 10;
    private boolean portalVisible = false;
    private boolean portalPageVisible = false;
    private LinearLayout portalRoot;
    private LinearLayout dashboardRoot;
    private boolean detailVisible = false;
    private SharedPreferences authPrefs;
    private SharedPreferences rosterCachePrefs;
    private volatile boolean updateCheckRunning = false;
    private File pendingUpdateFile;
    private boolean waitingForInstallPermission = false;

    private boolean inspectionRunning = false;
    private boolean pageLoaded = false;
    private boolean destroyed = false;
    private int consecutiveSyncFailures = 0;
    private long nextSyncDelayMs = 30000L;
    private static final long SYNC_INTERVAL_MS = 30000L;
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
        renderFollowUpAlerts(currentRiders, true);

        long now = System.currentTimeMillis();
        long lastAlert = rosterCachePrefs == null ? 0L
                : rosterCachePrefs.getLong("sync_failure_alert_at", 0L);
        if (consecutiveSyncFailures >= 3
                && now - lastAlert >= 30L * 60L * 1000L) {
            if (rosterCachePrefs != null) {
                rosterCachePrefs.edit()
                        .putLong("sync_failure_alert_at", now).apply();
            }
            publishOperationsAlert(
                    "تعذر تحديث الروستر 3 مرات متتالية؛ جارٍ إعادة الاتصال بالبوابة.");
        }

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
        ensureOperationsNotificationChannel();
        String savedUser = authPrefs.getString("username", null);
        boolean loggedIn = authPrefs.getBoolean("logged_in", false);
        if (savedUser == null) {
            showLoginScreen(true);
        } else if (loggedIn) {
            buildDashboard();
        } else {
            showLoginScreen(false);
        }

        // افحص التحديث بعد تجهيز شاشة الدخول أو لوحة التحكم.
        handler.postDelayed(() -> checkForAppUpdate(false), 2500L);
    }


    private void checkForAppUpdate(boolean manual) {
        if (updateCheckRunning) return;
        updateCheckRunning = true;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL api = new URL(
                    "https://api.github.com/repos/ramyzero126-sudo/ALABTAL-Portal-Test/releases/latest");
                connection = (HttpURLConnection) api.openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "ALABTAL-Portal-Test");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);

                if (connection.getResponseCode() != 200) {
                    throw new IOException("تعذر فحص آخر إصدار");
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
                }

                JSONObject release = new JSONObject(
                    new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                String tag = release.optString("tag_name", "");
                String[] tagParts = tag.split("\\.");
                int latestCode = Integer.parseInt(tagParts[tagParts.length - 1]);

                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset != null
                                && "app-debug.apk".equals(asset.optString("name"))) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (apkUrl == null || apkUrl.isEmpty()) {
                    throw new IOException("ملف التحديث غير موجود في الإصدار");
                }

                final String downloadUrl = apkUrl;
                final int remoteCode = latestCode;
                runOnUiThread(() -> {
                    if (remoteCode > BuildConfig.VERSION_CODE) {
                        new AlertDialog.Builder(this)
                            .setTitle("تحديث جديد متاح")
                            .setMessage("الإصدار الحالي: " + BuildConfig.VERSION_NAME
                                + "\nالإصدار الجديد: " + tag
                                + "\n\nهننزّل التحديث ونفتح شاشة التثبيت.")
                            .setNegativeButton("لاحقًا", (d, which) -> d.dismiss())
                            .setPositiveButton("تنزيل التحديث", (d, which) ->
                                downloadAndInstallUpdate(downloadUrl))
                            .show();
                    } else if (manual) {
                        Toast.makeText(this, "أنت تستخدم أحدث إصدار بالفعل",
                            Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) runOnUiThread(() ->
                    Toast.makeText(this, "تعذر فحص التحديث: تحقق من الإنترنت",
                        Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
                updateCheckRunning = false;
            }
        }).start();
    }

    private void downloadAndInstallUpdate(String downloadUrl) {
        Toast.makeText(this, "جاري تنزيل تحديث التطبيق...", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);

                if (connection.getResponseCode() != 200) {
                    throw new IOException("فشل تنزيل ملف التحديث");
                }

                File folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (folder == null) throw new IOException("مساحة التنزيل غير متاحة");
                if (!folder.exists() && !folder.mkdirs()) {
                    throw new IOException("تعذر إنشاء مجلد التنزيل");
                }

                File apk = new File(folder, "alabtal-update.apk");
                if (apk.exists()) apk.delete();

                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] chunk = new byte[16384];
                    int n;
                    while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
                    out.flush();
                }

                if (!apk.isFile() || apk.length() < 100000) {
                    throw new IOException("ملف التحديث غير مكتمل");
                }
                runOnUiThread(() -> installDownloadedUpdate(apk));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                    "فشل تنزيل التحديث. جرّب مرة أخرى مع اتصال إنترنت مستقر.",
                    Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void installDownloadedUpdate(File apk) {
        if (apk == null || !apk.isFile()) {
            Toast.makeText(this, "ملف التحديث غير موجود", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateFile = apk;
            waitingForInstallPermission = true;
            Intent settingsIntent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
            startActivity(settingsIntent);
            Toast.makeText(this,
                "اسمح للتطبيق بتثبيت التطبيقات من هذا المصدر ثم ارجع هنا",
                Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri,
                "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح مُثبّت التحديث",
                Toast.LENGTH_LONG).show();
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
                new int[]{Color.rgb(13, 17, 25), Color.rgb(5, 7, 11),
                          Color.rgb(12, 9, 7)}
        );
        root.setBackground(dashboardBg);
        root.setPadding(dp(8), dp(4), dp(8), dp(5));

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
                new int[]{Color.rgb(52, 24, 8), Color.rgb(8, 12, 20)}
        );
        brandBg.setCornerRadius(dp(12));
        brandBg.setStroke(dp(1), Color.rgb(255, 135, 25));
        brand.setBackground(brandBg);
        brand.setElevation(dp(7));

        TextView title = makeText("AL ABTAL", 17, ORANGE, true);
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
        liveBadge.setContentDescription("فحص تحديث التطبيق");
        liveBadge.setOnClickListener(v -> checkForAppUpdate(true));
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
                new int[]{Color.rgb(58, 25, 5), Color.rgb(8, 13, 22),
                          Color.rgb(58, 25, 5)}
        );
        ledBg.setCornerRadius(dp(8));
        ledBg.setStroke(dp(1), ORANGE);
        banner.setBackground(ledBg);

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(25));
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
        summary.setPadding(dp(7), dp(3), dp(7), dp(3));
        summary.setBackground(round(Color.rgb(10, 14, 21),
                Color.rgb(87, 48, 22), 1));
        root.addView(summary);

        TextView statsTitle = makeText(
                "◈  لوحة مؤشرات التشغيل", 11, ORANGE, true);
        statsTitle.setPadding(dp(3), dp(1), dp(3), dp(1));

        dashboardStats = new LinearLayout(this);
        dashboardStats.setOrientation(LinearLayout.VERTICAL);
        dashboardStats.setPadding(0, 0, 0, dp(1));
        dashboardStats.setVisibility(View.VISIBLE);
        statsTitle.setText("ملخص الشغل المباشر");
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
            chipBg.setCornerRadius(dp(10));
            chipBg.setStroke(dp(1), Color.rgb(185, 79, 19));
            chip.setBackground(chipBg);
            chip.setElevation(dp(4));
            chip.setTranslationZ(dp(1));
            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-2, dp(26));
            cp.setMargins(0, 0, dp(3), 0);
            filterRow.addView(chip, cp);
            chip.setOnClickListener(v -> {
                riderFilter = filter;
                renderRiderCards();
            });
        }
        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        topOrderAlertBanner = new TextView(this);
        topOrderAlertBanner.setTextColor(WHITE);
        topOrderAlertBanner.setTextSize(14);
        topOrderAlertBanner.setMaxLines(2);
        topOrderAlertBanner.setGravity(Gravity.CENTER);
        topOrderAlertBanner.setPadding(dp(16), dp(12), dp(16), dp(12));
        topOrderAlertBanner.setBackground(round(
                Color.rgb(25, 22, 18), ORANGE, 2));
        topOrderAlertBanner.setElevation(dp(10));
        topOrderAlertBanner.setVisibility(View.GONE);

        topOrderAlertPopup = new PopupWindow(
                topOrderAlertBanner, -1, -2, false);
        topOrderAlertPopup.setElevation(dp(12));
        topOrderAlertPopup.setClippingEnabled(true);

        // تبويبات مضغوطة: المناديب والخريطة
        LinearLayout mapTabs = new LinearLayout(this);
        mapTabs.setOrientation(LinearLayout.HORIZONTAL);
        mapTabs.setGravity(Gravity.CENTER_VERTICAL);
        mapTabs.setPadding(dp(4), dp(2), dp(2), dp(2));
        mapTabs.setBackground(round(
                Color.rgb(10, 14, 21), Color.rgb(43, 52, 65), 1));
        mapTabs.setElevation(dp(2));

        Button ridersTab = makeButton("☷ المناديب");
        Button mapTab = makeButton("📍 الخريطة");
        Button sheetTab = makeButton("📋 ردود الشيت");
        Button bookingsTab = makeButton("📅 الحجوزات");
        ridersTab.setTextSize(8);
        mapTab.setTextSize(8);
        sheetTab.setTextSize(8);
        bookingsTab.setTextSize(8);
        ridersTab.setBackground(round(Color.rgb(48, 27, 10), ORANGE, 1));
        mapTab.setBackground(round(Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
        sheetTab.setBackground(round(Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
        bookingsTab.setBackground(round(Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
        ridersTab.setElevation(dp(2));
        mapTab.setElevation(dp(2));
        sheetTab.setElevation(dp(2));
        bookingsTab.setElevation(dp(2));
        ridersTab.setTranslationZ(0);
        mapTab.setTranslationZ(0);
        sheetTab.setTranslationZ(0);
        bookingsTab.setTranslationZ(0);

        LinearLayout.LayoutParams tabParams =
                new LinearLayout.LayoutParams(0, dp(28), 1);
        tabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(ridersTab, tabParams);
        LinearLayout.LayoutParams mapTabParams =
                new LinearLayout.LayoutParams(0, dp(28), 1);
        mapTabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(mapTab, mapTabParams);
        LinearLayout.LayoutParams sheetTabParams =
                new LinearLayout.LayoutParams(0, dp(28), 1);
        sheetTabParams.setMargins(0, 0, dp(3), 0);
        mapTabs.addView(sheetTab, sheetTabParams);
        LinearLayout.LayoutParams bookingsParams =
                new LinearLayout.LayoutParams(0, dp(28), 1);
        mapTabs.addView(bookingsTab, bookingsParams);


        FrameLayout contentFrame = new FrameLayout(this);

        riderCardsScroll = new ScrollView(this);
        riderCardsScroll.setFillViewport(false);

        // الهيدر والكورة الأرضية والبانر ثابتون أعلى الشاشة.
        // الإحصائيات وكروت المندوبين داخل نفس منطقة التمرير.
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);

        // حاوية التمرير للتنبيهات والإحصائيات وكروت المندوبين.
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        movementAlertsPanel = new LinearLayout(this);
        movementAlertsPanel.setOrientation(LinearLayout.VERTICAL);
        movementAlertsPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        android.graphics.drawable.GradientDrawable alertBg =
                new android.graphics.drawable.GradientDrawable();
        alertBg.setColor(android.graphics.Color.rgb(18, 20, 24));
        alertBg.setCornerRadius(dp(12));
        alertBg.setStroke(dp(1), ORANGE);
        movementAlertsPanel.setBackground(alertBg);

        LinearLayout alertHeader = new LinearLayout(this);
        alertHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        alertHeader.setOrientation(LinearLayout.HORIZONTAL);

        TextView alertTitle = new TextView(this);
        alertTitle.setText("🔔 حركة الأوردرات والمناديب");
        alertTitle.setTextColor(WHITE);
        alertTitle.setTextSize(13);
        alertTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        alertHeader.addView(alertTitle,
                new LinearLayout.LayoutParams(0, -2, 1));

        movementAlertsCount = new TextView(this);
        movementAlertsCount.setTextColor(ORANGE);
        movementAlertsCount.setTextSize(12);
        movementAlertsCount.setPadding(dp(5), 0, dp(5), 0);
        alertHeader.addView(movementAlertsCount);

        movementAlertsToggle = new TextView(this);
        movementAlertsToggle.setText("عرض");
        movementAlertsToggle.setTextColor(ORANGE);
        movementAlertsToggle.setTextSize(12);
        movementAlertsToggle.setPadding(dp(8), dp(6), dp(4), dp(6));
        alertHeader.addView(movementAlertsToggle);

        TextView markRead = new TextView(this);
        markRead.setText("مقروء");
        markRead.setTextColor(WHITE);
        markRead.setTextSize(11);
        markRead.setPadding(dp(7), dp(6), dp(3), dp(6));
        alertHeader.addView(markRead);

        movementAlertsPanel.addView(alertHeader);
        movementAlertsList = new LinearLayout(this);
        movementAlertsList.setOrientation(LinearLayout.VERTICAL);
        movementAlertsList.setVisibility(View.GONE);
        movementAlertsPanel.addView(movementAlertsList);

        movementAlertsToggle.setOnClickListener(v -> {
            movementAlertsExpanded = !movementAlertsExpanded;
            renderMovementAlerts();
        });

        markRead.setOnClickListener(v -> {
            markMovementAlertsRead();
            renderMovementAlerts();
        });

        // لوحة الحالات التي تحتاج متابعة
        followUpPanel = new LinearLayout(this);
        followUpPanel.setOrientation(LinearLayout.VERTICAL);
        followUpPanel.setPadding(dp(8), dp(6), dp(8), dp(6));
        followUpPanel.setBackground(round(
                Color.rgb(16, 18, 22), ORANGE, 1));

        LinearLayout followUpHeader = new LinearLayout(this);
        followUpHeader.setGravity(Gravity.CENTER_VERTICAL);
        followUpHeader.setOrientation(LinearLayout.HORIZONTAL);

        TextView followUpTitle = makeText(
                "⚠ مين محتاج متابعة دلوقتي؟", 12, WHITE, true);
        followUpHeader.addView(followUpTitle,
                new LinearLayout.LayoutParams(0, -2, 1));

        followUpCount = makeText("في انتظار البيانات", 10, ORANGE, true);
        followUpHeader.addView(followUpCount);
        followUpPanel.addView(followUpHeader);

        followUpList = new LinearLayout(this);
        followUpList.setOrientation(LinearLayout.VERTICAL);
        TextView waiting = makeText(
                "في انتظار تحميل بيانات الروستر...", 10, MUTED, false);
        waiting.setPadding(dp(3), dp(5), dp(3), dp(2));
        followUpList.addView(waiting);
        followUpPanel.addView(followUpList);

        LinearLayout.LayoutParams followUpParams =
                new LinearLayout.LayoutParams(-1, -2);
        followUpParams.setMargins(0, dp(3), 0, dp(5));
        scrollContent.addView(followUpPanel, followUpParams);

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

        // شريط تنقّل سفلي ثابت، ومحتوى الداش يتمرر فوقه.
        root.addView(mapTabs,
                new LinearLayout.LayoutParams(-1, dp(50)));

        ridersTab.setOnClickListener(v -> {
            mapTabActive = false;
            sheetTabActive = false;
            riderCardsScroll.setVisibility(View.VISIBLE);
            riderMapView.setVisibility(View.GONE);
            sheetView.setVisibility(View.GONE);
            ridersTab.setTextColor(ORANGE);
            mapTab.setTextColor(WHITE);
            sheetTab.setTextColor(WHITE);
            bookingsTab.setTextColor(WHITE);
            ridersTab.setBackground(round(
                    Color.rgb(48, 27, 10), ORANGE, 1));
            mapTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            sheetTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            bookingsTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
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
            bookingsTab.setTextColor(WHITE);
            mapTab.setBackground(round(
                    Color.rgb(48, 27, 10), ORANGE, 1));
            ridersTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            sheetTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            bookingsTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
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
            bookingsTab.setBackground(round(
                    Color.rgb(48, 27, 10), ORANGE, 1));
            ridersTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            mapTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            sheetTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
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
            bookingsTab.setTextColor(WHITE);
            sheetTab.setBackground(round(
                    Color.rgb(48, 27, 10), ORANGE, 1));
            ridersTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            mapTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));
            bookingsTab.setBackground(round(
                    Color.rgb(16, 22, 31), Color.rgb(43, 53, 66), 1));

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
                new int[]{Color.rgb(27, 20, 17), Color.rgb(6, 10, 17)});
        tileBg.setCornerRadius(dp(10));
        tileBg.setStroke(dp(1), Color.rgb(115, 57, 27));
        box.setBackground(tileBg);
        box.setElevation(dp(4));

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
            "  const st = String(o.status || o.order_status || o.delivery_status || '').toLowerCase();" +
            "  const activeStage = /accepted|picked.?up|ongoing|in.?progress|assigned|preparing|ready|on.?the.?way|arrived|مقبول|استلم|جاري|في الطريق/.test(st);" +
            "  return (id !== '' && activeIds.includes(id)) || (activeStage && (activeIds.length === 0 || !activeIds.includes(id)));" +
            " }).map(o => ({" +
            "  delivery_id:String(o.delivery_id != null ? o.delivery_id :" +
            "   (o.order_id != null ? o.order_id :" +
            "   (o.id != null ? o.id :" +
            "   (o.deliveryId != null ? o.deliveryId : o.orderId))))," +
            "  order_code:o.order_code || o.code || o.reference || ''," +
            "  status:o.status || o.order_status || o.delivery_status || o.stage || ''," +
            "  vendor_name:o.vendor_name || o.restaurant_name || o.merchant_name || o.vendorName || o.restaurantName || (o.restaurant || {}).name || (o.vendor || {}).name || (o.merchant || {}).name || (o.store || {}).name || ''," +
            "  pickup_address:o.pickup_address || o.pickupAddress || o.pickup_location_address || (o.pickup || {}).address || (o.pickup_location || {}).address || (o.pickupLocation || {}).address || (o.origin || {}).address || (o.restaurant || {}).address || (o.vendor || {}).address || ''," +
            "  dropoff_address:o.dropoff_address || o.dropoffAddress || o.delivery_address || o.deliveryAddress || o.dropoff_location_address || (o.dropoff || {}).address || (o.dropoff_location || {}).address || (o.dropoffLocation || {}).address || (o.destination || {}).address || (o.customer || {}).address || ''," +
            "  timeline:o.timeline || o.events || o.status_history || []," +
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
            "  detail_error:detailError," +
            "  diagnostic_detail_keys:Object.keys(detail || {})," +
            "  diagnostic_delivery_info_keys:Object.keys((detail && detail.deliveries_info) || {})," +
            "  diagnostic_latest_count:Array.isArray(latest) ? latest.length : 0," +
            "  diagnostic_latest_samples:(Array.isArray(latest) ? latest : []).slice(0,10).map(o => ({keys:Object.keys(o || {}), ids:{delivery_id:o.delivery_id,order_id:o.order_id,id:o.id,deliveryId:o.deliveryId,orderId:o.orderId}, status:o.status || o.order_status || o.delivery_status || o.stage || '', vendor_fields:Object.keys(o || {}).filter(k => /vendor|restaurant|merchant|store/i.test(k)), vendor_values:{vendor_name:o.vendor_name,restaurant_name:o.restaurant_name,merchant_name:o.merchant_name,vendorName:o.vendorName,restaurantName:o.restaurantName,restaurant:(o.restaurant && o.restaurant.name) || '',vendor:(o.vendor && o.vendor.name) || '',merchant:(o.merchant && o.merchant.name) || '',store:(o.store && o.store.name) || ''}}))," +
            "  diagnostic_latest_statuses:(Array.isArray(latest) ? latest : []).slice(0,10).map(o => String((o && (o.status || o.order_status || o.delivery_status || o.stage)) || ''))" +
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
            updateTopOrderAlertBanner(riders);

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

            if (!fromCache) {
                notifySmartAlerts(riders);
                monitorRiderChanges(riders);
            }

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

            renderFollowUpAlerts(
                    riders, fromCache || consecutiveSyncFailures > 0);

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


    private void ensureOperationsNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                "alabtal_ops_alerts", "تنبيهات التشغيل",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("تنبيهات تشغيل AL ABTAL");
        manager.createNotificationChannel(channel);
    }

    private void publishOperationsAlert(String message) {
        if (message == null || message.trim().isEmpty()) return;
        try {
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            Intent intent = new Intent(this, MainActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pending = PendingIntent.getActivity(
                    this, 7301, intent, flags);

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "alabtal_ops_alerts")
                    : new Notification.Builder(this);

            Notification notification = builder
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("AL ABTAL • تنبيه تشغيل")
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build();

            manager.notify(7302, notification);
        } catch (Exception e) {
            android.util.Log.e("ALABTAL", "تعذر إرسال إشعار التشغيل", e);
        }
    }


    // يقارن التقرير الحي السابق بالحالي؛ لا يعتبر اختفاء المندوب تغيير حالة.

    private void recordMovementAlerts(java.util.ArrayList<String> messages) {
        if (messages == null || messages.isEmpty()
                || rosterCachePrefs == null) return;

        JSONArray saved;
        try {
            saved = new JSONArray(rosterCachePrefs.getString(
                    "movement_alerts_json", "[]"));
        } catch (Exception e) {
            saved = new JSONArray();
        }

        long now = System.currentTimeMillis();
        for (String message : messages) {
            if (message == null || message.trim().isEmpty()) continue;
            try {
                JSONObject event = new JSONObject();
                event.put("message", message);
                event.put("time", now);
                event.put("unread", true);
                saved.put(event);
            } catch (Exception ignored) {}
        }

        JSONArray limited = new JSONArray();
        int start = Math.max(0, saved.length() - 50);
        for (int i = start; i < saved.length(); i++) {
            limited.put(saved.opt(i));
        }
        rosterCachePrefs.edit()
                .putString("movement_alerts_json", limited.toString())
                .apply();
        renderMovementAlerts();
    }

    private void markMovementAlertsRead() {
        if (rosterCachePrefs == null) return;
        JSONArray saved;
        try {
            saved = new JSONArray(rosterCachePrefs.getString(
                    "movement_alerts_json", "[]"));
        } catch (Exception e) {
            return;
        }

        for (int i = 0; i < saved.length(); i++) {
            JSONObject event = saved.optJSONObject(i);
            if (event != null) {
                event.remove("unread");
                try { event.put("unread", false); }
                catch (Exception ignored) {}
            }
        }
        rosterCachePrefs.edit()
                .putString("movement_alerts_json", saved.toString())
                .apply();
    }

    private void renderMovementAlerts() {
        if (movementAlertsPanel == null || movementAlertsList == null
                || movementAlertsCount == null || rosterCachePrefs == null) {
            return;
        }

        JSONArray saved;
        try {
            saved = new JSONArray(rosterCachePrefs.getString(
                    "movement_alerts_json", "[]"));
        } catch (Exception e) {
            saved = new JSONArray();
        }

        int unread = 0;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject event = saved.optJSONObject(i);
            if (event != null && event.optBoolean("unread", false)) unread++;
        }

        movementAlertsCount.setText(unread > 0
                ? "جديد " + unread : "لا يوجد جديد");
        movementAlertsToggle.setText(movementAlertsExpanded ? "إخفاء" : "عرض");
        movementAlertsList.removeAllViews();
        movementAlertsList.setVisibility(
                movementAlertsExpanded ? View.VISIBLE : View.GONE);

        if (!movementAlertsExpanded) return;

        if (saved.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("لا توجد حركات مسجلة حتى الآن");
            empty.setTextColor(android.graphics.Color.LTGRAY);
            empty.setTextSize(12);
            empty.setPadding(0, dp(10), 0, dp(5));
            movementAlertsList.addView(empty);
            return;
        }

        int start = Math.max(0, saved.length() - 8);
        for (int i = saved.length() - 1; i >= start; i--) {
            JSONObject event = saved.optJSONObject(i);
            if (event == null) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(6), dp(7), dp(4), dp(7));

            TextView message = new TextView(this);
            message.setText(event.optString("message", "حركة تشغيل"));
            message.setTextColor(event.optBoolean("unread", false)
                    ? WHITE : android.graphics.Color.LTGRAY);
            message.setTextSize(12);
            row.addView(message);

            long time = event.optLong("time", 0L);
            if (time > 0L) {
                TextView stamp = new TextView(this);
                stamp.setText(new java.text.SimpleDateFormat(
                        "dd/MM  HH:mm", Locale.getDefault())
                        .format(new java.util.Date(time)));
                stamp.setTextColor(ORANGE);
                stamp.setTextSize(10);
                row.addView(stamp);
            }

            movementAlertsList.addView(row);
            if (i > start) {
                android.view.View line = new android.view.View(this);
                line.setBackgroundColor(android.graphics.Color.rgb(48, 49, 54));
                movementAlertsList.addView(line,
                        new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
    }


    private void addFollowUpItem(
            String message, int color, Runnable action) {
        TextView item = makeText(message, 11, color, true);
        item.setPadding(dp(8), dp(6), dp(8), dp(6));
        item.setMaxLines(2);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.setBackground(round(Color.rgb(25, 22, 20), color, 1));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(3), 0, 0);
        followUpList.addView(item, params);

        if (action != null) {
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> action.run());
        }
    }

    private void renderFollowUpAlerts(
            JSONArray riders, boolean staleData) {
        if (followUpPanel == null || followUpList == null
                || followUpCount == null) {
            return;
        }

        followUpList.removeAllViews();
        int count = 0;

        if (staleData) {
            String message = riders == null || riders.length() == 0
                    ? "تعذر التحديث؛ لا توجد بيانات حديثة متاحة"
                    : "⚠ فشل التحديث؛ البيانات المعروضة قديمة";

            addFollowUpItem(message, RED, () -> {
                if (inspectButton != null) inspectButton.performClick();
            });
            count++;
        }

        // الأوردرات التي تجاوزت 30 دقيقة حسب التوقيت المتاح.
        int orderAlerts = 0;
        long now = System.currentTimeMillis();

        if (riders != null) {
            for (int i = 0; i < riders.length() && count < 5; i++) {
                JSONObject rider = riders.optJSONObject(i);
                if (rider == null) continue;

                JSONArray orders = rider.optJSONArray("active_orders");
                if (orders == null) continue;

                String name = firstValue(
                        rider, "name", "rider_name", "employee_name");
                if (name.isEmpty() || "غير متاح".equals(name)) {
                    name = "مندوب";
                }

                for (int j = 0; j < orders.length()
                        && count < 5 && orderAlerts < 3; j++) {
                    JSONObject order = orders.optJSONObject(j);
                    if (order == null) continue;

                    String rawTime = firstValue(order,
                            "stage_started_at", "status_changed_at",
                            "status_updated_at", "picked_up_at", "pickedUpAt",
                            "accepted_at", "acceptedAt", "created_at",
                            "createdAt", "order_created_at",
                            "orderCreatedAt", "created_at_utc");

                    long started = parseTimestampMillis(rawTime);
                    if (started <= 0L || now < started) continue;

                    long minutes = (now - started) / 60000L;
                    if (minutes < 30L) continue;

                    String code = firstValue(order,
                            "order_code", "delivery_id", "order_id", "id");
                    if (code.isEmpty() || "غير متاح".equals(code)) {
                        code = "بدون كود";
                    }

                    final JSONObject targetRider = rider;
                    final JSONArray targetOrders = orders;

                    addFollowUpItem(
                            "⚠ أوردر " + code + " مع " + name
                                    + " • " + minutes
                                    + " دقيقة — اضغط للمراجعة",
                            RED,
                            () -> showRiderDetails(
                                    targetRider, targetOrders));

                    count++;
                    orderAlerts++;
                }
            }
        }

        // لا نعتبر عدم ظهور المندوب غيابًا مؤكدًا.
        // نعرض فقط الحالات التي تعذرت فيها مطابقة بيانات الشيت.
        JSONArray bookingRows = cachedBookingRows;
        int bookingAlerts = 0;

        if (bookingRows != null && bookingRows.length() > 0
                && riders != null) {
            for (int i = 0; i < riders.length()
                    && count < 5 && bookingAlerts < 2; i++) {
                JSONObject rider = riders.optJSONObject(i);
                if (rider == null || bookingStatusForRider(rider) >= 0) {
                    continue;
                }

                String name = firstValue(
                        rider, "name", "rider_name", "employee_name");
                if (name.isEmpty() || "غير متاح".equals(name)) {
                    name = "مندوب";
                }

                JSONArray orders = rider.optJSONArray("active_orders");
                final JSONArray targetOrders =
                        orders == null ? new JSONArray() : orders;
                final JSONObject targetRider = rider;

                addFollowUpItem(
                        "📅 تعذرت مطابقة حجز بكرة للمندوب "
                                + name + " — اضغط للمراجعة",
                        ORANGE,
                        () -> showRiderDetails(
                                targetRider, targetOrders));

                count++;
                bookingAlerts++;
            }
        }

        followUpCount.setText(count + (count == 1 ? " حالة" : " حالات"));

        if (count == 0) {
            addFollowUpItem(
                    "✓ لا توجد حالات تحتاج متابعة حاليًا",
                    GREEN, null);
        }
    }

    private void updateTopOrderAlertBanner(JSONArray riders) {
        if (topOrderAlertBanner == null || riders == null) return;

        long now = System.currentTimeMillis();

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            JSONArray orders = rider.optJSONArray("active_orders");
            if (orders == null) continue;

            String name = firstValue(
                    rider, "name", "rider_name", "employee_name");
            if (name.isEmpty()) name = "مندوب";

            for (int j = 0; j < orders.length(); j++) {
                JSONObject order = orders.optJSONObject(j);
                if (order == null) continue;

                String rawStage = firstValue(order,
                        "status", "stage", "order_status", "delivery_status")
                        .trim().toLowerCase(Locale.ROOT)
                        .replace('_', ' ').replace('-', ' ');

                boolean arrived =
                        rawStage.equals("at dropoff")
                        || rawStage.equals("arrived at dropoff")
                        || rawStage.equals("at customer")
                        || rawStage.equals("arrived at customer")
                        || rawStage.equals("reached customer")
                        || rawStage.contains("وصل عند العميل")
                        || rawStage.contains("وصل للعميل")
                        || rawStage.contains("وصل إلى العميل");

                if (!arrived) continue;

                String orderId = firstValue(order,
                        "delivery_id", "order_code", "order_id", "id");
                if (orderId.isEmpty()) {
                    orderId = firstValue(
                            rider, "employee_id", "id", "rider_id", "name");
                }
                if (orderId.isEmpty()) orderId = name;

                long arrivedAt = parseTimestampMillis(firstValue(order,
                        "arrived_at", "arrivedAt",
                        "arrived_at_customer", "arrivedAtCustomer",
                        "customer_arrived_at", "customerArrivedAt",
                        "reached_customer_at", "reachedCustomerAt",
                        "arrival_time", "arrivalTime",
                        "stage_started_at", "status_changed_at",
                        "status_updated_at"));

                String safeId = orderId.replaceAll(
                        "[^A-Za-z0-9_-]", "_");
                String marker = arrivedAt > 0
                        ? String.valueOf(arrivedAt) : "arrived";
                String prefKey = "top_order_arrival_seen_" + safeId;

                if (rosterCachePrefs != null) {
                    String previous = rosterCachePrefs.getString(
                            prefKey, "");
                    if (marker.equals(previous)) continue;

                    // لا تعرض حدث وصول قديم عند اكتشافه متأخرًا.
                    if (arrivedAt > 0 && now >= arrivedAt
                            && now - arrivedAt > 15L * 60L * 1000L) {
                        rosterCachePrefs.edit()
                                .putString(prefKey, marker).apply();
                        continue;
                    }

                    rosterCachePrefs.edit()
                            .putString(prefKey, marker).apply();
                }

                String elapsed = "";
                if (arrivedAt > 0 && now >= arrivedAt) {
                    long minutes = (now - arrivedAt) / 60000L;
                    if (minutes < 1) {
                        elapsed = " منذ أقل من دقيقة";
                    } else if (minutes < 60) {
                        elapsed = " منذ " + minutes + " دقيقة";
                    } else {
                        long hours = minutes / 60;
                        long remainder = minutes % 60;
                        elapsed = " منذ " + hours + " ساعة"
                                + (remainder > 0
                                    ? " و" + remainder + " دقيقة" : "");
                    }
                }

                topOrderAlertBanner.setText(
                        "🔔 " + name + " وصل عند العميل" + elapsed);
                topOrderAlertBanner.setVisibility(View.VISIBLE);

                handler.removeCallbacks(topOrderBannerRunnable);
                if (topOrderAlertPopup != null) {
                    if (topOrderAlertPopup.isShowing()) {
                        topOrderAlertPopup.dismiss();
                    }
                    int popupWidth = getResources().getDisplayMetrics().widthPixels
                            - dp(28);
                    topOrderAlertPopup.setWidth(popupWidth);
                    topOrderAlertPopup.showAtLocation(
                            getWindow().getDecorView(),
                            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                            0, dp(72));
                }
                handler.postDelayed(topOrderBannerRunnable, 10000L);
                return;
            }
        }
    }

    private void monitorRiderChanges(JSONArray riders) {
        if (riders == null || rosterCachePrefs == null) return;

        final String prefKey = "operations_last_snapshot";
        JSONObject previous;
        try {
            String raw = rosterCachePrefs.getString(prefKey, "");
            previous = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception e) {
            previous = new JSONObject();
        }

        JSONObject current = new JSONObject();
        java.util.ArrayList<String> alerts = new java.util.ArrayList<>();
        boolean hasBaseline = previous.length() > 0;

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            String id = firstValue(rider, "employee_id", "id", "rider_id");
            if (id.isEmpty()) id = firstValue(rider, "name");
            if (id.isEmpty()) continue;

            String state = firstValue(
                    rider, "rider_status", "status", "work_status")
                    .trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');

            JSONArray ids = rider.optJSONArray("active_delivery_ids");
            if (ids == null) ids = new JSONArray();
            java.util.ArrayList<String> orderIds = new java.util.ArrayList<>();

            for (int j = 0; j < ids.length(); j++) {
                Object item = ids.opt(j);
                String orderId = "";
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    orderId = firstValue(obj,
                            "delivery_id", "order_id", "id");
                } else if (item != null && item != JSONObject.NULL) {
                    orderId = String.valueOf(item).trim();
                }
                if (!orderId.isEmpty() && !orderIds.contains(orderId)) {
                    orderIds.add(orderId);
                }
            }

            // إذا غاب active_delivery_ids من المصدر، استخدم active_orders إن توفرت.
            if (!rider.has("active_delivery_ids")) {
                JSONArray orders = rider.optJSONArray("active_orders");
                if (orders != null) {
                    for (int j = 0; j < orders.length(); j++) {
                        JSONObject order = orders.optJSONObject(j);
                        if (order == null) continue;
                        String orderId = firstValue(order,
                                "delivery_id", "order_code", "order_id", "id");
                        if (!orderId.isEmpty() && !orderIds.contains(orderId)) {
                            orderIds.add(orderId);
                        }
                    }
                }
            }

            JSONArray activeOrdersForStage = rider.optJSONArray("active_orders");
            if (activeOrdersForStage != null) {
                for (int j = 0; j < activeOrdersForStage.length(); j++) {
                    JSONObject order = activeOrdersForStage.optJSONObject(j);
                    if (order == null) continue;
                    String orderId = firstValue(order,
                            "delivery_id", "order_code", "order_id", "id");
                    String stage = firstValue(order,
                            "status", "stage", "order_status", "delivery_status");
                    if (!orderId.isEmpty() && !stage.isEmpty()) {
                        orderIds.add(orderId + "@" + stage.trim()
                                .toLowerCase(Locale.ROOT)
                                .replace('-', '_').replace(' ', '_'));
                    }
                }
            }
            java.util.Collections.sort(orderIds);
            StringBuilder orderSignature = new StringBuilder();
            for (String orderId : orderIds) {
                if (orderSignature.length() > 0) orderSignature.append(",");
                orderSignature.append(orderId);
            }

            JSONObject item = new JSONObject();
            try {
                item.put("state", state);
                item.put("orders", orderSignature.toString());
                current.put(id, item);
            } catch (Exception ignored) {
                continue;
            }

            if (!hasBaseline) continue;

            JSONObject old = previous.optJSONObject(id);
            if (old == null) continue;

            String oldState = old.optString("state", "");
            if (!state.isEmpty() && !oldState.isEmpty()
                    && !state.equals(oldState)) {
                alerts.add(firstValue(rider, "name").isEmpty() ? id
                        : firstValue(rider, "name")
                        + ": تغيرت الحالة إلى " + state);
            }

            String oldOrders = old.optString("orders", "");
            String newOrders = orderSignature.toString();
            if (!oldOrders.equals(newOrders)) {
                String name = firstValue(rider, "name");
                if (name.isEmpty()) name = id;

                if (oldOrders.isEmpty() && !newOrders.isEmpty()) {
                    alerts.add(name + ": بدأ أوردر جديد");
                } else if (!oldOrders.isEmpty() && newOrders.isEmpty()) {
                    alerts.add(name + ": انتهت الأوردرات النشطة");
                } else if (!oldOrders.equals(newOrders)) {
                    alerts.add(name + ": تغيرت الأوردرات النشطة");
                }
            }
        }

        // أول تقرير يُستخدم كخط أساس فقط.
        rosterCachePrefs.edit().putString(prefKey, current.toString()).apply();

        recordMovementAlerts(alerts);

        if (!alerts.isEmpty()) {
            StringBuilder message = new StringBuilder();
            int shown = Math.min(5, alerts.size());
            for (int i = 0; i < shown; i++) {
                if (message.length() > 0) message.append("\n");
                message.append("• ").append(alerts.get(i));
            }
            if (alerts.size() > shown) {
                message.append("\nو ").append(alerts.size() - shown)
                        .append(" تغييرات أخرى");
            }
            publishOperationsAlert(message.toString());
        }
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
            publishOperationsAlert(message.toString());
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
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(5), dp(3), dp(5), dp(3));

        GradientDrawable panel = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(23, 30, 41), Color.rgb(7, 10, 16)});
        panel.setCornerRadius(dp(14));
        panel.setStroke(dp(1), Color.rgb(76, 49, 30));
        section.setBackground(panel);
        section.setElevation(dp(4));

        LinearLayout.LayoutParams sectionLp =
                new LinearLayout.LayoutParams(-1, -2);
        sectionLp.setMargins(0, dp(1), 0, dp(2));
        parent.addView(section, sectionLp);

        TextView title = makeText("◆  " + heading, 9, ORANGE, true);
        title.setPadding(dp(2), 0, dp(2), dp(2));
        title.setShadowLayer(dp(3), 0, 0, ORANGE_DEEP);
        section.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int count = Math.min(labels.length, Math.min(values.length, colors.length));
        for (int i = 0; i < count; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(2), dp(2), dp(2), dp(2));

            GradientDrawable tileBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(32, 40, 53), Color.rgb(8, 12, 19)});
            tileBg.setCornerRadius(dp(11));
            tileBg.setStroke(dp(1), colors[i]);
            tile.setBackground(tileBg);
            tile.setElevation(dp(4));

            View led = new View(this);
            GradientDrawable ledBg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.TRANSPARENT, colors[i], Color.TRANSPARENT});
            ledBg.setCornerRadius(dp(2));
            tile.addView(led, new LinearLayout.LayoutParams(-1, dp(2)));

            TextView value = makeText(values[i], 11, colors[i], true);
            value.setGravity(Gravity.CENTER);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            value.setShadowLayer(dp(3), 0, 0, colors[i]);
            LinearLayout.LayoutParams valueLp =
                    new LinearLayout.LayoutParams(-1, -2);
            valueLp.topMargin = dp(1);
            tile.addView(value, valueLp);

            TextView label = makeText(labels[i], 7, WHITE, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp =
                    new LinearLayout.LayoutParams(-1, -2);
            labelLp.topMargin = dp(1);
            tile.addView(label, labelLp);

            LinearLayout.LayoutParams tileLp =
                    new LinearLayout.LayoutParams(0, dp(39), 1f);
            tileLp.setMargins(dp(1), dp(1), dp(1), dp(1));
            row.addView(tile, tileLp);
        }
        section.addView(row);
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
                ".note{position:absolute;z-index:999;bottom:14px;left:12px;right:12px;" +
                "background:linear-gradient(135deg,#172332ee,#090d14f2);border:1px solid #ff8a22;border-radius:15px;" +
                "padding:12px;text-align:center;font:700 13px sans-serif;color:#fff;box-shadow:0 0 22px #ff790033}" +
                ".hud{position:absolute;z-index:999;top:12px;left:12px;right:12px;display:flex;justify-content:space-between;gap:8px;pointer-events:none}" +
                ".chip{background:#0b111be8;border:1px solid #ff8a2277;border-radius:11px;padding:8px 10px;color:#ffb46a;font:700 11px sans-serif}" +
                ".leaflet-popup-content-wrapper{background:linear-gradient(145deg,#1b2634,#0b1018);color:#fff;border:1px solid #ff7900;border-radius:13px}" +
                ".leaflet-popup-tip{background:#111b27}.leaflet-control-zoom a{background:#111923!important;color:#ff9b3d!important;border-color:#3a4655!important}</style></head><body>" +
                "<div id='map'></div><div class='hud'><div class='chip'>AL ABTAL • LIVE MAP</div><div id='count' class='chip'>GPS: —</div></div><div id='note' class='note'>جاري تجهيز الخريطة...</div>" +
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
                "const map=L.map('map',{zoomControl:false}).setView([30.7865,31.0004],12);" +
                "L.control.zoom({position:'bottomright'}).addTo(map);" +
                "const tiles=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'," +
                "{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);" +
                "tiles.on('tileerror',function(){const n=document.getElementById('note');" +
                "if(n)n.textContent='تحميل الخريطة متعثر؛ تحقق من الإنترنت ثم أعد فتح الخريطة.';});" +
                "setTimeout(function(){map.invalidateSize(true);},500);" +
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
        if (rider == null) return;
        if (orders == null) orders = new JSONArray();

        final JSONObject riderData = rider;
        final JSONArray riderOrders = orders;

        String name = firstValue(rider, "name", "employee_name", "rider_name");
        if (name.isEmpty()) name = "مندوب بدون اسم";
        String id = firstValue(rider, "employee_id", "id", "rider_id");
        String state = firstValue(rider, "rider_status", "state", "status", "work_status");
        String stateLabel = translateRiderStatus(state);
        String batch = firstValue(rider, "batch", "batch_number", "patch",
                "patch_number", "rider_batch");
        String wallet = firstValue(rider, "wallet_balance", "wallet",
                "walletBalance", "balance");
        String totalOrders = firstValue(rider, "total_orders",
                "orders_count", "totalOrders");
        String worked = firstValue(rider, "worked_time", "worked",
                "worked_hours", "worked_duration", "work_time");
        String breakTime = firstValue(rider, "break_time", "break_duration",
                "break_minutes", "breaks", "breakTime");

        String normalizedState = state == null ? ""
                : state.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');

        boolean onBreak = normalizedState.equals("break")
                || normalizedState.equals("on_break")
                || normalizedState.equals("onbreak")
                || "استراحة".equals(stateLabel);

        boolean working = normalizedState.equals("working")
                || normalizedState.equals("active")
                || normalizedState.equals("on_delivery")
                || normalizedState.equals("on_delivery_order")
                || "شغال".equals(stateLabel);

        JSONArray activeIds = rider.optJSONArray("active_delivery_ids");
        boolean hasOrders = orders.length() > 0
                || rider.optInt("active_count") > 0
                || rider.optBoolean("has_active_deliveries", false)
                || (activeIds != null && activeIds.length() > 0);

        String diagnosticStage = "";
        JSONArray diagnosticStatuses = rider.optJSONArray("diagnostic_latest_statuses");
        if (diagnosticStatuses != null) {
            for (int i = 0; i < diagnosticStatuses.length(); i++) {
                String candidate = diagnosticStatuses.optString(i, "").trim();
                String normalized = candidate.toLowerCase(Locale.ROOT)
                        .replace('_', ' ').replace('-', ' ');
                if (normalized.matches(".*(accepted|picked up|ongoing|in progress|assigned|preparing|ready|on the way|arrived|مقبول|استلم|جاري|في الطريق).*")) {
                    hasOrders = true;
                    diagnosticStage = candidate;
                    break;
                }
            }
        }

        int stateColor = onBreak ? RED
                : working ? GREEN : Color.rgb(255, 193, 7);

        // كارت مدمج بحد علوي مضيء وظل خفيف.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(5), dp(7), dp(5));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 29, 37), Color.rgb(10, 13, 19)});
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), Color.rgb(63, 53, 43));
        card.setBackground(bg);
        card.setElevation(dp(4));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(2), 0, dp(3));
        cards.addView(card, cardLp);

        View led = new View(this);
        GradientDrawable ledBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(95, 43, 12), ORANGE,
                        Color.rgb(255, 188, 82)});
        ledBg.setCornerRadius(dp(3));
        led.setBackground(ledBg);
        card.addView(led, new LinearLayout.LayoutParams(-1, dp(3)));

        // الهوية والحالة في سطر واحد.
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingLp =
                new LinearLayout.LayoutParams(-1, -2);
        headingLp.topMargin = dp(3);
        card.addView(heading, headingLp);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView nameView = makeText(name, 14, WHITE, true);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(nameView);

        if (batch.equalsIgnoreCase("غير متاح")
                || batch.equalsIgnoreCase("n/a")
                || batch.equalsIgnoreCase("null")
                || batch.equals("-") || batch.equals("—")) {
            batch = "";
        }

        LinearLayout idRow = new LinearLayout(this);
        idRow.setOrientation(LinearLayout.HORIZONTAL);
        idRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView idView = makeText(
                id.isEmpty() ? "🆔 —" : "🆔 " + id, 9, MUTED, false);
        idRow.addView(idView);

        if (!id.isEmpty()) {
            TextView copyIdButton = makeText("نسخ", 9, ORANGE, true);
            copyIdButton.setGravity(Gravity.CENTER);
            copyIdButton.setPadding(dp(7), dp(2), dp(7), dp(2));
            copyIdButton.setBackground(round(
                    Color.rgb(42, 29, 17), ORANGE, 1));
            LinearLayout.LayoutParams copyLp =
                    new LinearLayout.LayoutParams(-2, -2);
            copyLp.leftMargin = dp(6);
            idRow.addView(copyIdButton, copyLp);
            copyIdButton.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                    "Rider ID", id));
                    android.widget.Toast.makeText(
                            this, "تم نسخ رقم المندوب",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (!batch.isEmpty()) {
            TextView batchView = makeText("باتش " + batch, 9, MUTED, false);
            LinearLayout.LayoutParams batchLp =
                    new LinearLayout.LayoutParams(-2, -2);
            batchLp.leftMargin = dp(8);
            idRow.addView(batchView, batchLp);
        }
        identity.addView(idRow);
        heading.addView(identity);

        TextView stateBadge = makeText(
                (working ? "⚡ " : "● ") + stateLabel,
                10, stateColor, true);
        if (working) {
            stateBadge.setShadowLayer(dp(7), 0, 0, GREEN);
            ObjectAnimator boltGlow = ObjectAnimator.ofFloat(
                    stateBadge, "alpha", 0.68f, 1f);
            boltGlow.setDuration(700);
            boltGlow.setRepeatMode(ObjectAnimator.REVERSE);
            boltGlow.setRepeatCount(ObjectAnimator.INFINITE);
            boltGlow.start();
        }
        stateBadge.setGravity(Gravity.CENTER);
        stateBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable stateBg = new GradientDrawable();
        stateBg.setColor(Color.argb(24, Color.red(stateColor),
                Color.green(stateColor), Color.blue(stateColor)));
        stateBg.setCornerRadius(dp(10));
        stateBg.setStroke(dp(1), stateColor);
        stateBadge.setBackground(stateBg);
        heading.addView(stateBadge);

        // مؤشرات تشغيل مضغوطة.
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setWeightSum(4f);
        LinearLayout.LayoutParams metricsLp =
                new LinearLayout.LayoutParams(-1, -2);
        metricsLp.topMargin = dp(3);
        card.addView(metrics, metricsLp);

        addWalletMetricWithOrderStatus(
                metrics, displayValue(wallet), hasOrders, stage);
        addCompactMetric(metrics, "الأوردرات",
                totalOrders.isEmpty()
                        ? String.valueOf(orders.length()) : totalOrders);
        addCompactMetric(metrics, "وقت العمل",
                worked.isEmpty() ? "—" : worked);
        addCompactMetric(metrics, "البريك",
                breakTime.isEmpty() ? "—" : breakTime);

        // الشيفت في شريط واحد.
        String shiftStart = firstValue(rider, "shift_start", "shiftStart",
                "shift_start_time", "session_start", "planned_shift_start");
        String shiftEnd = firstValue(rider, "shift_end", "shiftEnd",
                "shift_end_time", "session_end", "planned_shift_end");

        LinearLayout shift = new LinearLayout(this);
        shift.setOrientation(LinearLayout.HORIZONTAL);
        shift.setGravity(Gravity.CENTER_VERTICAL);
        shift.setPadding(dp(7), dp(5), dp(7), dp(5));
        shift.setBackground(round(Color.rgb(17, 21, 28),
                Color.rgb(91, 57, 30), 1));

        TextView from = makeText("بداية  " + displayValue(shiftStart),
                9, ORANGE, true);
        from.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        shift.addView(from);

        TextView to = makeText("نهاية  " + displayValue(shiftEnd),
                9, ORANGE, true);
        to.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        to.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        shift.addView(to);

        LinearLayout.LayoutParams shiftLp =
                new LinearLayout.LayoutParams(-1, -2);
        shiftLp.topMargin = dp(5);
        card.addView(shift, shiftLp);

        addSmartAlerts(card, rider, orders, state);

        // إجراءات مختصرة.
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp =
                new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(5);
        card.addView(actions, actionsLp);

        addRiderAction(actions, rider, "☎", "اتصال", ORANGE);
        addRiderAction(actions, rider, "＋", "تزويد", GREEN);
        addRiderAction(actions, rider, "☕", "بريك", ORANGE);
        addRiderAction(actions, rider, "↗", "التفاصيل", ORANGE);

        // صفحة منفصلة لبيانات المندوب والأوردر والعناوين.
        View.OnClickListener openDetails = v ->
                showRiderDetails(riderData, riderOrders);
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            if (child instanceof LinearLayout) {
                View caption = ((LinearLayout) child).getChildAt(1);
                if (caption instanceof TextView
                        && "التفاصيل".contentEquals(((TextView) caption).getText())) {
                    child.setOnClickListener(openDetails);
                    child.setContentDescription("فتح صفحة تفاصيل المندوب والأوردر");
                }
            }
        }
    }

    // 1 = حاجز، 0 = مش حاجز، -1 = لا توجد بيانات كافية للعرض.
    private String normalizeBookingId(String value) {
        if (value == null) return "";
        return value.trim()
                .replace("#", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeBookingName(String value) {
        if (value == null) return "";
        String name = value.trim().replaceAll("\\s+", " ");
        name = name.replaceAll("(?i)\\s*_EL\\s*ABTAL.*$", "");
        name = name.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return name.toLowerCase(Locale.ROOT);
    }

    private int bookingStatusForRider(JSONObject rider) {
        JSONArray rows = cachedBookingRows;
        if (rows == null || rows.length() == 0 || rider == null) return -1;

        int headerIndex = findBookingDateHeaderRowIndex(rows);
        JSONArray header = headerIndex >= 0 ? rows.optJSONArray(headerIndex) : null;
        if (header == null) return -1;

        java.util.Calendar bookingCalendar = java.util.Calendar.getInstance();
        bookingCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        String targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(bookingCalendar.getTime());
        int dateColumn = -1;

        for (int c = 5; c < header.length(); c++) {
            if (targetDate.equals(bookingDateIso(sheetCell(header, c)))) {
                dateColumn = c;
                break;
            }
        }
        if (dateColumn < 0) return -1;

        String riderId = firstValue(
                rider, "employee_id", "id", "rider_id", "riderId").trim();
        String riderName = firstValue(
                rider, "name", "employee_name", "rider_name")
                .replaceAll("\\s+", " ").trim();

        JSONArray matched = null;

        if (!riderId.isEmpty()) {
            for (int i = 0; i < rows.length(); i++) {
                if (i == headerIndex) continue;
                JSONArray row = rows.optJSONArray(i);
                if (row != null && !normalizeBookingId(riderId).isEmpty()
                        && normalizeBookingId(riderId).equals(
                                normalizeBookingId(sheetCell(row, 0)))) {
                    matched = row;
                    break;
                }
            }
        }

        if (matched == null && !riderName.isEmpty()) {
            for (int i = 0; i < rows.length(); i++) {
                if (i == headerIndex) continue;
                JSONArray row = rows.optJSONArray(i);
                if (row == null) continue;

                String sheetName = sheetCell(row, 1);
                if (!normalizeBookingName(riderName).isEmpty()
                        && normalizeBookingName(riderName).equals(
                                normalizeBookingName(sheetName))) {
                    matched = row;
                    break;
                }
            }
        }

        if (matched == null) return -1;

        String value = sheetCell(matched, dateColumn)
                .trim().toLowerCase(Locale.ROOT);

        boolean notBooked = value.isEmpty()
                || value.contains("إجاز")
                || value.contains("اجاز")
                || value.contains("emptychoice")
                || value.contains("مش مختار")
                || value.contains("مطلوب يختار")
                || value.contains("غير مختار")
                || value.contains("مش حاجز")
                || value.contains("غير حاجز")
                || value.contains("لم يحجز")
                || value.contains("لم يتم الحجز")
                || value.contains("لا يوجد حجز")
                || value.equals("no")
                || value.equals("false")
                || value.equals("-");

        return notBooked ? 0 : 1;
    }

    private void addAnimatedBookingBadge(
            LinearLayout parent, boolean booked) {
        final int color = booked ? GREEN : RED;
        final String fullText = booked ? "✓ حاجز" : "✕ مش حاجز";

        TextView badge = makeText("", 11, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setShadowLayer(dp(5), 0, 0, color);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                booked
                        ? new int[]{Color.rgb(12, 42, 27), Color.rgb(14, 25, 20)}
                        : new int[]{Color.rgb(48, 15, 20), Color.rgb(25, 15, 18)});
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), color);
        badge.setBackground(background);
        badge.setElevation(dp(3));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        parent.addView(badge, params);

        final int[] position = {0};
        Runnable typing = new Runnable() {
            @Override public void run() {
                if (badge.getParent() == null) return;

                if (position[0] < fullText.length()) {
                    badge.setText(
                            fullText.substring(0, ++position[0]) + "▌");
                    handler.postDelayed(this, 85);
                } else {
                    badge.setText(fullText);
                    ObjectAnimator glow = ObjectAnimator.ofFloat(
                            badge, View.ALPHA, 0.72f, 1f);
                    glow.setDuration(650);
                    glow.setRepeatCount(1);
                    glow.setRepeatMode(ObjectAnimator.REVERSE);
                    glow.start();
                }
            }
        };
        handler.postDelayed(typing, 100);
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

    private void addWalletMetricWithOrderStatus(
            LinearLayout parent, String walletValue,
            boolean hasOrders, String rawStage) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(4), dp(3), dp(4));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 39, 53), Color.rgb(8, 13, 21)});
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), GREEN);
        item.setBackground(bg);
        item.setElevation(dp(5));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(item, lp);

        TextView label = makeText("المحفظة", 9, ORANGE, true);
        label.setGravity(Gravity.CENTER);
        item.addView(label);

        TextView value = makeText(walletValue, 12, WHITE, true);
        value.setGravity(Gravity.CENTER);
        value.setMaxLines(1);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(value);

        String status = hasOrders
                ? "🚚 " + (rawStage == null || rawStage.trim().isEmpty()
                    ? "معاه أوردر" : arabicStatus(rawStage))
                : "بدون أوردر";

        TextView orderState = makeText("", 8,
                hasOrders ? ORANGE : MUTED, true);
        orderState.setGravity(Gravity.CENTER);
        orderState.setSingleLine(true);
        orderState.setEllipsize(android.text.TextUtils.TruncateAt.END);
        orderState.setShadowLayer(dp(3), 0, 0,
                hasOrders ? ORANGE : Color.TRANSPARENT);
        item.addView(orderState);

        if (hasOrders) {
            final android.animation.ValueAnimator typing =
                    android.animation.ValueAnimator.ofInt(0, status.length());
            typing.setDuration(Math.max(700, status.length() * 65L));
            typing.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            typing.setRepeatMode(android.animation.ValueAnimator.RESTART);
            typing.addUpdateListener(a -> {
                int count = (Integer) a.getAnimatedValue();
                orderState.setText(status.substring(0,
                        Math.min(count, status.length())));
            });
            typing.start();
        } else {
            orderState.setText(status);
        }
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

        if (st.equals("dispatched") || st.equals("assigned")
                || st.equals("courier notified")) {
            stage = "📨 تم إسناد الأوردر";
            color = Color.rgb(255, 193, 7);
            background = Color.rgb(57, 44, 15);
        } else if (st.equals("accepted") || st.equals("accepted order")) {
            stage = "✓ المندوب قبل الأوردر";
            color = Color.rgb(255, 154, 67);
            background = Color.rgb(65, 39, 18);
        } else if (st.equals("completed") || st.equals("delivered")) {
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

    private boolean isBookingDateHeader(String header) {
        if (header == null) return false;
        return java.util.regex.Pattern.compile(
            "(?<!\\d)(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2})(?!\\d)"
        ).matcher(header.trim()).find();
    }

    private int findBookingDateHeaderRowIndex(JSONArray rows) {
        if (rows == null) return -1;

        int bestIndex = -1;
        int bestCount = 0;
        int limit = Math.min(rows.length(), 30);

        for (int r = 0; r < limit; r++) {
            JSONArray candidate = rows.optJSONArray(r);
            if (candidate == null) continue;

            int count = 0;
            for (int c = 5; c < candidate.length(); c++) {
                if (isBookingDateHeader(sheetCell(candidate, c).trim())) {
                    count++;
                }
            }

            if (count > bestCount) {
                bestCount = count;
                bestIndex = r;
            }
        }
        return bestIndex;
    }

    private void addSelectedShiftsToDetails(LinearLayout page, JSONObject rider) {
        JSONArray rows = cachedBookingRows;
        String riderId = firstValue(rider, "employee_id", "id", "rider_id").trim();
        String riderName = firstValue(rider, "name", "employee_name", "rider_name").trim();
        JSONArray match = null;

        int bookingHeaderIndex = findBookingDateHeaderRowIndex(rows);

        if (!riderId.isEmpty()) {
            for (int i = 1; i < rows.length(); i++) {
                if (i == bookingHeaderIndex) continue;
                JSONArray row = rows.optJSONArray(i);
                if (row == null) continue;
                String id = sheetCell(row, 0).trim();
                if (!id.isEmpty() && riderId.equalsIgnoreCase(id)) {
                    match = row;
                    break;
                }
            }
        }

        if (match == null && !riderName.isEmpty()) {
            for (int i = 1; i < rows.length(); i++) {
                if (i == bookingHeaderIndex) continue;
                JSONArray row = rows.optJSONArray(i);
                if (row == null) continue;
                String name = sheetCell(row, 1).trim();
                if (!name.isEmpty()
                        && !normalizeBookingName(riderName).isEmpty()
                        && normalizeBookingName(riderName).equals(
                                normalizeBookingName(name))) {
                    match = row;
                    break;
                }
            }
        }

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(9), dp(7), dp(9), dp(8));
        section.setBackground(round(PANEL, ORANGE, 1));

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, dp(5), 0, dp(5));
        page.addView(section, sectionParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = makeText("📅", 19, ORANGE, true);
        icon.setGravity(Gravity.CENTER);
        heading.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout headingText = new LinearLayout(this);
        headingText.setOrientation(LinearLayout.VERTICAL);
        headingText.addView(makeText("الشيفتات المختارة", 13, ORANGE, true));
        headingText.addView(makeText("حجوزات المندوب", 9, MUTED, false));
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1f));
        section.addView(heading);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(76, 49, 29));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(5), 0, dp(6));
        section.addView(divider, dividerParams);

        if (rows.length() == 0) {
            section.addView(makeText(
                    "افتح الحجوزات وحمّل الشيت أولًا.", 10, MUTED, false));
            return;
        }
        if (match == null) {
            section.addView(makeText(
                    "لم يتم العثور على المندوب في الحجوزات.", 10, MUTED, false));
            return;
        }

        JSONArray headerRow = bookingHeaderIndex >= 0
                ? rows.optJSONArray(bookingHeaderIndex) : null;
        int shown = 0;
        if (headerRow != null) {
            for (int c = 5; c < match.length() && c < headerRow.length(); c++) {
                String date = sheetCell(headerRow, c).trim();
                if (!isBookingDateHeader(date)) continue;

                String value = sheetCell(match, c).trim();
                if (value.isEmpty()) value = "مش مختار";
                addBookingShiftRow(section, "📅 " + date, value);
                shown++;
            }
        }
        if (shown == 0) {
            section.addView(makeText(
                    "لم يتم العثور على أعمدة تواريخ الشيفتات في صف العناوين.",
                    10, MUTED, false));
        }
    }


    private void addRiderSheetRequestsToDetails(
            LinearLayout page, JSONObject rider) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(9), dp(8), dp(9), dp(8));
        section.setBackground(round(PANEL, ORANGE, 1));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(6));
        page.addView(section, params);

        section.addView(makeText(
                "📋 طلبات التزويد والبريك", 13, ORANGE, true));

        String riderId = firstValue(
                rider, "employee_id", "id", "rider_id").trim();

        if (riderId.isEmpty()) {
            section.addView(makeText(
                    "معرّف المندوب غير متاح للمطابقة.",
                    10, MUTED, false));
            return;
        }

        int count = appendRiderRequests(
                section, cachedShiftRequestRows, riderId, true);
        count += appendRiderRequests(
                section, cachedBreakRequestRows, riderId, false);

        if (count == 0) {
            section.addView(makeText(
                    "لا توجد طلبات مطابقة في الشيتات المحمّلة.",
                    10, MUTED, false));
        }
    }

    private int appendRiderRequests(
            LinearLayout parent, JSONArray rows,
            String riderId, boolean isShift) {
        if (rows == null || rows.length() < 2) return 0;

        int idColumn = isShift ? 3 : 2;
        int shown = 0;
        boolean headingAdded = false;

        for (int i = rows.length() - 1; i >= 1 && shown < 5; i--) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null) continue;

            String id = sheetCell(row, idColumn);
            if (id.isEmpty() || !riderId.equalsIgnoreCase(id)) continue;

            if (!headingAdded) {
                parent.addView(makeText(
                        isShift ? "طلبات تزويد الشيفتات" : "طلبات فك البريك",
                        11, WHITE, true));
                headingAdded = true;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(7), dp(8), dp(7));
            card.setBackground(round(
                    Color.rgb(25, 27, 32),
                    Color.rgb(67, 47, 31), 1));

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, dp(4), 0, dp(5));
            parent.addView(card, cp);

            addRequestDetailLine(card, "وقت الطلب", sheetCell(row, 0), false);
            addRequestDetailLine(card, "نوع الطلب", sheetCell(row, 1), false);

            if (isShift) {
                addRequestDetailLine(card, "ساعات التزويد", sheetCell(row, 2), false);
                addRequestDetailLine(card, "المدينة", sheetCell(row, 4), false);
                addRequestDetailLine(card, "حالة الطلب", sheetCell(row, 5), true);
                addRequestDetailLine(card, "الرد على الطلب", sheetCell(row, 6), true);
                addRequestDetailLine(card, "سبب الرفض", sheetCell(row, 7), false);
            } else {
                addRequestDetailLine(card, "حالة الطلب", sheetCell(row, 3), true);
                addRequestDetailLine(card, "القرار النهائي", sheetCell(row, 4), true);
                addRequestDetailLine(card, "سبب الرفض", sheetCell(row, 5), false);
            }
            shown++;
        }
        return shown;
    }

    private void addRequestDetailLine(
            LinearLayout parent, String label,
            String value, boolean status) {
        if (value == null || value.trim().isEmpty()) return;

        String v = value.trim();
        int color = MUTED;

        if (status) {
            String lower = v.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("مرفوض") || lower.contains("رفض")) {
                color = Color.rgb(255, 75, 75);
            } else if (lower.contains("مقبول")
                    || lower.contains("موافق")
                    || lower.contains("معتمد")
                    || lower.contains("تمت الموافقة")
                    || lower.contains("تم القبول")
                    || lower.contains("مكتمل")) {
                color = GREEN;
            } else {
                color = Color.rgb(255, 195, 65);
            }
        }

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(makeText(label + ": ", 10, MUTED, false));

        TextView valueView = makeText(v, 10, color, status);
        line.addView(valueView,
                new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(line);
    }

    private void addBookingShiftRow(
            LinearLayout parent, String label, String rawValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setBackground(round(Color.rgb(25, 27, 32),
                Color.rgb(67, 47, 31), 1));

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(5));
        parent.addView(row, rowParams);

        row.addView(makeText(label, 11, ORANGE, true));

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile(
                "(?i)(\\d{1,2}[:.]\\d{2}\\s*(?:صباحًا|مساءً|صباحا|مساء|ص|م|AM|PM)?"
                + "|\\d{1,2}\\s*(?:AM|PM|صباحًا|مساءً|صباحا|مساء|ص|م))");
        java.util.regex.Matcher matcher = pattern.matcher(rawValue);
        java.util.ArrayList<String> times = new java.util.ArrayList<>();
        while (matcher.find()) times.add(matcher.group().trim());

        if (times.size() >= 2) {
            LinearLayout timeRow = new LinearLayout(this);
            timeRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView from = makeText(
                    "بداية\n" + times.get(0), 10, WHITE, true);
            from.setGravity(Gravity.CENTER);
            from.setPadding(dp(3), dp(5), dp(3), dp(5));
            from.setBackground(round(Color.rgb(43, 31, 22), ORANGE, 1));

            TextView to = makeText(
                    "نهاية\n" + times.get(1), 10, WHITE, true);
            to.setGravity(Gravity.CENTER);
            to.setPadding(dp(3), dp(5), dp(3), dp(5));
            to.setBackground(round(Color.rgb(43, 31, 22), ORANGE, 1));

            LinearLayout.LayoutParams timeParams =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            timeParams.setMargins(0, dp(5), dp(4), 0);
            timeRow.addView(from, timeParams);

            LinearLayout.LayoutParams endParams =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            endParams.setMargins(dp(4), dp(5), 0, 0);
            timeRow.addView(to, endParams);
            row.addView(timeRow);
        } else {
            row.addView(makeText(rawValue, 11, WHITE, false));
        }
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

        addSelectedShiftsToDetails(page, rider);
        addRiderSheetRequestsToDetails(page, rider);

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
    private static final String BOOKINGS_GOOGLE_ACCOUNT =
        "ramyzero120@gmail.com";
    private static final String RESPONSES_GOOGLE_ACCOUNT =
        "deltalogistics.elabtal@gmail.com";

    private void requestSheetAccess() {
        sheetAuthBookingMode = bookingMode;
        String targetEmail = sheetAuthBookingMode
            ? BOOKINGS_GOOGLE_ACCOUNT : RESPONSES_GOOGLE_ACCOUNT;

        showSheetMessage("جاري الاتصال بحساب Google: " + targetEmail);

        AuthorizationRequest request = AuthorizationRequest.builder()
            .setAccount(new Account(targetEmail, "com.google"))
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
                        loadPrivateSheets(token, sheetAuthBookingMode);
                    }
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ALABTAL_SHEETS",
                    "Google authorization failed for " + targetEmail, e);
                showSheetError("تعذر تفويض حساب " + targetEmail
                    + ". تأكد أن الحساب مضاف على الهاتف ومسموح له بالتطبيق.");
            });
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
                loadPrivateSheets(token, sheetAuthBookingMode);
            }
        } catch (ApiException e) {
            showSheetError("تعذر إكمال تفويض Google. حاول مرة أخرى.");
        }
    }

    private void loadPrivateSheets(String token, boolean forBookings) {
        if (forBookings) {
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

                cachedShiftRequestRows = shifts;
                cachedBreakRequestRows = breaks;

                String html = buildSheetHtml(shifts, breaks);
                runOnUiThread(() -> {
                    if (sheetView != null && sheetTabActive && !bookingMode) {
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
                    token, BOOKINGS_SHEET_ID, "'ان اصين المكتب'!A:ZZ"
                );
                cachedBookingRows = rows;
                String html = buildBookingsHtml(rows);
                runOnUiThread(() -> {
                    if (sheetView != null && bookingMode) {
                        sheetView.loadDataWithBaseURL(
                            "https://alabtal.local/",
                            html, "text/html", "UTF-8", null
                        );
                    }
                    if (currentRiders.length() > 0) renderRiderCards();
                    renderFollowUpAlerts(
                            currentRiders, consecutiveSyncFailures > 0);
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
         .append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
         .append("<style>")
         .append("*{box-sizing:border-box}")
         .append("body{margin:0;padding:9px;background:#08090c;color:#f5f6f8;font-family:Arial,sans-serif}")
         .append(".head{padding:12px;border:1px solid #70421f;border-right:4px solid #ff7900;border-radius:13px;background:linear-gradient(135deg,#302116,#15171c);margin-bottom:10px}")
         .append(".title{color:#ff9b3d;font-size:17px;font-weight:900}")
         .append(".sub{color:#a0a6b0;font-size:10px;margin-top:4px}")
         .append(".pick{display:flex;align-items:center;gap:8px;margin-top:11px}")
         .append(".pick label{font-size:12px;color:#ffb16d;font-weight:bold;white-space:nowrap}")
         .append(".pick input{min-width:0;flex:1;background:#101115;color:#fff;border:1px solid #70421f;border-radius:9px;padding:9px;font-size:13px;min-height:38px}")
         .append(".card{position:relative;padding:12px 11px 11px;margin-bottom:10px;border:1px solid #3a414d;border-right:5px solid #ff7900;border-radius:16px;background:linear-gradient(145deg,#222b38 0%,#121720 56%,#090c12 100%);box-shadow:0 7px 18px #000a;overflow:hidden}")
         .append(".person{display:flex;align-items:center;gap:8px;min-width:0}")
         .append(".num{flex:0 0 29px;width:29px;height:29px;display:flex;align-items:center;justify-content:center;border:1px solid #70421f;border-radius:9px;background:#2c1b10;color:#ffb16d;font-weight:bold;font-size:12px}")
         .append(".namebox{min-width:0;flex:1}.name{font-size:14px;font-weight:bold;overflow-wrap:anywhere}")
         .append(".id{font-size:10px;color:#9da3ae;margin-top:3px;overflow-wrap:anywhere}")
         .append(".dates{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-top:9px}")
         .append(".datebox{background:#15171b;border:1px solid #39343a;border-radius:8px;padding:7px;min-width:0}")
         .append(".label{font-size:10px;color:#9da3ae;display:block;margin-bottom:4px}")
         .append(".datevalue{font-size:11px;color:#ffb16d;font-weight:bold;overflow-wrap:anywhere}")
         .append(".status{margin-top:7px;border-radius:8px;padding:8px;text-align:center;font-size:12px;font-weight:bold;border:1px solid #6c5828;background:#302817;color:#ffd166}")
         .append(".status.booked{border-color:#315b43;background:#11271c;color:#45dc91}")
         .append(".status.vacation{border-color:#79343a;background:#301518;color:#ff626b}")
         .append(".empty{padding:18px;text-align:center;color:#a0a6b0;border:1px dashed #383a40;border-radius:12px}")
         .append(".legend{display:flex;gap:7px;flex-wrap:wrap;margin:8px 0 10px;font-size:10px;color:#c2c5cb}")
         .append(".legend span{padding:5px 7px;border-radius:7px;background:#15171b}")
         .append(".green{color:#45dc91}.red{color:#ff626b}.yellow{color:#ffd166}")
         .append("</style></head><body>")
         .append("<div class='head'><div class='title'>📦 حجوزات المناديب</div>")
         .append("<div class='sub'>ان اصين المكتب • متابعة حجز كل مندوب حسب التاريخ</div>")
         .append("<div class='pick'><label for='bookingDate'>📅 التاريخ</label>")
         .append("<input id='bookingDate' type='date' aria-label='اختيار تاريخ الحجز'></div></div>")
         .append("<div class='legend'><span class='green'>● معاه حجز</span><span class='red'>● إجازة</span><span class='yellow'>● مفيش اختيار</span></div>");

        int headerIndex = findBookingDateHeaderRowIndex(rows);
        JSONArray headerRow = headerIndex >= 0 ? rows.optJSONArray(headerIndex) : null;
        int shown = 0;

        for (int i = 1; i < rows.length(); i++) {
            if (i == headerIndex) continue;
            JSONArray row = rows.optJSONArray(i);
            if (row == null) continue;

            String id = sheetCell(row, 0).trim();
            String name = sheetCell(row, 1).trim();
            if (name.isEmpty()) continue;

            java.util.ArrayList<String> dates = new java.util.ArrayList<>();
            java.util.ArrayList<String> states = new java.util.ArrayList<>();
            String firstDate = "";
            String lastDate = "";

            if (headerRow != null) {
                for (int c = 5; c < headerRow.length(); c++) {
                    String date = sheetCell(headerRow, c).trim();
                    if (!isBookingDateHeader(date)) continue;
                    String iso = bookingDateIso(date);
                    if (iso.isEmpty()) continue;

                    String value = sheetCell(row, c).trim();
                    String lower = value.toLowerCase(java.util.Locale.ROOT);
                    boolean vacation = lower.contains("إجاز") || lower.contains("اجاز");
                    boolean emptyChoice = value.isEmpty()
                            || lower.contains("مش مختار")
                            || lower.contains("مطلوب يختار")
                            || lower.contains("غير مختار");
                    String state = vacation ? "vacation"
                            : (!emptyChoice ? "booked" : "none");

                    dates.add(iso);
                    states.add(state);
                    if ("booked".equals(state)) {
                        if (firstDate.isEmpty()) firstDate = date;
                        lastDate = date;
                    }
                }
            }

            h.append("<div class='card' data-booking-card='1'><div class='person'><div class='num'>")
             .append(shown + 1)
             .append("</div><div class='namebox'><div class='name'>")
             .append(escapeSheetHtml(name))
             .append("</div>");
            if (!id.isEmpty()) {
                h.append("<div class='id'>معرّف المندوب: ")
                 .append(escapeSheetHtml(id)).append("</div>");
            }
            h.append("</div></div><div class='dates'>")
             .append("<div class='datebox'><span class='label'>بداية الحجز</span><span class='datevalue'>")
             .append(firstDate.isEmpty() ? "—" : escapeSheetHtml(firstDate))
             .append("</span></div><div class='datebox'><span class='label'>نهاية الحجز</span><span class='datevalue'>")
             .append(lastDate.isEmpty() ? "—" : escapeSheetHtml(lastDate))
             .append("</span></div></div><div class='status' data-role='state'");

            for (int d = 0; d < dates.size(); d++) {
                h.append(" data-day-").append(dates.get(d)).append("='")
                 .append(states.get(d)).append("'");
            }
            h.append(">○ مفيش اختيار للتاريخ ده</div></div>");
            shown++;
        }

        if (shown == 0) {
            h.append("<div class='empty'>لا توجد أسماء في العمود B، أو أن الورقة فارغة.</div>");
        }

        h.append("<script>(function(){")
         .append("var input=document.getElementById('bookingDate');")
         .append("function today(){var d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}")
         .append("function update(){var day=input.value;document.querySelectorAll('[data-booking-card]').forEach(function(card){")
         .append("var st=card.querySelector('[data-role=state]');if(!st)return;var state=day?st.getAttribute('data-day-'+day):null;")
         .append("st.className='status '+(state||'none');")
         .append("st.textContent=state==='booked'?'✓ معاه حجز في التاريخ ده':state==='vacation'?'✕ إجازة في التاريخ ده':'○ مفيش اختيار للتاريخ ده';")
         .append("});}input.value=today();input.addEventListener('change',update);update();})();</script>")
         .append("</body></html>");
        return h.toString();
    }

    private String bookingDateIso(String value) {
        if (value == null) return "";
        String date = value.trim();
        String[] formats = {"M/d/yyyy", "MM/dd/yyyy", "d/M/yyyy",
                "dd/MM/yyyy", "yyyy-M-d", "yyyy-MM-dd", "d-M-yyyy", "dd-MM-yyyy"};
        for (String format : formats) {
            java.text.SimpleDateFormat parser =
                    new java.text.SimpleDateFormat(format, java.util.Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            try {
                java.util.Date parsed = parser.parse(date);
                if (parsed != null) {
                    java.text.SimpleDateFormat output =
                            new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                    output.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    return output.format(parsed);
                }
            } catch (java.text.ParseException ignored) {
            }
        }
        return "";
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
         .append(".card{position:relative;background:linear-gradient(145deg,#222b38 0%,#121720 58%,#090c12 100%);border:1px solid #394351;border-right:5px solid #ff7900;border-radius:16px;padding:13px 11px 11px;margin-bottom:11px;overflow-wrap:anywhere;box-shadow:0 8px 20px #000a;overflow:hidden}")
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

        if (waitingForInstallPermission) {
            waitingForInstallPermission = false;
            File apk = pendingUpdateFile;
            pendingUpdateFile = null;

            if (apk != null && apk.isFile()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().canRequestPackageInstalls()) {
                installDownloadedUpdate(apk);
            } else if (apk != null && apk.isFile()) {
                Toast.makeText(this,
                    "لم يتم تفعيل صلاحية تثبيت التطبيقات",
                    Toast.LENGTH_LONG).show();
            }
        }

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
        handler.removeCallbacks(topOrderBannerRunnable);
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
