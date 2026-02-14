import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
}

// Load keystore properties from file (if it exists)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { input ->
        keystoreProperties.load(input)
    }
}

// Load .env file from project root (FASTPAY_BASE) if it exists
val envFile = rootProject.file(".env")
val env = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val idx = trimmed.indexOf("=")
        if (idx <= 0) return@forEach
        val key = trimmed.substring(0, idx).trim()
        val value = trimmed.substring(idx + 1).trim().trim('"')
        env[key] = value
    }
}

fun envOrDefault(key: String, defaultValue: String): String {
    val raw = env[key] ?: defaultValue
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

// When building from CLI scripts: use build_cli so R.jar isn't locked by IDE/Defender in app/build
if (project.hasProperty("cliBuildDir")) {
    layout.buildDirectory.set(project.layout.projectDirectory.dir("build_cli"))
}

android {
    namespace = "com.example.fast"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.fast"
        minSdk = 27
        targetSdk = 36
        versionCode = 411
        versionName = "4.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Debug builds use staging API by default (unit and instrumented tests).
        buildConfigField(
            "String",
            "DJANGO_API_BASE_URL",
            "\"${envOrDefault("DJANGO_API_BASE_URL", "https://api-staging.fastpaygaming.com/api")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_STORAGE_BUCKET",
            "\"${envOrDefault("FIREBASE_STORAGE_BUCKET", "fastpay-9d825.appspot.com")}\""
        )
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val keystoreFile = keystoreProperties["KEYSTORE_FILE"] as String?
                storeFile = if (keystoreFile != null) {
                    file(keystoreFile)
                } else {
                    file("release.keystore")
                }
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String? ?: ""
                keyAlias = keystoreProperties["KEY_ALIAS"] as String? ?: ""
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String? ?: ""
            } else {
                storeFile = file("release.keystore")
                storePassword = ""
                keyAlias = ""
                keyPassword = ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    applicationVariants.all {
        val variant = this
        val istFormat = SimpleDateFormat("ddMM-HHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val timestamp = istFormat.format(Date())
        val prefix = if (variant.buildType.name == "debug") "d" else "r"
        variant.outputs.all {
            val output = this as BaseVariantOutputImpl
            output.outputFileName =
                "${prefix}fastpay-${variant.versionCode}-${timestamp}.apk"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.androidx.cardview)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    implementation(libs.prexocore)

    implementation(libs.circleimageview)
    implementation(libs.lottie)
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.timber)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}

// APKFILE: FASTPAY_APK/APKFILE - old APKs are kept (no delete)
val apkfileDir = file("${rootProject.rootDir.parent}/APKFILE")

tasks.register("copyReleaseApk", Copy::class) {
    description = "Copy release APK to repo root APKFILE/"
    group = "distribution"
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("rfastpay-*.apk")
    }
    into(apkfileDir)
    doFirst {
        apkfileDir.mkdirs()
    }
}

tasks.register("copyDebugApk", Copy::class) {
    description = "Copy debug APK to repo root APKFILE/"
    group = "distribution"
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("dfastpay-*.apk")
    }
    into(apkfileDir)
    doFirst {
        apkfileDir.mkdirs()
    }
}

tasks.register("testClasses") {
    description = "Compiles test classes for all build types"
    group = "verification"
    dependsOn("testDebugUnitTestClasses")
}
