package com.beautysense.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardPcmDecoderTest {
    @Test fun keepsSixteenKilohertzPcmUnchanged() {
        val input = byteArrayOf(0, 0, 0x10, 0x27, 0xF0.toByte(), 0xD8.toByte())
        assertArrayEquals(input, BoardPcmDecoder.resample(input, 16_000))
    }

    @Test fun downsamplesStereoDecodedMonoToBoardRate() {
        val input = byteArrayOf(0, 0, 0x10, 0x27, 0x20, 0x4e, 0x30, 0x75)
        val output = BoardPcmDecoder.resample(input, 32_000)
        assertEquals(4, output.size)
        assertArrayEquals(byteArrayOf(0, 0, 0x20, 0x4e), output)
    }

    @Test fun interpolatesEightKilohertzSamples() {
        val output = BoardPcmDecoder.resample(byteArrayOf(0, 0, 0x10, 0x27), 8_000)
        assertEquals(8, output.size)
        assertArrayEquals(byteArrayOf(0, 0, 0x88.toByte(), 0x13, 0x10, 0x27, 0x10, 0x27), output)
    }
}
