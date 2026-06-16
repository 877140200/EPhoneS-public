plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.susking.ephone_s.qq"
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
        dataBinding = true
    }
    
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        named("main") {
            res.srcDirs("src/main/res/layout/bubble")
            res.srcDirs("src/main/res/layout/forward")
            res.srcDirs("src/main/res/layout/qq")
            res.srcDirs("src/main/res/layout/QQcontactList")
            res.srcDirs("src/main/res/layout/QQfeed")
            res.srcDirs("src/main/res/layout/QQicon_bar")
            res.srcDirs("src/main/res/layout/QQmemories")
            res.srcDirs("src/main/res/layout/QQfavorites")
            res.srcDirs("src/main/res/layout/QQsticker")
            res.srcDirs("src/main/res/layout/QQvideo_call")
            res.srcDirs("src/main/res/layout/wallet")
            res.srcDirs("src/main/res/layout/search")
            res.srcDirs("src/main/res/layout")
            res.srcDirs("src/main/res")
        }
    }
}

dependencies {
    // 核心模块依赖
    implementation(project(":core"))
    implementation(project(":aidata"))

    // Brain 模块
    implementation(project(":brain"))

    implementation(project(":settings"))

    // 可选依赖
    implementation(project(":album"))
    
    // Kotlin 标准库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    
    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Hilt 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Glide 图片加载
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    
    // Coil 图片加载
    implementation(libs.coil)
    
    // 其他必要库
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.work.runtime.ktx)
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // 日历视图
    implementation("com.kizitonwose.calendar:view:2.5.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")


    // 测试依赖
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // For JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Pinyin helper for converting Chinese characters to Pinyin
    implementation("com.github.promeg:tinypinyin:2.0.3") // Core library

    implementation(libs.image.cropper)
    implementation(libs.photoview)

    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3") // Optional: for logging requests
    
    // Markwon - Markdown 和 HTML 渲染库
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2") // HTML 支持
    implementation("io.noties.markwon:image:4.6.2") // 图片支持
    implementation("io.noties.markwon:linkify:4.6.2") // 自动链接识别
    implementation("io.noties.markwon:ext-strikethrough:4.6.2") // 删除线支持
    implementation("io.noties.markwon:ext-tables:4.6.2") // 表格支持
    
    // Jsoup - HTML 解析库，用于解析网页内容
    implementation("org.jsoup:jsoup:1.17.2")
}