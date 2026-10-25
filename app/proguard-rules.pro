# SolarDream ProGuard / R8 Rules

# Preserve Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.service.dreams.DreamService
-keep public class * extends android.view.View

# Preserve models, drivers and UI
-keep class dev.jairoserrano.solardream.model.** { *; }
-keep class dev.jairoserrano.solardream.ui.** { *; }
-keep class dev.jairoserrano.solardream.dtu.** { *; }
-keep class dev.jairoserrano.solardream.driver.** { *; }

# Preserve line numbers and source file attributes for debug stack traces
-keepattributes SourceFile,LineNumberTable
