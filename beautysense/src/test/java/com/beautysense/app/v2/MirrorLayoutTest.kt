package com.beautysense.app.v2
import org.junit.Assert.*
import org.junit.Test
class MirrorLayoutTest {
 @Test fun portraitLeavesReadablePanelAndProtectsWholeOval() {
  for((w,h) in listOf(393f to 787f,375f to 603f,420f to 816f)) {
   val l=mirrorLayout(w,h)
   assertTrue(l.panel.height>=h*.4f)
   assertTrue(l.oval.height>l.oval.width*1.25f)
   assertTrue(l.oval.y>=0f)
   assertTrue(l.oval.y+l.oval.height+12f<=l.panel.y)
  }
 }
 @Test fun landscapeKeepsInfoBesideFace() {
  val l=mirrorLayout(851f,329f)
  assertTrue(l.camera.x+l.camera.width<=l.panel.x)
  assertTrue(l.panel.width>=851f*.38f)
  assertTrue(l.oval.x+l.oval.width<l.panel.x)
 }
 @Test fun chinOutsideOvalCannotConfirmEvenWhenNoseIsCentered() {
  val l=mirrorLayout(393f,787f)
  val p=(0 until 478).map {(.5f+.18f*kotlin.math.cos(it.toFloat())) to (.5f+.28f*kotlin.math.sin(it.toFloat()))}.toMutableList()
  assertTrue(faceInGuide(p,540,720,l))
  p[152]=.5f to .99f
  assertFalse(faceInGuide(p,540,720,l))
 }
 @Test fun collapsedMirrorKeepsOriginalProtectedAreaAndOnlySmallControls() {
  val open=mirrorLayout(393f,787f);val closed=mirrorLayout(393f,787f,collapsed=true)
  assertEquals(open.oval,closed.oval)
  assertEquals(56f,closed.panel.height,0f)
  assertTrue(closed.oval.y+closed.oval.height<closed.panel.y)
  assertEquals(787f,closed.camera.height,0f)
 }

}
