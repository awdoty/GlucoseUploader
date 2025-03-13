// Top-level build file

plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.android.library") version "8.9.0" apply false
}

// Optional clean task
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}