# Add project specific Proguard rules here.
# By default, the Proguard rules in this file are appended to the default Proguard rules.

# For Room compiler generated code
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao
