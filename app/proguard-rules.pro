# Add project specific ProGuard rules here.
# Keep SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep Room entities
-keep class com.offpay.app.data.** { *; }
