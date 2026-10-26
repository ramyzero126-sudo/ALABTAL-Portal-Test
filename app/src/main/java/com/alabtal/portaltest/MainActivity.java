package com.alabtal.portaltest;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
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
import android.widget.EditText;
import android.provider.MediaStore;
import android.os.Environment;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(7, 11, 17);
    private static final int BG_2 = Color.rgb(14, 19, 25);
    private static final int PANEL = Color.rgb(19, 23, 30);
    private static final int PANEL_2 = Color.rgb(26, 31, 38);
    private static final int ORANGE = Color.rgb(255, 142, 68);
    private static final int ORANGE_SOFT = Color.rgb(255, 176, 98);
    private static final int GOLD = Color.rgb(255, 204, 120);
    private static final int ORANGE_DEEP = Color.rgb(130, 63, 20);
    private static final int WHITE = Color.rgb(245, 246, 248);
    private static final int MUTED = Color.rgb(160, 166, 176);
    private static final int GREEN = Color.rgb(45, 210, 125);
    private static final int RED = Color.rgb(255, 82, 82);
    private static final int CARD_BG = Color.rgb(22, 25, 31);
    private static final int CARD_INNER = Color.rgb(18, 19, 22);

    private static final String PORTAL_URL =
        "https://eg.me.logisticsbackoffice.com/dashboard/rooster/live-3pl";

    private static final String RIDERS_URL =
        "/api/rider-live-operations/v1/external/city/214/riders?page=0&size=10";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private TextView status;
    private TextView banner;
    private TextView summary;
    private LinearLayout dashboardStats;
    private LinearLayout cards;
    private Button inspectButton;
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

    private int bannerPosition = 0;
    private final String bannerMessage = "AL ABTAL  •  TALABAT  •  طنطا";

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            if (pageLoaded && !inspectionRunning) {
                inspectRoster();
            }

            handler.postDelayed(this, 30000);
        }
    };

    private final Runnable bannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || banner == null) return;

            bannerPosition++;
            if (bannerPosition > bannerMessage.length()) {
                bannerPosition = 0;
            }

            banner.setText(bannerMessage.substring(0, bannerPosition));
            handler.postDelayed(this, 130);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authPrefs = getSharedPreferences("alabtal_local_auth", MODE_PRIVATE);
        rosterCachePrefs = getSharedPreferences(
                "alabtal_roster_cache", MODE_PRIVATE);
        showLoginScreen(authPrefs.getString("username", null) == null);
    }

    private void buildDashboard() {
        detailVisible = false;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        dashboardRoot = new LinearLayout(this);
        LinearLayout root = dashboardRoot;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8), dp(3), dp(8), dp(3));

        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(7, 10, 15), Color.rgb(12, 17, 23), Color.rgb(8, 12, 17)}
        );
        root.setBackground(rootBg);

        LinearLayout portalRow = new LinearLayout(this);
        portalRow.setOrientation(LinearLayout.HORIZONTAL);
        portalRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        portalRow.setPadding(0, 0, 0, dp(2));

        Button portalButton = makeButton("🌐 البوابة");
        LinearLayout.LayoutParams portalParams =
                new LinearLayout.LayoutParams(-2, dp(34));
        portalParams.setMargins(0, 0, 0, dp(3));
        portalRow.addView(portalButton, portalParams);
        portalButton.setOnClickListener(v -> showPortalPage());
        root.addView(portalRow);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(dp(8), dp(2), dp(8), dp(3));

        TextView title = makeText("AL ABTAL", 24, ORANGE, true);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.10f);
        title.setShadowLayer(dp(5), 0, 0, ORANGE);
        brand.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView subtitle = makeText("L I V E   O P E R A T I O N S", 9, WHITE, true);
        subtitle.setGravity(Gravity.CENTER);
        brand.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(16)));

        GradientDrawable brandBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(70, 33, 8), Color.rgb(22, 24, 29), Color.rgb(8, 11, 17)}
        );
        brandBg.setCornerRadius(dp(20));
        brandBg.setStroke(dp(1), ORANGE);
        brand.setBackground(brandBg);
        brand.setElevation(dp(6));

        LinearLayout.LayoutParams brandParams =
                new LinearLayout.LayoutParams(-1, dp(58));
        brandParams.setMargins(0, dp(2), 0, dp(5));
        root.addView(brand, brandParams);

        banner = makeText("", 14, GOLD, true);
        banner.setGravity(Gravity.CENTER);
        banner.setSingleLine(true);
        banner.setLetterSpacing(0.025f);
        banner.setShadowLayer(dp(4), 0, 0, GOLD);
        banner.setPadding(dp(8), dp(4), dp(8), dp(4));

        GradientDrawable ledBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(45, 25, 12), Color.rgb(18, 20, 23), Color.rgb(45, 25, 12)}
        );
        ledBg.setCornerRadius(dp(16));
        ledBg.setStroke(dp(1), ORANGE);
        banner.setBackground(ledBg);

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(32));
        bp.setMargins(0, 0, 0, dp(5));
        root.addView(banner, bp);
        handler.post(bannerRunnable);

        summary = makeText(
            "في انتظار تحميل بيانات الروستر...",
            12, MUTED, false
        );
        summary.setTextSize(10);
        summary.setMaxLines(2);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setPadding(dp(12), dp(8), dp(12), dp(8));
        summary.setBackground(round(PANEL_2, ORANGE, 1));
        summary.setMinHeight(dp(42));
        root.addView(summary);

        TextView statsTitle = makeText(
                "⌄ ملخص التشغيل المباشر", 12, ORANGE_SOFT, true);
        statsTitle.setPadding(dp(8), dp(8), dp(8), dp(8));
        statsTitle.setBackgroundColor(Color.argb(30, 255, 142, 68));
        statsTitle.setMinHeight(dp(30));
        root.addView(statsTitle);

        dashboardStats = new LinearLayout(this);
        dashboardStats.setOrientation(LinearLayout.VERTICAL);
        dashboardStats.setVisibility(View.GONE);
        dashboardStats.setPadding(dp(8), dp(8), dp(8), dp(8));
        dashboardStats.setBackground(round(PANEL_2, ORANGE, 1));
        root.addView(dashboardStats);
        statsTitle.setOnClickListener(v -> {
            boolean show = dashboardStats.getVisibility() != View.VISIBLE;
            dashboardStats.setVisibility(show ? View.VISIBLE : View.GONE);
            statsTitle.setText(show ? "⌃ ملخص التشغيل المباشر" : "⌄ ملخص التشغيل المباشر");
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(2), 0, dp(2));

        status = makeText(
            "في انتظار بيانات الروستر...",
            10, ORANGE_SOFT, false
        );
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setBackground(round(PANEL, ORANGE, 1));
        controls.addView(
            status, new LinearLayout.LayoutParams(0, -2, 1)
        );

        inspectButton = makeButton("⟳");
        inspectButton.setTextSize(22);
        inspectButton.setPadding(0, 0, 0, 0);
        inspectButton.setContentDescription("تحديث البيانات");
        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(dp(42), dp(38));
        refreshParams.setMargins(dp(3), 0, 0, 0);
        controls.addView(inspectButton, refreshParams);

        root.addView(controls);

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
                pageLoaded = true;
                status.setText("تم تحميل صفحة البوابة؛ جاري التحقق من الجلسة...");

                handler.removeCallbacks(refreshRunnable);
                handler.post(refreshRunnable);
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
                });
            }
        }, "AlabtalBridge");

        root.addView(
            webView,
            new LinearLayout.LayoutParams(-1, dp(155))
        );

        TextView listTitle = makeText("المندوبون والأوردرات", 16, ORANGE_SOFT, true);
        listTitle.setTextSize(14);
        listTitle.setPadding(dp(8), dp(8), dp(8), dp(4));
        listTitle.setBackgroundColor(Color.argb(20, 255, 142, 68));
        root.addView(listTitle);

        ScrollView cardScroll = new ScrollView(this);
        cardScroll.setFillViewport(false);
        cardScroll.setBackgroundColor(BG_2);
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(dp(4), dp(4), dp(4), dp(8));
        cardScroll.addView(cards);

        root.addView(
            cardScroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        setContentView(root);

        inspectButton.setOnClickListener(v -> inspectRoster());

        addEmptyCard(
            "بانتظار البيانات",
            "جاري استعادة آخر بيانات محفوظة..."
        );

        restoreCachedReport();

        webView.loadUrl(PORTAL_URL);
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
        b.setElevation(dp(4));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ORANGE, Color.rgb(240, 125, 28)}
        );
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), GOLD);
        b.setBackground(d);
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
        box.setPadding(dp(8), dp(6), dp(8), dp(6));

        GradientDrawable tileBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(46, 28, 14), Color.rgb(18, 20, 23)}
        );
        tileBg.setCornerRadius(dp(14));
        tileBg.setStroke(dp(1), ORANGE_DEEP);
        box.setBackground(tileBg);
        box.setElevation(dp(2));

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

            Button syncButton = makeButton("⟳ تحديث");
            LinearLayout.LayoutParams syncParams =
                    new LinearLayout.LayoutParams(0, dp(46), 1);
            syncParams.setMargins(dp(6), 0, 0, 0);
            toolbar.addView(syncButton, syncParams);
            syncButton.setOnClickListener(v -> {
                if (pageLoaded) {
                    inspectRoster();
                } else {
                    status.setText("انتظر تحميل البوابة وتسجيل الدخول أولًا.");
                    if (webView != null) webView.reload();
                }
            });

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

        if (!pageLoaded) {
            openPortal();
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

    private void inspectRoster() {
        if (inspectionRunning || destroyed) return;

        if (webView == null || !pageLoaded) {
            status.setText("افتح البوابة وانتظر تحميلها أولًا.");
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
            " const response = await fetch(url, {" +
            "  credentials:'include'," +
            "  headers:{'Accept':'application/json'}" +
            " });" +
            " if (!response.ok) throw new Error('HTTP '+response.status+' - '+url);" +
            " return await response.json();" +
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
            "   const hasId = id && new RegExp('(?:^|\\D)' + id + '(?:\\D|$)').test(text);" +
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
            "   const hasId = id && new RegExp('(?:^|\\D)' + id + '(?:\\D|$)').test(cells.join(' '));" +
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
            " const perf = (detail && detail.performance && typeof detail.performance === 'object') ? detail.performance : ((r.performance_info && typeof r.performance_info === 'object') ? r.performance_info : {});" +
            " const timeSpent = (detail && detail.time_spent && typeof detail.time_spent === 'object') ? detail.time_spent : ((r.time_spent && typeof r.time_spent === 'object') ? r.time_spent : {});" +
            " const perfRaw = (typeof r.performance === 'number' || typeof r.performance === 'string') ? r.performance : (perf.utilization_rate ?? perf.score ?? perf.value ?? r.performance_score ?? r.avg_performance ?? r.performanceRate ?? r.performance_rate ?? (r.performance_info || {}).rate ?? '');" +
            " const acceptanceRaw = r.acceptance_rate ?? r.acceptance_percentage ?? r.acceptance ?? r.acceptanceRate ?? perf.acceptance_rate ?? (r.acceptance_info || {}).rate ?? (r.acceptance_info || {}).percentage ?? '';" +
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
            "  timeline:o.timeline || []" +
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
            "  shift_start:(r.shift_start || r.shiftStart || r.planned_shift_start || r.session_start || (r.shift_info || {}).start_time || (r.shift_info || {}).start || (r.shift || {}).start_time || (r.shift || {}).start || tableShift.start)," +
            "  shift_end:(r.shift_end || r.shiftEnd || r.planned_shift_end || r.session_end || (r.shift_info || {}).end_time || (r.shift_info || {}).end || (r.shift || {}).end_time || (r.shift || {}).end || tableShift.end)," +
            "  performance:perfValue || tableData.performance || ''," +
            "  acceptance_rate:acceptanceValue || tableData.acceptance_rate || ''," +
            "  worked_time:r.worked_time || r.worked_hours || r.worked || tableData.worked_time || ''," +
            "  vehicle:(typeof r.vehicle === 'string' ? r.vehicle : ((r.vehicle || {}).name || (r.vehicle || {}).type || (r.vehicle || {}).vehicle_type || r.vehicle_type || r.vehicleType || (r.vehicle || {}).vehicleName || '')))," +
            "  total_orders:(r.total_orders != null ? r.total_orders : (r.total_deliveries_count != null ? r.total_deliveries_count : (info.total_orders != null ? info.total_orders : (info.total_deliveries_count != null ? info.total_deliveries_count : (info.completed_deliveries_count != null ? info.completed_deliveries_count : tableData.total_orders))))," +
            "  total_orders_source:(r.total_orders != null || r.total_deliveries_count != null || info.total_orders != null || info.total_deliveries_count != null || info.completed_deliveries_count != null || tableData.total_orders > 0) ? 'source' : 'missing'," +
            "  worked_seconds:(timeSpent.worked_seconds != null && Number.isFinite(Number(timeSpent.worked_seconds))) ? Number(timeSpent.worked_seconds) : ((detail && detail.worked_seconds != null && Number.isFinite(Number(detail.worked_seconds))) ? Number(detail.worked_seconds) : (tableData.worked_seconds > -1 ? tableData.worked_seconds : -1))," +
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

            cards.removeAllViews();

            int ridersWithOrders = 0;
            int totalOrders = 0;

            for (int i = 0; i < riders.length(); i++) {
                JSONObject rider = riders.getJSONObject(i);
                JSONArray orders = rider.optJSONArray("active_orders");

                if (orders == null) orders = new JSONArray();

                boolean hasOrders = orders.length() > 0 || rider.optInt("active_count") > 0;
                if (hasOrders) ridersWithOrders++;
                totalOrders += rider.optInt("total_orders", orders.length());

                addRiderCard(rider, orders);
            }

            summary.setText(
                "المندوبون: " + riders.length() +
                "    •    لديهم أوردرات ظاهرة: " + ridersWithOrders +
                "    •    الأوردرات بالمصدر: " + totalOrders
            );

            renderDashboardStats(riders, ridersWithOrders, totalOrders);

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

                rosterCachePrefs.edit()
                    .putString("last_successful_report", report.toString())
                    .apply();
            }

        } catch (Exception e) {
            status.setText("تعذر عرض التقرير: " + e.getMessage());
            Toast.makeText(this, "فشل قراءة بيانات الروستر", Toast.LENGTH_LONG).show();
        }
    }

    private void renderDashboardStats(
            JSONArray riders, int ridersWithOrders, int totalOrders) {
        if (dashboardStats == null) return;
        dashboardStats.removeAllViews();

        int working = 0;
        int onBreak = 0;
        int ready = 0;
        int late = 0;
        long totalWorkedSeconds = 0;
        int ridersWithWorkTime = 0;

        for (int i = 0; i < riders.length(); i++) {
            JSONObject rider = riders.optJSONObject(i);
            if (rider == null) continue;

            long workedSeconds = rider.optLong("worked_seconds", -1);
            if (workedSeconds >= 0) {
                totalWorkedSeconds += workedSeconds;
                ridersWithWorkTime++;
            }

            String state = rider.optString(
                    "rider_status", rider.optString("status", ""))
                    .trim().toLowerCase(Locale.ROOT);

            if (state.contains("break")) {
                onBreak++;
            } else if (state.contains("late")) {
                late++;
            } else if (state.contains("working")
                    || state.contains("on_delivery")
                    || state.contains("on delivery")) {
                working++;
            } else if (state.contains("ready")
                    || state.contains("available")
                    || state.contains("idle")) {
                ready++;
            }
        }

        addTileRow(dashboardStats,
                "👥 إجمالي المندوبين", String.valueOf(riders.length()),
                "📦 معاهم أوردر", String.valueOf(ridersWithOrders));

        addTileRow(dashboardStats,
                "🧾 الأوردرات بالمصدر", String.valueOf(totalOrders),
                "⭕ بدون أوردر ظاهر",
                String.valueOf(Math.max(0, riders.length() - ridersWithOrders)));

        addTileRow(dashboardStats,
                "🟢 شغال", String.valueOf(working),
                "☕ بريك", String.valueOf(onBreak));

        addTileRow(dashboardStats,
                "🟠 جاهز / متاح", String.valueOf(ready),
                "⏰ متأخر", String.valueOf(late));

        addTileRow(dashboardStats,
                "⏱️ إجمالي ساعات العمل اليوم",
                ridersWithWorkTime > 0
                    ? formatWorkDuration(totalWorkedSeconds)
                    : "غير متاح من المصدر",
                "👤 مندوبون ببيانات وقت العمل",
                String.valueOf(ridersWithWorkTime));
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

    private void addRiderCard(JSONObject rider, JSONArray orders) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(12));

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(24, 27, 33), Color.rgb(14, 16, 21)}
        );
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), ORANGE_DEEP);
        card.setBackground(cardBg);
        card.setElevation(dp(9));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(dp(4), dp(6), dp(4), dp(8));
        cards.addView(card, cardParams);

        String name = firstValue(
                rider, "name", "employee_name", "rider_name");
        if (name.equals("غير متاح")) name = "مندوب بدون اسم";

        String id = firstValue(
                rider, "employee_id", "id", "rider_id");

        String wallet = firstValue(
                rider, "wallet_balance", "wallet", "walletBalance",
                "balance");

        String totalOrders = firstValue(
                rider, "total_orders", "orders_count", "totalOrders");

        String performance = firstValue(
                rider, "performance", "performance_score");

        String workTime = firstValue(
                rider, "worked_time", "worked_hours", "worked",
                "worked_duration", "work_time");

        String rawState = firstValue(
                rider, "rider_status", "state", "status", "work_status");

        String state = translateRiderStatus(rawState);
        if (state.equals("غير متاح")) state = "غير محدد";

        boolean hasOrders = orders != null && orders.length() > 0;

        int stateColor = GREEN;
        String lowerState = rawState.toLowerCase(Locale.ROOT);
        if (lowerState.contains("break")) {
            stateColor = ORANGE;
        } else if (lowerState.contains("offline")
                || lowerState.contains("inactive")) {
            stateColor = RED;
        }

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, 0, 0, dp(6));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setLayoutParams(
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView nameView = makeText(name, 14, WHITE, true);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(nameView);

        TextView idView = makeText(
                id.equals("غير متاح") ? "مندوب" : "ID: " + id,
                10, MUTED, false);
        idView.setMaxLines(1);
        identity.addView(idView);

        heading.addView(identity);

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.argb(60, 69, 210, 125));
        pillBg.setCornerRadius(dp(18));
        pillBg.setStroke(dp(1), stateColor);
        statusPill.setBackground(pillBg);

        TextView statusText = makeText(
                "● " + state, 10, stateColor, true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setMaxLines(1);
        statusPill.addView(statusText);
        heading.addView(statusPill);

        card.addView(heading);

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        mainRow.setGravity(Gravity.CENTER_VERTICAL);
        mainRow.setPadding(0, dp(2), 0, dp(8));

        TextView orderBadge = makeText(
                hasOrders ? "● معاه أوردر" : "○ بدون أوردر",
                10, hasOrders ? GREEN : MUTED, true);
        mainRow.addView(orderBadge);

        TextView separator = makeText("  •  ", 10, MUTED, false);
        mainRow.addView(separator);

        TextView zoneText = makeText(
                "منطقة: " + displayValue(firstValue(rider, "zone", "area", "city")),
                10, ORANGE_SOFT, false);
        mainRow.addView(zoneText);

        card.addView(mainRow);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setPadding(0, dp(2), 0, dp(8));

        String orderValue = totalOrders;
        if (orderValue.equals("غير متاح")) {
            orderValue = String.valueOf(
                    orders == null ? 0 : orders.length());
        }

        addCompactMetric(metrics, "المحفظة",
                wallet.equals("غير متاح") ? "—" : wallet);
        addCompactMetric(metrics, "الأوردرات", orderValue);
        addCompactMetric(metrics, "الأداء",
                performance.equals("غير متاح") ? "—" : performance);
        addCompactMetric(metrics, "وقت العمل",
                workTime.equals("غير متاح") ? "—" : workTime);

        card.addView(metrics);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(4), dp(4), dp(4), dp(4));
        details.setBackground(round(Color.rgb(15, 18, 23), ORANGE, 1));
        card.addView(details);

        addDetailChip(details, "الحالة", state);
        addDetailChip(details, "وقت البريك",
                displayValue(firstValue(rider, "break_time", "break_duration",
                        "break_minutes", "breaks", "breakTime")));
        addDetailChip(details, "المنطقة", displayValue(firstValue(rider, "zone", "area", "city")));
        addDetailChip(details, "الأداء", performance.equals("غير متاح") ? "—" : performance);
        addDetailChip(details, "نسبة القبول",
                firstValue(rider, "acceptance_rate", "acceptance", "acceptanceRate"));
        addDetailChip(details, "بداية الشيفت",
                firstValue(rider, "shift_start", "shiftStart", "shift_start_time", "session_start"));
        addDetailChip(details, "نهاية الشيفت",
                firstValue(rider, "shift_end", "shiftEnd", "shift_end_time", "session_end"));

        if (hasOrders) {
            TextView activeOrdersTitle = makeText("أوردرات نشطة", 10, ORANGE_SOFT, true);
            activeOrdersTitle.setPadding(dp(4), dp(8), dp(4), dp(4));
            details.addView(activeOrdersTitle);

            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order == null) continue;

                LinearLayout orderRow = new LinearLayout(this);
                orderRow.setOrientation(LinearLayout.HORIZONTAL);
                orderRow.setGravity(Gravity.CENTER_VERTICAL);
                orderRow.setPadding(dp(4), dp(4), dp(4), dp(4));
                orderRow.setBackground(round(Color.rgb(20, 22, 27), ORANGE, 1));

                TextView orderNum = makeText("#" + (i + 1), 10, ORANGE, true);
                orderNum.setMinWidth(dp(38));
                orderRow.addView(orderNum);

                TextView orderInfo = makeText(
                        firstValue(order, "order_code", "order_id", "id"),
                        10, WHITE, false);
                orderInfo.setMaxLines(1);
                orderInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams infoParams =
                        new LinearLayout.LayoutParams(0, -2, 1);
                infoParams.setMargins(dp(6), 0, dp(6), 0);
                orderRow.addView(orderInfo, infoParams);

                TextView orderStatus = makeText(
                        arabicStatus(firstValue(order, "status", "order_status", "stage")),
                        9, GREEN, true);
                orderRow.addView(orderStatus);

                details.addView(orderRow);
            }
        } else {
            TextView noOrder = makeText(
                    "لا يوجد أوردر ظاهر لهذا المندوب",
                    10, MUTED, false);
            noOrder.setPadding(dp(4), dp(8), dp(4), dp(4));
            details.addView(noOrder);
        }
    }

    private void addDetailChip(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(5), dp(6), dp(5));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView key = makeText(label + ": ", 10, MUTED, false);
        key.setMinWidth(dp(82));
        row.addView(key);

        TextView val = makeText(value == null || value.trim().isEmpty() ? "—" : value, 10, WHITE, true);
        val.setMaxLines(1);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(val);

        parent.addView(row);
    }

    private void addCompactMetric(
            LinearLayout parent, String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setMinimumHeight(dp(49));
        item.setPadding(dp(4), dp(5), dp(4), dp(5));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(24, 27, 31), Color.rgb(16, 18, 23)}
        );
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), ORANGE_DEEP);
        item.setBackground(bg);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(item, lp);

        TextView title = makeText(label, 9, MUTED, false);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView val = makeText(
                value == null || value.trim().isEmpty()
                        ? "—" : value,
                11, ORANGE_SOFT, true);
        val.setGravity(Gravity.CENTER);
        val.setMaxLines(1);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(val, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addCompactDetail(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView key = makeText(label, 11, Color.LTGRAY, false);
        key.setLayoutParams(new LinearLayout.LayoutParams(dp(94), -2));
        row.addView(key);

        TextView val = makeText(value, 11, Color.WHITE, false);
        val.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(val);
        parent.addView(row);
    }

    private void addOrderProgressTrack(LinearLayout target, String rawStatus) {
        String st = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);

        int active = -1;
        if (st.equals("dispatched") || st.equals("courier_notified")
                || st.equals("accepted") || st.equals("assigned")) {
            active = 0;
        } else if (st.equals("near_pickup") || st.equals("at_pickup")
                || st.equals("arrived_at_pickup") || st.equals("picked_up")
                || st.equals("left_pickup")) {
            active = 1;
        } else if (st.equals("in_transit") || st.equals("on_the_way")
                || st.equals("near_dropoff")) {
            active = 2;
        } else if (st.equals("at_dropoff") || st.equals("arrived_at_dropoff")) {
            active = 3;
        }

        if (st.equals("completed")) {
            TextView done = makeText("✓ تم تسليم الأوردر", 11, GREEN, true);
            done.setPadding(dp(3), dp(8), dp(3), dp(2));
            target.addView(done);
            return;
        }
        if (st.equals("cancelled")) {
            TextView cancelled = makeText("✕ الأوردر ملغي", 11, RED, true);
            cancelled.setPadding(dp(3), dp(8), dp(3), dp(2));
            target.addView(cancelled);
            return;
        }

        String[] labels = {
            "معاه أوردر", "وصل المطعم",
            "في الطريق للعميل", "وصل عند العميل"
        };

        TextView heading = makeText("مسار الأوردر  /  " + arabicStatus(rawStatus),
                11, ORANGE, true);
        heading.setPadding(dp(2), dp(8), dp(2), dp(5));
        target.addView(heading);

        FrameLayout trackContainer = new FrameLayout(this);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < labels.length; i++) {
            LinearLayout station = new LinearLayout(this);
            station.setOrientation(LinearLayout.VERTICAL);
            station.setGravity(Gravity.CENTER);
            station.setPadding(dp(2), dp(22), dp(2), dp(5));

            boolean reached = active >= i;
            int led = reached ? ORANGE : Color.rgb(72, 53, 47);

            TextView dot = makeText(
                    reached ? "●" : "○",
                    12, led, true);
            dot.setGravity(Gravity.CENTER);

            TextView label = makeText(labels[i], 8,
                    reached ? WHITE : MUTED, reached);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);

            station.addView(dot);
            station.addView(label);

            LinearLayout.LayoutParams sp =
                    new LinearLayout.LayoutParams(0, -2, 1);
            track.addView(station, sp);
        }

        trackContainer.addView(track, new FrameLayout.LayoutParams(-1, -2));

        TextView bike = makeText("🏍️", 17, ORANGE, true);
        bike.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams bikeParams = new FrameLayout.LayoutParams(
                dp(27), dp(24), Gravity.TOP | Gravity.LEFT);
        trackContainer.addView(bike, bikeParams);

        LinearLayout.LayoutParams tp =
                new LinearLayout.LayoutParams(-1, -2);
        tp.setMargins(0, 0, 0, dp(3));
        target.addView(trackContainer, tp);

        final int targetStage = active;
        trackContainer.post(() -> {
            if (trackContainer.getWidth() <= dp(27)) return;

            float maxTravel = trackContainer.getWidth() - dp(27);
            float destination = maxTravel *
                    (Math.max(0, Math.min(3, targetStage)) / 3.0f);

            bike.setTranslationX(0f);
            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    bike, View.TRANSLATION_X, 0f, destination);
            animator.setDuration(1100);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.start();
        });
    }

    private void showRiderDetails(JSONObject rider, JSONArray orders) {
        detailVisible = true;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(14), dp(14), dp(20));
        page.setBackgroundColor(BG);
        scroll.addView(page);

        Button back = makeButton("← رجوع إلى المندوبين");
        back.setOnClickListener(v -> returnToDashboard());
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView title = makeText("تفاصيل المندوب", 22, ORANGE, true);
        title.setPadding(0, dp(14), 0, dp(8));
        page.addView(title);
        TextView name = makeText("👤 " + rider.optString("name", "مندوب بدون اسم"), 19, WHITE, true);
        page.addView(name);
        TextView id = makeText("رقم المندوب: " + rider.optString("employee_id", "غير متاح"), 12, MUTED, false);
        page.addView(id);
        TextView state = makeText("الحالة: " + translateRiderStatus(rider.optString("rider_status", "")), 13, GREEN, true);
        state.setPadding(0, dp(6), 0, dp(10));
        page.addView(state);

        addDetailLine(page, "المحفظة", firstValue(rider, "wallet_balance", "wallet", "balance"));
        addDetailLine(page, "بداية الشيفت", firstValue(rider, "shift_start", "planned_shift_start", "session_start"));
        addDetailLine(page, "نهاية الشيفت", firstValue(rider, "shift_end", "planned_shift_end", "session_end"));
        addDetailLine(page, "عدد الأوردرات", firstValue(rider, "total_orders"));
        addDetailLine(page, "الأداء", firstValue(rider, "performance", "performance_score"));
        addDetailLine(page, "نسبة القبول", firstValue(rider, "acceptance_rate", "acceptance", "acceptance_percentage"));
        addDetailLine(page, "ساعات العمل", firstValue(rider, "worked_time", "worked_hours", "worked"));
        addDetailLine(page, "المحطة", firstValue(rider, "starting_point"));
        addDetailLine(page, "المنطقة", firstValue(rider, "zone"));
        addDetailLine(page, "وسيلة التوصيل", firstValue(rider, "vehicle", "vehicle_type"));

        TextView ordersTitle = makeText("تفاصيل الأوردر النشط", 18, ORANGE, true);
        ordersTitle.setPadding(0, dp(16), 0, dp(8));
        page.addView(ordersTitle);

        if (orders.length() == 0) {
            boolean activeConfirmed =
                    rider.optBoolean("has_active_deliveries", false)
                    || rider.optInt("active_count") > 0;

            String message = activeConfirmed
                    ? "المصدر يؤكد وجود أوردر نشط، لكن تفاصيله غير متاحة حاليًا."
                    : "لا يوجد أوردر نشط مؤكد لهذا المندوب.";

            page.addView(makeText(message, 14, MUTED, false));
        } else {
            JSONObject order = orders.optJSONObject(0);

            if (order == null) {
                page.addView(makeText(
                        "تعذر قراءة تفاصيل الأوردر النشط.",
                        14, MUTED, false));
            } else {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(12), dp(12), dp(12), dp(12));
                box.setBackground(round(
                        Color.rgb(24, 20, 17), ORANGE, 1));

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(0, 0, 0, dp(10));
                page.addView(box, params);

                String code = order.optString("order_code", "");
                if (code.isEmpty()) {
                    code = order.optString("delivery_id", "غير متاح");
                }

                box.addView(makeText(
                        "📦 رقم الأوردر: " + code, 15, ORANGE, true));
                box.addView(makeText(
                        "🚦 المرحلة: "
                                + arabicStatus(order.optString("status", "")),
                        14, GREEN, true));
                box.addView(makeText(
                        "🍔 المطعم: "
                                + displayValue(order.optString("vendor_name", "")),
                        13, WHITE, false));
                addAddress(box, "عنوان الاستلام",
                        order.optString("pickup_address", ""));
                addAddress(box, "عنوان العميل",
                        order.optString("dropoff_address", ""));

                JSONArray timeline = order.optJSONArray("timeline");
                if (timeline != null && timeline.length() > 0) {
                    box.addView(makeText(
                            "أحداث التوصيل المسجلة: " + timeline.length(),
                            11, MUTED, false));
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
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));
        page.setBackgroundColor(BG);

        TextView brand = makeText("AL ABTAL", 30, ORANGE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setShadowLayer(dp(5), 0, 0, ORANGE);
        page.addView(brand, new LinearLayout.LayoutParams(-1, dp(60)));
        TextView subtitle = makeText(setup ? "إنشاء حساب الدخول للتطبيق" : "تسجيل الدخول للتطبيق", 17, WHITE, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(20));
        page.addView(subtitle);

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setTextColor(WHITE);
        username.setHintTextColor(MUTED);
        username.setHint("اسم المستخدم");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        username.setPadding(dp(12), dp(8), dp(12), dp(8));
        username.setBackground(round(PANEL, ORANGE, 1));
        page.addView(username, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setTextColor(WHITE);
        password.setHintTextColor(MUTED);
        password.setHint("كلمة المرور (6 أحرف على الأقل عند الإنشاء)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(12), dp(8), dp(12), dp(8));
        password.setBackground(round(PANEL, ORANGE, 1));
        LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(-1, dp(52));
        passParams.setMargins(0, dp(12), 0, dp(16));
        page.addView(password, passParams);

        Button submit = makeButton(setup ? "إنشاء الحساب والمتابعة" : "دخول");
        page.addView(submit, new LinearLayout.LayoutParams(-1, dp(50)));
        TextView note = makeText("تسجيل الدخول محلي على هذا الجهاز، ومنفصل عن حساب بوابة الشركة.", 11, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        page.addView(note);
        submit.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String pw = password.getText().toString();
            if (u.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "اكتب اسم المستخدم وكلمة المرور", Toast.LENGTH_SHORT).show();
                return;
            }
            if (setup) {
                if (pw.length() < 6) {
                    Toast.makeText(this, "كلمة المرور يجب أن تكون 6 أحرف على الأقل", Toast.LENGTH_LONG).show();
                    return;
                }
                authPrefs.edit().putString("username", u).putString("password_hash", hashPassword(pw)).apply();
                Toast.makeText(this, "تم إنشاء حساب الدخول على هذا الجهاز", Toast.LENGTH_SHORT).show();
                buildDashboard();
            } else {
                String savedUser = authPrefs.getString("username", "");
                String savedHash = authPrefs.getString("password_hash", "");
                if (u.equals(savedUser) && hashPassword(pw).equals(savedHash)) {
                    buildDashboard();
                } else {
                    Toast.makeText(this, "اسم المستخدم أو كلمة المرور غير صحيحة", Toast.LENGTH_LONG).show();
                }
            }
        });
        if (!setup) {
            Button resetLogin = makeButton("إعادة تعيين بيانات الدخول");
            resetLogin.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                    .setTitle("إنشاء بيانات دخول جديدة")
                    .setMessage("سيتم حذف اسم المستخدم وكلمة المرور المحليين فقط. بيانات الروستر المحفوظة لن تُحذف.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("متابعة", (dialog, which) -> {
                        authPrefs.edit()
                            .remove("username")
                            .remove("password_hash")
                            .apply();
                        showLoginScreen(true);
                    })
                    .show()
            );
            page.addView(resetLogin);
        }

        setContentView(page);
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

    private void logoutApp() {
        detailVisible = false;
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
