// ================================================================================================
// ГЛАВНОЕ ANDROID ПРИЛОЖЕНИЕ - АУДИОСИНХРОНИЗАТОР
// Многоустройственная синхронная стерео-система для Android
// ОБНОВЛЕНО НА ИЮЛЬ 2025: Gradle 9.0.0, Kotlin 2.2.0, JDK 21, AGP 8.10.0
// ================================================================================================

// Использование общей конвенции для Android приложения
plugins {
    id("android-application-convention")
    id("kotlin-parcelize")
}

android {
    namespace = "ru.audiosynchronizer"
    
    defaultConfig {
        applicationId = "ru.audiosynchronizer"
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }
}

dependencies {
    // ============================================================================================
    // ВНЕШНИЕ ЗАВИСИМОСТИ ДЛЯ QR-КОДОВ И КАМЕРЫ
    // ============================================================================================
    
    // CameraX для работы с камерой
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Guava для CameraX (для решения проблемы с ListenableFuture)
    implementation("com.google.guava:guava:32.1.3-android")
    
    // ML Kit для сканирования QR-кодов
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // ZXing для генерации QR-кодов
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

