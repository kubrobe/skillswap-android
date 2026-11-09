# R8 rules for SkillSwap.
# Room, Compose, Coroutines, Navigation and Haze ship their own rules inside their libraries.
# Our data classes are kept whole so database entities and JSON parsing stay readable in crash logs.
-keep class uz.skillswap.app.data.** { *; }
-keepattributes SourceFile,LineNumberTable
