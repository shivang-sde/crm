package com.shivang.crm.modules.analytics.export;

/**
 * Minimal RFC-4180 style CSV field escaping plus CSV-injection (formula)
 * neutralization for string cells. Numeric cells are emitted unquoted so
 * spreadsheet applications parse them as numbers.
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (value instanceof String str) {
            if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
                s = "'" + s;
            }
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}