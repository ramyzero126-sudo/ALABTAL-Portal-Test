package com.alabtal.portaltest;

import android.app.Activity;
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
            "var t=document.querySelector('table.cape-table');" +
            "if(!t)return JSON.stringify({ok:false,message:'لم يتم العثور على جدول cape-table في الصفحة الحالية',title:document.title,url:location.href});" +
            "var rows=Array.from(t.querySelectorAll('tr')).map(function(r){" +
            "return Array.from(r.querySelectorAll('th,td')).map(function(c){return (c.innerText||c.textContent||'').trim()})" +
            "});" +
            "return JSON.stringify({ok:true,title:document.title,url:location.href,rowCount:rows.length,rows:rows.slice(0,30)});" +
            "})()";

        webView.evaluateJavascript(script, value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                String data = decoded == null ? "" : decoded.toString();
                result.setText(data);
                status.setText("اكتمل فحص الصفحة الحالية.");
            } catch (Exception e) {
                result.setText(value == null ? "لا توجد نتيجة" : value);
                status.setText("تعذر تنسيق النتيجة.");
            }
        });
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
