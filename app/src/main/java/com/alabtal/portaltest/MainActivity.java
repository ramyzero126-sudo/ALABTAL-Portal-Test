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
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.content.ContentValues;
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

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
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

        Button openButton = makeButton("فتح البوابة");
        controls.addView(
            openButton, new LinearLayout.LayoutParams(0, dp(45), 1)
        );

        inspectButton = makeButton("تحديث البيانات");
        LinearLayout.LayoutParams ip =
            new LinearLayout.LayoutParams(0, dp(45), 1);
        ip.setMargins(dp(7), 0, 0, 0);
        controls.addView(inspectButton, ip);

        root.addView(controls);

        status = makeText(
            "سجّل الدخول إلى بوابة الشركة بالأسفل.",
            12, ORANGE, false
        );
        status.setPadding(2, dp(6), 2, dp(6));
        root.addView(status);

        // Embedded portal: login and authenticated session
        webView = new WebView(this);
        webView.setBackgroundColor(BG);
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

        TextView listTitle = makeText("المندوبون والأوردرات النشطة", 16, ORANGE, true);
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

        openButton.setOnClickListener(v -> openPortal());
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

    private void openPortal() {
        if (webView != null) {
            status.setText("جاري فتح بوابة الروستر...");
            webView.loadUrl(PORTAL_URL);
        }
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

            " if (activeIds.length > 0) {" +
            "  try {" +
            "   detail = await getJson(base +" +
            "    '/api/rider-live-operations/v2/external/rider/' +" +
            "    encodeURIComponent(String(r.employee_id)));" +
            "  } catch(e) { detailError = String(e.message || e); }" +
            " }" +

            " const deliveryInfo = (detail && detail.deliveries_info) || {};" +
            " const latest = deliveryInfo.latest_deliveries || [];" +

            " const activeOrders = latest.filter(o => {" +
            "  const id = String(o.delivery_id != null ? o.delivery_id : o.order_id);" +
            "  const s = String(o.status || '').toLowerCase();" +
            "  return activeIds.includes(id) && s !== 'completed' && s !== 'cancelled';" +
            " }).map(o => ({" +
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

            int activeRiders = 0;
            int totalOrders = 0;

            for (int i = 0; i < riders.length(); i++) {
                JSONObject rider = riders.getJSONObject(i);
                JSONArray orders = rider.optJSONArray("active_orders");

                if (orders == null) orders = new JSONArray();

                boolean hasOrders = rider.optBoolean(
                    "has_active_deliveries",
                    rider.optInt("active_count") > 0
                );

                if (hasOrders) activeRiders++;
                totalOrders += rider.optInt("active_count");

                addRiderCard(rider, orders);
            }

            summary.setText(
                "المندوبون: " + riders.length() +
                "    •    معهم أوردرات: " + activeRiders +
                "    •    الأوردرات النشطة: " + totalOrders
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
                "📦 الأوردرات النشطة", String.valueOf(activeCount),
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

        if (orders.length() == 0 && hasOrders) {
            TextView missing = makeText(
                    "⚠️ يوجد أوردر نشط، لكن تفاصيله غير متاحة في استجابة المصدر.",
                    12, ORANGE, false);
            missing.setPadding(0, dp(8), 0, 0);
            card.addView(missing);

            String error = rider.optString("detail_error", "");
            if (!error.isEmpty()) {
                TextView err = makeText(
                        "تفاصيل الخطأ: " + error, 10, MUTED, false);
                card.addView(err);
            }
        }

        if (orders.length() == 0 && !hasOrders) {
            TextView none = makeText(
                    "لا توجد أوردرات نشطة ظاهرة لهذا المندوب.",
                    12, MUTED, false);
            none.setPadding(0, dp(8), 0, 0);
            card.addView(none);
        }

        for (int j = 0; j < orders.length(); j++) {
            JSONObject order = orders.optJSONObject(j);
            if (order == null) continue;

            LinearLayout orderBox = new LinearLayout(this);
            orderBox.setOrientation(LinearLayout.VERTICAL);
            orderBox.setPadding(dp(10), dp(10), dp(10), dp(10));

            GradientDrawable orderBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(35, 25, 17), Color.rgb(18, 19, 22)}
            );
            orderBg.setCornerRadius(dp(11));
            orderBg.setStroke(dp(1), Color.rgb(190, 91, 24));
            orderBox.setBackground(orderBg);

            LinearLayout.LayoutParams op =
                    new LinearLayout.LayoutParams(-1, -2);
            op.setMargins(0, dp(10), 0, 0);
            card.addView(orderBox, op);

            String code = order.optString("order_code", "");
            if (code.isEmpty()) {
                code = order.optString("delivery_id", "غير متاح");
            }

            TextView orderTitle = makeText(
                    "🛵  الأوردر " + (j + 1) + "  •  " + code,
                    14, ORANGE, true);
            orderBox.addView(orderTitle);

            TextView vendor = makeText(
                    "🍔  المطعم: " + displayValue(
                            order.optString("vendor_name", "")),
                    12, WHITE, true);
            vendor.setPadding(0, dp(6), 0, 0);
            orderBox.addView(vendor);

            TextView stage = makeText(
                    "🚦  المرحلة: " + arabicStatus(
                            order.optString("status", "")),
                    12, GREEN, true);
            stage.setPadding(0, dp(6), 0, 0);
            orderBox.addView(stage);

            addAddress(orderBox, "🟠 عنوان الاستلام",
                    order.optString("pickup_address", ""));
            addAddress(orderBox, "📍 عنوان العميل",
                    order.optString("dropoff_address", ""));

            JSONArray timeline = order.optJSONArray("timeline");
            if (timeline != null && timeline.length() > 0) {
                TextView timelineView = makeText(
                        "🧭 أحداث التوصيل المسجلة: " + timeline.length(),
                        10, MUTED, false);
                timelineView.setPadding(0, dp(6), 0, 0);
                orderBox.addView(timelineView);
            }
        }
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
                return "اقترب من المطعم";
            case "picked_up":
                return "استلم الأوردر";
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
        if (webView != null && webView.canGoBack()) {
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
