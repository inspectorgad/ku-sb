plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Play-compatible id (com.example.* is rejected by Google Play); also
    // distinct from the KU WBB, KU Volleyball, and KC Diamonds apps so they
    // all install side by side.
    applicationId = "io.github.inspectorgad.kusb"
    minSdk = 24
    targetSdk = 36
    // Play requires a strictly increasing versionCode; CI passes the
    // workflow run number.
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = "1.0." + (System.getenv("VERSION_CODE") ?: "0")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // The upload key is supplied by the environment or not at all. It used to
    // be a keystore committed to this public repository with its password in
    // this file, which was defensible only while Play App Signing held the
    // real key — and this app has never been published, so it signed an AAB
    // nobody ever downloaded. The keystore is gone; see PLAY-SETUP.md for
    // generating a fresh one when the app does go to Play.
    val uploadKeystore = System.getenv("KEYSTORE_PATH")
    val uploadStorePassword = System.getenv("STORE_PASSWORD")
    val uploadKeyPassword = System.getenv("KEY_PASSWORD")
    if (uploadKeystore != null && uploadStorePassword != null && uploadKeyPassword != null) {
      create("release") {
        storeFile = file(uploadKeystore)
        storePassword = uploadStorePassword
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = uploadKeyPassword
      }
    }
    // The debug keystore stays committed on purpose. Its password is
    // "android", the value every Android debug keystore in the world uses, so
    // it is not a secret — and keeping one fixed debug identity is what lets
    // `adb install -r` replace an installed build instead of demanding an
    // uninstall that would take the reader's hand-entered stat lines with it.
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Signed only when the upload key was supplied by the environment.
      // Without it the release build is simply unsigned — it still compiles,
      // it just cannot be uploaded anywhere, which is the honest outcome
      // rather than signing with a key published in this repository.
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
