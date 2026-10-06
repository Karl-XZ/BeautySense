package com.beautysense.app.audio

data class WakeUtterance(val addressed: Boolean, val command: String)

object WakeWord {
    private val prefix = Regex("^(?:(?:你好|您好|嗨|嘿|喂)[，,。！!？?\\s]*)?小妆(?:[，,。！!？?：:\\s]*小妆)?[，,。！!？?：:\\s]*")

    fun parse(value: String): WakeUtterance {
        val text = value.trim()
        val match = prefix.find(text) ?: return WakeUtterance(false, text)
        return WakeUtterance(true, text.substring(match.value.length).trim())
    }
}
