# ================================================================================================
# ПРАВИЛА PROGUARD ДЛЯ АУДИОСИНХРОНИЗАТОР
# Конфигурация обфускации и оптимизации кода для релизной сборки
# ================================================================================================

# ----------------------------------------------------------------------------
# ОБЩИЕ ПРАВИЛА ANDROID
# ----------------------------------------------------------------------------

# Сохраняем номера строк для отладки крашей в продакшене
-keepattributes SourceFile,LineNumberTable

# Переименовываем исходный файл для скрытия структуры проекта
-renamesourcefileattribute SourceFile

# Сохраняем аннотации для рефлексии
-keepattributes *Annotation*

# ----------------------------------------------------------------------------
# KOTLIN И КОРУТИНЫ
# ----------------------------------------------------------------------------

# Сохраняем метаданные Kotlin для корректной работы рефлексии
-keep class kotlin.Metadata { *; }

# Правила для Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Сохраняем suspend функции
-keep class * {
    kotlin.jvm.functions.Function* *;
}

# ----------------------------------------------------------------------------
# JETPACK COMPOSE
# ----------------------------------------------------------------------------

# Сохраняем Composable функции
-keep @androidx.compose.runtime.Composable class * { *; }

# Правила для Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# ----------------------------------------------------------------------------
# HILT DEPENDENCY INJECTION
# ----------------------------------------------------------------------------

# Сохраняем классы с Hilt аннотациями
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Сохраняем Hilt компоненты
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keep class dagger.hilt.** { *; }

# ----------------------------------------------------------------------------
# СЕТЕВЫЕ БИБЛИОТЕКИ
# ----------------------------------------------------------------------------

# OkHttp и Retrofit (если используются)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# JSON сериализация (Gson, Moshi, Kotlinx.serialization)
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ----------------------------------------------------------------------------
# АУДИО И BLUETOOTH
# ----------------------------------------------------------------------------

# Android Media и AudioManager
-keep class android.media.** { *; }
-keep class android.bluetooth.** { *; }

# ExoPlayer (если используется)
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ----------------------------------------------------------------------------
# МОДЕЛИ ДАННЫХ ПРОЕКТА
# ----------------------------------------------------------------------------

# Сохраняем все data классы (модели для передачи по сети и хранения)
-keep class ru.аудиосинхронизатор.**.model.** { *; }
-keep class ru.аудиосинхронизатор.**.entity.** { *; }
-keep class ru.аудиосинхронизатор.**.dto.** { *; }

# Сохраняем Parcelable классы
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ----------------------------------------------------------------------------
# ROOM DATABASE (если используется)
# ----------------------------------------------------------------------------

-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ----------------------------------------------------------------------------
# ПРОИЗВОДИТЕЛЬНОСТЬ И ОПТИМИЗАЦИЯ
# ----------------------------------------------------------------------------

# Удаляем логи в релизной сборке
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Оптимизация кода
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# ----------------------------------------------------------------------------
# ПОЛЬЗОВАТЕЛЬСКИЕ ПРАВИЛА
# ----------------------------------------------------------------------------

# Сохраняем все классы синхронизации аудио (критически важные для функционала)
-keep class ru.аудиосинхронизатор.ядро.синхронизация.** { *; }
-keep class ru.аудиосинхронизатор.ядро.аудио.** { *; }
-keep class ru.аудиосинхронизатор.функции.синхронизатор.** { *; }

# Сохраняем сетевые протоколы (важно для совместимости между устройствами)
-keep class ru.аудиосинхронизатор.сеть.протоколы.** { *; }

# Сохраняем публичные API интерфейсы
-keep public class ru.аудиосинхронизатор.**.api.** { 
    public *; 
}