package com.alabtal.portaltest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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

    private static final int BG = Color.rgb(7, 8, 10);
    private static final int PANEL = Color.rgb(17, 19, 23);
    private static final int ORANGE = Color.rgb(255, 121, 0);
    private static final int WHITE = Color.rgb(245, 246, 248);
    private static final int MUTED = Color.rgb(160, 166, 176);
    private static final int GREEN = Color.rgb(45, 210, 125);
    private static final int RED = Color.rgb(255, 82, 82);

    private static final String PORTAL_URL =
        "https://eg.me.logisticsbackoffice.com/dashboard/rooster/live-3pl";

    private static final String RIDERS_URL =
        "/api/rider-live-operations/v1/external/city/214/riders?page=0&size=10";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private TextView status;
    private TextView banner;
    private TextView summary;
    private LinearLayout cards;
    private Button inspectButton;
    private boolean portalVisible = false;
    private LinearLayout dashboardRoot;
    private boolean detailVisible = false;
    private SharedPreferences authPrefs;

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
        root.setPadding(dp(12), dp(5), dp(12), dp(5));

        // Premium AL ABTAL LED Header
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(dp(8), dp(5), dp(8), dp(7));

        TextView globe = makeText("🌐", 24, ORANGE, true);
        globe.setGravity(Gravity.CENTER);
        globe.setOnClickListener(v -> togglePortal());
        globe.setGravity(Gravity.CENTER);
        brand.addView(globe, new LinearLayout.LayoutParams(-1, dp(29)));

        TextView title = makeText("AL ABTAL", 29, ORANGE, true);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setLetterSpacing(0.10f);
        title.setShadowLayer(dp(5), 0, 0, ORANGE);
        brand.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView subtitle = makeText("L I V E   O P E R A T I O N S", 10, WHITE, true);
        subtitle.setGravity(Gravity.CENTER);
        brand.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(20)));

        GradientDrawable brandBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(36, 21, 10), Color.rgb(8, 9, 11)}
        );
        brandBg.setCornerRadius(dp(12));
        brandBg.setStroke(dp(1), ORANGE);
        brand.setBackground(brandBg);

        LinearLayout.LayoutParams brandParams =
                new LinearLayout.LayoutParams(-1, dp(101));
        brandParams.setMargins(0, dp(3), 0, dp(7));
        root.addView(brand, brandParams);

        banner = makeText("", 14, ORANGE, true);
        banner.setGravity(Gravity.CENTER);
        banner.setSingleLine(true);
        banner.setLetterSpacing(0.025f);
        banner.setShadowLayer(dp(4), 0, 0, ORANGE);
        banner.setPadding(dp(5), dp(7), dp(5), dp(7));

        GradientDrawable ledBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(35, 16, 4), Color.rgb(12, 12, 13),
                          Color.rgb(35, 16, 4)}
        );
        ledBg.setCornerRadius(dp(8));
        ledBg.setStroke(dp(1), ORANGE);
        banner.setBackground(ledBg);

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(43));
        bp.setMargins(0, 0, 0, dp(9));
        root.addView(banner, bp);
        handler.post(bannerRunnable);

        TextView rosterTitle = makeText("متابعة المناديب — طنطا", 17, WHITE, true);
        root.addView(rosterTitle);

        summary = makeText(
            "في انتظار تحميل بيانات الروستر...",
            12, MUTED, false
        );
        summary.setPadding(0, dp(5), 0, dp(5));
        root.addView(summary);

        // Controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button openButton = makeButton("🌐 البوابة");
        controls.addView(
            openButton, new LinearLayout.LayoutParams(0, dp(45), 1)
        );

        inspectButton = makeButton("تحديث البيانات");
        LinearLayout.LayoutParams ip =
            new LinearLayout.LayoutParams(0, dp(45), 1);
        ip.setMargins(dp(7), 0, 0, 0);
        controls.addView(inspectButton, ip);

        Button logoutButton = makeButton("خروج");
        logoutButton.setBackground(round(Color.rgb(85, 30, 25), RED, 1));
        LinearLayout.LayoutParams logoutParams =
            new LinearLayout.LayoutParams(0, dp(45), 0.7f);
        logoutParams.setMargins(dp(7), 0, 0, 0);
        controls.addView(logoutButton, logoutParams);

        root.addView(controls);

        status = makeText(
            "افتح البوابة وسجّل الدخول لقراءة بيانات الروستر.",
            12, ORANGE, false
        );
        status.setPadding(2, dp(6), 2, dp(6));
        root.addView(status);

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
                pageLoaded = true;
                status.setText("تم تحميل الصفحة. سجّل الدخول إذا طُلب منك ذلك.");

                handler.removeCallbacks(refreshRunnable);
                handler.postDelayed(refreshRunnable, 30000);
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

        // Keep the portal accessible while leaving most space for rider cards.
        root.addView(
            webView,
            new LinearLayout.LayoutParams(-1, dp(155))
        );

        TextView listTitle = makeText("المندوبون والأوردرات", 16, ORANGE, true);
        listTitle.setPadding(0, dp(9), 0, dp(6));
        root.addView(listTitle);

        ScrollView cardScroll = new ScrollView(this);
        cardScroll.setFillViewport(false);
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cardScroll.addView(cards);

        root.addView(
            cardScroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        setContentView(root);

        openButton.setOnClickListener(v -> togglePortal());
        logoutButton.setOnClickListener(v -> logoutApp());
        inspectButton.setOnClickListener(v -> inspectRoster());

        addEmptyCard(
            "بانتظار البيانات",
            "افتح البوابة وسجّل الدخول، ثم اضغط تحديث البيانات."
        );

        openPortal();
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

        TextView l = makeText(label, 10, MUTED, false);
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

        row.addView(
            createTile(label1, value1),
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        LinearLayout.LayoutParams second =
            new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(7), 0, 0, 0);

        row.addView(createTile(label2, value2), second);

        LinearLayout.LayoutParams rp =
            new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dp(7), 0, 0);
        target.addView(row, rp);
    }

    private LinearLayout createTile(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(8), dp(7), dp(8));
        box.setBackground(round(Color.rgb(25, 27, 32), Color.rgb(54, 57, 65), 1));

        TextView l = makeText(label, 10, MUTED, false);
        TextView v = makeText(value, 13, WHITE, true);

        box.addView(l);
        box.addView(v);
        return box;
    }

    private void togglePortal() {
        if (webView == null) return;
        portalVisible = !portalVisible;
        webView.setVisibility(portalVisible ? View.VISIBLE : View.GONE);
        if (portalVisible) {
            if (!pageLoaded) openPortal();
            else status.setText("البوابة مفتوحة. يمكنك تحديث البيانات.");
        } else {
            status.setText("تم إخفاء البوابة؛ بيانات المناديب تظل ظاهرة.");
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

            "const output = await Promise.all(riders.map(async r => {" +
            " const info = r.deliveries_info || {};" +
            " const activeIds = (info.active_delivery_ids || []).map(x => String(x));" +
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

            " const activeOrders = latest.map(o => ({" +
            "  delivery_id:String(o.delivery_id != null ? o.delivery_id : o.order_id || '')," +
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
            "  wallet_balance:(r.wallet_info || {}).balance," +
            "  zone:(r.zone || {}).name || ''," +
            "  starting_point:(r.starting_point || {}).name || ''," +
            "  shift_start:r.shift_start || r.shiftStart || (r.shift_info || {}).start_time || (r.shift || {}).start_time || ''," +
            "  shift_end:r.shift_end || r.shiftEnd || (r.shift_info || {}).end_time || (r.shift || {}).end_time || ''," +
            "  performance:r.performance_score || r.performance || ''," +
            "  acceptance_rate:r.acceptance_rate || r.acceptance_percentage || r.acceptance || ''," +
            "  worked_time:r.worked_time || r.worked_hours || r.worked || ''," +
            "  vehicle:r.vehicle || r.vehicle_type || ''," +
            "  total_orders:r.total_orders || r.total_deliveries_count || info.total_orders || info.total_deliveries_count || info.completed_deliveries_count || latest.length || 0," +
            "  total_orders_source:(r.total_orders != null || r.total_deliveries_count != null || info.total_orders != null || info.total_deliveries_count != null || info.completed_deliveries_count != null) ? 'counter' : 'latest_deliveries'," +
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

    private void handleReport(String json) {
        inspectionRunning = false;
        inspectButton.setEnabled(true);

        try {
            JSONObject report = new JSONObject(json);
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
                "    •    مندوبون لديهم أوردرات ظاهرة: " + ridersWithOrders +
                "    •    عدد الأوردرات المتاحة بالمصدر: " + totalOrders
            );

            String savedName = saveInspection(report.toString());

            status.setText(
                "آخر تحديث: " + formatTime(report.optString("checked_at")) +
                " • التقرير: " + savedName
            );

        } catch (Exception e) {
            status.setText("تعذر عرض التقرير: " + e.getMessage());
            Toast.makeText(this, "فشل قراءة بيانات الروستر", Toast.LENGTH_LONG).show();
        }
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
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(24, 23, 21), Color.rgb(12, 13, 16)}
        );
        cardBg.setCornerRadius(dp(15));
        cardBg.setStroke(dp(1), ORANGE);
        card.setBackground(cardBg);
        card.setElevation(dp(3));

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(12));
        cards.addView(card, cp);

        String name = rider.optString("name", "مندوب بدون اسم");
        String id = rider.optString("employee_id", "—");

        TextView heading = makeText("👤  " + name, 18, WHITE, true);
        heading.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        card.addView(heading);

        TextView idView = makeText("🪪  رقم المندوب: " + id, 11, MUTED, false);
        idView.setPadding(0, dp(3), 0, dp(7));
        card.addView(idView);

        boolean hasOrders =
                rider.optBoolean("has_active_deliveries", false)
                || rider.optInt("active_count") > 0
                || orders.length() > 0;

        String riderStatus = rider.optString("rider_status",
                rider.optString("status", "غير معروف"));
        String riderStatusArabic = translateRiderStatus(riderStatus);

        TextView state = makeText(
                (hasOrders ? "📦  معاه أوردر" : "✓  مفيش أوردر نشط")
                + "     •     " + riderStatusArabic,
                12, hasOrders ? GREEN : MUTED, true
        );
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(7), dp(8), dp(7), dp(8));
        state.setBackground(round(
                Color.rgb(20, 30, 25),
                hasOrders ? GREEN : Color.rgb(75, 80, 85), 1
        ));
        LinearLayout.LayoutParams stateParams =
                new LinearLayout.LayoutParams(-1, -2);
        stateParams.setMargins(0, 0, 0, dp(9));
        card.addView(state, stateParams);

        String wallet = firstValue(rider,
                "wallet_balance", "wallet", "balance");
        int activeCount = Math.max(
                rider.optInt("active_count"), orders.length());

        addTileRow(card,
                "📦 عدد الأوردرات", firstValue(rider, "total_orders"),
                "💰 المحفظة", wallet);

        addTileRow(card,
                "📍 المحطة", firstValue(rider, "starting_point"),
                "🗺️ المنطقة", firstValue(rider, "zone"));

        addTileRow(card,
                "⚡ الأداء", firstValue(rider,
                        "performance", "performance_score"),
                "✅ نسبة القبول", firstValue(rider,
                        "acceptance_rate", "acceptance", "acceptance_percentage"));

        addTileRow(card,
                "⏱️ ساعات العمل", firstValue(rider,
                        "worked_time", "worked_hours", "worked"),
                "🕒 بداية الشيفت", firstValue(rider,
                        "shift_start", "planned_shift_start", "session_start"));

        addTileRow(card,
                "🏁 نهاية الشيفت", firstValue(rider,
                        "shift_end", "planned_shift_end", "session_end"),
                "🚲 وسيلة التوصيل", firstValue(rider,
                        "vehicle", "vehicle_type"));

        TextView detailsHint = makeText(orders.length() > 0
                ? "📦 الأوردرات الظاهرة: " + orders.length() + " • اضغط لعرض التفاصيل والمراحل"
                : "اضغط على الكارت لعرض تفاصيل المندوب والأوردرات المتاحة",
                12, ORANGE, true);
        detailsHint.setPadding(0, dp(10), 0, dp(2));
        card.addView(detailsHint);
        card.setOnClickListener(v -> showRiderDetails(rider, orders));

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

        TextView ordersTitle = makeText("الأوردرات ومراحلها", 18, ORANGE, true);
        ordersTitle.setPadding(0, dp(16), 0, dp(8));
        page.addView(ordersTitle);
        if (orders.length() == 0) {
            TextView none = makeText("لم تصل تفاصيل أوردرات من المصدر لهذا المندوب.", 13, MUTED, false);
            page.addView(none);
        }
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null) continue;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12), dp(11), dp(12), dp(11));
            box.setBackground(round(Color.rgb(24, 20, 17), ORANGE, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(10));
            page.addView(box, params);
            String code = order.optString("order_code", "");
            if (code.isEmpty()) code = order.optString("delivery_id", "غير متاح");
            box.addView(makeText("📦 أوردر " + (i + 1) + " • " + code, 15, ORANGE, true));
            box.addView(makeText("🚦 المرحلة: " + arabicStatus(order.optString("status", "")), 13, GREEN, true));
            box.addView(makeText("🍔 المطعم: " + displayValue(order.optString("vendor_name", "")), 12, WHITE, false));
            addAddress(box, "عنوان الاستلام", order.optString("pickup_address", ""));
            addAddress(box, "عنوان العميل", order.optString("dropoff_address", ""));
            JSONArray timeline = order.optJSONArray("timeline");
            if (timeline != null && timeline.length() > 0) {
                TextView time = makeText("أحداث التوصيل المسجلة: " + timeline.length(), 11, MUTED, false);
                time.setPadding(0, dp(6), 0, 0);
                box.addView(time);
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
        if (detailVisible) {
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
