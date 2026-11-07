package com.beautysense.app.v2
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
/** A deadline is a recoverable failure; user cancellation still propagates unchanged. */
internal suspend fun generationDeadline(timeoutMillis:Long,isCurrent:()->Boolean,onTimeout:suspend ()->Unit,work:suspend ()->Unit) {
 try { withTimeout(timeoutMillis) { work() } }
 catch(_:TimeoutCancellationException) { if(isCurrent()) onTimeout() }
}
