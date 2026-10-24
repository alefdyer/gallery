package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.asinosoft.photogallery.R
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.minutes

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    onClicked: () -> Unit = {}
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val adView = remember { mutableStateOf<BannerAdView?>(null) }
    val adSize = remember { mutableStateOf<BannerAdSize?>(null) }
    val adUnitId = stringResource(R.string.yandex_ads_unit_id)

    LaunchedEffect(adView.value, adSize.value, adUnitId) {
        adView.value?.let { adView ->
            adSize.value?.let { adSize ->
                adView.setAdSize(adSize)

                while (isActive) {
                    adView.loadAd(AdRequest.Builder(adUnitId).build())
                    delay(1.minutes)
                }
            }
        }
    }

    AndroidView(
        factory = {
            BannerAdView(it).apply {
                adView.value = this
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClicked)
            .onPlaced { place ->
                place.parentCoordinates?.size?.let { size ->
                    adSize.value = BannerAdSize.fixed(
                        context,
                        (size.width / density).toInt(),
                        (size.height / density).toInt()
                    )
                }
            }
    )
}
