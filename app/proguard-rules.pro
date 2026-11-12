# R8 rules for the release build. AndroidX (Room, WorkManager, Glance, DataStore) and OkHttp ship consumer
# rules for their own reflection; only what they don't cover goes here.

# Keep file/line info so crash stack traces from users can be mapped back (mapping.txt is kept per release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
