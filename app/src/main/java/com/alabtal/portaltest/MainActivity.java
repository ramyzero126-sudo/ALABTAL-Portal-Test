package com.alabtal.portaltest;

import android.app.Activity;
import android.provider.MediaStore;
import android.os.Environment;
import android.os.Build;
import android.net.Uri;
import android.content.ContentValues;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import org.json.JSONTokener;

public class MainActivity extends Activity {
    private WebView webView;
    private TextView status;
    private TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 10, 10));

        TextView title = new TextView(this);
        title.setText("AL ABTAL • PORTAL TEST");
        title.setTextColor(Color.rgb(255, 121, 0));
        title.setTextSize(19);
        title.setGravity(Gravity.CENTER);
        title.setPadding(8, 14, 8, 10);
        root.addView(title);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button open = new Button(this);
        open.setText("فتح البوابة");
        controls.addView(open, new LinearLayout.LayoutParams(0, -2, 1));

        Button inspect = new Button(this);
        inspect.setText("فحص الروستر");
        controls.addView(inspect, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(controls);

        status = new TextView(this);
        status.setText("افتح البوابة وسجّل الدخول بنفسك.");
        status.setTextColor(Color.WHITE);
        status.setPadding(10, 8, 10, 8);
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
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        result = new TextView(this);
        result.setTextColor(Color.WHITE);
        result.setTextSize(12);
        result.setPadding(10, 8, 10, 8);
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(result);
        root.addView(resultScroll, new LinearLayout.LayoutParams(-1, 150));

        setContentView(root);

        open.setOnClickListener(v -> openPortal());
        inspect.setOnClickListener(v -> inspectRoster());

        openPortal();
    }

    private void openPortal() {
        webView.loadUrl(
            "https://eg.me.logisticsbackoffice.com/dashboard/rooster/live-3pl"
        );
    }

    private void inspectRoster() {
        String script =
            "(function(){" +
            "var all=Array.from(document.querySelectorAll('body *'));" +
            "var visible=function(e){" +
            "var r=e.getBoundingClientRect(),s=getComputedStyle(e);" +
            "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'" +
            "};" +
            "var desc=function(e){" +
            "return {tag:e.tagName,text:(e.innerText||e.textContent||'').trim().slice(0,700)," +
            "aria:e.getAttribute('aria-label'),expanded:e.getAttribute('aria-expanded')," +
            "title:e.getAttribute('title'),role:e.getAttribute('role')," +
            "cls:typeof e.className==='string'?e.className:''," +
            "html:(e.outerHTML||'').slice(0,500)}" +
            "};" +
            "var keys=/order|delivery|dropoff|pickup|address|status|active|expand|chevron|arrow/i;" +
            "var matches=all.filter(function(e){" +
            "var z=((e.innerText||e.textContent||'')+' '+(e.className||'')+' '+" +
            "(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('data-testid')||''));" +
            "return visible(e)&&keys.test(z)&&z.length<1800" +
            "}).filter(function(e){" +
            "return !Array.from(e.children).some(function(c){" +
            "return keys.test((c.innerText||c.textContent||'')+' '+(c.className||''))" +
            "})" +
            "}).slice(0,100).map(desc);" +
            "var tables=Array.from(document.querySelectorAll('table')).map(function(t){" +
            "return {className:typeof t.className==='string'?t.className:''," +
            "rows:Array.from(t.querySelectorAll('tr')).slice(0,20).map(function(r){" +
            "return {text:(r.innerText||'').trim().slice(0,900)," +
            "controls:Array.from(r.querySelectorAll('button,[role=button],a,[aria-expanded],svg'))" +
            ".slice(0,15).map(desc)}" +
            "})}" +
            "});" +
            "var body=(document.body.innerText||'');" +
            "return JSON.stringify({title:document.title,url:location.href," +
            "pageLength:body.length,orderMatches:matches,tables:tables," +
            "tail:body.slice(-9000)});" +
            "})()";

        webView.evaluateJavascript(script, value -> {
            String report;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                report = decoded == null ? "" :
                    (decoded instanceof String ? (String) decoded : decoded.toString());
            } catch (Exception e) {
                report = value == null ? "لا توجد نتيجة" : value;
            }

            result.setText(report);
            try {
                saveInspection(report);
                status.setText("اكتمل الفحص وتم حفظ النتيجة في التنزيلات.");
                Toast.makeText(this,
                    "تم حفظ roster-inspection.txt في التنزيلات",
                    Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                status.setText("ظهر الفحص، لكن تعذر حفظ الملف: " + e.getMessage());
                Toast.makeText(this, "تعذر حفظ نتيجة الفحص",
                    Toast.LENGTH_LONG).show();
            }
        });
    }


    private void saveInspection(String report) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new Exception("هذا الحفظ يحتاج Android 10 أو أحدث.");
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "roster-inspection.txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/ALABTAL-Portal-Test");

        Uri uri = getContentResolver().insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            throw new Exception("لم يتم إنشاء ملف التنزيلات.");
        }

        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new Exception("تعذر فتح الملف للكتابة.");
            }
            out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }
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
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
