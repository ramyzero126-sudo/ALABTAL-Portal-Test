
package com.alabtal.portaltest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
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

    private WebView webView;
    private TextView status;
    private TextView result;
    private Button inspectButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean inspectionRunning = false;

    private static final String PORTAL_URL =
        "https://eg.me.logisticsbackoffice.com/dashboard/rooster/live-3pl";

    private static final String RIDERS_URL =
        "/api/rider-live-operations/v1/external/city/214/riders?page=0&size=10";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 10, 10));

        TextView title = new TextView(this);
        title.setText("AL ABTAL • LIVE ORDERS");
        title.setTextColor(Color.rgb(255, 121, 0));
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(8, 14, 8, 12);
        root.addView(title);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button open = new Button(this);
        open.setText("فتح البوابة");
        controls.addView(open,
            new LinearLayout.LayoutParams(0, -2, 1));

        inspectButton = new Button(this);
        inspectButton.setText("فحص الأوردرات");
        controls.addView(inspectButton,
            new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(controls);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(12, 8, 12, 8);
        status.setText("افتح البوابة وسجّل الدخول.");
        root.addView(status);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportMultipleWindows(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                status.setText("الصفحة: " + url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // الجسر مخصص لاستلام تقرير الفحص فقط.
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
                    status.setText("تعذر الفحص: " + message);
                    Toast.makeText(
                        MainActivity.this,
                        "فشل جلب البيانات",
                        Toast.LENGTH_LONG
                    ).show();
                });
            }
        }, "AlabtalBridge");

        root.addView(webView,
            new LinearLayout.LayoutParams(-1, 0, 1));

        result = new TextView(this);
        result.setTextColor(Color.WHITE);
        result.setTextSize(13);
        result.setPadding(12, 10, 12, 10);
        result.setTextIsSelectable(true);

        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(result);
        root.addView(resultScroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        open.setOnClickListener(v -> openPortal());
        inspectButton.setOnClickListener(v -> inspectRoster());

        openPortal();
    }

    private void openPortal() {
        webView.loadUrl(PORTAL_URL);
    }

    private void inspectRoster() {
        if (inspectionRunning) {
            return;
        }

        if (webView == null) {
            status.setText("صفحة البوابة غير متاحة.");
            return;
        }

        inspectionRunning = true;
        inspectButton.setEnabled(false);
        status.setText("جاري جلب المندوبين والأوردرات...");

        String script =
            "(async function() {" +
            "try {" +

            "const base = location.origin;" +

            "async function getJson(url) {" +
            " const response = await fetch(url, {" +
            "  credentials: 'include'," +
            "  headers: {'Accept':'application/json'}" +
            " });" +
            " if (!response.ok) throw new Error('HTTP ' + response.status + ' - ' + url);" +
            " return await response.json();" +
            "}" +

            "const data = await getJson(" +
            " base + " + JSONObject.quote(RIDERS_URL) +
            ");" +

            "const riders = data.content || data.riders || [];" +

            "const output = await Promise.all(riders.map(async r => {" +

            " const info = r.deliveries_info || {};" +
            " const activeIds = (info.active_delivery_ids || []).map(x => String(x));" +
            " let detail = null;" +
            " let detailError = '';" +

            " if (activeIds.length > 0) {" +
            "  try {" +
            "   detail = await getJson(base + " +
            "    '/api/rider-live-operations/v2/external/rider/' +" +
            "    encodeURIComponent(String(r.employee_id)));" +
            "  } catch (e) { detailError = String(e.message || e); }" +
            " }" +

            " const deliveryInfo = (detail && detail.deliveries_info) || {};" +
            " const latest = deliveryInfo.latest_deliveries || [];" +

            " const activeOrders = latest.filter(o => {" +
            "  const id = String(o.delivery_id != null ? o.delivery_id : o.order_id);" +
            "  const s = String(o.status || '').toLowerCase();" +
            "  return activeIds.includes(id) &&" +
            "   s !== 'completed' && s !== 'cancelled';" +
            " }).map(o => ({" +
            "  delivery_id: String(o.delivery_id != null ? o.delivery_id : o.order_id || '')," +
            "  order_code: o.order_code || ''," +
            "  status: o.status || ''," +
            "  vendor_name: o.vendor_name || ''," +
            "  pickup_address: o.pickup_address || ''," +
            "  dropoff_address: o.dropoff_address || ''," +
            "  timeline: o.timeline || []" +
            " }));" +

            " return {" +
            "  employee_id: String(r.employee_id || '')," +
            "  name: r.name || ''," +
            "  rider_status: r.status || ''," +
            "  has_active_deliveries: info.has_active_deliveries === true," +
            "  active_delivery_ids: activeIds," +
            "  active_count: activeIds.length," +
            "  wallet_balance: (r.wallet_info || {}).balance," +
            "  zone: (r.zone || {}).name || ''," +
            "  starting_point: (r.starting_point || {}).name || ''," +
            "  active_orders: activeOrders," +
            "  detail_error: detailError" +
            " };" +
            "}));" +

            "const report = {" +
            " url: location.href," +
            " pageLength: (document.body.innerText || '').length," +
            " cardCount: output.length," +
            " total_elements: data.total_elements || output.length," +
            " checked_at: new Date().toISOString()," +
            " riders: output" +
            "};" +

            "window.AlabtalBridge.onReport(JSON.stringify(report));" +

            "} catch (e) {" +
            " window.AlabtalBridge.onError(String(e.message || e));" +
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

            StringBuilder text = new StringBuilder();
            int activeRiders = 0;
            int activeOrderCount = 0;

            text.append("AL ABTAL — تقرير الروستر\n");
            text.append("وقت الفحص: ")
                .append(report.optString("checked_at"))
                .append("\n");
            text.append("عدد المندوبين: ")
                .append(riders.length())
                .append("\n\n");

            for (int i = 0; i < riders.length(); i++) {
                JSONObject rider = riders.getJSONObject(i);
                JSONArray orders = rider.optJSONArray("active_orders");
                if (orders == null) orders = new JSONArray();

                boolean hasOrders =
                    rider.optBoolean("has_active_deliveries");

                if (hasOrders) activeRiders++;
                activeOrderCount += rider.optInt("active_count");

                text.append("━━━━━━━━━━━━━━━━━━\n");
                text.append(rider.optString("name", "مندوب"))
                    .append("\n");

                text.append("رقم المندوب: ")
                    .append(rider.optString("employee_id"))
                    .append("\n");

                text.append("الحالة: ")
                    .append(hasOrders ? "معاه أوردر" : "مفيش أوردر نشط")
                    .append("\n");

                text.append("عدد الأوردرات النشطة: ")
                    .append(rider.optInt("active_count"))
                    .append("\n");

                text.append("المحطة: ")
                    .append(rider.optString("starting_point"))
                    .append("\n");

                text.append("المحفظة: ")
                    .append(rider.optString("wallet_balance", "غير متاح"))
                    .append("\n");

                if (orders.length() == 0 && hasOrders) {
                    text.append(
                        "تفاصيل الأوردر غير متاحة في استجابة التفاصيل.\n"
                    );
                    String err = rider.optString("detail_error", "");
                    if (!err.isEmpty()) {
                        text.append("سبب جلب التفاصيل: ")
                            .append(err).append("\n");
                    }
                }

                for (int j = 0; j < orders.length(); j++) {
                    JSONObject order = orders.getJSONObject(j);

                    text.append("\n  الأوردر ")
                        .append(j + 1).append("\n");

                    text.append("  الكود: ")
                        .append(order.optString("order_code", "غير متاح"))
                        .append("\n");

                    text.append("  المطعم: ")
                        .append(order.optString("vendor_name", "غير متاح"))
                        .append("\n");

                    text.append("  الحالة: ")
                        .append(arabicStatus(order.optString("status")))
                        .append("\n");

                    text.append("  الاستلام: ")
                        .append(order.optString("pickup_address", "غير متاح"))
                        .append("\n");

                    text.append("  التسليم: ")
                        .append(order.optString("dropoff_address", "غير متاح"))
                        .append("\n");
                }

                text.append("\n");
            }

            text.insert(
                text.indexOf("\n\n") + 2,
                "مندوبون معهم أوردرات: " + activeRiders +
                "\nإجمالي معرّفات الأوردرات النشطة: " +
                activeOrderCount + "\n\n"
            );

            result.setText(text.toString());

            String savedName = saveInspection(report.toString());

            status.setText(
                "اكتمل الفحص. تم حفظ التقرير: " + savedName
            );

            Toast.makeText(
                this,
                "تم حفظ التقرير في التنزيلات",
                Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            status.setText("تعذر قراءة التقرير: " + e.getMessage());
            result.setText(json);
        }
    }

    private String arabicStatus(String status) {
        switch (status) {
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
                return status.isEmpty() ? "غير معروف" : status;
        }
    }

    private String saveInspection(String report) throws Exception {
        String fileName = "roster-inspection-" +
            new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
            ).format(new Date()) + ".json";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(
            MediaStore.MediaColumns.MIME_TYPE,
            "application/json"
        );
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

        try (OutputStream out =
                 getContentResolver().openOutputStream(uri, "wt")) {
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
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.removeJavascriptInterface("AlabtalBridge");
            webView.destroy();
        }

        super.onDestroy();
    }
}
