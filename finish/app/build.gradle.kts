plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.gms.google-services")
}

android {
    namespace = "org.tensorflow.lite.codelabs.textclassification"
    compileSdk = 33

    defaultConfig {
        applicationId = "org.tensorflow.lite.codelabs.textclassification"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(fileTree("libs").include("*.jar"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.android.support.constraint:constraint-layout:2.0.4")
    implementation("org.jetbrains:annotations:15.0")

    // TODO 1: Add Firebase ML dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.0.0"))
    implementation("com.google.firebase:firebase-ml-modeldownloader:24.1.2")

    // TODO 4: Add TFLite Task API (Text) dependency
    implementation("org.tensorflow:tensorflow-lite-task-text:0.3.0")

    testImplementation("androidx.test:core:1.5.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation ("org.robolectric:robolectric:4.3.1")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
}

project(":app").tasks {
    withType(Test::class.java) {
        enabled = false
    }
}
