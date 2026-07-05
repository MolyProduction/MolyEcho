# Keep JNI bridge – native methods must not be renamed
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Whisper JNI classes (accessed by name from native code)
-keep class com.whispercpp.whisper.** { *; }

# Keep sherpa-onnx JNI config classes – the native .so accesses fields by name via
# GetFieldID (e.g. "decodingMethod", "featureDim"). R8 must not rename these fields.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# NOTE: The former blanket keeps
#   -keep class com.module.notelycompose.** { *; }
#   -keep class de.molyecho.notlyvoice.** { *; }
# neutralized R8 for the entire app code (larger DEX, zero shrinking). Koin resolves
# constructors at compile time and needs no keeps; Activities/Services are kept via the
# manifest; the JNI callback methods keep their names because the WhisperCallback
# interface (com.whispercpp.**) is kept above and R8 never renames overrides of kept
# interface methods. If a release build ever crashes with reflection errors, re-add a
# targeted keep for the affected class instead of the blanket rule.

# SQLDelight generated database implementation is looked up via the generated Schema —
# keep the generated package to be safe.
-keep class com.module.notelycompose.database.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.coroutines.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# SQLDelight
-keep class com.squareup.sqldelight.** { *; }
