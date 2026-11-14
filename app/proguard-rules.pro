# R8 rules for SkillSwap.
# Room, Compose, Coroutines, Navigation and Haze ship their own rules inside their libraries.
# Our data classes are kept whole so database entities and JSON parsing stay readable in crash logs.
-keep class uz.skillswap.app.data.** { *; }
-keepattributes SourceFile,LineNumberTable

# Credential Manager (Sign in with Google) finds its Google Play services part by reflection: keep it in release builds
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Vosk (offline voice) calls its native library through JNA, which finds classes and fields by name
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
