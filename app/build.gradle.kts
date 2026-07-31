import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// 签名配置：优先读环境变量，其次读项目根目录 keystore.properties（该文件已在 .gitignore 中忽略）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
  keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.alosir.xdown"
    minSdk = 24
    targetSdk = 36
    versionCode = 5
    versionName = "1.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = rootProject.file(
        System.getenv("KEYSTORE_PATH")
          ?: keystoreProperties.getProperty("storeFile")
          ?: "my-upload-key.jks"
      )
      storePassword = System.getenv("STORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
      keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
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

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
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
  "ksp"(libs.moshi.kotlin.codegen)
}

abstract class CopyLauncherTask : DefaultTask() {
    @get:InputFile
    var sourceIcon: java.io.File? = null

    @get:InputDirectory
    var resourceDir: java.io.File? = null

    @TaskAction
    fun execute() {
        val src = sourceIcon ?: return
        val res = resourceDir ?: return
        if (src.exists() && res.exists()) {
            val densities = listOf("hdpi", "mdpi", "xhdpi", "xxhdpi", "xxxhdpi")
            densities.forEach { density ->
                val destDir = res.resolve("mipmap-$density")
                if (destDir.exists()) {
                    destDir.resolve("ic_launcher.webp").delete()
                    destDir.resolve("ic_launcher_round.webp").delete()
                    src.copyTo(destDir.resolve("ic_launcher.png"), overwrite = true)
                    src.copyTo(destDir.resolve("ic_launcher_round.png"), overwrite = true)
                }
            }
        }
    }
}

val copyLauncherIcons = tasks.register<CopyLauncherTask>("copyLauncherIcons") {
    sourceIcon = file("src/main/res/drawable/ic_launcher_image.png")
    resourceDir = file("src/main/res")
}

tasks.matching { it.name.startsWith("preBuild") }.all {
    dependsOn(copyLauncherIcons)
}

