package com.silvercare.aiassistant;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;

public class MemoryStoreTest {
    @Test
    public void addLocationIncludesLocationInSummary() {
        MemoryStore store = new MemoryStore(new TestFakes.Preferences());

        store.addLocation("家门口", "白色门，旁边有鞋柜");

        assertThat(store.locationSummary(), containsString("家门口"));
        assertThat(store.locationSummary(), containsString("鞋柜"));
    }

    @Test
    public void logObjectDeduplicatesImmediateRepeatedObject() {
        MemoryStore store = new MemoryStore(new TestFakes.Preferences());

        store.logObject("杯子", "桌面", "木桌上有杯子");
        store.logObject("杯子", "桌面", "木桌上有杯子");

        String history = store.historyContext();
        assertThat(history, containsString("杯子"));
        assertThat(history.split("\\R").length, equalTo(1));
    }

    @Test
    public void cosmeticsInventoryRecordsAndFindsCosmetics() {
        MemoryStore store = new MemoryStore(new TestFakes.Preferences());

        store.addCosmetic("欧莱雅小黑管口红", "唇膏", "666法式正红", "梳妆台右侧收纳盒", "manual");
        store.addCosmetic("兰蔻菁纯散粉", "散粉", "01透明色", "梳妆台左上角", "manual");

        String summary = store.cosmeticsSummary();
        assertThat(summary, containsString("欧莱雅小黑管口红"));
        assertThat(summary, containsString("666法式正红"));
        assertThat(summary, containsString("梳妆台右侧收纳盒"));

        String found = store.findCosmeticLocation("口红");
        assertThat(found, containsString("欧莱雅小黑管口红"));
        assertThat(found, containsString("梳妆台右侧收纳盒"));

        store.updateBeautyPreference("风格偏好", "清透自然裸妆");
        assertThat(store.beautyPreferencesSummary(), containsString("清透自然裸妆"));
    }
}
