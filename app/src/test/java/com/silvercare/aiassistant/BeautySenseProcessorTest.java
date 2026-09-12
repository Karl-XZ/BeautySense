package com.silvercare.aiassistant;

import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class BeautySenseProcessorTest {

    @Test
    public void testBeautyPlanProposalAndAcceptance() {
        TestFakes.AiClient ai = new TestFakes.AiClient();
        TestFakes.Sink sink = new TestFakes.Sink();
        MemoryStore memory = new MemoryStore(new TestFakes.Preferences());
        SilverCareProcessor processor = new SilverCareProcessor(ai, memory, sink);

        String sampleImage = "data:image/png;base64,sample";

        // 1. 用户提出化妆诉求
        processor.processTextInquiry(sampleImage, "帮我化个妆");
        JSONObject proposalSpeak = sink.firstOfType("speak");
        assertThat(proposalSpeak, notNullValue());
        assertThat(proposalSpeak.optString("text"), containsString("方案"));

        // 2. 用户表示同意
        processor.processTextInquiry(sampleImage, "好的，开始吧");
        JSONObject stepUpdate = lastOfType(sink, "task_update");
        assertThat(stepUpdate, notNullValue());
        assertThat(stepUpdate.optString("visual_feedback"), containsString("第一步"));
        assertThat(stepUpdate.optString("speech"), containsString("粉底液"));
    }

    @Test
    public void testBeautyPlanNegotiation() {
        TestFakes.AiClient ai = new TestFakes.AiClient();
        TestFakes.Sink sink = new TestFakes.Sink();
        MemoryStore memory = new MemoryStore(new TestFakes.Preferences());
        SilverCareProcessor processor = new SilverCareProcessor(ai, memory, sink);

        String sampleImage = "data:image/png;base64,sample";

        // 1. 提议方案
        processor.processTextInquiry(sampleImage, "怎么化妆好呢");
        // 2. 用户协商调整：要求淡一点
        processor.processTextInquiry(sampleImage, "口红太艳了，换淡一点的");

        JSONObject adjustSpeak = lastOfType(sink, "speak");
        assertThat(adjustSpeak, notNullValue());
        assertThat(adjustSpeak.optString("text"), containsString("裸杏"));
    }

    @Test
    public void testBeautyStepQualityInspectionAndProgression() {
        TestFakes.AiClient ai = new TestFakes.AiClient();
        TestFakes.Sink sink = new TestFakes.Sink();
        MemoryStore memory = new MemoryStore(new TestFakes.Preferences());
        SilverCareProcessor processor = new SilverCareProcessor(ai, memory, sink);

        String sampleImage = "data:image/png;base64,sample";

        // 提议 -> 同意 -> 进入第一步
        processor.processTextInquiry(sampleImage, "帮我化妆");
        processor.processTextInquiry(sampleImage, "好的开始");

        // 用户说画好了 -> 触发质检
        processor.processTextInquiry(sampleImage, "我已经画好了");
        JSONObject passUpdate = lastOfType(sink, "task_update");
        assertThat(passUpdate, notNullValue());
        // 质检通过进入第二步
        assertThat(passUpdate.optString("visual_feedback"), containsString("第二步"));
        assertThat(passUpdate.optString("speech"), containsString("眉笔"));
    }

    @Test
    public void testCosmeticRecordAndFind() {
        TestFakes.AiClient ai = new TestFakes.AiClient();
        TestFakes.Sink sink = new TestFakes.Sink();
        MemoryStore memory = new MemoryStore(new TestFakes.Preferences());
        SilverCareProcessor processor = new SilverCareProcessor(ai, memory, sink);

        String sampleImage = "data:image/png;base64,sample";

        // 1. 记物
        processor.processTextInquiry(sampleImage, "记住我把YSL口红放了在梳妆台右侧");
        JSONObject recordResult = lastOfType(sink, "inquiry_result");
        assertThat(recordResult, notNullValue());
        assertThat(recordResult.optString("speech"), containsString("长效记录"));

        // 2. 找物
        processor.processTextInquiry(sampleImage, "口红放哪了");
        JSONObject findResult = lastOfType(sink, "inquiry_result");
        assertThat(findResult, notNullValue());
        assertThat(findResult.optString("speech"), containsString("梳妆台右侧"));
    }

    @Test
    public void testAccessibilityModeInstructions() {
        TestFakes.AiClient ai = new TestFakes.AiClient();
        TestFakes.Sink sink = new TestFakes.Sink();
        MemoryStore memory = new MemoryStore(new TestFakes.Preferences());
        SilverCareProcessor processor = new SilverCareProcessor(ai, memory, sink);
        processor.setBeautyMode("accessible");

        String sampleImage = "data:image/png;base64,sample";

        // 提议方案
        processor.processTextInquiry(sampleImage, "我想化个妆");
        JSONObject proposalSpeak = lastOfType(sink, "speak");
        assertThat(proposalSpeak.optString("text"), containsString("无障碍美妆方案"));

        // 用户同意开始
        processor.processTextInquiry(sampleImage, "好，开始");
        JSONObject stepUpdate = lastOfType(sink, "task_update");
        // 无障碍模式下应包含触觉/身体锚点导向
        assertThat(stepUpdate.optString("speech"), containsString("摸到"));
    }

    private static JSONObject lastOfType(TestFakes.Sink sink, String type) {
        JSONObject found = null;
        for (JSONObject message : sink.messages) {
            if (type.equals(message.optString("type"))) found = message;
        }
        return found;
    }
}
