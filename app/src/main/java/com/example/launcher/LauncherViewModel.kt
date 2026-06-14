package com.example.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AnimeNote
import com.example.data.LauncherDatabase
import com.example.data.LauncherPreferences
import com.example.data.LauncherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppItem(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable? = null
)

sealed interface CompanionState {
    object Idle : CompanionState
    object Loading : CompanionState
    data class Talking(val message: String) : CompanionState
    data class Error(val message: String) : CompanionState
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val contentResolver = context.contentResolver
    private val database = LauncherDatabase.getDatabase(context)
    private val repository = LauncherRepository(database.launcherDao())

    // --- State Backing ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _companionState = MutableStateFlow<CompanionState>(CompanionState.Idle)
    val companionState: StateFlow<CompanionState> = _companionState.asStateFlow()

    val preferences: StateFlow<LauncherPreferences> = repository.preferences
        .map { it ?: LauncherPreferences() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LauncherPreferences()
        )

    val allNotes: StateFlow<List<AnimeNote>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Search filter applied to actual installed applications ---
    val filteredApps: StateFlow<List<AppItem>> = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // --- Initialization ---
    init {
        loadInstalledApps()
        updateBatteryLevel()
        setInitialGreeting()
    }

    private fun setInitialGreeting() {
        viewModelScope.launch {
            val prefs = repository.getPreferencesSync()
            _companionState.value = CompanionState.Talking(prefs.customGreeting)
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolveInfos.mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                val className = info.activityInfo.name
                
                // Avoid listing our own launcher app as an launching shortcut option
                if (packageName == context.packageName) return@mapNotNull null

                val label = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)

                AppItem(
                    label = label,
                    packageName = packageName,
                    className = className,
                    icon = icon
                )
            }.sortedWith(compareBy { it.label.lowercase() })

            _installedApps.value = appsList
        }
    }

    fun updateBatteryLevel() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        _batteryLevel.value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // --- Search Query Management ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Preference Mutations ---
    fun setCustomGreeting(newGreeting: String) {
        viewModelScope.launch {
            val current = repository.getPreferencesSync()
            repository.savePreferences(current.copy(customGreeting = newGreeting))
            _companionState.value = CompanionState.Talking(newGreeting)
        }
    }

    fun changeTheme(themeName: String) {
        viewModelScope.launch {
            val current = repository.getPreferencesSync()
            repository.savePreferences(current.copy(themeName = themeName))
        }
    }

    fun changeMascot(mascotName: String) {
        viewModelScope.launch {
            val current = repository.getPreferencesSync()
            repository.savePreferences(current.copy(companionMascot = mascotName))
            triggerMascotGreet(mascotName)
        }
    }

    fun setShortcutPackage(shortcutType: String, packageName: String) {
        viewModelScope.launch {
            val current = repository.getPreferencesSync()
            val updated = when (shortcutType) {
                "MESSAGE" -> current.copy(messagingPackage = packageName)
                "BROWSER" -> current.copy(browserPackage = packageName)
                "SOCIAL" -> current.copy(socialPackage = packageName)
                "PHOTO" -> current.copy(photoPackage = packageName)
                "MUSIC" -> current.copy(musicPackage = packageName)
                "GALLERY" -> current.copy(galleryPackage = packageName)
                else -> current
            }
            repository.savePreferences(updated)
        }
    }

    private fun triggerMascotGreet(mascotName: String) {
        val message = when (mascotName) {
            "Neko-chan" -> "¡Nyaa~! Gracias por elegirme, Senpai. ¡Hoy nos divertiremos mucho! (≚ᄌ≚)✿"
            "Kitsune-chan" -> "Que los espíritus amables guíen tus pasos. Estoy a tu servicio, Sabio Viajero. 🦊⭐"
            "Kuma-chan" -> "*bostezo* ¿Ya es hora de despertar? Supongo que puedo acompañarte un ratito... 🐻💤"
            else -> "¡Hola! Estoy listo para asistirte hoy."
        }
        _companionState.value = CompanionState.Talking(message)
    }

    // --- Notes Management ---
    fun addNote(content: String) {
        viewModelScope.launch {
            if (content.isNotBlank()) {
                repository.insertNote(AnimeNote(content = content.trim()))
            }
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    // --- Launch Helper ---
    fun getLaunchIntentForShortcut(shortcutType: String, prefs: LauncherPreferences): Intent? {
        val pm = context.packageManager
        val customPkg = when (shortcutType) {
            "MESSAGE" -> prefs.messagingPackage
            "BROWSER" -> prefs.browserPackage
            "SOCIAL" -> prefs.socialPackage
            "PHOTO" -> prefs.photoPackage
            "MUSIC" -> prefs.musicPackage
            "GALLERY" -> prefs.galleryPackage
            else -> ""
        }

        if (customPkg.isNotBlank()) {
            val intent = pm.getLaunchIntentForPackage(customPkg)
            if (intent != null) return intent
        }

        // Implicit actions fallback
        return when (shortcutType) {
            "MESSAGE" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                }
                pm.resolveActivity(intent, 0)?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            }
            "BROWSER" -> {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                pm.resolveActivity(intent, 0)?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            }
            "SOCIAL" -> {
                // Try popular packages or browser representation
                var targetPackage = ""
                val socials = listOf("com.whatsapp", "com.instagram.android", "com.twitter.android", "com.facebook.katana")
                for (pkg in socials) {
                    if (isPackageInstalled(pkg)) {
                        targetPackage = pkg
                        break
                    }
                }
                if (targetPackage.isNotBlank()) pm.getLaunchIntentForPackage(targetPackage) else null
            }
            "PHOTO" -> {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                pm.resolveActivity(intent, 0)?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            }
            "MUSIC" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                }
                pm.resolveActivity(intent, 0)?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            }
            "GALLERY" -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                }
                pm.resolveActivity(intent, 0)?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            }
            else -> null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // --- Anime Mascot Talking interactions (Local pre-baked database / Gemini fallback) ---
    fun speakInteractiveQuote() {
        val mascot = preferences.value.companionMascot
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val quotes = getMascotQuotes(mascot, hour, batteryLevel.value)

        // Randomly fetch online quote from Gemini API if key exists and configured properly
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            generateGeminiDialogueOnline(apiKey, mascot)
        } else {
            // Offline Mode - fallback instantly to custom cute themed list
            _companionState.value = CompanionState.Talking(quotes.random())
        }
    }

    private fun generateGeminiDialogueOnline(apiKey: String, mascot: String) {
        _companionState.value = CompanionState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val promptText = """
                    You are "$mascot", a charming interactive anime assistant living inside an Android launcher.
                    Format rules:
                    1. Respond in SPANISH.
                    2. Keep it under 80 characters.
                    3. Sound cute, expressive, energetic. Use subtle anime emoji keyboard faces (e.g. ^w^, (•◡•), >.<, (๑>◡<๑), Nya~).
                    4. Reference a launcher action (e.g. checking notes, battery capacity, desktop stars, customizable theme, or cosmic weather, opening portals).
                    Mascot Personas:
                    - Neko-chan: Sassy cute kitten-girl, loves candy, calls user 'Senpai' or 'Master', uses Nya.
                    - Kitsune-chan: Clever mystical wizard fox, speaks gently and respectfully, uses ⭐, winds, spirits.
                    - Kuma-chan: Sleepy grumpy teddy-bear who is forced to help, bosteza, loves pillow, calls user 'Líder'.
                """.trimIndent()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val json = JSONObject().apply {
                    put("contents", JSONObject().apply {
                        put("parts", JSONObject().apply {
                            put("text", promptText)
                        })
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val responseJson = JSONObject(responseStr)
                    val candidates = responseJson.getJSONArray("candidates")
                    val text = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    
                    _companionState.value = CompanionState.Talking(text.trim().replace("\"", ""))
                } else {
                    throw IOException("Unsuccessful API call")
                }
            } catch (e: Exception) {
                // Return fallback locally
                val backupQuotes = getMascotQuotes(mascot, 12, batteryLevel.value)
                _companionState.value = CompanionState.Talking(backupQuotes.random() + " ✿")
            }
        }
    }

    private fun getMascotQuotes(mascot: String, hour: Int, battery: Int): List<String> {
        val greeting = when {
            hour < 12 -> "¡Buenos días!"
            hour < 18 -> "¡Buenas tardes!"
            else -> "¡Buenas noches!"
        }

        return when (mascot) {
            "Neko-chan" -> listOf(
                "¡$greeting, Master! ¿Qué portal abriremos hoy? (•◡•)",
                "¡Nyaa~! Mi energía mágica es del $battery%... ¡Lista para la acción!",
                "La constelación de tus aplicaciones brilla hoy con fuerza. (๑>◡<๑)",
                "Acaricia mi cabeza para darme más maná del sistema, Senpai. ♡",
                "He organizado tus widgets temáticos ordenadamente. ¡Míralos!",
                "¿Algún nuevo secreto en tus notas de anime? ¡No miraré, lo prometo! ≧◡≦",
                "¡Pst! Un universo te espera si buscas arriba en la lista de apps. Nya~",
                "¿Me llevarás contigo a explorar hoy? ¡Prepárame dulces!"
            )
            "Kitsune-chan" -> listOf(
                "Gracia divina y sabiduría. $greeting, Guardián.",
                "Nuestra energía espiritual está al $battery%. Un equilibrio perfecto. ⭐",
                "He conjurado este tema especial con polvos de estrellas mágicas.",
                "Escribe un deseo o tarea en nuestro papel celestial de notas.",
                "Los vientos del sistema aconsejan limpiar lo que no usas.",
                "Siento una gran armonía cósmica en tu Launcher hoy...",
                "¡Tu camino está guiado por la bendición del zorro de fuego! 🔥",
                "A veces, un momento de contemplación renueva tu alma."
            )
            "Kuma-chan" -> listOf(
                "Mm... $greeting... ¿podemos irnos a dormir otra vez? *bostezo*",
                "Tengo el $battery% de batería. Básicamente como yo... exhausto. 🐻💤",
                "No hagas clic tan rápido en mis widgets, Líder, me mareas...",
                "Guardé tus anotaciones bajo mi almohada para que nadie las vea.",
                "Un launcher ordenado es menos trabajo para mí. Gracias.",
                "Si buscas algo, usa el cofre de búsqueda espacial de arriba...",
                "¿Cambiamos de tema? El rosa me da sueño... o bueno, todos me dan sueño.",
                "*murmullo* Te veo muy concentrado hoy. ¡Buen trabajo, Líder!"
            )
            else -> listOf("¡Hola portal del sistema activo!")
        }
    }
}
