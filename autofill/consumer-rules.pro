# 自动填充库对外暴露的混淆保留规则。
# 桥接接口由宿主 app 反射式注册的场景较少，此处仅保留 Parcelable 与 RemoteViews 相关成员，
# 避免启用混淆后 PendingIntent 传参失败。
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep class xzynine.WebDAVPass.Autofill.model.** { *; }
