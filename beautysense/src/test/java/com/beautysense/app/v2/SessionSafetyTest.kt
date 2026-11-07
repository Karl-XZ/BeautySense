package com.beautysense.app.v2
import org.junit.Assert.*
import org.junit.Test
class SessionSafetyTest {
 @Test fun continuityStartsBeforeFirstAnalysisCompletes() { assertTrue(SessionSafety.requiresContinuity(Stage.ANALYZE)); assertTrue(SessionSafety.requiresContinuity(Stage.GENERATE)); assertFalse(SessionSafety.requiresContinuity(Stage.ALIGN)); assertFalse(SessionSafety.requiresContinuity(Stage.HOME)) }
 @Test fun analysisRequiresAlignmentAndNoPriorReport() {
  assertTrue(SessionSafety.canAnalyze(Stage.ALIGN,false))
  assertFalse(SessionSafety.canAnalyze(Stage.LIVE,true))
  assertFalse(SessionSafety.canAnalyze(Stage.ALIGN,true))
 }
 @Test fun anyMissingOrMultipleFaceEndsAnalyzedSession() {
  assertTrue(SessionSafety.endForTracking(true,0)); assertTrue(SessionSafety.endForTracking(true,2))
  assertFalse(SessionSafety.endForTracking(true,1)); assertFalse(SessionSafety.endForTracking(false,0))
 }
 @Test fun oldOwnerCannotReleaseNewOperation() {
  val ownership=OperationOwnership(); val old=ownership.begin(); val current=ownership.begin()
  assertFalse(ownership.owns(old)); assertTrue(ownership.owns(current)); ownership.invalidate(); assertFalse(ownership.owns(current))
 }
}
