import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jlleitschuh.gradle.ktlint")
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
    namespace = "com.asinosoft.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.asinosoft.gallery"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

//    base {
//        archivesName =
//            "gallery@${defaultConfig.versionName}.${defaultConfig.versionCode}"
//    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val roomVersion = "2.8.4"

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // DI
    implementation("com.google.dagger:hilt-android:2.59")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    ksp("com.google.dagger:hilt-android-compiler:2.59")

    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // KtLint
    ktlintRuleset("io.nlopez.compose.rules:ktlint:0.5.5")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.1")
}
