# Room Database reflection keep rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.TypeConverter
-keep class com.yscanner.data.** { *; }
