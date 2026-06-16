plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.susking.ephone_s.settings"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
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
    }
}

dependencies {
    // 模块依赖
    implementation(project(":core"))
    implementation(project(":aidata"))
    implementation(project(":desktop"))

    // Kotlin标准库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Fragment和ViewModel
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // UI组件
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // Kotlin协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Gson用于JSON序列化
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Retrofit（用于模型拉取功能）
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp（用于检查更新、意见反馈的轻量 HTTP 请求）
    implementation(libs.okhttp)

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation(libs.glide)
    ksp(libs.glide.ksp)
}