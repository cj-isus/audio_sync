// ============================================================================================
// BUILDSRC - ЦЕНТРАЛИЗОВАННЫЕ КОНВЕНЦИИ ПРОЕКТА
// ============================================================================================
// ОБНОВЛЕНО НА ИЮЛЬ 2025: Gradle 9.0.0, Kotlin 2.2.0, JDK 21, AGP 8.10.0
// Устраняет дублирование кода и обеспечивает архитектурную целостность
// ============================================================================================

plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Gradle API для создания плагинов
    implementation("com.android.tools.build:gradle:8.13.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.57")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.0")
}

// Настройки компиляции для JDK 21
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

