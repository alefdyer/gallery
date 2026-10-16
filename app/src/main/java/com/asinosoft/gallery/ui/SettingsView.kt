package com.asinosoft.gallery.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.asinosoft.gallery.BuildConfig
import com.asinosoft.gallery.R
import kotlinx.coroutines.launch

private const val TAB_NETWORK = 0
private const val TAB_GALLERY = 1
private const val TAB_ABOUT = 2
private const val TAB_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onClose: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = TAB_NETWORK == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_NETWORK) } },
                    text = {
                        Text(
                            text = stringResource(R.string.network),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = TAB_GALLERY == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_GALLERY) } },
                    text = {
                        Text(
                            text = stringResource(R.string.gallery_tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = TAB_ABOUT == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_ABOUT) } },
                    text = {
                        Text(
                            text = stringResource(R.string.about_app),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val pageModifier = Modifier.fillMaxSize()
                when (page) {
                    TAB_NETWORK -> StoragesView(pageModifier)
                    TAB_GALLERY -> GallerySettingsTab(pageModifier)
                    else -> AboutAppTab(
                        version = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
                        onSayThanks = {
                            val market = Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=${BuildConfig.APPLICATION_ID}".toUri()
                            )
                            val web = Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}".toUri()
                            )
                            try {
                                context.startActivity(market)
                            } catch (_: Exception) {
                                context.startActivity(web)
                            }
                        },
                        onRecommend = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            context.getString(R.string.share_app_text)
                                        )
                                    },
                                    context.getString(R.string.recommend_friends)
                                )
                            )
                        },
                        onSupport = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    "mailto:${context.getString(R.string.support_email)}".toUri()
                                )
                            )
                        },
                        onLicenses = onOpenLicenses,
                        onPrivacy = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    context.getString(R.string.privacy_policy_url).toUri()
                                )
                            )
                        },
                        modifier = pageModifier
                    )
                }
            }
        }
    }
}
