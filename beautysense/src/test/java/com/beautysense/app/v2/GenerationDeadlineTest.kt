package com.beautysense.app.v2
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class GenerationDeadlineTest {
 @Test fun actualTimeoutCancelsAndReturnsToSelection()=runBlocking {
  var stage=Stage.GENERATE; var busy=true; var cancels=0; var message=""
  try { generationDeadline(15,{true},{stage=Stage.SELECT;busy=false;cancels++;message="生成超时，请重试"}) { delay(1000) } } catch(_:TimeoutCancellationException) {}
  assertEquals(Stage.SELECT,stage);assertFalse(busy);assertEquals(1,cancels);assertTrue(message.contains("超时"))
 }
 @Test fun expiredOldRequestCannotChangeNewSession()=runBlocking {
  val owners=OperationOwnership();val old=owners.begin();var stage=Stage.ALIGN;var cancels=0
  generationDeadline(15,{owners.owns(old)},{stage=Stage.SELECT;cancels++}) {owners.invalidate();delay(1000)}
  assertEquals(Stage.ALIGN,stage);assertEquals(0,cancels)
 }
 @Test fun ordinaryCancellationDoesNotBecomeTimeout()=runBlocking {
  var timeout=false;var propagated=false
  try { generationDeadline(1000,{true},{timeout=true}) {throw CancellationException("user cancelled")} } catch(_:CancellationException) {propagated=true}
  assertTrue(propagated);assertFalse(timeout)
 }
}
