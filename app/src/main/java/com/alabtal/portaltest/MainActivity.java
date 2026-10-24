
private void inspectRoster() {
    status.setText("جاري فحص الصفحة والبحث عن بيانات الروستر...");
    result.setText("انتظر لحظة...");

    String script =
        "(function() {" +
        "var tables = Array.from(document.querySelectorAll('table'));" +
        "var target = document.querySelector('table.cape-table');" +
        "var allRows = [];" +
        "var headers = [];" +
        "var tableInfo = tables.map(function(t, i) {" +
        " var rs = Array.from(t.querySelectorAll('tr'));" +
        " return {" +
        "  index: i," +
        "  className: String(t.className || '')," +
        "  rowCount: rs.length," +
        "  headers: Array.from(t.querySelectorAll('thead th')).map(function(c) {" +
        "   return (c.innerText || c.textContent || '').trim();" +
        "  })" +
        " };" +
        "});" +

        "if (target) {" +
        " var head = target.querySelectorAll('thead th');" +
        " headers = Array.from(head).map(function(c) {" +
        "  return (c.innerText || c.textContent || '').trim();" +
        " });" +
        " var rows = Array.from(target.querySelectorAll('tr'));" +
        " allRows = rows.slice(0, 30).map(function(r) {" +
        "  return Array.from(r.querySelectorAll('th,td')).map(function(c) {" +
        "   return (c.innerText || c.textContent || '').trim();" +
        "  });" +
        " });" +
        "}" +

        "var bodyText = (document.body && document.body.innerText || '').trim();" +
        "return JSON.stringify({" +
        " ok: !!target," +
        " title: document.title," +
        " url: location.href," +
        " readyState: document.readyState," +
        " tableCount: tables.length," +
        " tableInfo: tableInfo," +
        " headers: headers," +
        " rowCount: target ? target.querySelectorAll('tr').length : 0," +
        " rows: allRows," +
        " pageText: bodyText.slice(0, 1800)" +
        "});" +
        "})()";

    webView.evaluateJavascript(script, value -> {
        try {
            Object decoded = new JSONTokener(value).nextValue();
            org.json.JSONObject data =
                new org.json.JSONObject(decoded.toString());

            StringBuilder out = new StringBuilder();

            out.append("=== نتيجة فحص الروستر ===\n");
            out.append("العنوان: ")
                .append(data.optString("title")).append("\n");
            out.append("الرابط: ")
                .append(data.optString("url")).append("\n");
            out.append("حالة التحميل: ")
                .append(data.optString("readyState")).append("\n");
            out.append("عدد الجداول: ")
                .append(data.optInt("tableCount")).append("\n");
            out.append("تم العثور على cape-table: ")
                .append(data.optBoolean("ok") ? "نعم" : "لا")
                .append("\n\n");

            org.json.JSONArray info = data.optJSONArray("tableInfo");
            if (info != null) {
                for (int i = 0; i < info.length(); i++) {
                    org.json.JSONObject t = info.optJSONObject(i);
                    if (t == null) continue;

                    out.append("جدول ").append(t.optInt("index"))
                        .append(" | class=")
                        .append(t.optString("className"))
                        .append(" | الصفوف=")
                        .append(t.optInt("rowCount"))
                        .append("\n");

                    org.json.JSONArray hs = t.optJSONArray("headers");
                    if (hs != null && hs.length() > 0) {
                        out.append("الأعمدة: ");
                        for (int j = 0; j < hs.length(); j++) {
                            if (j > 0) out.append(" | ");
                            out.append(hs.optString(j));
                        }
                        out.append("\n");
                    }
                }
            }

            out.append("\n=== أعمدة الروستر المستهدف ===\n");
            org.json.JSONArray headers = data.optJSONArray("headers");
            if (headers != null) {
                for (int i = 0; i < headers.length(); i++) {
                    out.append(i + 1).append(". ")
                        .append(headers.optString(i)).append("\n");
                }
            }

            out.append("\n=== أول 30 صفًا ===\n");
            org.json.JSONArray rows = data.optJSONArray("rows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    org.json.JSONArray row = rows.optJSONArray(i);
                    if (row == null) continue;

                    out.append("صف ").append(i + 1).append(": ");
                    for (int j = 0; j < row.length(); j++) {
                        if (j > 0) out.append(" | ");
                        out.append(row.optString(j));
                    }
                    out.append("\n");
                }
            }

            if (!data.optBoolean("ok")) {
                out.append("\n=== نص الصفحة للتشخيص ===\n")
                    .append(data.optString("pageText"));
            }

            result.setText(out.toString());
            status.setText(data.optBoolean("ok")
                ? "تم العثور على جدول الروستر."
                : "لم يتم العثور على الجدول؛ راجع تفاصيل الصفحة.");
        } catch (Exception e) {
            result.setText("خطأ أثناء قراءة النتيجة:\n"
                + e.getMessage() + "\n\nالرد الخام:\n" + value);
            status.setText("تعذر تحليل نتيجة الفحص.");
        }
    });
}
