package com.beautysense.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordTest {
    @Test fun parsesNameAndFollowingRequest() {
        assertEquals(WakeUtterance(true, ""), WakeWord.parse("小妆"))
        assertEquals(WakeUtterance(true, "帮我找眉笔"), WakeWord.parse("小妆，帮我找眉笔"))
        assertEquals(WakeUtterance(true, "看看我的皮肤"), WakeWord.parse("你好，小妆！看看我的皮肤"))
        assertEquals(WakeUtterance(false, "今天想化淡妆"), WakeWord.parse("今天想化淡妆"))
    }
}
