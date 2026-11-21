import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// F-Droid builds are always unsigned, even if this checkout has local credentials.
val fdroidBuild = providers.gradleProperty("fdroid").map(String::toBoolean).getOrElse(false)
val localSigning = Properties()
val signingFile = rootProject.file("signing.properties")
if (!fdroidBuild && signingFile.isFile) signingFile.inputStream().use(localSigning::load)
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasLocalSigning = signingKeys.all { !localSigning.getProperty(it).isNullOrBlank() }
if (!fdroidBuild && signingFile.isFile && !hasLocalSigning) {
    throw GradleException("signing.properties must define storeFile, storePassword, keyAlias and keyPassword")
}

android {
    namespace = "com.ashik.volumedot"
    compileSdk { version = release(37) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.ashik.floatvol"
        minSdk = 28
        targetSdk = 37
        versionCode = 10
        versionName = "3.0.2"
    }

    signingConfigs {
        if (hasLocalSigning) create("localRelease") {
            storeFile = rootProject.file(localSigning.getProperty("storeFile"))
            storePassword = localSigning.getProperty("storePassword")
            keyAlias = localSigning.getProperty("keyAlias")
            keyPassword = localSigning.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Keep pinned release builds independent of online tool-update notices.
        // Report available upgrades without treating them as app correctness errors.
        informational += "AndroidGradlePluginVersion"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("localRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Robolectric's Android 16 sandbox requires Java 21+. Keep app bytecode at Java 17.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.1")
    implementation("androidx.savedstate:savedstate-ktx:1.5.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    testImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
}
