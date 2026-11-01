# 轻量天气应用：几乎不混淆原生代码即可，保留 ViewBinding 生成的类
-keepattributes *Annotation*
-dontwarn org.json.**
-keep class com.example.weather.databinding.** { *; }
