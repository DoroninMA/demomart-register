import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val firebaseConfig = file("google-services.json")
if (firebaseConfig.isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val local = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.isFile) localFile.inputStream().use { local.load(it) }

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun serverUrl(key: String, fallback: String = ""): String =
    local.getProperty(key, fallback).trim().let { if (it.isEmpty()) it else it.trimEnd('/') + "/" }

val debugUrl = serverUrl("loyalty.serverUrl", "http://10.0.2.2:8000/")
val releaseUrl = serverUrl("loyalty.releaseServerUrl")
val debugMode = providers.gradleProperty("loyalty.authMode").orElse("fake").get()

android {
    namespace = "com.demomart.register"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.demomart.register"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "REGISTER_ID", quoted("R1"))
    }

    buildTypes {
        debug {
            buildConfigField("String", "SERVER_URL", quoted(debugUrl))
            buildConfigField("String", "AUTH_MODE", quoted(debugMode))
        }
        release {
            buildConfigField("String", "SERVER_URL", quoted(releaseUrl))
            buildConfigField("String", "AUTH_MODE", quoted("firebase"))
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    doLast {
        val url = runCatching { URI(releaseUrl) }.getOrNull()
        check(url?.scheme == "https" && !url.host.isNullOrEmpty()) {
            "Set loyalty.releaseServerUrl to an https:// URL in local.properties"
        }
        check(firebaseConfig.isFile) { "Release needs app/google-services.json" }
    }
}
val verifyDebugConfig = tasks.register("verifyDebugConfig") {
    doLast {
        check(debugMode == "fake" || debugMode == "firebase") {
            "loyalty.authMode must be fake or firebase"
        }
        check(debugMode != "firebase" || firebaseConfig.isFile) {
            "authMode=firebase needs app/google-services.json"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(verifyDebugConfig) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.compose)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.json)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.datastore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mock)
    testImplementation(libs.turbine)
}
