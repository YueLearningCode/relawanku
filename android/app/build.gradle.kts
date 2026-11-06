plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.relawanku"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.relawanku"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        val apiUrl =
            providers.gradleProperty("apiBaseUrl").orElse("http://10.0.2.2:8000/api/").get()
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${apiUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Use a complete JDK for javac/jlink, even when the IDE daemon uses a bundled JRE.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.glide)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockwebserver)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
