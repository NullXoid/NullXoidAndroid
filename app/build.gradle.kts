plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.nullxoid.android"
    compileSdk = 34

    val updateSigningStoreFile = providers.gradleProperty("NULLXOID_SIGNING_STORE_FILE")
        .orElse(providers.environmentVariable("NULLXOID_SIGNING_STORE_FILE"))
    val updateSigningStorePassword = providers.gradleProperty("NULLXOID_SIGNING_STORE_PASSWORD")
        .orElse(providers.environmentVariable("NULLXOID_SIGNING_STORE_PASSWORD"))
    val updateSigningKeyAlias = providers.gradleProperty("NULLXOID_SIGNING_KEY_ALIAS")
        .orElse(providers.environmentVariable("NULLXOID_SIGNING_KEY_ALIAS"))
    val updateSigningKeyPassword = providers.gradleProperty("NULLXOID_SIGNING_KEY_PASSWORD")
        .orElse(providers.environmentVariable("NULLXOID_SIGNING_KEY_PASSWORD"))
    val hasUpdateSigning = listOf(
        updateSigningStoreFile,
        updateSigningStorePassword,
        updateSigningKeyAlias,
        updateSigningKeyPassword
    ).all { it.isPresent }

    defaultConfig {
        applicationId = "com.nullxoid.android"
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.gradleProperty("APP_VERSION_CODE")
            .orElse(providers.environmentVariable("APP_VERSION_CODE"))
            .map(String::toInt)
            .getOrElse(14)
        versionName = providers.gradleProperty("APP_VERSION_NAME")
            .orElse(providers.environmentVariable("APP_VERSION_NAME"))
            .getOrElse("0.1.14")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (hasUpdateSigning) {
            create("update") {
                storeFile = file(updateSigningStoreFile.get())
                storePassword = updateSigningStorePassword.get()
                keyAlias = updateSigningKeyAlias.get()
                keyPassword = updateSigningKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["networkSecurityConfig"] = "@xml/debug_network_security_config"
            if (hasUpdateSigning) {
                signingConfig = signingConfigs.getByName("update")
            }
        }
        release {
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Embedded on-device backend (Ktor CIO server)
    val ktor = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-cio:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-default-headers:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
}
