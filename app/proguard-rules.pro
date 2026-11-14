# Rules for the optimized build, which is the real app. Compose and the backdrop library bring their own consumer rules.
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# GeckoView is called from native code by name. Its own consumer rules cover this; these two lines are a belt-and-braces guard
# (unverified on a device, see TESTING.md).
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.geckoview.** { *; }

# A crash trace must stay readable: keep the file names and line numbers. CI also publishes mapping.txt with every release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
