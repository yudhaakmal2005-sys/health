plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
}

// Konfigurasi dapat di-override lewat gradle.properties / -P / env agar tidak ada
// IP, domain, atau kredensial yang di-hardcode di source code.
fun cfg(name: String, default: String): String =
  (project.findProperty(name) as String?) ?: System.getenv(name.uppercase().replace('.', '_')) ?: default

android {
  namespace = "id.sehati.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "id.sehati.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Rilis hanya ditandatangani bila keystore disediakan lewat environment.
    val ks = System.getenv("KEYSTORE_PATH")
    if (ks != null && file(ks).exists()) {
      create("release") {
        storeFile = file(ks)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      buildConfigField("String", "BASE_URL", "\"${cfg("sehati.baseUrl.debug", "http://10.0.2.2:8080/")}\"")
      buildConfigField("boolean", "DEMO_MODE", "true")
      buildConfigField("boolean", "VERBOSE_LOG", "true")
    }
    create("staging") {
      initWith(getByName("release"))
      applicationIdSuffix = ".staging"
      versionNameSuffix = "-staging"
      matchingFallbacks += listOf("release")
      signingConfig = signingConfigs.getByName("debug")
      buildConfigField("String", "BASE_URL", "\"${cfg("sehati.baseUrl.staging", "https://staging.sehati.invalid/")}\"")
      buildConfigField("boolean", "DEMO_MODE", "true")
      buildConfigField("boolean", "VERBOSE_LOG", "false")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
      buildConfigField("String", "BASE_URL", "\"${cfg("sehati.baseUrl.release", "https://api.sehati.invalid/")}\"")
      buildConfigField("boolean", "DEMO_MODE", "false")
      buildConfigField("boolean", "VERBOSE_LOG", "false")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

ksp {
  arg("room.generateKotlin", "true")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.icons.extended)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.sqlite.ktx)
  implementation(libs.sqlcipher)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.health.connect)

  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.zxing.core)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
