package com.faisal.freshdownloader

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.ads.MaxAdView

@Composable
fun AppLovinBanner(modifier: Modifier = Modifier) {
    val sdkReady by AdsManager.initialized
    var loadFailed by remember { mutableStateOf(false) }
    var adView by remember { mutableStateOf<MaxAdView?>(null) }

    if (!sdkReady || !AdsConfig.bannerConfigured || loadFailed) return

    DisposableEffect(sdkReady, loadFailed) {
        onDispose {
            runCatching { adView?.destroy() }
            adView = null
        }
    }

    AndroidView(
        factory = { context ->
            AdsManager.createBanner(
                context = context,
                onLoaded = { loadFailed = false },
                onLoadFailed = { loadFailed = true }
            )?.also { adView = it } ?: FrameLayout(context)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}
