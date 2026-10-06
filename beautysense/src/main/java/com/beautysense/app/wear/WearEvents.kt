package com.beautysense.app.wear

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface WearEvent {
    data class FallCandidate(val accelerationG: Float, val angularVelocity: Float) : WearEvent
    data class BoardStatus(val connected: Boolean, val detail: String) : WearEvent
    data object ServiceStopped : WearEvent
}

object WearEvents {
    private val mutableEvents = MutableSharedFlow<WearEvent>(extraBufferCapacity = 8)
    val events = mutableEvents.asSharedFlow()

    fun emit(event: WearEvent) {
        mutableEvents.tryEmit(event)
    }
}
