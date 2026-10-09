# Правила R8 для release-сборки.
# Room, Hilt, Coil, Retrofit, kotlinx.serialization и WorkManager поставляют свои consumer-правила.

# Маршруты навигации и DTO сериализуются kotlinx.serialization
-keepclassmembers @kotlinx.serialization.Serializable class ru.whattowear.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
