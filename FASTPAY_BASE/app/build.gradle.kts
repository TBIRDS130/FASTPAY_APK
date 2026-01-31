import java.util.Properties
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
    id("jacoco")
}

// Load keystore properties from file (if it exists)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { input ->
        keystoreProperties.load(input)
    }
}

// Load .env file from repo root (if it exists)
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
        versionCode = 30
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig values (from .env or defaults)
        buildConfigField(
            "String",
            "DJANGO_API_BASE_URL",
            "\"${envOrDefault("DJANGO_API_BASE_URL", "https://api.fastpaygaming.com")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_STORAGE_BUCKET",
            "\"${envOrDefault("FIREBASE_STORAGE_BUCKET", "fastpay-9d825.appspot.com")}\""
        )
    }

    signingConfigs {
        create("release") {
            // Use keystore.properties if available, otherwise fall back to defaults
            // This allows builds to work without keystore.properties (e.g., for debug builds)
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
                // Fallback for development (will fail for release builds without keystore.properties)
                // Developers should copy keystore.properties.template to keystore.properties
                storeFile = file("release.keystore")
                storePassword = ""
                keyAlias = ""
                keyPassword = ""
            }
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true  // ✅ Enable ProGuard/R8
            isShrinkResources = true  // ✅ Remove unused resources
            signingConfig = signingConfigs.getByName("release")  // ✅ Use release signing config
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
    
    // ViewPager2 for swipeable cards
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // Firebase BoM - manages all Firebase library versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.androidx.cardview)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)
    
    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.prexocore)
    
    // CircleImageView for contact avatars
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.airbnb.android:lottie:6.6.6")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Gson for JSON serialization (for DataCache)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Kotlin Coroutines (for NavigationPreloader)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    
    // Timber for logging
    implementation(libs.timber)
    
    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}

// Ensure Hilt compile-only configurations include required annotations
configurations.matching { it.name.startsWith("hiltCompileOnly") }.configureEach {
    dependencies.add(project.dependencies.create("com.google.dagger:hilt-android:2.57.2"))
    dependencies.add(project.dependencies.create("com.google.dagger:hilt-core:2.57.2"))
}

// Create testClasses task for compatibility with tools that expect it
// Android Gradle Plugin uses testDebugUnitTestClasses/testReleaseUnitTestClasses instead
tasks.register("testClasses") {
    description = "Compiles test classes for all build types"
    group = "verification"
    dependsOn("testDebugUnitTestClasses")
}

kapt {
    correctErrorTypes = true
    arguments {
        // Ensure Hilt processor receives Gradle plugin args (workaround for missing args)
        arg("dagger.fastInit", "enabled")
        arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
        arg("dagger.hilt.android.internal.projectType", "APP")
        arg("dagger.hilt.internal.useAggregatingRootProcessor", "false")
    }
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.11"
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/di/**",
        "**/hilt/**"
    )
    
    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

tasks.register("jacocoTestCoverageVerification", JacocoCoverageVerification::class) {
    dependsOn("jacocoTestReport")
    
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal() // 50% minimum coverage
            }
        }
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/di/**",
        "**/hilt/**"
    )
    
    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(fileFilter)
    }
    
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec")
    })
}