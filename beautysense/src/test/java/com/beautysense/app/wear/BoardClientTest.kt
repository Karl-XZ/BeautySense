package com.beautysense.app.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardClientTest {
    @Test fun signsCanonicalRequestWithHmacSha256() {
        assertEquals(
            "d30ba38b55447751d9f6a4762eb5536b075993ae03c22031e1379496dc7c9239",
            BoardClient.signature(
                "0123456789abcdefghijklmnopqrstuv",
                "GET",
                "/v1/status",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            )
        )
    }

    @Test fun acceptsPrivateIpv4Only() {
        assertTrue(BoardClient.validAddress("192.168.4.1"))
        assertTrue(BoardClient.validAddress("10.2.3.4"))
        assertTrue(BoardClient.validAddress("172.16.0.2"))
        assertTrue(BoardClient.validAddress("172.31.255.254"))
        assertFalse(BoardClient.validAddress("172.32.0.1"))
        assertFalse(BoardClient.validAddress("8.8.8.8"))
        assertFalse(BoardClient.validAddress("127.0.0.1"))
        assertFalse(BoardClient.validAddress("192.168.01.1"))
        assertFalse(BoardClient.validAddress("192.168.1.1:8787"))
        assertFalse(BoardClient.validAddress("example.com"))
        assertFalse(BoardClient.validAddress("192.168.1.256"))
    }
}
