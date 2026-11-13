import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android { namespace="fi.hospitalos"; compileSdk=36
 defaultConfig { applicationId="fi.hospitalos.demo"; minSdk=31; targetSdk=36; versionCode=1; versionName="0.1"
 manifestPlaceholders["mwdat_application_id"]=local.getProperty("mwdat_application_id","0")
 manifestPlaceholders["mwdat_client_token"]=local.getProperty("mwdat_client_token","0") }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies { implementation("androidx.activity:activity-ktx:1.10.1"); implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1"); implementation("com.squareup.okhttp3:okhttp:4.12.0"); implementation("com.meta.wearable:mwdat-core:1.0.1"); implementation("com.meta.wearable:mwdat-display:1.0.1") }
