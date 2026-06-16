plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.susking.ephone_s.clouddreams"
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
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    
    // Room
    implementation(libs.androidx.room.runtime)
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.json)
    
    // RecyclerView
    implementation(libs.androidx.recyclerview)
    implementation(libs.swipereveallayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    
    // Markwon - Markdown渲染库
    implementation(libs.core) // Markwon核心库
    implementation(libs.html) // HTML支持
    implementation(libs.linkify) // 链接自动识别
    implementation(libs.ext.tables) // 表格支持
    implementation(libs.ext.tasklist) // 任务列表支持
    implementation(libs.ext.strikethrough) // 删除线支持
    implementation(libs.ext.latex) // LaTeX支持
    implementation(libs.image.glide) // 图片支持（使用Glide）
    
    // JSoup - HTML清理
    implementation(libs.jsoup)
    
    // Glide - 图片加载
    implementation(libs.glide)
    
    // Core模块依赖
    implementation(project(":core"))
    
    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}