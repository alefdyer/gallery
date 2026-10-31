import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.google.gms.google-services")
}

ksp {
    allWarningsAsErrors = true
}

kotlin {
    jvmToolchain {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

ktlint {
    android = true
    ignoreFailures = false
    reporters {
        reporter(ReporterType.HTML)
        reporter(ReporterType.PLAIN)
    }
}

android {
    namespace = "com.asinosoft.photogallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.asinosoft.photogallery"
        minSdk = 26
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // LibVLC/FFmpeg: без лишних ABI (armeabi-v7a/x86) — экономия десятков МБ.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(localProperties::load)
        }

        fun propOr(key: String, default: String): String =
            localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

        buildConfigField(
            "String",
            "DROPBOX_APP_KEY",
            "\"${propOr("dropbox.appKey", "")}\"",
        )
        buildConfigField(
            "String",
            "DROPBOX_REDIRECT_URI",
            "\"${propOr("dropbox.redirectUri", "com.asinosoft.photogallery://dropbox-auth")}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_APP_KEY",
            "\"${propOr("yandex.appKey", "b9d5243b463f443ab96529bd0ae607d4")}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_REDIRECT_URI",
            "\"${propOr("yandex.redirectUri", "com.asinosoft.photogallery://yandex-auth")}\"",
        )
        buildConfigField(
            "String",
            "ONEDRIVE_CLIENT_ID",
            "\"${propOr("onedrive.clientId", "")}\"",
        )
        buildConfigField(
            "String",
            "ONEDRIVE_REDIRECT_URI",
            "\"${propOr("onedrive.redirectUri", "com.asinosoft.photogallery://onedrive-auth")}\"",
        )
    }

    // Автоматический инкремент номера сборки
    val versionPropsFile = file("version.properties")
    if (versionPropsFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropsFile))
        val name = versionProps["VERSION_NAME"]
        val code = (versionProps["VERSION_CODE"] as String).toInt() + 1
        versionProps["VERSION_CODE"] = code.toString()
        versionProps.store(versionPropsFile.writer(), null)

        defaultConfig {
            versionCode = code
            versionName = "${name}"
            base.archivesName = "$applicationId@$versionName"
        }
    } else {
        throw GradleException("Could not read version.properties!")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    base.archivesName = "gallery@${defaultConfig.versionName}.${defaultConfig.versionCode}"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP emits the dependency list only for non-debuggable variants, so debug builds
// would show a placeholder "Debug License Info". Overwrite debug licenses with release.
afterEvaluate {
    val releaseLicenses = tasks.named("releaseOssLicensesTask")
    tasks.named("debugOssLicensesTask").configure {
        dependsOn(releaseLicenses)
        doLast {
            copy {
                from(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask"))
                into(layout.buildDirectory.dir("generated/res/debugOssLicensesTask"))
            }
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")

    // Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    // H.264 для шаринга в мессенджеры (HEVC там часто чёрный экран + звук)
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    // FFmpeg через LibVLC — MPEG/.MPG и прочие кодеки без MediaCodec
    implementation("org.videolan.android:libvlc-all:3.6.5")

    // Google Sign-In / Authorization (Drive App Data backup)
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.2")
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // YandexAds
    implementation("com.yandex.android:mobileads:8.6.0")

    // WebDAV
    implementation("com.github.thegrizzlylabs:sardine-android:0.9") {
        exclude(group = "stax", module = "stax-api")
        exclude(group = "xpp3", module = "xpp3")
    }

    // Coil
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")

    // Encrypted credentials (WebDAV/Nextcloud passwords, OAuth tokens)
    implementation("androidx.security:security-crypto:1.1.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")

    // Room
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // KtLint
    ktlintRuleset("io.nlopez.compose.rules:ktlint:0.6.8")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk-android:1.14.11")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
}
