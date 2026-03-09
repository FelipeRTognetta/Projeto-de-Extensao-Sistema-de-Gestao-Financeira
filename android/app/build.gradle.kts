plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.google.devtools.ksp")
    id("jacoco")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.psychologist.financial"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.psychologist.financial"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // Replace with real signing in production
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database + SQLCipher
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // Security & Biometrics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.google.crypto.tink:tink-android:1.10.0")

    // DataStore (replaces SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // CSV Export
    implementation("org.apache.commons:commons-csv:1.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON Serialization (backup export/import)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Testing - Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Testing - Android Instrumented Tests
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // Testing - Room Database Testing
    testImplementation("androidx.room:room-testing:2.6.1")

    // Debugging
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Code Coverage (JaCoCo)
    testImplementation("org.jacoco:org.jacoco.core:0.8.10")
}

// JaCoCo Configuration for Code Coverage
jacoco {
    toolVersion = "0.8.10"
}

// Task to generate test coverage report
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
    }

    val fileFilter = setOf(
        "**/R.class",
        "**/R$*.class",
        "**/*Test*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*"
    )

    val debugTree = fileTree(mapOf(
        "dir" to "${layout.buildDirectory}/intermediates/classes/debug",
        "excludes" to fileFilter
    ))

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom("src/main/kotlin")
    executionData.setFrom(fileTree(mapOf(
        "dir" to layout.buildDirectory,
        "includes" to listOf("jacoco/testDebugUnitTest.exec")
    )))
}

// Shorthand for running JaCoCo coverage
tasks.register("testCoverage") {
    dependsOn("testDebugUnitTest", "jacocoTestReport")
    doLast {
        println("✓ Code coverage report generated at: build/reports/jacoco/test/html/index.html")
    }
}
