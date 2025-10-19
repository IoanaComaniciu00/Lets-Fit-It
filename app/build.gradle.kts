plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.letsfitit"
    compileSdk = 35



    defaultConfig {
        applicationId = "com.example.letsfitit"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    aaptOptions {
        noCompress.add("tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packagingOptions {
        resources.excludes.add("META-INF/atomicfu.kotlin_module")
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/LICENSE-notice.md")
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.crashlytics.buildtools)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.glide)
    implementation(libs.gbutton)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation (libs.firebase.firestore.ktx)
    implementation(libs.lifecycle.viewmodel)
    implementation (libs.navigation.fragment)
    implementation (libs.navigation.ui)
    // implementation (libs.material.v180)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    annotationProcessor(libs.compiler)
    implementation (libs.pytorch.android.lite)
    implementation (libs.exifinterface)
    implementation(libs.ar.core)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    annotationProcessor(libs.compiler)

    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)


    //implementation (libs.camera)

    implementation( libs.camera.core)
    implementation (libs.camera.camera2)
    implementation (libs.camera.lifecycle)
    implementation (libs.camera.view)

    implementation (libs.guava)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mediapipe.tasks.core)




}