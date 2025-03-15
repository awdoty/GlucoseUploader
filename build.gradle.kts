// Top-level build file

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "1.5.7"
}

// Optional clean task
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}