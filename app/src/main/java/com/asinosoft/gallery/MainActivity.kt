package com.asinosoft.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.asinosoft.gallery.data.ImageRepository
import com.asinosoft.gallery.ui.MainView
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GalleryTheme {
                when (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    PackageManager.PERMISSION_GRANTED -> MainView(
                        repo = ImageRepository(applicationContext)
                    )

                    else -> PermissionDisclaimer {
                        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
                    }
                }
            }
        }
    }
}
