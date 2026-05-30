import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.volunteer.manager"
    compileSdk = 34

    // Đọc tệp local.properties để lấy API Key an toàn (tệp này được bỏ qua bởi Git)
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }
    val weatherApiKey = localProperties.getProperty("WEATHER_API_KEY") ?: "\"ee462d8db8119a5b1bc859d6b7560033\""
    val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: "\"AIzaSyA1sqnfb58KFDudvcIesCBRo-N0alDQh_Y\""

    defaultConfig {
        applicationId = "com.volunteer.manager"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Khai báo API Key vào BuildConfig
        buildConfigField("String", "WEATHER_API_KEY", weatherApiKey)

        // Tự động sinh ra string resource google_maps_key bảo mật từ local.properties
        resValue("string", "google_maps_key", mapsApiKey.replace("\"", ""))
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.swiperefreshlayout)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.messaging.ktx)

    // Maps & Location
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Retrofit & Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.glide)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}