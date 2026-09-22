# WhisperPair ProGuard Rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep data classes used in the app
-keep class com.zalexdev.whisperpair.FastPairDevice { *; }
-keep class com.zalexdev.whisperpair.FastPairDevice$* { *; }
-keep class com.zalexdev.whisperpair.MainActivity$AudioConnectionState { *; }
-keep class com.zalexdev.whisperpair.BluetoothAudioManager$* { *; }
-keep class com.zalexdev.whisperpair.FastPairExploit$* { *; }

# Bluetooth - keep callback classes
-keep class * extends android.bluetooth.BluetoothGattCallback { *; }
-keep class * extends android.bluetooth.le.ScanCallback { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# R8 full mode compatibility
-allowaccessmodification
-repackageclasses ''

# Optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
