package com.example.launcher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.AnimeNote
import com.example.data.LauncherPreferences
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Custom Hexagonal Shape for Anime Aesthetic
val HexagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val batteryLife by viewModel.batteryLevel.collectAsStateWithLifecycle()
    val companionState by viewModel.companionState.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()

    var showDrawer by remember { mutableStateOf(false) }
    var showCustomizer by remember { mutableStateOf(false) }
    var activePortalMessage by remember { mutableStateOf<String?>(null) }

    // Update battery status occasionally
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateBatteryLevel()
            delay(30000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launcher_root")
    ) {
        // --- Wallpaper Layer ---
        if (prefs.wallpaperPath == "default") {
            Image(
                painter = painterResource(id = R.drawable.anime_wallpaper),
                contentDescription = "Fondo de Pantalla de Anime",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Render beautiful deep theme-based digital gradient backgrounds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = getDynamicGradientColors(prefs.themeName),
                            center = Offset(400f, 600f),
                            radius = 1200f
                        )
                    )
            )
        }

        // Overlay scrim for maximum readability on widget cards
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // --- Core Desktop View ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Time / Date / Weather Row
            TimeWeatherHeader(prefs.themeName)

            // 2. Mascot & Speech Balloon Column
            MascotAndDialogueRow(
                mascot = prefs.companionMascot,
                companionState = companionState,
                themeName = prefs.themeName,
                onMascotClick = { viewModel.speakInteractiveQuote() }
            )

            // 3. Companion Stats Widget (HP/MP)
            MascotStatsCard(batteryLife = batteryLife, themeName = prefs.themeName)

            // 4. Custom wishes / notes widget ("Libreta de Deseos")
            AnimeNotesWidget(
                notes = notes,
                themeName = prefs.themeName,
                onAddNote = { viewModel.addNote(it) },
                onDeleteNote = { viewModel.deleteNote(it) }
            )

            // 5. Desktop Shortcuts Grid
            DesktopShortcutsGrid(
                prefs = prefs,
                themeName = prefs.themeName,
                onLaunchShortcut = { type ->
                    val intent = viewModel.getLaunchIntentForShortcut(type, prefs)
                    if (intent != null) {
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al abrir portal", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Polished fallback simulation if actual app isn't installed
                        val label = when (type) {
                            "MESSAGE" -> "Mensajes (SMS)"
                            "BROWSER" -> "Navegador Web"
                            "SOCIAL" -> "Redes Sociales"
                            "PHOTO" -> "Cámara / Foto"
                            "MUSIC" -> "Música"
                            "GALLERY" -> "Galería"
                            else -> "Portal"
                        }
                        val mascotQuote = when (prefs.companionMascot) {
                            "Neko-chan" -> "¡Nya~! Abriendo portal holográfico para $label... (•◡•)"
                            "Kitsune-chan" -> "Invocando ritual espiritual para abrir $label. 🦊⭐"
                            "Kuma-chan" -> "*bostezo* Abriendo portal para $label... no me hagas correr de prisa."
                            else -> "Iniciando enlace a $label..."
                        }
                        activePortalMessage = mascotQuote
                    }
                }
            )

            Spacer(modifier = Modifier.height(60.dp)) // Spacer for Dock padding
        }

        // --- Dock / Bottom Overlay Row ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            DockRow(
                themeName = prefs.themeName,
                onDrawerTrigger = { showDrawer = true },
                onCustomizerTrigger = { showCustomizer = true }
            )
        }

        // --- Simulated App Portal Dialog ---
        activePortalMessage?.let { msg ->
            PortalOverlayDialog(
                message = msg,
                themeName = prefs.themeName,
                onDismiss = { activePortalMessage = null }
            )
        }

        // --- Slid-Up App Drawer ---
        if (showDrawer) {
            AppDrawerDialog(
                filteredApps = filteredApps,
                searchQuery = searchQuery,
                themeName = prefs.themeName,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onDismiss = {
                    showDrawer = false
                    viewModel.updateSearchQuery("") // Reset query on close
                },
                onAppClick = { app ->
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "No se pudo abrir ${app.label}", Toast.LENGTH_SHORT).show()
                    }
                    showDrawer = false
                    viewModel.updateSearchQuery("")
                }
            )
        }

        // --- Settings / Customizer Side Panel ---
        if (showCustomizer) {
            CustomizerDrawerDialog(
                prefs = prefs,
                installedApps = viewModel.installedApps.value,
                themeName = prefs.themeName,
                onDismiss = { showCustomizer = false },
                onThemeSelect = { viewModel.changeTheme(it) },
                onMascotSelect = { viewModel.changeMascot(it) },
                onGreetingSave = { viewModel.setCustomGreeting(it) },
                onMapShortcut = { type, pkg -> viewModel.setShortcutPackage(type, pkg) }
            )
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun TimeWeatherHeader(themeName: String) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val calendar = Calendar.getInstance()
            currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
            currentDate = SimpleDateFormat("EEEE, d 'de' MMMM", Locale( "es", "ES")).format(calendar.time)
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, getThemePrimaryColor(themeName).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = getThemePrimaryColor(themeName),
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Weather Column with a custom cute interface
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(getThemeSecondaryColor(themeName).copy(alpha = 0.15f))
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudQueue,
                contentDescription = "Cute Weather Icon",
                tint = getThemeTertiaryColor(themeName),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "72°F",
                fontWeight = FontWeight.Bold,
                color = getThemeTertiaryColor(themeName),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Cariñoso Soleado",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MascotAndDialogueRow(
    mascot: String,
    companionState: CompanionState,
    themeName: String,
    onMascotClick: () -> Unit
) {
    val animScale by rememberInfiniteTransition().animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mascot avatar column layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(100.dp)
                .clickable { onMascotClick() }
                .padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(HexagonShape)
                    .background(getThemePrimaryColor(themeName).copy(alpha = 0.2f))
                    .border(2.dp, getThemePrimaryColor(themeName), HexagonShape)
                    .padding(4.dp)
            ) {
                // Determine cute drawing preset representing our mascot face
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                ) {
                    val w = size.width
                    val h = size.height
                    
                    // Center our drawings
                    val drawCenter = center
                    
                    // Draw Neko / Kitsune / Kuma face based on parameters
                    when (mascot) {
                        "Neko-chan" -> {
                            // Pink / Purple Cat Ears
                            val pathLeft = Path().apply {
                                moveTo(w * 0.2f, h * 0.4f)
                                lineTo(w * 0.1f, h * 0.1f)
                                lineTo(w * 0.45f, h * 0.35f)
                                close()
                            }
                            val pathRight = Path().apply {
                                moveTo(w * 0.8f, h * 0.4f)
                                lineTo(w * 0.9f, h * 0.1f)
                                lineTo(w * 0.55f, h * 0.35f)
                                close()
                            }
                            drawPath(pathLeft, color = Color(0xFFFFB5E8))
                            drawPath(pathRight, color = Color(0xFFFFB5E8))
                            
                            // Face circle representation
                            drawCircle(color = Color(0xFFFFF2FA), radius = w * 0.32f, center = drawCenter)
                            
                            // Eyes
                            drawCircle(color = Color(0xFF130E29), radius = w * 0.04f, center = Offset(w * 0.4f, h * 0.45f))
                            drawCircle(color = Color(0xFF130E29), radius = w * 0.04f, center = Offset(w * 0.60f, h * 0.45f))
                            drawCircle(color = Color.White, radius = w * 0.015f, center = Offset(w * 0.39f, h * 0.44f))
                            drawCircle(color = Color.White, radius = w * 0.015f, center = Offset(w * 0.59f, h * 0.44f))
                            
                            // Blush
                            drawCircle(color = Color(0xFFFF8AD2).copy(alpha = 0.6f), radius = w * 0.06f, center = Offset(w * 0.32f, h * 0.52f))
                            drawCircle(color = Color(0xFFFF8AD2).copy(alpha = 0.6f), radius = w * 0.06f, center = Offset(w * 0.68f, h * 0.52f))
                            
                            // Cat mouth (W shape)
                            val leftMouth = Path().apply {
                                moveTo(w * 0.46f, h * 0.52f)
                                quadraticTo(w * 0.5f, h * 0.56f, w * 0.5f, h * 0.52f)
                                quadraticTo(w * 0.5f, h * 0.56f, w * 0.54f, h * 0.52f)
                            }
                            drawPath(leftMouth, color = Color(0xFF130E29), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        }
                        "Kitsune-chan" -> {
                            // Fox ears (orange-gold)
                            val pathLeft = Path().apply {
                                moveTo(w * 0.25f, h * 0.4f)
                                lineTo(w * 0.15f, h * 0.1f)
                                lineTo(w * 0.45f, h * 0.35f)
                                close()
                            }
                            val pathRight = Path().apply {
                                moveTo(w * 0.75f, h * 0.4f)
                                lineTo(w * 0.85f, h * 0.1f)
                                lineTo(w * 0.55f, h * 0.35f)
                                close()
                            }
                            drawPath(pathLeft, color = Color(0xFFFFB76E))
                            drawPath(pathRight, color = Color(0xFFFFB76E))
                            
                            // Head shape
                            drawCircle(color = Color(0xFFFFF7EF), radius = w * 0.32f, center = drawCenter)
                            
                            // Closed happy slitted eyes
                            val eyeLeft = Path().apply {
                                moveTo(w * 0.36f, h * 0.46f)
                                quadraticTo(w * 0.4f, h * 0.42f, w * 0.44f, h * 0.46f)
                            }
                            val eyeRight = Path().apply {
                                moveTo(w * 0.56f, h * 0.46f)
                                quadraticTo(w * 0.60f, h * 0.42f, w * 0.64f, h * 0.46f)
                            }
                            drawPath(eyeLeft, color = Color(0xFF2B161B), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                            drawPath(eyeRight, color = Color(0xFF2B161B), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                            
                            // Red mystical face mark
                            val mark = Path().apply {
                                moveTo(w * 0.5f, h * 0.28f)
                                lineTo(w * 0.5f, h * 0.38f)
                            }
                            drawPath(mark, color = Color(0xFFFF3366), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                            
                            // Fox muzzle / nose
                            drawCircle(color = Color(0xFFFFB76E), radius = w * 0.03f, center = Offset(w * 0.5f, h * 0.51f))
                        }
                        "Kuma-chan" -> {
                            // Bear Round Ears
                            drawCircle(color = Color(0xFFC4A484), radius = w * 0.14f, center = Offset(w * 0.25f, h * 0.28f))
                            drawCircle(color = Color(0xFFC4A484), radius = w * 0.14f, center = Offset(w * 0.75f, h * 0.28f))
                            drawCircle(color = Color(0xFFFFDAB9), radius = w * 0.07f, center = Offset(w * 0.25f, h * 0.28f))
                            drawCircle(color = Color(0xFFFFDAB9), radius = w * 0.07f, center = Offset(w * 0.75f, h * 0.28f))

                            // Face base
                            drawCircle(color = Color(0xFFD2B48C), radius = w * 0.32f, center = drawCenter)

                            // Droopy lazy eyes (-_- vibe)
                            drawLine(color = Color(0xFF332211), start = Offset(w * 0.34f, h * 0.45f), end = Offset(w * 0.44f, h * 0.45f), strokeWidth = 4f)
                            drawLine(color = Color(0xFF332211), start = Offset(w * 0.56f, h * 0.45f), end = Offset(w * 0.66f, h * 0.45f), strokeWidth = 4f)

                            // Cute Snout circle
                            drawCircle(color = Color(0xFFFFF8DC), radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.53f))
                            drawCircle(color = Color(0xFF332211), radius = w * 0.03f, center = Offset(w * 0.5f, h * 0.51f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mascot,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = getThemePrimaryColor(themeName)
            )
        }

        // Animated dialog speech bubble taking remaining space
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                }
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(getThemePrimaryColor(themeName), getThemeSecondaryColor(themeName))
                    ),
                    RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(14.dp)
        ) {
            when (companionState) {
                is CompanionState.Idle -> {
                    Text(
                        text = "Toca en mí para consultar augurios del sistema o dialogar conmigo, Senpai! ♪",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                is CompanionState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = getThemePrimaryColor(themeName),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Consultando constelación...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is CompanionState.Talking -> {
                    Text(
                        text = (companionState as CompanionState.Talking).message,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is CompanionState.Error -> {
                    Text(
                        text = "¡Oh no! " + (companionState as CompanionState.Error).message,
                        fontSize = 13.sp,
                        color = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun MascotStatsCard(batteryLife: Int, themeName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, getThemeSecondaryColor(themeName).copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Widget Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Stats Icon",
                    tint = getThemeSecondaryColor(themeName),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ESTADO DE MASCOTA (SISTEMA)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = getThemeSecondaryColor(themeName),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HP (Battery Level)
            StatProgressBar(
                label = "HP (Batería)",
                value = batteryLife,
                maxValue = 100,
                unit = "%",
                themeColor = getThemePrimaryColor(themeName)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // MP (Memory representation)
            StatProgressBar(
                label = "MP (Mana de Almacén)",
                value = 74,
                maxValue = 100,
                unit = "%",
                themeColor = getThemeTertiaryColor(themeName)
            )
        }
    }
}

@Composable
fun StatProgressBar(
    label: String,
    value: Int,
    maxValue: Int,
    unit: String,
    themeColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text(text = "$value/$maxValue$unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress track representation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            // Active progression indicator block
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = value.toFloat() / maxValue.toFloat())
                    .clip(RoundedCornerShape(4.dp))
                    .background(themeColor)
            )
        }
    }
}

@Composable
fun AnimeNotesWidget(
    notes: List<AnimeNote>,
    themeName: String,
    onAddNote: (String) -> Unit,
    onDeleteNote: (Int) -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, getThemePrimaryColor(themeName).copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Widget Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Notes Icon",
                        tint = getThemePrimaryColor(themeName),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LIBRETA DE DESEOS (NOTAS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = getThemePrimaryColor(themeName),
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "${notes.size} deseos",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Note input block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { if (it.length <= 40) newNoteText = it },
                    placeholder = { Text("Escribe una tarea o deseo...", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = getThemePrimaryColor(themeName),
                        unfocusedBorderColor = getThemePrimaryColor(themeName).copy(alpha = 0.4f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newNoteText.isNotBlank()) {
                            onAddNote(newNoteText)
                            newNoteText = ""
                        }
                        keyboardController?.hide()
                    })
                )

                IconButton(
                    onClick = {
                        if (newNoteText.isNotBlank()) {
                            onAddNote(newNoteText)
                            newNoteText = ""
                        }
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getThemePrimaryColor(themeName))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Note Button",
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notes list or empty state representation
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ningún deseo guardado aún. Escribe arriba para que tu compañero los custodie.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                ) {
                    notes.forEach { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "★ " + note.content,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { onDeleteNote(note.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopShortcutsGrid(
    prefs: LauncherPreferences,
    themeName: String,
    onLaunchShortcut: (String) -> Unit
) {
    val shortcuts = listOf(
        ShortcutItem("MESSAGE", "Mensaje", Icons.Filled.Mail, getThemePrimaryColor(themeName)),
        ShortcutItem("BROWSER", "Navegar", Icons.Filled.Star, getThemeTertiaryColor(themeName)),
        ShortcutItem("SOCIAL", "Social", Icons.Filled.Favorite, getThemeSecondaryColor(themeName)),
        ShortcutItem("PHOTO", "Cámara", Icons.Filled.CameraAlt, getThemePrimaryColor(themeName)),
        ShortcutItem("MUSIC", "Música", Icons.Filled.Headphones, getThemeTertiaryColor(themeName)),
        ShortcutItem("GALLERY", "Galería", Icons.Filled.Image, getThemeSecondaryColor(themeName))
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Double row of custom hexagon shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            shortcuts.take(3).forEach { item ->
                HexagonalShortcutButton(item = item, themeName = themeName, onClick = { onLaunchShortcut(item.key) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            shortcuts.drop(3).take(3).forEach { item ->
                HexagonalShortcutButton(item = item, themeName = themeName, onClick = { onLaunchShortcut(item.key) })
            }
        }
    }
}

data class ShortcutItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun HexagonalShortcutButton(
    item: ShortcutItem,
    themeName: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(HexagonShape)
                .background(item.color.copy(alpha = 0.15f))
                .border(2.dp, item.color, HexagonShape)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.color,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun DockRow(
    themeName: String,
    onDrawerTrigger: () -> Unit,
    onCustomizerTrigger: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, getThemePrimaryColor(themeName).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Interactive Personalization trigger button
        IconButton(
            onClick = onCustomizerTrigger,
            modifier = Modifier
                .size(46.dp)
                .clip(HexagonShape)
                .background(getThemeSecondaryColor(themeName).copy(alpha = 0.15f))
                .border(1.dp, getThemeSecondaryColor(themeName), HexagonShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Trigger Customizer",
                tint = getThemeSecondaryColor(themeName),
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Main Portal / Apps drawer trigger button and glowing frame
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onDrawerTrigger() }
                .clip(HexagonShape)
                .background(getThemePrimaryColor(themeName).copy(alpha = 0.25f))
                .border(2.dp, getThemePrimaryColor(themeName), HexagonShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDrawerTrigger,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Open App Portal",
                    tint = getThemePrimaryColor(themeName),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 3. Simple Search trigger visual
        IconButton(
            onClick = onDrawerTrigger, // Opens search filter in drawer
            modifier = Modifier
                .size(46.dp)
                .clip(HexagonShape)
                .background(getThemeTertiaryColor(themeName).copy(alpha = 0.15f))
                .border(1.dp, getThemeTertiaryColor(themeName), HexagonShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Launch System Search",
                tint = getThemeTertiaryColor(themeName),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// --- APP DRAWER OVERLAY COMPOSABLE ---
@Composable
fun AppDrawerDialog(
    filteredApps: List<AppItem>,
    searchQuery: String,
    themeName: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAppClick: (AppItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E1E).copy(alpha = 0.95f)),
            border = BorderStroke(2.dp, getThemePrimaryColor(themeName))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header of Portal drawer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = "Explore System Header Icon",
                            tint = getThemePrimaryColor(themeName),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "PORTAL DE SISTEMA",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = getThemePrimaryColor(themeName)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Drawer", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Buscar aplicación...", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = getThemePrimaryColor(themeName),
                        unfocusedBorderColor = getThemePrimaryColor(themeName).copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("app_search_field"),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon inside drawer",
                            tint = getThemePrimaryColor(themeName)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Installed apps Grid or empty state
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron dimensiones instaladas con ese nombre.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            AppItemButton(app = app, themeName = themeName, onClick = { onAppClick(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemButton(
    app: AppItem,
    themeName: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Render system icon gracefully or load fallback
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(HexagonShape)
                .background(getThemePrimaryColor(themeName).copy(alpha = 0.08f))
                .border(1.dp, getThemePrimaryColor(themeName).copy(alpha = 0.4f), HexagonShape),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = app.icon),
                    contentDescription = app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Default app icon",
                    tint = getThemePrimaryColor(themeName),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            fontSize = 10.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// --- LAUNCH SIMULATOR COMPOSABLE ---
@Composable
fun PortalOverlayDialog(
    message: String,
    themeName: String,
    onDismiss: () -> Unit
) {
    val animScale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        animScale.animateTo(1.0f, tween(300, easing = OvershootInterpolator(1.4f)::getInterpolation))
        delay(2000)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    scaleX = animScale.value
                    scaleY = animScale.value
                }
                .clip(HexagonShape)
                .background(Color(0xFF0C0A19).copy(alpha = 0.95f))
                .border(2.dp, getThemePrimaryColor(themeName), HexagonShape)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Portal Star Icon",
                    tint = getThemePrimaryColor(themeName),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ABRIENDO PORTAL...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = getThemePrimaryColor(themeName),
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// --- PERSONALIZATIONDRAWER COMPOSABLE ---
@Composable
fun CustomizerDrawerDialog(
    prefs: LauncherPreferences,
    installedApps: List<AppItem>,
    themeName: String,
    onDismiss: () -> Unit,
    onThemeSelect: (String) -> Unit,
    onMascotSelect: (String) -> Unit,
    onGreetingSave: (String) -> Unit,
    onMapShortcut: (String, String) -> Unit
) {
    var greetingText by remember { mutableStateOf(prefs.customGreeting) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // To select custom programs maps
    var mappingShortcutType by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E1E).copy(alpha = 0.96f)),
            border = BorderStroke(2.dp, getThemePrimaryColor(themeName))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = "Theme Brush Header Icon",
                            tint = getThemePrimaryColor(themeName),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "TEMPLO DE PERSONALIZACIÓN",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = getThemePrimaryColor(themeName)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Menu", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title: Themes
                Text(
                    text = "Elegir Tema Visual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = getThemePrimaryColor(themeName)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeSelectOption(
                        label = "Dreamy Lavender (Violeta Cósmico)",
                        themeKey = "DREAMY_LAVENDER",
                        isActive = prefs.themeName == "DREAMY_LAVENDER",
                        color = Color(0xFFB19FFB),
                        onClick = { onThemeSelect("DREAMY_LAVENDER") }
                    )
                    ThemeSelectOption(
                        label = "Sakura Blossom (Rosa Imperial)",
                        themeKey = "SAKURA_BLOSSOM",
                        isActive = prefs.themeName == "SAKURA_BLOSSOM",
                        color = Color(0xFFFFB7B2),
                        onClick = { onThemeSelect("SAKURA_BLOSSOM") }
                    )
                    ThemeSelectOption(
                        label = "Cyber Neon (Hacker de Neo-Tokio)",
                        themeKey = "CYBER_NEON",
                        isActive = prefs.themeName == "CYBER_NEON",
                        color = Color(0xFF00FFCC),
                        onClick = { onThemeSelect("CYBER_NEON") }
                    )
                    ThemeSelectOption(
                        label = "Forest Spirit (Arboleda de Espíritus)",
                        themeKey = "FOREST_SPIRIT",
                        isActive = prefs.themeName == "FOREST_SPIRIT",
                        color = Color(0xFF99FFDD),
                        onClick = { onThemeSelect("FOREST_SPIRIT") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title: Companion Mascot
                Text(
                    text = "Elegir Compañero Anime",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = getThemePrimaryColor(themeName)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mascots = listOf("Neko-chan", "Kitsune-chan", "Kuma-chan")
                    mascots.forEach { name ->
                        val isActive = prefs.companionMascot == name
                        Button(
                            onClick = { onMascotSelect(name) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) getThemePrimaryColor(themeName) else Color.White.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                color = if (isActive) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title: Custom Companion Greeting text
                Text(
                    text = "Saludo de Compañero",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = getThemePrimaryColor(themeName)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = greetingText,
                        onValueChange = { greetingText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = getThemePrimaryColor(themeName),
                            unfocusedBorderColor = getThemePrimaryColor(themeName).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            onGreetingSave(greetingText)
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = getThemePrimaryColor(themeName)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Custom Shortcuts remapper panel
                Text(
                    text = "Asignar Aplicaciones de Atajos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = getThemePrimaryColor(themeName)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecciona qué aplicación resolverá cada atajo de tu escritorio.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val shortcutMappingTypes = listOf(
                    "MESSAGE" to "Mensaje (SMS)",
                    "BROWSER" to "Navegar (Web)",
                    "SOCIAL" to "Social (Red)",
                    "PHOTO" to "Cámara (Foto)",
                    "MUSIC" to "Música (Reproductor)",
                    "GALLERY" to "Galería (Fotos)"
                )

                shortcutMappingTypes.forEach { (key, title) ->
                    val activeMapPkg = when (key) {
                        "MESSAGE" -> prefs.messagingPackage
                        "BROWSER" -> prefs.browserPackage
                        "SOCIAL" -> prefs.socialPackage
                        "PHOTO" -> prefs.photoPackage
                        "MUSIC" -> prefs.musicPackage
                        "GALLERY" -> prefs.galleryPackage
                        else -> ""
                    }
                    val resolvingLabel = if (activeMapPkg.isNotBlank()) {
                        installedApps.find { it.packageName == activeMapPkg }?.label ?: activeMapPkg
                    } else "Por defecto de Android"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .clickable { mappingShortcutType = key }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = resolvingLabel, fontSize = 10.sp, color = getThemePrimaryColor(themeName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            imageVector = Icons.Default.SwapCalls,
                            contentDescription = "Map button option",
                            tint = getThemeSecondaryColor(themeName),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Interactive mini app selection sheet for shortcut mapping
    mappingShortcutType?.let { sKey ->
        Dialog(onDismissRequest = { mappingShortcutType = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E1E)),
                border = BorderStroke(1.dp, getThemeSecondaryColor(themeName))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vincular Aplicación a Atajo",
                        fontWeight = FontWeight.Bold,
                        color = getThemeSecondaryColor(themeName),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Standard Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMapShortcut(sKey, "")
                                mappingShortcutType = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Default preset map icon", tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Resolución Automática de Android", color = Color.White, fontSize = 12.sp)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(installedApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onMapShortcut(sKey, app.packageName)
                                        mappingShortcutType = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = app.icon),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Apps, contentDescription = app.label, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = app.label, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelectOption(
    label: String,
    themeKey: String,
    isActive: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (isActive) color else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(GenericShape { size, _ ->
                        val w = size.width
                        val h = size.height
                        moveTo(w / 2f, 0f)
                        lineTo(w, h / 4f)
                        lineTo(w, h * 3f / 4f)
                        lineTo(w / 2f, h)
                        lineTo(0f, h * 3f / 4f)
                        lineTo(0f, h / 4f)
                        close()
                    })
                    .background(color)
            )
            Text(text = label, color = Color.White, fontSize = 12.sp)
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active Theme Icon indicator",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// --- COLOR AND GRADIENT HELPER METHOD PRESETS ---

fun getThemePrimaryColor(themeName: String): Color {
    return when (themeName) {
        "SAKURA_BLOSSOM" -> com.example.ui.theme.SakuraPrimary
        "CYBER_NEON" -> com.example.ui.theme.CyberPrimary
        "FOREST_SPIRIT" -> com.example.ui.theme.ForestPrimary
        else -> com.example.ui.theme.LavenderPrimary
    }
}

fun getThemeSecondaryColor(themeName: String): Color {
    return when (themeName) {
        "SAKURA_BLOSSOM" -> com.example.ui.theme.SakuraSecondary
        "CYBER_NEON" -> com.example.ui.theme.CyberSecondary
        "FOREST_SPIRIT" -> com.example.ui.theme.ForestSecondary
        else -> com.example.ui.theme.LavenderSecondary
    }
}

fun getThemeTertiaryColor(themeName: String): Color {
    return when (themeName) {
        "SAKURA_BLOSSOM" -> com.example.ui.theme.SakuraTertiary
        "CYBER_NEON" -> com.example.ui.theme.CyberTertiary
        "FOREST_SPIRIT" -> com.example.ui.theme.ForestTertiary
        else -> com.example.ui.theme.LavenderTertiary
    }
}

fun getDynamicGradientColors(themeName: String): List<Color> {
    return when (themeName) {
        "SAKURA_BLOSSOM" -> listOf(com.example.ui.theme.SakuraBackground, Color(0xFF1F0E12))
        "CYBER_NEON" -> listOf(com.example.ui.theme.CyberBackground, Color(0xFF03070D))
        "FOREST_SPIRIT" -> listOf(com.example.ui.theme.ForestBackground, Color(0xFF020705))
        else -> listOf(com.example.ui.theme.LavenderBackground, Color(0xFF0B061A))
    }
}

class OvershootInterpolator(private val tension: Float) {
    fun getInterpolation(t: Float): Float {
        var tMutable = t - 1.0f
        return tMutable * tMutable * ((tension + 1) * tMutable + tension) + 1.0f
    }
}
