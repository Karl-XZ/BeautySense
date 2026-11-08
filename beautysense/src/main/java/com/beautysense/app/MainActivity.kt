package com.beautysense.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.beautysense.app.ui.BeautySenseTheme
import com.beautysense.app.v2.*
class MainActivity:ComponentActivity() {
 private val model by viewModels<MirrorViewModel>()
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(255,250,246);window.navigationBarColor=android.graphics.Color.rgb(255,250,246);androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}; val synthetic=DebugPortraitInput.load(this,intent); setContent { BeautySenseTheme { MirrorScreen(model,synthetic) } } }
 override fun onStop() { super.onStop(); model.suspendCamera() }
}
