plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.umbra.qr"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.umbra.qr"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Ship only the languages the app is translated into; drops unused library translations.
        localeFilters += listOf("en", "pt-rBR")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        // Backports Java 9+ library APIs (Optional.or, String.strip, ...) used by :core to minSdk 26.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Do not embed the Google-encrypted dependency metadata block in the APK/AAB.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        // The viewfinder animates forever; Espresso needs an idle main thread.
        animationsDisabled = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Also check :core for APIs missing on older Android versions.
        checkDependencies = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
}
