package com.beautysense.app.v2
import org.junit.Assert.*
import org.junit.Test
class MirrorStateTest {
 @Test fun cameraStartDoesNotRequireMicrophone() { assertEquals(Stage.ALIGN, MirrorState().start().stage) }
 @Test fun categoryReplacementAndVariantReplacement() {
  val s = MirrorState().select(Item("base", "a", "v1")).select(Item("base", "b", "v2")).select(Item("skin", "c", "v3"))
  assertEquals(2, s.items.size); assertEquals("b", s.items["base"]!!.productId)
 }
 @Test(expected=IllegalArgumentException::class) fun refusesBlankVariant() { MirrorState().select(Item("base","p","")) }
 @Test fun emptySelectionCannotGenerate() { assertFalse(MirrorState().canGenerate) }
 @Test fun lateJobCannotApplyAfterSelectionChanges() {
  val a = MirrorState().select(Item("base","a","v1")); val b = a.select(Item("base","b","v2"))
  assertFalse(b.accepts(a.epoch,a.revision)); assertTrue(b.accepts(b.epoch,b.revision))
 }
 @Test fun resetInvalidatesPrivateDataAndCallbacks() {
  val a=MirrorState().select(Item("base","a","v")); val b=a.reset()
  assertFalse(b.accepts(a.epoch,a.revision)); assertTrue(b.items.isEmpty()); assertEquals(Stage.ALIGN,b.stage)
 }
}
