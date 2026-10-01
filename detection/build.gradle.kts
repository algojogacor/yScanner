plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yscanner.detection"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":geometry"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.camera.core)
    implementation(libs.opencv)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.bundles.unit.test)
}
