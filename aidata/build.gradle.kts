plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.susking.ephone_s.aidata"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // 允许Android框架类返回默认值，避免"not mocked"错误
            isReturnDefaultValues = true
        }
    }
}

// Health Connect(connect-client:1.1.0-alpha11) 声明了
// com.google.guava:listenablefuture:{strictly 9999.0-empty-to-avoid-conflict-with-guava}
// 这会把 WorkManager 依赖的真实 listenablefuture:1.0 顶成空壳，导致 ListenableFuture 类消失、
// getWorkInfosByTag().get() 等 WorkManager API 编译失败。
// 这里强制恢复 listenablefuture:1.0(仅含该接口的微型构件,正是 WorkManager 所需),不引入完整 guava。
configurations.all {
    resolutionStrategy {
        force("com.google.guava:listenablefuture:1.0")
    }
}

dependencies {
    // Core模块依赖
    implementation(project(":core"))

    // Kotlin标准库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Room数据库
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Kotlin协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Gson用于JSON序列化
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coil图片加载库
    implementation("io.coil-kt:coil:2.5.0")
    
    // DataStore用于桌面布局数据存储
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager用于后台任务
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Health Connect 客户端：从系统健康数据仓库读取步数/睡眠/心率等健康数据，
    // 供「AI 健康关怀」功能使用。1.1.0-alpha11 兼容 compileSdk 35（稳定版 1.1.0 需 36）。
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    
    // Hilt WorkManager集成
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.google.truth:truth:1.1.5")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}