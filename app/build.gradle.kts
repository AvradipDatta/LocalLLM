plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  id("com.google.gms.google-services")
  id("org.jetbrains.kotlin.kapt")
}

android {
  namespace = "com.google.ai.edge.gallery"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.google.aiedge.gallery"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.4"

    // Needed for HuggingFace auth workflows.
    manifestPlaceholders["appAuthRedirectScheme"] = "com.google.ai.edge.gallery.oauth"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
    freeCompilerArgs += "-Xcontext-receivers"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      // Exclude duplicate META-INF files or pick first occurrence
      excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/LICENSE.md",
        "META-INF/NOTICE.md"
      )
    }
  }


}
/*
repositories {
  google()
  mavenCentral()
}
*/


protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:4.26.1"
  }
  generateProtoTasks {
    all().forEach {
      it.plugins {
        create("java") {
          option("lite") // or full option if needed
        }
      }
    }
  }
}


dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.com.google.code.gson)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.mediapipe.tasks.text)
  implementation(libs.mediapipe.tasks.genai)
  implementation(libs.mediapipe.tasks.imagegen)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.tflite)
  implementation(libs.tflite.gpu)
  implementation("io.coil-kt:coil-compose:2.5.0")

  implementation(libs.tflite.support)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.protobuf.javalite)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  kapt(libs.hilt.android.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.hilt.android.testing)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  // Firebase & Google sign-in
  implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-analytics")
  implementation("com.google.android.gms:play-services-auth:20.1.0")

  // Network and API clients
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.google.api-client:google-api-client-android:1.35.0")
  implementation("com.google.apis:google-api-services-gmail:v1-rev110-1.25.0")
  //implementation("com.google.api-client:google-api-client-android:1.34.0")
  implementation("com.google.apis:google-api-services-gmail:v1-rev110-1.25.0")
  implementation("com.google.http-client:google-http-client-gson:1.43.3")
  implementation("com.google.http-client:google-http-client-android:1.43.3") // <- ✅ required for AndroidHttp
  implementation("com.sun.mail:android-mail:1.6.7")
  implementation("com.sun.mail:android-activation:1.6.7")
  implementation("com.google.api-client:google-api-client-android:1.33.2")
  implementation("com.google.api-client:google-api-client-gson:1.33.2")





}

/*protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.26.1" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}*/
