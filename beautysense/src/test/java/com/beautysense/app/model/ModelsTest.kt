package com.beautysense.app.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun planParserPreservesModelAuthoredSpeechAndCriteria() {
        val json = JSONObject("""{"title":"路演清透妆","summary":"自然、精神","spokenIntro":"今天做路演清透妆，一共1步。现在开始吗？","estimatedMinutes":6,"steps":[{"id":"base","title":"薄涂粉底","productName":"粉底液","instruction":"在额头和两颊少量点涂。","spokenInstruction":"右手拿粉底液，在额头和两颊各点一点，再轻轻拍开。","completionCriteria":["没有明显漏涂","边缘自然"]}]}""")

        val plan = json.toMakeupPlan()

        assertEquals("路演清透妆", plan.title)
        assertEquals(1, plan.steps.size)
        assertEquals("粉底液", plan.steps[0].productName)
        assertTrue(plan.steps[0].spokenInstruction.contains("右手"))
        assertEquals("没有明显漏涂；边缘自然", plan.steps[0].completionCriterion)
    }

    @Test
    fun planParserReadsStructuredLookConfig() {
        val json = JSONObject("""{"title":"法式妆","lookConfig":{"version":3,"style":{"id":"french","name":"法式微醺"},"foundation":{"type":"cushion","typeName":"气垫","finish":"dewy","finishName":"水光","coverage":"light","coverageName":"轻薄遮盖"},"lip":{"type":"tint","typeName":"唇釉","colorName":"正红色","colorHex":"#C52F35","finish":"glossy","finishName":"水光"}},"steps":[]}""")

        val look = json.toMakeupPlan().lookConfig

        assertEquals("french", look.styleId)
        assertEquals("cushion", look.foundationType)
        assertEquals("#C52F35", look.lipColorHex)
        assertTrue(look.spokenSummary().contains("正红色"))
    }

    @Test
    fun verificationParserKeepsOptionalRepairChoice() {
        val result = JSONObject("""{"status":"REPAIR","repairLevel":"OPTIONAL","speech":"整体很好，只是建议再轻拍一下。继续微调还是进入下一步？","repairInstruction":"左眼下再轻拍一下","confidence":0.82,"hapticCue":"left"}""").toVerification()
        assertEquals("REPAIR", result.status)
        assertEquals("OPTIONAL", result.repairLevel)
        assertTrue(result.speech.contains("下一步"))
        assertEquals("left", result.hapticCue)
    }

    @Test
    fun skinParserReadsLocalPipelineScores() {
        val report = JSONObject("""{"scores":{"hydration":72,"oiliness":31,"redness":18,"texture":42,"pores":27,"blemishes":15,"pigment":22}}""").toSkinReport()
        assertEquals(7, report.metrics.size)
        assertEquals(72, report.metrics.first { it.key == "hydration" }.score)
    }
}
