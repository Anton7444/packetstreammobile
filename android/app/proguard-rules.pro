# WorkManager & Room Database
# AGP 9.0+ enables strict full mode in R8 by default, which removes the parameterless
# default constructor of RoomDatabase implementations (such as WorkDatabase_Impl)
# unless explicitly preserved. WorkManager relies on Room runtime reflection
# to instantiate WorkDatabase_Impl during initialization on app launch.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}

# Keep WorkManager ListenableWorker classes and constructor for reflection-based WorkerFactory instantiation
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
