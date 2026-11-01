package com.lightweather.desktop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（零依赖，够用即可）。
 * 解析结果为：Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        return v;
    }

    // ---------------- 取值辅助 ----------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static Object get(Object o, String... path) {
        Object cur = o;
        for (String p : path) {
            if (cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(p);
            } else if (cur instanceof List && isInt(p)) {
                List<?> list = (List<?>) cur;
                int idx = Integer.parseInt(p);
                cur = idx >= 0 && idx < list.size() ? list.get(idx) : null;
            } else {
                return null;
            }
        }
        return cur;
    }

    public static List<Object> list(Object o, String key) {
        Object v = get(o, key);
        return v instanceof List ? arr(v) : new ArrayList<>();
    }

    public static double d(Object o, String key, double def) {
        Object v = get(o, key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static int i(Object o, String key, int def) {
        return (int) Math.round(d(o, key, def));
    }

    public static String s(Object o, String key, String def) {
        Object v = get(o, key);
        return v == null ? def : String.valueOf(v);
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static double num(Object o, double def) {
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public static int inum(Object o, int def) {
        return o instanceof Number ? (int) Math.round(((Number) o).doubleValue()) : def;
    }

    private static boolean isInt(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    // ---------------- 解析实现 ----------------

    private Object value() {
        skipWs();
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                pos += 4;
                return Boolean.TRUE;
            case 'f':
                pos += 5;
                return Boolean.FALSE;
            case 'n':
                pos += 4;
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (pos < src.length()) {
            skipWs();
            if (src.charAt(pos) == '}') {
                pos++;
                break;
            }
            String key = string();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ':') pos++;
            Object v = value();
            map.put(key, v);
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                break;
            }
            break;
        }
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (pos < src.length()) {
            list.add(value());
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                break;
            }
            break;
        }
        return list;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        pos++; // "
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                char e = src.charAt(pos++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double number() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || Character.isDigit(c)) pos++;
            else break;
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    // ---------------- 序列化（缓存用） ----------------

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeTo(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                escape(sb, e.getKey());
                sb.append(':');
                writeTo(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object v : (List<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                writeTo(sb, v);
            }
            sb.append(']');
        } else if (o instanceof String) {
            escape(sb, (String) o);
        } else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else {
            sb.append(o);
        }
    }

    private static void escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
