package com.beautysense.app.v2
internal object SessionSafety {
 fun requiresContinuity(stage:Stage)=stage!=Stage.HOME && stage!=Stage.ALIGN
 fun canAnalyze(stage:Stage,hasAnalysis:Boolean)=stage==Stage.ALIGN && !hasAnalysis
 fun endForTracking(hasAnalysis:Boolean,faceCount:Int)=hasAnalysis && faceCount!=1
}
internal class OperationOwnership {
 private var version=0L
 fun current()=version
 fun begin()=++version
 fun invalidate() { version++ }
 fun owns(value:Long)=value==version
}
