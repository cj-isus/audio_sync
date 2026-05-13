pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Добавляем репозитории для отсутствующих зависимостей
        maven { url = uri("https://repo1.maven.org/maven2/") } // Maven Central
        maven { url = uri("https://jcenter.bintray.com/") } // JCenter (для старых библиотек)
        maven { url = uri("https://maven.google.com/") } // Google Maven
        maven { url = uri("https://jitpack.io/") } // JitPack (для GitHub библиотек)
    }
}

rootProject.name = "АудиоСинхронизатор"

// ============================================================================================
// ОПТИМИЗИРОВАННАЯ СТРУКТУРА МОДУЛЕЙ ПРОЕКТА
// ============================================================================================

// Основное приложение
include(":приложение")

// Упрощенная архитектура - только основные модули
// Остальные модули удалены для оптимизации проекта