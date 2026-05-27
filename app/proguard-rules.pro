# Add project specific ProGuard rules here.

# ─── SQLCipher ──────────────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ─── Room ───────────────────────────────────────────────────────────────────────
-keep class com.offpay.app.data.** { *; }
# Room uses reflection for DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ─── AndroidX SQLite (used by Room + SQLCipher bridge) ──────────────────────────
-keep class androidx.sqlite.** { *; }
-keep class androidx.sqlite.db.** { *; }

# ─── DataStore ──────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ─── Kotlin Coroutines ──────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ─── Kotlin Reflect (used by R.drawable/R.raw reflection in easter eggs) ────────
-keep class com.offpay.app.R$* { *; }

# ─── OffPay Application class ───────────────────────────────────────────────────
-keep class com.offpay.app.OffPayApplication { *; }

# ─── ML Kit Barcode ─────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ─── Compose (safety net — AGP usually handles this) ────────────────────────────
-dontwarn androidx.compose.**