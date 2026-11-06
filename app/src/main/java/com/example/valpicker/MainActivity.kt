package com.example.valpicker

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------- Datos ----------

enum class Role(val label: String, val color: Color) {
    Controller("Controller", Color(0xFF4FC3F7)),
    Sentinel("Sentinel", Color(0xFF81C784)),
    Initiator("Initiator", Color(0xFFFFB74D)),
    Duelist("Duelist", Color(0xFFFF4655))
}

data class Agent(val name: String, val role: Role, val icon: String? = null)

val DEFAULT_AGENTS = listOf("Astra", "Brimstone", "Clove", "Harbor", "Miks", "Omen", "Viper")
    .map { Agent(it, Role.Controller) } +
    listOf("Chamber", "Cypher", "Deadlock", "Killjoy", "Sage", "Veto", "Vyse")
        .map { Agent(it, Role.Sentinel) } +
    listOf("Breach", "Fade", "Gekko", "KAY/O", "Skye", "Sova", "Tejo")
        .map { Agent(it, Role.Initiator) } +
    listOf("Hush", "Iso", "Jett", "Neon", "Phoenix", "Raze", "Reyna", "Waylay", "Yoru")
        .map { Agent(it, Role.Duelist) }

// Lista viva de agentes: se actualiza desde la API
val agentList = mutableStateOf(DEFAULT_AGENTS)
val AGENTS: List<Agent> get() = agentList.value

val POINTER_COLORS = listOf(Color(0xFFFF4655), Color.White, Color(0xFFFFD54F), Color(0xFF4FC3F7))

// ---------- Estado y guardado ----------

class AppState(prefs: SharedPreferences) {
    val pool = mutableStateListOf<String>()
    val roleOff = mutableStateListOf<Role>()
    var singleSec by mutableStateOf(8)
    var teamSec by mutableStateOf(4)
    var intensity by mutableStateOf(5)
    var tickOn by mutableStateOf(true)
    var tickVol by mutableStateOf(0.5f)
    var winVol by mutableStateOf(0.5f)
    var pointerIdx by mutableStateOf(0)
    var teamName by mutableStateOf("")
    var teamSize by mutableStateOf(5)
    val slotRoles = mutableStateListOf<Role?>(null, null, null, null, null)
    var randomizeRoles by mutableStateOf(false)

    init {
        pool.addAll(AGENTS.map { it.name })
        prefs.getString("state", null)?.let { decode(it) }
    }

    fun available(): List<Agent> = AGENTS.filter { it.name in pool && it.role !in roleOff }

    fun resetSettings() {
        singleSec = 8; teamSec = 4; intensity = 5
        tickOn = true; tickVol = 0.5f; winVol = 0.5f; pointerIdx = 0
    }

    fun encode(): String = listOf(
        pool.joinToString(","),
        roleOff.joinToString(",") { it.name },
        singleSec, teamSec, intensity, tickOn, tickVol, winVol, pointerIdx,
        teamName.replace("\n", " "), teamSize,
        slotRoles.joinToString(",") { it?.name ?: "-" },
        randomizeRoles
    ).joinToString("\n")

    private fun decode(s: String) {
        try {
            val p = s.split("\n")
            if (p.size < 13) return
            val names = p[0].split(",").filter { n -> AGENTS.any { it.name == n } }
            pool.clear(); pool.addAll(names)
            roleOff.clear()
            roleOff.addAll(p[1].split(",").mapNotNull { r -> Role.values().firstOrNull { it.name == r } })
            singleSec = p[2].toInt(); teamSec = p[3].toInt(); intensity = p[4].toInt()
            tickOn = p[5].toBoolean(); tickVol = p[6].toFloat(); winVol = p[7].toFloat()
            pointerIdx = p[8].toInt().coerceIn(0, POINTER_COLORS.size - 1)
            teamName = p[9]; teamSize = p[10].toInt().coerceIn(1, 5)
            p[11].split(",").forEachIndexed { i, r ->
                if (i < 5) slotRoles[i] = Role.values().firstOrNull { it.name == r }
            }
            randomizeRoles = p[12].toBoolean()
        } catch (e: Exception) {
        }
    }
}

class Presets(private val prefs: SharedPreferences, private val key: String) {
    val map = mutableStateMapOf<String, String>()

    init {
        (prefs.getString(key, "") ?: "").split("\n").forEach { l ->
            val i = l.indexOf('|')
            if (i > 0) map[l.substring(0, i)] = l.substring(i + 1)
        }
    }

    private fun clean(s: String) = s.replace("|", " ").replace("\n", " ").trim()

    fun put(name: String, value: String) {
        map[clean(name)] = value.replace("\n", " ")
        persist()
    }

    fun remove(name: String) {
        map.remove(clean(name))
        persist()
    }

    private fun persist() {
        prefs.edit().putString(key, map.entries.joinToString("\n") { "${it.key}|${it.value}" }).apply()
    }
}

class TonePlayer(private val tone: Int, private val ms: Int) {
    private var tg: ToneGenerator? = null
    private var vol = -1

    fun play(v: Float) {
        val iv = (v * 100).toInt().coerceIn(0, 100)
        if (iv == 0) return
        try {
            if (tg == null || vol != iv) {
                tg?.release()
                tg = ToneGenerator(AudioManager.STREAM_MUSIC, iv)
                vol = iv
            }
            tg?.startTone(tone, ms)
        } catch (e: RuntimeException) {
        }
    }

    fun release() {
        tg?.release(); tg = null
    }
}

suspend fun fetchAgents(): List<Agent> = withContext(Dispatchers.IO) {
    val conn = URL("https://valorant-api.com/v1/agents?isPlayableCharacter=true")
        .openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 10000
    try {
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("data")
        val list = mutableListOf<Agent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("displayName")
            val roleName = o.optJSONObject("role")?.optString("displayName") ?: ""
            val role = Role.values().firstOrNull { it.label.equals(roleName, true) }
            val icon = o.optString("displayIcon").ifBlank { null }
            if (name.isNotBlank() && role != null) list.add(Agent(name, role, icon))
        }
        list.sortedWith(compareBy({ it.role.ordinal }, { it.name }))
    } finally {
        conn.disconnect()
    }
}

fun saveAgentCache(prefs: SharedPreferences, list: List<Agent>) {
    prefs.edit().putString(
        "agents_cache",
        list.joinToString("\n") { "${it.name}|${it.role.name}|${it.icon ?: ""}" }
    ).apply()
}

fun loadAgentCache(prefs: SharedPreferences): List<Agent>? {
    val list = (prefs.getString("agents_cache", "") ?: "").split("\n").mapNotNull { l ->
        val p = l.split("|")
        if (p.size < 3) return@mapNotNull null
        val role = Role.values().firstOrNull { it.name == p[1] } ?: return@mapNotNull null
        Agent(p[0], role, p[2].ifBlank { null })
    }
    return list.ifEmpty { null }
}

@Composable
fun AgentImage(url: String?, size: Dp) {
    if (url == null) {
        Spacer(Modifier.size(size))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    }
}

fun rollTeam(st: AppState, avail: List<Agent>): List<Agent>? {
    var roles: List<Role?> = st.slotRoles.take(st.teamSize)
    if (st.randomizeRoles) roles = roles.shuffled()
    val used = mutableSetOf<String>()
    val result = arrayOfNulls<Agent>(roles.size)
    val order = roles.indices.sortedBy { if (roles[it] == null) 1 else 0 }
    for (i in order) {
        val cand = avail.filter { it.name !in used && (roles[i] == null || it.role == roles[i]) }
        val pick = cand.randomOrNull() ?: return null
        used += pick.name
        result[i] = pick
    }
    return result.map { it!! }
}

// ---------- Actividad ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wheel", MODE_PRIVATE)
        loadAgentCache(prefs)?.let { agentList.value = it }
        val st = AppState(prefs)
        val poolPresets = Presets(prefs, "pool_presets")
        val teamPresets = Presets(prefs, "team_presets")
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF4655),
                    background = Color(0xFF0F1923),
                    surface = Color(0xFF1B2733)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(st, poolPresets, teamPresets, prefs)
                }
            }
        }
    }
}

// ---------- Ruleta ----------

@Composable
fun Wheel(agents: List<Agent>, rotation: Float, pointer: Color, modifier: Modifier = Modifier) {
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
            color = 0xFF0F1923.toInt()
        }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val n = agents.size
        if (n == 0) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.94f
        val sweep = 360f / n
        paint.textSize = r * (if (n > 26) 0.065f else 0.08f)
        rotate(rotation, pivot = Offset(cx, cy)) {
            agents.forEachIndexed { i, a ->
                val start = -90f + i * sweep
                drawArc(
                    color = a.role.color.copy(alpha = if (i % 2 == 0) 0.95f else 0.7f),
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r)
                )
                drawArc(
                    color = Color(0xFF0F1923),
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r),
                    style = Stroke(width = 2f)
                )
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.rotate(start + sweep / 2f, cx, cy)
                    nc.drawText(a.name, cx + r - r * 0.06f, cy + paint.textSize / 3f, paint)
                    nc.restore()
                }
            }
        }
        drawCircle(Color(0xFF0F1923), radius = r * 0.12f, center = Offset(cx, cy))
        val tri = Path().apply {
            moveTo(cx - r * 0.06f, cy - r - r * 0.05f)
            lineTo(cx + r * 0.06f, cy - r - r * 0.05f)
            lineTo(cx, cy - r + r * 0.1f)
            close()
        }
        drawPath(tri, pointer)
    }
}

// ---------- Pantalla principal ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(st: AppState, poolPresets: Presets, teamPresets: Presets, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rot = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Agent?>(null) }
    var mode by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var teamDisplay by remember { mutableStateOf<List<Agent?>>(emptyList()) }
    var teamError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val fresh = fetchAgents()
            if (fresh.isNotEmpty()) {
                val known = prefs.getString("known", null)
                    ?.split(",")?.filter { it.isNotBlank() }?.toSet()
                    ?: AGENTS.map { it.name }.toSet()
                val newOnes = fresh.map { it.name }.filter { it !in known }
                newOnes.forEach { if (it !in st.pool) st.pool.add(it) }
                agentList.value = fresh
                saveAgentCache(prefs, fresh)
                prefs.edit().putString("known", (known + fresh.map { it.name }).joinToString(",")).apply()
                if (newOnes.isNotEmpty()) {
                    Toast.makeText(ctx, "Nuevo agente: ${newOnes.joinToString()}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
        }
    }

    val tickSound = remember { TonePlayer(ToneGenerator.TONE_CDMA_PIP, 25) }
    val winSound = remember { TonePlayer(ToneGenerator.TONE_PROP_ACK, 350) }
    DisposableEffect(Unit) { onDispose { tickSound.release(); winSound.release() } }

    LaunchedEffect(Unit) {
        snapshotFlow { st.encode() }.collect { prefs.edit().putString("state", it).apply() }
    }

    val avail = st.available()

    LaunchedEffect(avail.size) {
        var last = -1
        var lastT = 0L
        snapshotFlow { rot.value }.collect { v ->
            val n = avail.size
            if (n > 0) {
                val i = ((((-v) % 360f) + 360f) % 360f / (360f / n)).toInt()
                if (i != last) {
                    last = i
                    val now = System.currentTimeMillis()
                    if (spinning && st.tickOn && now - lastT > 35) {
                        lastT = now
                        tickSound.play(st.tickVol)
                    }
                }
            }
        }
    }

    fun spinSingle() {
        if (spinning) return
        val list = st.available()
        if (list.isEmpty()) return
        scope.launch {
            spinning = true
            val n = list.size
            val idx = Random.nextInt(n)
            val sweep = 360f / n
            val jitter = 0.1f + Random.nextFloat() * 0.8f
            val residue = (((-(idx + jitter) * sweep) % 360f) + 360f) % 360f
            val cur = rot.value
            val curMod = ((cur % 360f) + 360f) % 360f
            val delta = ((residue - curMod) + 360f) % 360f
            val target = cur + 360f * (3 + st.intensity) + delta
            rot.animateTo(
                target,
                tween(st.singleSec * 1000, easing = CubicBezierEasing(0.1f, 0.75f, 0.15f, 1f))
            )
            winner = list[idx]
            winSound.play(st.winVol)
            spinning = false
        }
    }

    fun spinTeam() {
        if (spinning) return
        val pool = st.available()
        val final = rollTeam(st, pool)
        if (final == null) {
            teamError = "No hay suficientes agentes para esa composición"
            return
        }
        teamError = null
        scope.launch {
            spinning = true
            val total = st.teamSec * 1000L
            val t0 = System.currentTimeMillis()
            val size = final.size
            var locked = 0
            while (true) {
                val el = System.currentTimeMillis() - t0
                if (el >= total) break
                val newLocked = ((el * size) / total).toInt()
                if (newLocked > locked) {
                    locked = newLocked
                    if (st.tickOn) tickSound.play(st.tickVol)
                }
                teamDisplay = List(size) { i -> if (i < locked) final[i] else pool.random() }
                delay(70)
            }
            teamDisplay = final
            winSound.play(st.winVol)
            spinning = false
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VALORANT AGENT WHEEL", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Individual") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Equipo") })
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Role.values().forEach { role ->
                FilterChip(
                    selected = role !in st.roleOff,
                    onClick = { if (role in st.roleOff) st.roleOff.remove(role) else st.roleOff.add(role) },
                    label = { Text(role.label, color = role.color) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (mode == 0) {
            if (avail.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay agentes disponibles.\nActívalos en Agent Pool.",
                        textAlign = TextAlign.Center, color = Color.Gray
                    )
                }
            } else {
                Wheel(
                    avail, rot.value, POINTER_COLORS[st.pointerIdx],
                    Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { spinSingle() },
                enabled = !spinning && avail.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("GIRAR", fontSize = 16.sp) }
        } else {
            if (st.teamName.isNotBlank()) {
                Text(st.teamName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            repeat(st.teamSize) { i ->
                val a = teamDisplay.getOrNull(i)
                val slotRole = st.slotRoles[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = Color.Gray, modifier = Modifier.width(24.dp))
                    if (a != null) {
                        AgentImage(a.icon, 48.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        a?.name?.uppercase() ?: "?",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = a?.role?.color ?: Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(slotRole?.label ?: "Any", color = Color.Gray, fontSize = 12.sp)
                }
            }
            teamError?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { spinTeam() },
                enabled = !spinning && avail.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("GIRAR EQUIPO", fontSize = 16.sp) }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "pool" }) { Text("Agent Pool") }
            OutlinedButton(onClick = { dialog = "team" }) { Text("Editar equipo") }
            OutlinedButton(onClick = { dialog = "settings" }) { Text("Ajustes") }
        }
    }

    // ----- Ganador -----
    winner?.let { w ->
        AlertDialog(
            onDismissRequest = { winner = null },
            title = { Text("Ganador") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AgentImage(w.icon, 160.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(w.name.uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Black, color = w.role.color)
                    Text(w.role.label, color = Color.Gray)
                    TextButton(onClick = { st.pool.remove(w.name); winner = null }) {
                        Text("Quitar de la ruleta")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Me ha tocado ${w.name} en Valorant Agent Wheel")
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }) { Text("Compartir") }
            },
            dismissButton = { TextButton(onClick = { winner = null }) { Text("Cerrar") } }
        )
    }

    // ----- Agent Pool -----
    if (dialog == "pool") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
            title = { Text("Agent Pool") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Toca los agentes para meterlos o sacarlos de la ruleta.", fontSize = 12.sp, color = Color.Gray)
                    TextButton(onClick = {
                        st.pool.clear(); st.pool.addAll(AGENTS.map { it.name })
                    }) { Text("Activar todos los agentes") }
                    Role.values().forEach { role ->
                        Text(role.label, color = role.color, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AGENTS.filter { it.role == role }.forEach { a ->
                                FilterChip(
                                    selected = a.name in st.pool,
                                    onClick = {
                                        if (a.name in st.pool) st.pool.remove(a.name) else st.pool.add(a.name)
                                    },
                                    label = { Text(a.name) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Presets", fontWeight = FontWeight.Bold)
                    PresetBar(
                        poolPresets,
                        makeValue = { st.pool.joinToString(",") },
                        applyValue = { v ->
                            val names = v.split(",").filter { n -> AGENTS.any { it.name == n } }
                            st.pool.clear(); st.pool.addAll(names)
                        }
                    )
                }
            }
        )
    }

    // ----- Equipo -----
    if (dialog == "team") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
            title = { Text("Equipo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = st.teamName, onValueChange = { st.teamName = it },
                        label = { Text("Nombre del equipo") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tamaño del equipo", Modifier.weight(1f))
                        TextButton(onClick = { if (st.teamSize > 1) st.teamSize-- }) { Text("−") }
                        Text("${st.teamSize}", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { if (st.teamSize < 5) st.teamSize++ }) { Text("+") }
                    }
                    Text("Toca cada hueco para cambiar su rol.", fontSize = 12.sp, color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(st.teamSize) { i ->
                            val role = st.slotRoles[i]
                            AssistChip(
                                onClick = {
                                    val options: List<Role?> = listOf<Role?>(null) + Role.values().toList()
                                    val next = (options.indexOf(role) + 1) % options.size
                                    st.slotRoles[i] = options[next]
                                },
                                label = { Text("${i + 1}: ${role?.label ?: "Any"}", color = role?.color ?: Color.White) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mezclar roles entre compañeros", Modifier.weight(1f))
                        Switch(checked = st.randomizeRoles, onCheckedChange = { st.randomizeRoles = it })
                    }
                    Text("Los huecos sin rol se rellenan con cualquier agente.", fontSize = 12.sp, color = Color.Gray)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Presets", fontWeight = FontWeight.Bold)
                    PresetBar(
                        teamPresets,
                        makeValue = {
                            "${st.teamName.replace(";", " ")};${st.teamSize};" +
                                "${st.slotRoles.joinToString(",") { it?.name ?: "-" }};${st.randomizeRoles}"
                        },
                        applyValue = { v ->
                            try {
                                val p = v.split(";")
                                st.teamName = p[0]
                                st.teamSize = p[1].toInt().coerceIn(1, 5)
                                p[2].split(",").forEachIndexed { i, r ->
                                    if (i < 5) st.slotRoles[i] = Role.values().firstOrNull { it.name == r }
                                }
                                st.randomizeRoles = p[3].toBoolean()
                            } catch (e: Exception) {
                            }
                        }
                    )
                }
            }
        )
    }

    // ----- Ajustes -----
    if (dialog == "settings") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { st.resetSettings() }) { Text("Restablecer") } },
            title = { Text("Giro y sonido") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Color del puntero")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        POINTER_COLORS.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(34.dp).background(c, CircleShape)
                                    .border(if (i == st.pointerIdx) 3.dp else 0.dp, Color.Gray, CircleShape)
                                    .clickable { st.pointerIdx = i }
                            )
                        }
                    }
                    Text("Duración giro individual: ${st.singleSec}s")
                    Slider(
                        value = st.singleSec.toFloat(), onValueChange = { st.singleSec = it.roundToInt() },
                        valueRange = 2f..20f, steps = 17
                    )
                    Text("Duración giro de equipo: ${st.teamSec}s")
                    Slider(
                        value = st.teamSec.toFloat(), onValueChange = { st.teamSec = it.roundToInt() },
                        valueRange = 1f..10f, steps = 8
                    )
                    Text("Intensidad del giro: ${st.intensity}")
                    Slider(
                        value = st.intensity.toFloat(), onValueChange = { st.intensity = it.roundToInt() },
                        valueRange = 1f..10f, steps = 8
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sonido de ticks", Modifier.weight(1f))
                        Switch(checked = st.tickOn, onCheckedChange = { st.tickOn = it })
                    }
                    Text("Volumen de ticks: ${(st.tickVol * 100).roundToInt()}%")
                    Slider(value = st.tickVol, onValueChange = { st.tickVol = it })
                    Text("Volumen de victoria: ${(st.winVol * 100).roundToInt()}%")
                    Slider(value = st.winVol, onValueChange = { st.winVol = it })
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetBar(presets: Presets, makeValue: () -> String, applyValue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text("Nombre del preset") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Button(onClick = { if (name.isNotBlank()) presets.put(name, makeValue()) }) { Text("Guardar") }
        OutlinedButton(onClick = { presets.remove(name); name = "" }) { Text("Borrar") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.map.keys.sorted().forEach { k ->
            AssistChip(
                onClick = { name = k; presets.map[k]?.let { applyValue(it) } },
                label = { Text(k) }
            )
        }
    }
}
