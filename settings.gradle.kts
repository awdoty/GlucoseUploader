pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.android.application") version "8.9.0" apply false
    id("com.android.library") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GlucoseUploader"
include(":app")