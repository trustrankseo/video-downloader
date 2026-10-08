package com.faisal.freshdownloader

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AppLovinBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val initialized by AdsManager.initializedState
    if (!AdsManager.isConfigured || !initialized) return

    val adView = remember(context, initialized) {
        AdsManager.createBanner()
    } ?: return

    DisposableEffect(adView) {
        onDispose {
            runCatching { adView.destroy() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                adView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
    }
}
