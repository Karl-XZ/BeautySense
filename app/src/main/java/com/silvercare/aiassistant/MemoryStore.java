package com.silvercare.aiassistant;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class MemoryStore {
    private static final String KEY_LOCATIONS = "locations_json";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_COSMETICS = "cosmetics_inventory_json";
    private static final String KEY_BEAUTY_PREF = "beauty_preferences_json";
    private static final int MAX_HISTORY = 100;
    private static final int MAX_COSMETICS = 100;

    private final SharedPreferences preferences;

    MemoryStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    synchronized void addCosmetic(String name, String category, String shade, String locationTag, String source) {
        if (name == null || name.trim().isEmpty()) return;
        try {
            JSONArray items = cosmeticsInventory();
            JSONObject existing = null;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (name.equalsIgnoreCase(item.optString("name", ""))) {
                    existing = item;
                    break;
                }
            }

            if (existing != null) {
                if (category != null && !category.isEmpty()) existing.put("category", category);
                if (shade != null && !shade.isEmpty()) existing.put("shade", shade);
                if (locationTag != null && !locationTag.isEmpty()) existing.put("location_tag", locationTag);
                if (source != null && !source.isEmpty()) existing.put("source", source);
                existing.put("last_updated", System.currentTimeMillis());
            } else {
                JSONObject newItem = new JSONObject()
                    .put("id", "cos_" + System.currentTimeMillis())
                    .put("name", name.trim())
                    .put("category", category == null ? "cosmetic" : category)
                    .put("shade", shade == null ? "" : shade)
                    .put("location_tag", locationTag == null ? "梳妆台" : locationTag)
                    .put("source", source == null ? "manual" : source)
                    .put("last_updated", System.currentTimeMillis());
                items.put(newItem);
            }

            while (items.length() > MAX_COSMETICS) {
                items.remove(0);
            }
            preferences.edit().putString(KEY_COSMETICS, items.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized JSONArray cosmeticsInventory() {
        try {
            return new JSONArray(preferences.getString(KEY_COSMETICS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    synchronized String cosmeticsSummary() {
        try {
            JSONArray items = cosmeticsInventory();
            if (items.length() == 0) return "暂未记录已有化妆品。";

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (sb.length() > 0) sb.append("，");
                sb.append(item.optString("name", ""));
                String shade = item.optString("shade", "");
                if (!shade.isEmpty()) sb.append("（色号:").append(shade).append("）");
                String loc = item.optString("location_tag", "");
                if (!loc.isEmpty()) sb.append("在“").append(loc).append("”");
            }
            return sb.toString();
        } catch (Exception e) {
            return "暂未记录已有化妆品。";
        }
    }

    synchronized String findCosmeticLocation(String query) {
        String cleanQuery = query == null ? "" : query.replaceAll("(在哪|放哪了|放哪儿|找一下|帮我找|帮找|我的|一个|请问|吗|呢)", "").trim();
        if (cleanQuery.isEmpty()) cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) return "";
        try {
            JSONArray items = cosmeticsInventory();
            for (int i = items.length() - 1; i >= 0; i--) {
                JSONObject item = items.getJSONObject(i);
                String name = item.optString("name", "");
                String category = item.optString("category", "");
                if (name.contains(cleanQuery) || cleanQuery.contains(name) ||
                    category.contains(cleanQuery) || cleanQuery.contains(category) ||
                    (cleanQuery.contains("口红") && (name.contains("口红") || name.contains("唇膏"))) ||
                    (cleanQuery.contains("粉底") && (name.contains("粉底") || name.contains("底妆"))) ||
                    (cleanQuery.contains("眉笔") && name.contains("眉笔"))) {
                    String loc = item.optString("location_tag", "");
                    String shade = item.optString("shade", "");
                    StringBuilder ans = new StringBuilder(name);
                    if (!shade.isEmpty()) ans.append("（").append(shade).append("）");
                    if (!loc.isEmpty()) ans.append("位于").append(loc);
                    else ans.append("已在您的梳妆台资产中");
                    return ans.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    synchronized void updateBeautyPreference(String key, String value) {
        if (key == null || key.trim().isEmpty()) return;
        try {
            JSONObject pref = beautyPreferences();
            pref.put(key.trim(), value == null ? "" : value.trim());
            preferences.edit().putString(KEY_BEAUTY_PREF, pref.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized JSONObject beautyPreferences() {
        try {
            return new JSONObject(preferences.getString(KEY_BEAUTY_PREF, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    synchronized String beautyPreferencesSummary() {
        try {
            JSONObject pref = beautyPreferences();
            if (pref.length() == 0) return "无特殊偏好（默认自然缎光风格）";
            StringBuilder sb = new StringBuilder();
            JSONArray names = pref.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (sb.length() > 0) sb.append("，");
                    sb.append(k).append(": ").append(pref.optString(k));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "无特殊偏好";
        }
    }

    synchronized void addLocation(String name, String description) {
        try {
            JSONObject locations = locations();
            locations.put(name, new JSONObject()
                .put("description", description == null ? "" : description)
                .put("timestamp", System.currentTimeMillis()));
            preferences.edit().putString(KEY_LOCATIONS, locations.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized void logObject(String objectName, String locationTag, String scene) {
        if (objectName == null || objectName.trim().isEmpty()) return;
        try {
            JSONArray history = history();
            if (history.length() > 0) {
                JSONObject last = history.getJSONObject(history.length() - 1);
                long lastTime = last.optLong("timestamp", 0L);
                String lastName = last.optString("name", last.optString("object", ""));
                if (objectName.equals(lastName) && System.currentTimeMillis() - lastTime < 10000) {
                    return;
                }
            }

            history.put(new JSONObject()
                .put("name", objectName)
                .put("location", locationTag == null ? "" : locationTag)
                .put("scene", scene == null ? "" : scene)
                .put("timestamp", System.currentTimeMillis()));

            while (history.length() > MAX_HISTORY) {
                history.remove(0);
            }

            preferences.edit().putString(KEY_HISTORY, history.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized String locationSummary() {
        try {
            JSONObject locations = locations();
            if (locations.length() == 0) return "还没有标记过的地点。";

            StringBuilder builder = new StringBuilder();
            JSONArray names = locations.names();
            if (names == null) return "还没有标记过的地点。";
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                JSONObject entry = locations.getJSONObject(name);
                if (builder.length() > 0) builder.append("，");
                builder.append("“").append(name).append("”：")
                    .append(entry.optString("description", ""));
            }
            return builder.toString();
        } catch (Exception e) {
            return "还没有标记过的地点。";
        }
    }

    synchronized String historyContext() {
        try {
            JSONArray history = history();
            if (history.length() == 0) return "还没有记录过物体历史。";

            int start = Math.max(0, history.length() - 30);
            StringBuilder builder = new StringBuilder();
            SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.CHINA);
            for (int i = start; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                if (builder.length() > 0) builder.append("\n");
                String time = format.format(new Date(entry.optLong("timestamp", System.currentTimeMillis())));
                builder.append("[").append(time).append("] 看到 ")
                    .append(entry.optString("name", entry.optString("object", "")));
                String location = entry.optString("location", "");
                if (!location.isEmpty()) {
                    builder.append(" 在“").append(location).append("”");
                }
                String scene = entry.optString("scene", "");
                if (!scene.isEmpty()) {
                    builder.append("（").append(scene).append("）");
                }
            }
            return builder.toString();
        } catch (Exception e) {
            return "还没有记录过物体历史。";
        }
    }

    synchronized String findObjectLocation(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) return "";
        try {
            JSONArray history = history();
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject entry = history.getJSONObject(i);
                String name = entry.optString("name", entry.optString("object", ""));
                if (name.isEmpty()) continue;
                if (!cleanQuery.contains(name) && !name.contains(cleanQuery)) continue;

                String location = entry.optString("location", "");
                String scene = entry.optString("scene", "");
                if (!location.isEmpty() && !scene.isEmpty()) return name + "在" + location + "，" + scene;
                if (!location.isEmpty()) return name + "在" + location;
                if (!scene.isEmpty()) return name + "最近出现在：" + scene;
                return name + "最近被看见过，但没有明确位置。";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private JSONObject locations() {
        try {
            return new JSONObject(preferences.getString(KEY_LOCATIONS, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONArray history() {
        try {
            return new JSONArray(preferences.getString(KEY_HISTORY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
