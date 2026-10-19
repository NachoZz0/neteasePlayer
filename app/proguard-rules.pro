# ============================================================
# MetingMusicPlayer — release 混淆规则
# ============================================================

# ---------- 保留行号，便于线上排查崩溃 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- 注解（Compose 的 @Immutable / @Stable 依赖它） ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep @androidx.compose.runtime.Stable class * { *; }

# ---------- 数据模型：Gson 通过反射实例化与赋值，字段名不能改 ----------
-keep class com.example.metingmusic.data.model.** { *; }
-keep class com.example.metingmusic.data.remote.** { *; }
-keep class com.example.metingmusic.data.local.** { *; }
-keepclassmembers class com.example.metingmusic.data.** {
    <init>();
    <fields>;
}

# ---------- Gson 通用规则 ----------
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson 内部用到的泛型签名/TypeToken 匿名子类
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ---------- Kotlin @Transient => JVM transient，无需额外规则，但保留 DefaultConstructorMarker ----------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------- Media3 / ExoPlayer ----------
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
# 播放器通过反射创建音频解码器
-keep class androidx.media3.common.util.** { *; }
-keepclassmembers class * implements androidx.media3.common.Player { *; }

# ---------- Coil ----------
-dontwarn coil.**
-keep class coil.** { *; }

# ---------- Compose ----------
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.ComposerImpl { *; }

# 关键：Compose UI 的接口大量使用 Kotlin 默认方法（-Xjvm-default=all 生成的
# bridge / DefaultImpls）。R8 在混淆时会把不同接口里的同签名默认方法重命名为
# 同一个短名，运行时在类验证阶段抛：
#   IncompatibleClassChangeError: Conflicting default method implementations
#        boolean androidx.compose.ui.modifier.ModifierLocalProvider.all(...)
#
# 解法不是关掉全部优化（那样体积会反弹），而是：
#  1) 保留 androidx.compose.ui 下接口成员的原始签名，不参与重命名；
#  2) 禁止 R8 对接口做垂直类合并（-keep,allowshrinking 已能抑制，这里再显式加固）。
-keep interface androidx.compose.ui.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep,allowshrinking class androidx.compose.ui.**
-keepclassmembers class androidx.compose.ui.** {
    <init>(...);
    <methods>;
    <fields>;
}
# 保留接口默认方法的原签名，杜绝跨接口的同名短名冲突
-keepclassmembers interface * {
    public <methods>;
}

# ---------- 通用：保留枚举的 values()/valueOf()（反射反序列化会用到） ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- 保留 Parcelable CREATOR ----------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ---------- 保留 Application / Activity / Service / Receiver 入口 ----------
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ---------- 去掉调试日志，减小体积 ----------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
