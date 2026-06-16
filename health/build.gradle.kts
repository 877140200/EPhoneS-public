plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.susking.ephone_s.health"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

// 与 aidata 同理：Health Connect 把 listenablefuture 顶成空壳，强制恢复 1.0 微型构件。
configurations.all {
    resolutionStrategy {
        force("com.google.guava:listenablefuture:1.0")
    }
}

dependencies {
    // 共享资源与数据层
    implementation(project(":core"))
    implementation(project(":aidata"))

    // AndroidX 核心库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Lifecycle（ViewModel + 协程）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Health Connect：健康页内发起权限请求需要 PermissionController 契约。
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // Hilt 依赖注入（@AndroidEntryPoint 注入 HealthRepository）
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
