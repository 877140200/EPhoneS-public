pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "EPhone-S"
include(":app")
include(":album")
include(":alipay")
include(":brain")
include(":core")
include(":clouddreams")
include(":aidata")
include(":cphone")
include(":desktop")
include(":settings")
include(":shopping")
include(":qq")
include(":eventgraph")
include(":schedule")
include(":tavern")
include(":health")