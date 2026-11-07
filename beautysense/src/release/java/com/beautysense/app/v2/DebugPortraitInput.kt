package com.beautysense.app.v2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.json.JSONObject
/** Release builds never read or enable synthetic portrait input. */
object DebugPortraitInput {
 fun load(context:Context,intent:Intent):Bitmap?=null
 @Composable fun Preview(bitmap:Bitmap,modifier:Modifier,effect:JSONObject?,strength:Float,original:Boolean,onFrame:(LiveFrame)->Unit,onError:(String)->Unit) = Unit
}
