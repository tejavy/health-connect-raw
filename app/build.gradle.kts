plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
fun signingValue(name: String): String {
    val value = providers.environmentVariable(name).orNull
    if (releaseRequested && value.isNullOrBlank()) throw GradleException("Release signing requires $name.")
    return value.orEmpty()
}
val signingPath = signingValue("HEALTH_RAW_KEYSTORE_PATH")
val signingPassword = signingValue("HEALTH_RAW_STORE_PASSWORD")
val signingAlias = signingValue("HEALTH_RAW_KEY_ALIAS")
val signingKeyPassword = signingValue("HEALTH_RAW_KEY_PASSWORD")
android {
    namespace = "space.tejaswi.healthraw"
    compileSdk = 36
    defaultConfig {
        applicationId = "space.tejaswi.healthraw"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }
    signingConfigs {
        create("release") {
            if (releaseRequested) {
                require(file(signingPath).isFile) { "Health Raw release keystore is missing." }
                storeFile = file(signingPath)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Raw export inspects public SDK getters; preserve names and properties.
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
