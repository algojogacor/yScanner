plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yscanner.pdf"
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
    implementation(project(":processing"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.bundles.unit.test)
}
