# Reglas de R8 (L8-T1).
#
# Las librerías ya traen las suyas (consumer rules) y no se repiten aquí:
# - SQLCipher (net.zetetic): clases y métodos nativos de JNI.
# - Room: entidades, DAOs generados y la base (`MercadoDatabase_Impl`).
# - ML Kit (códigos y texto), CameraX, WorkManager, Biometric, DataStore.
# - kotlinx.serialization: serializadores generados por el plugin.
#
# Propias:

# DTOs @Serializable de la semilla, el respaldo y las rutas de navegación: se conservan el
# `Companion` y `serializer()` que el código generado busca por nombre.
-keepclassmembers @kotlinx.serialization.Serializable class co.daniel.mercado.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class co.daniel.mercado.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Los nombres de los enums se guardan como texto en la base y en el respaldo
# (UnitType, PriceSource, ListSource): `valueOf`/`name` deben seguir funcionando.
-keepclassmembers enum co.daniel.mercado.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Trazas de error legibles sin exponer nombres (solo archivo y línea).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit descubre sus componentes por reflexión (constructor sin argumentos de cada
# `ComponentRegistrar`). Con R8 en modo completo (AGP 9) sus reglas no bastan: sin esto el
# escáner y el lector de etiquetas fallan solo en release (visto en L8-T1 en el teléfono).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
