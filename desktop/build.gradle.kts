plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.susking.ephone_s.desktop"
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

dependencies {
    // Core module
    implementation(project(":core"))
    implementation(project(":aidata"))
    
    // 功能模块依赖 - desktop 负责管理所有应用的导航
    implementation(project(":clouddreams"))
    implementation(project(":cphone"))
    implementation(project(":album"))
    implementation(project(":eventgraph"))
    implementation(project(":schedule"))
    implementation(project(":tavern"))
    implementation(project(":health"))
    
    // AndroidX 核心库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Hilt 依赖注入
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    
    // Gson for serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coil for image loading
    implementation("io.coil-kt:coil:2.6.0")
    
    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}