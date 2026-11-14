import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

val local =
    Properties().apply {
      rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

android {
  namespace = "fi.hospitalos"
  compileSdk = 36
  defaultConfig {
    applicationId = "fi.hospitalos.demo"
    minSdk = 31
    targetSdk = 36
    versionCode = 2
    versionName = "0.2"
    // Developer Mode uses "0"; production IDs go in local.properties, never Git.
    manifestPlaceholders["mwdat_application_id"] = local.getProperty("mwdat_application_id", "0")
    manifestPlaceholders["mwdat_client_token"] = local.getProperty("mwdat_client_token", "0")
  }
  buildFeatures { compose = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  debugImplementation("com.meta.wearable:mwdat-mockdevice:1.0.1")
  implementation(platform("androidx.compose:compose-bom:2026.05.01"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.meta.wearable:mwdat-core:1.0.1")
  implementation("com.meta.wearable:mwdat-display:1.0.1")
  implementation("com.meta.wearable:mwdat-motion:1.0.1")
  implementation("com.meta.wearable:mwdat-speech:1.0.1")
  // On-phone Gemma (MediaPipe LLM Inference). The model file is pushed separately, not bundled.
  implementation("com.google.mediapipe:tasks-genai:0.10.35")
}
