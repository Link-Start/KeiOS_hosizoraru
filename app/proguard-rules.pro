# ---------- KeiOS R8 baseline ----------
# Keep enum member names stable because multiple stores persist enum.name across app restarts/upgrades.
-keepclassmembernames enum os.kei.** { *; }

# Keep internal component class names stable for manifest lookups and About-page introspection.
# This covers future KeiOS activities/services/receivers/providers without needing manual updates.
-keepnames class os.kei.** extends android.app.Application
-keepnames class os.kei.** extends android.app.Activity
-keepnames class os.kei.** extends android.app.Service
-keepnames class os.kei.** extends android.content.BroadcastReceiver
-keepnames class os.kei.** extends android.content.ContentProvider

# Keep Navigation3 route keys stable across release save/restore boundaries.
# The route surface is tiny; keeping members preserves Kotlin object instances and serializer entry points.
-keep class os.kei.ui.navigation.KeiosRoute { *; }
-keep class os.kei.ui.navigation.KeiosRoute$* { *; }
-keep class os.kei.ui.navigation.KeiosRoute$*$$serializer { *; }
-keepclassmembers class os.kei.ui.navigation.KeiosRoute$* {
    public static ** Companion;
    public static ** INSTANCE;
    public static *** serializer(...);
}

# ShizukuApiUtils reflects these no-arg static method names.
# Keep only the reflective surface so the rest can still be optimized/shrunk.
-keepclassmembers class rikka.shizuku.Shizuku {
    public static *** getUid(...);
    public static *** getVersion(...);
    public static *** getServerPatchVersion(...);
    public static *** getSELinuxContext(...);
    public static *** getLatestServiceVersion(...);
    private static *** newProcess(java.lang.String[],java.lang.String[],java.lang.String);
}

# Keep annotation/signature metadata used by Kotlin + library runtime features.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# focus-api ships without consumer rules. Keep only the payload contract that HyperOS reads:
# 1) kotlinx.serialization entry points for miui.focus.param JSON
# 2) sealed factory names because their fully qualified subclass names become the JSON "type"
# 3) template fields used by declaredFields copy helpers
# This keeps the Bundle/JSON schema stable while letting R8 shrink and optimize unused helpers.
-keepnames class com.xzakota.hyper.notification.focus.FocusNotification
-keepnames class com.xzakota.hyper.notification.focus.FocusNotification$FocusTemplateFactory
-keepnames class com.xzakota.hyper.notification.focus.FocusNotification$FocusTemplateFactory$*
-keep,allowoptimization class com.xzakota.hyper.notification.**$$serializer { *; }
-keepclassmembers class com.xzakota.hyper.notification.** {
    public static ** Companion;
    public static ** INSTANCE;
    public static *** serializer(...);
}
-keepclassmembers,allowoptimization,allowobfuscation class com.xzakota.hyper.notification.focus.template.BaseFocusTemplate {
    <fields>;
}
-keepclassmembers,allowoptimization,allowobfuscation class com.xzakota.hyper.notification.focus.template.FocusTemplate {
    <fields>;
}
-keepclassmembers,allowoptimization,allowobfuscation class com.xzakota.hyper.notification.focus.template.FocusTemplateV3 {
    <fields>;
}
-keepclassmembers,allowoptimization,allowobfuscation class com.xzakota.hyper.notification.focus.template.CustomFocusTemplateV3 {
    <fields>;
}

# Keep notification boundary class names readable for release-only payload debugging.
# Their members stay optimizable because the system consumes the Focus extras, not these helpers.
-keepnames class os.kei.core.notification.focus.MiFocusNotificationTemplate
-keepnames class os.kei.mcp.framework.notification.builder.MiIslandNotificationBuilder
-keepnames class os.kei.feature.github.notification.GitHubRefreshNotificationHelper
-keepnames class os.kei.feature.github.notification.GitHubShareImportNotificationHelper

# Drop direct release Log calls to reduce overhead and method count.
# AppLogger warning/error paths still run because this rule only targets android.util.Log calls.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Ktor debug probe references JDK-only management APIs on Android.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# R8 diagnostics:
# - Compare app/build/outputs/mapping/{release,benchmark}/configuration.txt, mapping.txt, and usage.txt.
# - Use temporary -whyareyoukeeping rules only during local diagnosis; do not commit them.
