plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Temporarily disable custom plugins to isolate issues
    // id("memexos.android.test")
    // id("memexos.static.analysis")
    // id("memexos.performance.test")
}

android {
    namespace = "com.memexagent.app"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.memexagent.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            // For PoC, only supporting arm64-v8a and x86_64
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fexceptions")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    // Simplified workaround for JdkImageTransform issues
    System.setProperty("com.android.build.gradle.overrideVersionCheck", "true")
    
    buildFeatures {
        viewBinding = true
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Voice-controlled browser agent dependencies
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("androidx.mediarouter:mediarouter:1.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Enhanced JSON processing for command parsing
    implementation("org.json:json:20231013")
    
    // LeakCanary for memory leak detection (debug builds only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.5.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
