plugins {
    id("com.android.library")
}

android {
    namespace = "com.miniproject.transport.tor"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-crypto"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("info.guardianproject:tor-android:0.4.8.22")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
