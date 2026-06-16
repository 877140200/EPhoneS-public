import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    id("kotlin-parcelize")
}

// 从 local.properties 读取签名密钥配置（该文件已被 .gitignore 排除，密码不进仓库）。
// 缺少任一字段时 signingConfig 不生效，release 包会回退到默认（debug）签名，便于本地无密钥时仍能构建。
val keystorePropertiesFile: File = rootProject.file("local.properties")
val keystoreProperties: Properties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.susking.ephone_s"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    // 正式发布签名配置。
    // 四个字段从 local.properties 读取：RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD /
    // RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD。任一缺失则跳过该配置，避免本地无密钥时构建失败。
    signingConfigs {
        val storeFilePath: String? = keystoreProperties.getProperty("RELEASE_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.susking.ephone_s"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 优先使用正式 release 签名；若 local.properties 未配置密钥则该配置不存在，
            // 回退为 null（即默认 debug 签名），保证无密钥环境仍可构建调试。
            // 正式发布给网友的包，务必在 local.properties 配好密钥后再打 release。
            signingConfig = signingConfigs.findByName("release")
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
            res.srcDirs("src/main/res/layout/brain")
            res.srcDirs("src/main/res/layout/theme")
            res.srcDirs("src/main/res/layout/wallet")
            res.srcDirs("src/main/res/layout/worldbook")
            res.srcDirs("src/main/res/layout")
            res.srcDirs("src/main/res")
        }
    }
}

dependencies {
    // Core 模块（共享资源）
    implementation(project(":core"))
    // aidata 模块
    implementation(project(":aidata"))
    // Brain 模块
    implementation(project(":brain"))
    // Desktop 模块（包含了对其他功能模块的依赖）
    implementation(project(":desktop"))
    
    // Album 模块 - app 模块的 EPhoneSApplication 需要实现 Album 的接口
    implementation(project(":album"))
    
    // Alipay 模块 - 支付宝功能
    implementation(project(":alipay"))
    
    // Settings 模块 - 设置界面
    implementation(project(":settings"))
    
    // Shopping 模块 - 商城功能
    implementation(project(":shopping"))
    
    // QQ 模块 - QQ 功能
    implementation(project(":qq"))

    // Schedule 模块 - 课程表与校园动态功能
    implementation(project(":schedule"))

    // 酒馆模块
    implementation(project(":tavern"))
    
    // 注意: CloudDreams、CPhone 等功能模块的依赖已经移到 desktop 模块中
    // app 模块不再直接依赖这些模块,通过 desktop 模块间接访问

    implementation(libs.photoview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0") // For LiveData testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("io.insert-koin:koin-test:3.5.0")
    androidTestImplementation("io.insert-koin:koin-test-junit4:3.5.0")


    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // 添加 viewModelScope 支持
    // ProcessLifecycleOwner：提供应用级（整个进程）的前后台生命周期状态，
    // 供后台 Worker 判断 AI 来电时用户是否正在使用小手机（前台→弹界面，后台→发全屏通知）。
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")

    // For networking
    implementation(libs.okhttp)
    // For JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // DataStore for persistent storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Health Connect 客户端：从系统健康数据仓库读取睡眠/步数/心率/血氧等。
    // 仅做数据来源验证，后续正式集成会下沉到 aidata 模块。
    // 用 1.1.0-alpha11（兼容 compileSdk 35）；稳定版 1.1.0 要求 compileSdk 36，暂不升级。
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    ksp("androidx.room:room-compiler:2.6.1")

    // Room 依赖
    implementation("androidx.room:room-runtime:2.6.1") // 请使用最新稳定版本

    // Room Kotlin 协程支持 (可选，但推荐)
    implementation("androidx.room:room-ktx:2.6.1")

    implementation(libs.image.cropper)
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.work.runtime.ktx)

    implementation("javax.inject:javax.inject:1")


    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Hilt Work - 支持 WorkManager 依赖注入
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // build.gradle (Groovy)
    implementation("io.coil-kt:coil:2.6.0")

    // SwipeRevealLayout for swipe-to-reveal functionality
    implementation(libs.swipereveallayout)

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-android:3.5.0")

    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3") // Optional: for logging requests

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}

