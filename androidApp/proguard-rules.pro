# Правила R8/ProGuard для Efir (Android).

# Сохраняем kotlinx.serialization-модели протокола (полиморфная (де)сериализация
# использует сгенерированные сериализаторы по имени класса).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class ru.efir.core.protocol.** {
    *** Companion;
}
-keepclasseswithmembers class ru.efir.core.protocol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.efir.core.**$$serializer { *; }

# Ktor: движки и сериализация используют reflection.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Coroutines / kotlinx.datetime
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.datetime.**

# Службы и приёмники, объявленные в манифесте, не удаляем.
-keep class ru.efir.core.input.EfirAccessibilityService { *; }
-keep class ru.efir.core.comms.EfirNotificationListenerService { *; }
-keep class ru.efir.core.comms.EfirSmsReceiver { *; }
-keep class ru.efir.android.** { *; }
