package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "launcher_preferences")
data class LauncherPreferences(
    @PrimaryKey val id: Int = 1,
    val themeName: String = "DREAMY_LAVENDER",
    val wallpaperPath: String = "default",
    val companionMascot: String = "Neko-chan",
    val customGreeting: String = "¡Bienvenido, Senpai! (•◡•) ♡",
    val companionDialogueCount: Int = 0,
    val messagingPackage: String = "",
    val browserPackage: String = "",
    val socialPackage: String = "",
    val photoPackage: String = "",
    val musicPackage: String = "",
    val galleryPackage: String = ""
)

@Entity(tableName = "anime_notes")
data class AnimeNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO ---

@Dao
interface LauncherDao {
    @Query("SELECT * FROM launcher_preferences WHERE id = 1")
    fun getPreferencesFlow(): Flow<LauncherPreferences?>

    @Query("SELECT * FROM launcher_preferences WHERE id = 1")
    suspend fun getPreferences(): LauncherPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: LauncherPreferences)

    @Query("SELECT * FROM anime_notes ORDER BY timestamp DESC")
    fun getAllNotesFlow(): Flow<List<AnimeNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: AnimeNote)

    @Query("DELETE FROM anime_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)
}

// --- DATABASE ---

@Database(entities = [LauncherPreferences::class, AnimeNote::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getDatabase(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "neko_launcher_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORY ---

class LauncherRepository(private val launcherDao: LauncherDao) {
    val preferences: Flow<LauncherPreferences?> = launcherDao.getPreferencesFlow()
    val allNotes: Flow<List<AnimeNote>> = launcherDao.getAllNotesFlow()

    suspend fun getPreferencesSync(): LauncherPreferences {
        return launcherDao.getPreferences() ?: LauncherPreferences()
    }

    suspend fun savePreferences(prefs: LauncherPreferences) {
        launcherDao.savePreferences(prefs)
    }

    suspend fun insertNote(note: AnimeNote) {
        launcherDao.insertNote(note)
    }

    suspend fun deleteNote(id: Int) {
        launcherDao.deleteNoteById(id)
    }
}
