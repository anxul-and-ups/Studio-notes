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
    applicationId = "com.aistudio.aunotes.mstrly"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

    signingConfigs {
    create("persistent") {
      storeFile = rootProject.file("my-upload-key.jks")
      storePassword = "CodeForgeAU2026!"
      keyAlias = "upload"
      keyPassword = "CodeForgeAU2026!"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 shrinking + resource shrinking: produces a lightweight, fast production APK
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("persistent")
    }
    debug {
      // Use persistent signing key so locally built APKs can update without reinstalling
      signingConfig = signingConfigs.getByName("persistent")
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

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation("androidx.biometric:biometric:1.2.0-alpha05")
  implementation("androidx.fragment:fragment-ktx:1.8.6")
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.haze)

  // Installs the Compose baseline profile on first start (faster startup and scrolling)
  implementation("androidx.profileinstaller:profileinstaller:1.4.1")

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation("androidx.work:work-runtime-ktx:2.10.1")

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

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
      "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
      "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
      "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
    )
  }
}
