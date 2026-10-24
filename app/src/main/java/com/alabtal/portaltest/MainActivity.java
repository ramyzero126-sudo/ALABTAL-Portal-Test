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
            "var txt=function(e){return (e&&(e.innerText||e.textContent)||'').trim()};" +
            "var visible=function(e){" +
            "var r=e.getBoundingClientRect(),s=getComputedStyle(e);" +
            "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';" +
            "};" +
            "var desc=function(e){" +
            "return {tag:e.tagName,text:txt(e).slice(0,500)," +
            "id:e.id||'',role:e.getAttribute('role')," +
            "aria:e.getAttribute('aria-label'),expanded:e.getAttribute('aria-expanded')," +
            "controls:e.getAttribute('aria-controls'),title:e.getAttribute('title')," +
            "cls:typeof e.className==='string'?e.className:''," +
            "html:(e.outerHTML||'').slice(0,1200)};" +
            "};" +
            "var all=Array.from(document.querySelectorAll('body *'));" +
            "var cards=all.filter(function(e){" +
            "return visible(e)&&e.children.length>0&&" +
            "/ID:\\s*\\d+/.test(txt(e))&&/Name:/.test(txt(e))&&txt(e).length<5000;" +
            "}).filter(function(e){" +
            "return !Array.from(e.children).some(function(c){" +
            "return visible(c)&&/ID:\\s*\\d+/.test(txt(c))&&/Name:/.test(txt(c));" +
            "});" +
            "}).slice(0,30).map(function(card){" +
            "var buttons=Array.from(card.querySelectorAll(" +
            "'button,[role=button],summary,a,[aria-expanded],svg'))" +
            ".filter(visible).slice(0,30).map(function(e){" +
            "var chain=[],n=e;" +
            "for(var i=0;n&&i<4;i++,n=n.parentElement){" +
            "chain.push({tag:n.tagName,id:n.id||''," +
            "cls:typeof n.className==='string'?n.className:''," +
            "text:txt(n).slice(0,500)});" +
            "}" +
            "return {element:desc(e),ancestors:chain};" +
            "});" +
            "return {cardText:txt(card).slice(0,1800)," +
            "cardClass:typeof card.className==='string'?card.className:''," +
            "buttons:buttons};" +
            "});" +
            "var controls=all.filter(function(e){" +
            "if(!visible(e))return false;" +
            "var tag=e.tagName.toLowerCase();" +
            "var label=[e.getAttribute('aria-label'),e.getAttribute('title')," +
            "e.getAttribute('data-testid'),typeof e.className==='string'?e.className:''," +
            "txt(e)].join(' ');" +
            "return /button|summary/.test(tag)||e.getAttribute('role')==='button'||" +
            "e.hasAttribute('aria-expanded')||/chevron|accordion|expand|collapse|arrow/i.test(label);" +
            "}).filter(function(e){" +
            "return !Array.from(e.children).some(function(c){" +
            "return visible(c)&&(/button|summary/.test(c.tagName.toLowerCase())||" +
            "c.getAttribute('role')==='button'||c.hasAttribute('aria-expanded'));" +
            "});" +
            "}).slice(0,100).map(desc);" +
            "var orderEls=all.filter(function(e){" +
            "return visible(e)&&e.children.length===0&&" +
            "/Order ID:|HF_[A-Z0-9]+|Accepted Order|Ongoing orders|Past orders|" +
            "Picked up|Near drop off|Delivered|dropoff_address|pickup_address/i.test(txt(e));" +
            "}).slice(0,100).map(function(e){" +
            "var chain=[],n=e;" +
            "for(var i=0;n&&i<5;i++,n=n.parentElement){" +
            "chain.push({tag:n.tagName,cls:typeof n.className==='string'?n.className:''," +
            "text:txt(n).slice(0,1500),id:n.id||''});" +
            "}" +
            "return {element:desc(e),ancestors:chain};" +
            "});" +
            "var detailHeadings=all.filter(function(e){" +
"var t=txt(e);" +
"return visible(e)&&e.children.length<3&&" +
"(t==='Ongoing orders'||t.indexOf('Past orders')===0);" +
"}).map(function(e){" +
"var chain=[],n=e;" +
"for(var i=0;n&&i<6;i++,n=n.parentElement){" +
"chain.push({tag:n.tagName,cls:typeof n.className==='string'?n.className:''," +
"text:txt(n).slice(0,1800),html:(n.outerHTML||'').slice(0,2500)});" +
"}" +
"return {heading:txt(e),ancestors:chain};" +
"});" +
"var orderCards=all.filter(function(e){" +
"return visible(e)&&e.children.length>0&&" +
"/Order ID:\\s*HF_[A-Z0-9]+/i.test(txt(e))&&txt(e).length<7000;" +
"}).filter(function(e){" +
"return !Array.from(e.children).some(function(c){" +
"return visible(c)&&/Order ID:\\s*HF_[A-Z0-9]+/i.test(txt(c));" +
"});" +
"}).slice(0,30).map(function(e){" +
"var chain=[],n=e;" +
"for(var i=0;n&&i<8;i++,n=n.parentElement){" +
"chain.push({tag:n.tagName,cls:typeof n.className==='string'?n.className:''," +
"text:txt(n).slice(0,2500),html:(n.outerHTML||'').slice(0,3500)});" +
"}" +
"return {orderText:txt(e).slice(0,3000),ancestors:chain};" +
"});" +
"var detailPanels=all.filter(function(e){" +
            "var t=txt(e);" +
            "return visible(e)&&t.indexOf('Rider ID:')>=0&&" +
            "t.indexOf('Ongoing orders')>=0&&t.indexOf('Past orders')>=0&&t.length<20000;" +
            "}).filter(function(e){" +
            "return !Array.from(e.children).some(function(c){" +
            "var t=txt(c);" +
            "return visible(c)&&t.indexOf('Rider ID:')>=0&&" +
            "t.indexOf('Ongoing orders')>=0&&t.indexOf('Past orders')>=0;" +
            "});" +
            "}).slice(0,3).map(function(e){" +
            "return {panelText:txt(e).slice(0,15000)," +
            "panelClass:typeof e.className==='string'?e.className:''," +
            "panelHTML:(e.outerHTML||'').slice(0,9000)};" +
            "});" +
            "var activeOrderPanels=Array.from(document.querySelectorAll('[role=tabpanel][data-active=true]'))" +
            ".filter(visible).map(function(e){" +
            "var c=e.cloneNode(true);" +
            "c.querySelectorAll('svg,button,[aria-hidden=true]').forEach(function(x){x.remove();});" +
            "var t=(c.innerText||c.textContent||'').replace(/\\s+/g,' ').trim();" +
            "var ids=t.match(/HF_[A-Za-z0-9_-]+/g)||[];" +
            "return {tabId:e.id||'',text:t.slice(0,6000)," +
            "orderIds:Array.from(new Set(ids))," +
            "html:(e.outerHTML||'').slice(0,14000)};" +
            "});" +
            "var detailInspection={headings:detailHeadings,orderCards:orderCards," +
            "panels:detailPanels,activeOrderPanels:activeOrderPanels};" +
            "var clickCandidates=all.filter(function(e){" +
            "if(!visible(e))return false;" +
            "var tag=e.tagName.toLowerCase(),s=getComputedStyle(e);" +
            "var hit=/^(svg|path|button|summary|a)$/.test(tag)||" +
            "e.getAttribute('role')==='button'||e.hasAttribute('aria-expanded')||" +
            "s.cursor==='pointer'||typeof e.onclick==='function';" +
            "if(!hit)return false;" +
            "var n=e,inside=false;" +
            "for(var i=0;n&&i<8;i++,n=n.parentElement){" +
            "if(/ID:\\s*\\d+/.test(txt(n))&&/Name:/.test(txt(n))){inside=true;break;}" +
            "}" +
            "return inside;" +
            "}).slice(0,120).map(function(e){" +
            "var n=e,chain=[];" +
            "for(var i=0;n&&i<4;i++,n=n.parentElement){" +
            "chain.push({tag:n.tagName,id:n.id||''," +
            "cls:typeof n.className==='string'?n.className:''," +
            "role:n.getAttribute('role'),aria:n.getAttribute('aria-label')," +
            "expanded:n.getAttribute('aria-expanded'),title:n.getAttribute('title')," +
            "cursor:getComputedStyle(n).cursor,text:txt(n).slice(0,250)," +
            "html:(n.outerHTML||'').slice(0,900)});" +
            "}" +
            "return {element:desc(e),cursor:getComputedStyle(e).cursor," +
            "onclick:!!e.onclick,tabindex:e.getAttribute('tabindex')," +
            "pathD:e.tagName.toLowerCase()==='path'?e.getAttribute('d'):null," +
            "chain:chain};" +
            "});" +
            "var body=txt(document.body);" +
            "return JSON.stringify({title:document.title,url:location.href," +
            "pageLength:body.length,cardCount:cards.length,cards:cards," +
            "clickCandidates:clickCandidates," +
            "controls:controls,orderElements:orderEls,detailInspection:detailInspection,tail:body.slice(-7000)});" +
            "})()";

        if (webView == null) {
            status.setText("فشل الفحص: صفحة الروستر غير متاحة.");
            return;
        }

        status.setText("جاري فحص الصفحة...");
        webView.evaluateJavascript(script, value -> {
            String report;

            try {
                if (value == null || "null".equals(value.trim())) {
                    throw new Exception(
                        "JavaScript لم يُرجع نتيجة. افتح البوابة وسجّل الدخول وانتظر تحميل الصفحة."
                    );
                }

                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) {
                    throw new Exception(
                        "النتيجة ليست تقريرًا نصيًا صالحًا: " + String.valueOf(decoded)
                    );
                }

                report = (String) decoded;
                if (report.trim().isEmpty() ||
                    "null".equals(report.trim()) ||
                    "undefined".equals(report.trim())) {
                    throw new Exception("التقرير فارغ أو غير صالح.");
                }

                org.json.JSONObject root = new org.json.JSONObject(report);
                if (!root.has("url") || !root.has("cardCount") ||
                    !root.has("pageLength")) {
                    throw new Exception(
                        "نتيجة JavaScript لا تحتوي على حقول تقرير الروستر المطلوبة."
                    );
                }

                int cardCount = root.optInt("cardCount", -1);
                int pageLength = root.optInt("pageLength", 0);

                result.setText(report);

                String savedName = saveInspection(report);
                if (cardCount == 0) {
                    status.setText(
                        "تم حفظ تقرير التشخيص: " + savedName +
                        " — لم تُكتشف بطاقات مندوبين. تأكد من تسجيل الدخول واكتمال تحميل الروستر."
                    );
                } else if (pageLength == 0) {
                    status.setText(
                        "تم حفظ التقرير، لكن محتوى الصفحة فارغ. راجع التقرير: " + savedName
                    );
                } else {
                    status.setText(
                        "اكتمل الفحص الأولي: " + cardCount +
                        " بطاقة. التقرير محفوظ باسم: " + savedName
                    );
                }

                Toast.makeText(
                    this,
                    "تم حفظ التقرير: " + savedName,
                    Toast.LENGTH_LONG
                ).show();

            } catch (Exception e) {
                String message = "فشل الفحص أو التحقق من النتيجة: " +
                    e.getClass().getSimpleName() + " - " + e.getMessage();
                result.setText(message);
                status.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String saveInspection(String report) throws Exception {
        String fileName = "roster-inspection-" +
            new java.text.SimpleDateFormat(
                "yyyyMMdd-HHmmss", java.util.Locale.US
            ).format(new java.util.Date()) + ".txt";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/"
        );

        Uri uri = getContentResolver().insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        );

        if (uri == null) {
            throw new Exception("تعذر إنشاء التقرير في مجلد التنزيلات");
        }

        try (java.io.OutputStream out =
                getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new Exception("تعذر فتح ملف التقرير للكتابة");
            }
            out.write(report.getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ));
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
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
