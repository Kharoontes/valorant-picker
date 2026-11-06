@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.valpicker

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlin.random.Random

// ============================================================
//  DATOS
// ============================================================

val BG = Color(0xFF0F1923)
val SURFACE = Color(0xFF1B2733)
val OUTLINE = Color(0xFF2B3948)
val RED = Color(0xFFFF4655)
val GREEN = Color(0xFF66D18F)

enum class Role(val label: String, val es: String, val color: Color) {
    Controller("Controller", "Controlador", Color(0xFF4FC3F7)),
    Sentinel("Sentinel", "Centinela", Color(0xFF81C784)),
    Initiator("Initiator", "Iniciador", Color(0xFFFFB74D)),
    Duelist("Duelist", "Duelista", Color(0xFFFF4655))
}

fun roleOf(name: String): Role? = when (name.trim().lowercase()) {
    "controller", "controlador" -> Role.Controller
    "sentinel", "centinela" -> Role.Sentinel
    "initiator", "iniciador" -> Role.Initiator
    "duelist", "duelista" -> Role.Duelist
    else -> null
}

data class RoleInfo(val icon: String?, val desc: String?)
data class Ability(val name: String, val icon: String?)
data class Agent(
    val name: String,
    val role: Role,
    val icon: String? = null,
    val abilities: List<Ability> = emptyList()
)

data class Weapon(val name: String, val cat: String, val icon: String?, val cost: Int?)

val WEAPON_CATS = listOf("Sidearm", "SMG", "Shotgun", "Rifle", "Sniper", "Heavy", "Melee")

fun catEs(c: String) = when (c) {
    "Sidearm" -> "Pistolas"
    "SMG" -> "Subfusiles"
    "Shotgun" -> "Escopetas"
    "Rifle" -> "Rifles"
    "Sniper" -> "Francotiradores"
    "Heavy" -> "Pesadas"
    "Melee" -> "Cuerpo a cuerpo"
    else -> c
}

val DEFAULT_AGENTS = listOf("Astra", "Brimstone", "Clove", "Harbor", "Miks", "Omen", "Viper")
    .map { Agent(it, Role.Controller) } +
    listOf("Chamber", "Cypher", "Deadlock", "Killjoy", "Sage", "Veto", "Vyse")
        .map { Agent(it, Role.Sentinel) } +
    listOf("Breach", "Fade", "Gekko", "KAY/O", "Skye", "Sova", "Tejo")
        .map { Agent(it, Role.Initiator) } +
    listOf("Hush", "Iso", "Jett", "Neon", "Phoenix", "Raze", "Reyna", "Waylay", "Yoru")
        .map { Agent(it, Role.Duelist) }

// Estado vivo: se actualiza desde la API
val agentList = mutableStateOf(DEFAULT_AGENTS)
val AGENTS: List<Agent> get() = agentList.value
val roleInfo = mutableStateOf<Map<Role, RoleInfo>>(emptyMap())
val weaponList = mutableStateOf<List<Weapon>>(emptyList())

val POINTER_COLORS = listOf(RED, Color.White, Color(0xFFFFD54F), Color(0xFF4FC3F7))

// ============================================================
//  API Y CACHÉ
// ============================================================

fun JSONObject.str(k: String): String? {
    if (isNull(k)) return null
    return optString(k).ifBlank { null }
}

suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 15000
    try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

suspend fun fetchAgentsText(): String = try {
    httpGet("https://valorant-api.com/v1/agents?isPlayableCharacter=true&language=es-MX")
} catch (e: Exception) {
    httpGet("https://valorant-api.com/v1/agents?isPlayableCharacter=true")
}

fun parseAgents(text: String): Pair<List<Agent>, Map<Role, RoleInfo>> {
    val arr = JSONObject(text).getJSONArray("data")
    val list = mutableListOf<Agent>()
    val roles = mutableMapOf<Role, RoleInfo>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val name = o.str("displayName") ?: continue
        val ro = o.optJSONObject("role")
        val role = roleOf(ro?.str("displayName") ?: "") ?: continue
        if (ro != null && role !in roles) roles[role] = RoleInfo(ro.str("displayIcon"), ro.str("description"))
        val abs = mutableListOf<Ability>()
        val aa = o.optJSONArray("abilities")
        if (aa != null) {
            for (j in 0 until aa.length()) {
                val a = aa.getJSONObject(j)
                val ic = a.str("displayIcon")
                if (!(a.str("slot") ?: "").equals("Passive", true) && ic != null) {
                    abs.add(Ability(a.str("displayName") ?: "", ic))
                }
            }
        }
        list.add(Agent(name, role, o.str("displayIcon"), abs))
    }
    return Pair(list.sortedWith(compareBy({ it.role.ordinal }, { it.name })), roles)
}

fun parseWeapons(text: String): List<Weapon> {
    val arr = JSONObject(text).getJSONArray("data")
    val list = mutableListOf<Weapon>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val name = o.str("displayName") ?: continue
        val cat = (o.str("category") ?: "").substringAfterLast("::")
        val icon = o.str("displayIcon") ?: continue
        val shop = o.optJSONObject("shopData")
        val cost = if (shop == null || shop.isNull("cost")) null else shop.optInt("cost")
        if (cat in WEAPON_CATS) list.add(Weapon(name, cat, icon, cost))
    }
    return list.sortedWith(compareBy({ WEAPON_CATS.indexOf(it.cat) }, { it.cost ?: 0 }, { it.name }))
}

fun agentsToJson(list: List<Agent>): String {
    val arr = JSONArray()
    list.forEach { a ->
        val o = JSONObject()
        o.put("n", a.name); o.put("r", a.role.name); o.put("i", a.icon ?: "")
        val ab = JSONArray()
        a.abilities.forEach { b -> ab.put(JSONObject().put("n", b.name).put("i", b.icon ?: "")) }
        o.put("a", ab)
        arr.put(o)
    }
    return arr.toString()
}

fun agentsFromJson(s: String): List<Agent> {
    val arr = JSONArray(s)
    val out = mutableListOf<Agent>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val role = Role.values().firstOrNull { it.name == o.getString("r") } ?: continue
        val abs = mutableListOf<Ability>()
        val aa = o.optJSONArray("a")
        if (aa != null) {
            for (j in 0 until aa.length()) {
                val b = aa.getJSONObject(j)
                abs.add(Ability(b.getString("n"), b.getString("i").ifBlank { null }))
            }
        }
        out.add(Agent(o.getString("n"), role, o.getString("i").ifBlank { null }, abs))
    }
    return out
}

fun rolesToJson(m: Map<Role, RoleInfo>): String {
    val o = JSONObject()
    m.forEach { (r, v) -> o.put(r.name, JSONObject().put("i", v.icon ?: "").put("d", v.desc ?: "")) }
    return o.toString()
}

fun rolesFromJson(s: String): Map<Role, RoleInfo> {
    val o = JSONObject(s)
    val m = mutableMapOf<Role, RoleInfo>()
    Role.values().forEach { r ->
        o.optJSONObject(r.name)?.let {
            m[r] = RoleInfo(it.optString("i").ifBlank { null }, it.optString("d").ifBlank { null })
        }
    }
    return m
}

fun weaponsToJson(l: List<Weapon>): String {
    val arr = JSONArray()
    l.forEach { w ->
        arr.put(JSONObject().put("n", w.name).put("c", w.cat).put("i", w.icon ?: "").put("k", w.cost ?: -1))
    }
    return arr.toString()
}

fun weaponsFromJson(s: String): List<Weapon> {
    val arr = JSONArray(s)
    val out = mutableListOf<Weapon>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(Weapon(o.getString("n"), o.getString("c"), o.getString("i").ifBlank { null }, o.getInt("k").takeIf { it >= 0 }))
    }
    return out
}

fun loadCaches(prefs: SharedPreferences) {
    try {
        prefs.getString("agents_json", null)?.let { s ->
            val l = agentsFromJson(s)
            if (l.isNotEmpty()) agentList.value = l
        }
    } catch (e: Exception) {
    }
    try {
        prefs.getString("roles_json", null)?.let { roleInfo.value = rolesFromJson(it) }
    } catch (e: Exception) {
    }
    try {
        prefs.getString("weapons_json", null)?.let { weaponList.value = weaponsFromJson(it) }
    } catch (e: Exception) {
    }
}

// ============================================================
//  ESTADO GUARDADO, SONIDO
// ============================================================

class AppState(prefs: SharedPreferences) {
    val roleOff = mutableStateListOf<Role>()
    val catOff = mutableStateListOf("Melee")
    val team = mutableStateListOf<String?>(null, null, null, null, null)
    var singleSec by mutableStateOf(8)
    var weaponSec by mutableStateOf(4)
    var intensity by mutableStateOf(5)
    var tickOn by mutableStateOf(true)
    var tickVol by mutableStateOf(0.5f)
    var winVol by mutableStateOf(0.5f)
    var pointerIdx by mutableStateOf(0)

    init {
        prefs.getString("state2", null)?.let { decode(it) }
    }

    fun available(): List<Agent> = AGENTS.filter { it.role !in roleOff }
    fun weapons(): List<Weapon> = weaponList.value.filter { it.cat !in catOff }

    fun resetSettings() {
        singleSec = 8; weaponSec = 4; intensity = 5
        tickOn = true; tickVol = 0.5f; winVol = 0.5f; pointerIdx = 0
    }

    fun encode(): String = listOf(
        roleOff.joinToString(",") { it.name },
        catOff.joinToString(","),
        singleSec, weaponSec, intensity, tickOn, tickVol, winVol, pointerIdx,
        team.joinToString(",") { it ?: "-" }
    ).joinToString("\n")

    private fun decode(s: String) {
        try {
            val p = s.split("\n")
            if (p.size < 10) return
            roleOff.clear()
            roleOff.addAll(p[0].split(",").mapNotNull { r -> Role.values().firstOrNull { it.name == r } })
            catOff.clear()
            catOff.addAll(p[1].split(",").filter { it in WEAPON_CATS })
            singleSec = p[2].toInt(); weaponSec = p[3].toInt(); intensity = p[4].toInt()
            tickOn = p[5].toBoolean(); tickVol = p[6].toFloat(); winVol = p[7].toFloat()
            pointerIdx = p[8].toInt().coerceIn(0, POINTER_COLORS.size - 1)
            p[9].split(",").forEachIndexed { i, n -> if (i < 5) team[i] = if (n == "-") null else n }
        } catch (e: Exception) {
        }
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

// ============================================================
//  RECOMENDACIONES
// ============================================================

data class Advice(val text: String, val ok: Boolean)
data class RoleRec(val role: Role, val reason: String)

val PRIORITY = listOf(Role.Controller, Role.Initiator, Role.Duelist, Role.Sentinel)
val FLEX_ORDER = listOf(Role.Duelist, Role.Initiator, Role.Sentinel, Role.Controller)

fun missingReason(r: Role) = when (r) {
    Role.Controller -> "sin humos te costará controlar las zonas"
    Role.Initiator -> "sin información ni apoyo para entrar a los sitios"
    Role.Duelist -> "nadie que abra espacio en los enfrentamientos"
    Role.Sentinel -> "nadie que asegure los flancos y el retake"
}

fun plural(s: String) = if (s.endsWith("r")) s + "es" else s + "s"

fun teamAdvice(team: List<Agent>): List<Advice> {
    if (team.isEmpty()) return emptyList()
    val out = mutableListOf<Advice>()
    val counts = Role.values().associateWith { r -> team.count { it.role == r } }
    PRIORITY.filter { counts[it] == 0 }.forEach {
        out += Advice("Sin ${it.es.lowercase()}: ${missingReason(it)}.", false)
    }
    Role.values().filter { counts[it]!! >= 3 }.forEach {
        out += Advice("Hay ${counts[it]} ${plural(it.es.lowercase())}: diversifica tu composición.", false)
    }
    if (out.isEmpty()) {
        out += if (team.size >= 5) Advice("Composición equilibrada: tienes todos los roles cubiertos.", true)
        else Advice("Vas bien: ya cubres todos los roles. Completa con el agente que más te guste.", true)
    }
    return out
}

fun buildRecs(team: List<Agent>): List<RoleRec> {
    val counts = Role.values().associateWith { r -> team.count { it.role == r } }
    val slotsLeft = 5 - team.size
    val missing = PRIORITY.filter { counts[it] == 0 }
    val take = if (slotsLeft > 0) slotsLeft else missing.size
    val recs = mutableListOf<RoleRec>()
    missing.take(take).forEach { recs += RoleRec(it, "Tu equipo no tiene ${it.es.lowercase()}") }
    val sim = counts.toMutableMap()
    recs.forEach { sim[it.role] = sim[it.role]!! + 1 }
    while (recs.size < slotsLeft) {
        val r = FLEX_ORDER.minByOrNull { sim[it]!! }!!
        if (recs.any { it.role == r }) break
        recs += RoleRec(r, "Opción flexible para completar el equipo")
        sim[r] = sim[r]!! + 1
    }
    return recs
}

// ============================================================
//  ACTIVIDAD
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wheel", MODE_PRIVATE)
        loadCaches(prefs)
        val st = AppState(prefs)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = RED,
                    background = BG,
                    surface = SURFACE
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = BG) {
                    App(st, prefs)
                }
            }
        }
    }
}

// ============================================================
//  COMPONENTES BÁSICOS
// ============================================================

@Composable
fun AgentImage(url: String?, modifier: Modifier, fallback: String = "") {
    if (url == null) {
        Box(modifier.clip(CircleShape).background(OUTLINE), contentAlignment = Alignment.Center) {
            Text(fallback.take(1), fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = fallback,
            modifier = modifier.clip(CircleShape).background(OUTLINE)
        )
    }
}

@Composable
fun RoleIcon(role: Role, size: Dp) {
    val url = roleInfo.value[role]?.icon
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = role.es,
            colorFilter = ColorFilter.tint(role.color),
            modifier = Modifier.size(size)
        )
    } else {
        Box(Modifier.size(size * 0.6f).background(role.color, CircleShape))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, color = Color.Gray, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun Wheel(agents: List<Agent>, rotation: () -> Float, pointer: Color, modifier: Modifier = Modifier) {
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
        rotate(rotation(), pivot = Offset(cx, cy)) {
            agents.forEachIndexed { i, a ->
                val start = -90f + i * sweep
                drawArc(
                    color = a.role.color.copy(alpha = if (i % 2 == 0) 0.95f else 0.7f),
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r)
                )
                drawArc(
                    color = BG,
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
        drawCircle(BG, radius = r * 0.12f, center = Offset(cx, cy))
        val tri = Path().apply {
            moveTo(cx - r * 0.06f, cy - r - r * 0.05f)
            lineTo(cx + r * 0.06f, cy - r - r * 0.05f)
            lineTo(cx, cy - r + r * 0.1f)
            close()
        }
        drawPath(tri, pointer)
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Cargando imágenes…")
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ============================================================
//  APP
// ============================================================

@Composable
fun App(st: AppState, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var spinning by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var imagesReady by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Agent?>(null) }
    var shownWeapon by remember { mutableStateOf<Weapon?>(null) }
    val rot = remember { Animatable(0f) }

    val tickSound = remember { TonePlayer(ToneGenerator.TONE_CDMA_PIP, 25) }
    val winSound = remember { TonePlayer(ToneGenerator.TONE_PROP_ACK, 350) }
    DisposableEffect(Unit) { onDispose { tickSound.release(); winSound.release() } }

    // Guardado automático de ajustes
    LaunchedEffect(Unit) {
        snapshotFlow { st.encode() }.collect { prefs.edit().putString("state2", it).apply() }
    }

    // Descarga de agentes y armas desde la API
    LaunchedEffect(Unit) {
        try {
            val (fresh, roles) = parseAgents(fetchAgentsText())
            if (fresh.isNotEmpty()) {
                val known = prefs.getString("known", null)
                    ?.split(",")?.filter { it.isNotBlank() }?.toSet()
                    ?: AGENTS.map { it.name }.toSet()
                val newOnes = fresh.map { it.name }.filter { it !in known }
                agentList.value = fresh
                if (roles.isNotEmpty()) roleInfo.value = roles
                prefs.edit()
                    .putString("agents_json", agentsToJson(fresh))
                    .putString("roles_json", rolesToJson(roleInfo.value))
                    .putString("known", (known + fresh.map { it.name }).joinToString(","))
                    .apply()
                if (newOnes.isNotEmpty()) {
                    Toast.makeText(ctx, "Nuevo agente: ${newOnes.joinToString()}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
        }
        try {
            val w = parseWeapons(httpGet("https://valorant-api.com/v1/weapons"))
            if (w.isNotEmpty()) {
                weaponList.value = w
                prefs.edit().putString("weapons_json", weaponsToJson(w)).apply()
            }
        } catch (e: Exception) {
        }
    }

    // Precarga de imágenes
    val agentsNow = agentList.value
    val weaponsNow = weaponList.value
    val rolesNow = roleInfo.value
    LaunchedEffect(agentsNow, weaponsNow, rolesNow) {
        imagesReady = false
        val loader = ctx.imageLoader
        suspend fun preload(urls: List<String>) {
            coroutineScope {
                urls.map { u ->
                    async {
                        try {
                            loader.execute(ImageRequest.Builder(ctx).data(u).build())
                        } catch (e: Exception) {
                        }
                    }
                }.awaitAll()
            }
        }
        val main = (agentsNow.mapNotNull { it.icon } +
            rolesNow.values.mapNotNull { it.icon } +
            weaponsNow.mapNotNull { it.icon }).distinct()
        preload(main)
        imagesReady = true
        preload(agentsNow.flatMap { a -> a.abilities.mapNotNull { it.icon } }.distinct())
    }

    suspend fun waitForImages(url: String?) {
        waiting = true
        withTimeoutOrNull(6000) { snapshotFlow { imagesReady }.first { it } }
        if (url != null) {
            withTimeoutOrNull(3000) {
                try {
                    ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).build())
                } catch (e: Exception) {
                }
            }
        }
        waiting = false
    }

    val avail = st.available()

    // Ticks de la ruleta
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

    fun spinAgent() {
        if (spinning) return
        val list = st.available()
        if (list.isEmpty()) return
        scope.launch {
            spinning = true
            val n = list.size
            val idx = Random.nextInt(n)
            waitForImages(list[idx].icon)
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

    fun spinWeapon() {
        if (spinning) return
        val list = st.weapons()
        if (list.isEmpty()) return
        scope.launch {
            spinning = true
            val n = list.size
            val win = Random.nextInt(n)
            waitForImages(list[win].icon)
            val steps = ((st.weaponSec * 1000) / 160).coerceAtLeast(8)
            for (k in 0 until steps) {
                val idx = (((win - (steps - 1 - k)) % n) + n) % n
                shownWeapon = list[idx]
                if (st.tickOn) tickSound.play(st.tickVol)
                val f = k.toFloat() / (steps - 1)
                delay((40 + 360 * f * f).toLong())
            }
            winSound.play(st.winVol)
            spinning = false
        }
    }

    Scaffold(
        containerColor = BG,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (tab) {
                        0 -> "AGENTES"
                        1 -> "TU EQUIPO"
                        else -> "ARMAS"
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.Gray)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF16212C)) {
                val items = listOf(
                    Icons.Filled.Person to "Agentes",
                    Icons.Filled.Lightbulb to "Recomendaciones",
                    Icons.Filled.GpsFixed to "Armas"
                )
                items.forEachIndexed { i, (icon, desc) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = desc, modifier = Modifier.size(28.dp)) },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RED,
                            unselectedIconColor = Color.Gray,
                            indicatorColor = RED.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> AgentsTab(st, rot, avail, spinning, waiting) { spinAgent() }
                1 -> TeamTab(st)
                else -> WeaponsTab(st, shownWeapon, spinning, waiting) { spinWeapon() }
            }
        }
    }

    // Resultado de agente
    winner?.let { w ->
        AlertDialog(
            onDismissRequest = { winner = null },
            confirmButton = {
                TextButton(onClick = { winner = null; spinAgent() }) { Text("Girar otra vez") }
            },
            dismissButton = { TextButton(onClick = { winner = null }) { Text("Cerrar") } },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AgentImage(w.icon, Modifier.size(150.dp), w.name)
                    Spacer(Modifier.height(10.dp))
                    Text(w.name.uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = w.role.color)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoleIcon(w.role, 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(w.role.es, color = Color.Gray)
                    }
                }
            }
        )
    }

    if (showSettings) SettingsDialog(st) { showSettings = false }
}

// ============================================================
//  PESTAÑA AGENTES
// ============================================================

@Composable
fun AgentsTab(
    st: AppState,
    rot: Animatable<Float, *>,
    avail: List<Agent>,
    spinning: Boolean,
    waiting: Boolean,
    onSpin: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Role.values().forEach { role ->
                val on = role !in st.roleOff
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.weight(1f).height(52.dp).clip(shape)
                        .background(if (on) role.color.copy(alpha = 0.18f) else SURFACE)
                        .border(2.dp, if (on) role.color else OUTLINE, shape)
                        .clickable { if (on) st.roleOff.add(role) else st.roleOff.remove(role) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.alpha(if (on) 1f else 0.35f)) { RoleIcon(role, 28.dp) }
                }
            }
        }

        if (avail.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Activa al menos un rol\narriba para girar la ruleta.",
                    textAlign = TextAlign.Center, color = Color.Gray
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Wheel(avail, { rot.value }, POINTER_COLORS[st.pointerIdx], Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton("GIRAR", enabled = !spinning && avail.isNotEmpty(), loading = waiting, onClick = onSpin)
    }
}

// ============================================================
//  PESTAÑA RECOMENDACIONES (EQUIPO)
// ============================================================

@Composable
fun TeamTab(st: AppState) {
    var pickSlot by remember { mutableStateOf<Int?>(null) }
    val team = st.team.mapNotNull { n -> AGENTS.firstOrNull { it.name == n } }
    val advice = teamAdvice(team)
    val recs = buildRecs(team)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionTitle("Elige a tu equipo")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { i ->
                val a = AGENTS.firstOrNull { it.name == st.team[i] }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                            .background(SURFACE)
                            .border(2.dp, a?.role?.color ?: OUTLINE, CircleShape)
                            .clickable { pickSlot = i },
                        contentAlignment = Alignment.Center
                    ) {
                        if (a != null) AgentImage(a.icon, Modifier.fillMaxSize(), a.name)
                        else Icon(Icons.Filled.Add, contentDescription = "Añadir", tint = Color.Gray)
                    }
                    Text(
                        a?.name ?: " ", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = Color.Gray, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Resumen de roles
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Role.values().forEach { role ->
                val n = team.count { it.role == role }
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.weight(1f).height(44.dp).clip(shape)
                        .background(if (n > 0) role.color.copy(alpha = 0.18f) else SURFACE)
                        .border(1.5.dp, if (n > 0) role.color else OUTLINE, shape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.alpha(if (n > 0) 1f else 0.4f)) { RoleIcon(role, 22.dp) }
                    Spacer(Modifier.width(6.dp))
                    Text("$n", fontWeight = FontWeight.Bold, color = if (n > 0) Color.White else Color.Gray)
                }
            }
        }

        if (team.isEmpty()) {
            Text(
                "Toca un círculo y elige los agentes de tu equipo. Te diré qué roles te faltan y qué agentes te convienen.",
                color = Color.Gray, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(SURFACE).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                advice.forEach { ad ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.padding(top = 6.dp).size(8.dp)
                                .background(if (ad.ok) GREEN else RED, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(ad.text, fontSize = 14.sp)
                    }
                }
            }

            if (recs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionTitle(if (team.size < 5) "Te recomendamos" else "Para equilibrar, cambia por")
                recs.forEach { rec ->
                    val info = roleInfo.value[rec.role]
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RoleIcon(rec.role, 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(rec.role.es, fontWeight = FontWeight.Bold, color = rec.role.color)
                                Text(rec.reason, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        info?.desc?.let {
                            Text(
                                it, fontSize = 12.sp, color = Color.Gray, maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        val names = team.map { it.name }.toSet()
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(AGENTS.filter { it.role == rec.role && it.name !in names }) { a ->
                                val shape = RoundedCornerShape(16.dp)
                                Column(
                                    Modifier.width(128.dp).clip(shape).background(SURFACE)
                                        .border(1.dp, rec.role.color.copy(alpha = 0.5f), shape)
                                        .clickable {
                                            val free = st.team.indexOfFirst { it == null }
                                            if (free >= 0) st.team[free] = a.name
                                        }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AgentImage(a.icon, Modifier.size(64.dp), a.name)
                                    Spacer(Modifier.height(6.dp))
                                    Text(a.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        a.abilities.take(4).forEach { b ->
                                            AsyncImage(
                                                model = b.icon, contentDescription = b.name,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pickSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { pickSlot = null },
            title = { Text("Elige un agente") },
            confirmButton = { TextButton(onClick = { pickSlot = null }) { Text("Cerrar") } },
            dismissButton = {
                if (st.team[slot] != null) {
                    TextButton(onClick = { st.team[slot] = null; pickSlot = null }) { Text("Quitar") }
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Role.values().forEach { role ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoleIcon(role, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(role.es, color = role.color, fontWeight = FontWeight.Bold)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AGENTS.filter { it.role == role }.forEach { a ->
                                val taken = a.name in st.team && st.team[slot] != a.name
                                Column(
                                    Modifier.width(60.dp).alpha(if (taken) 0.3f else 1f)
                                        .clickable(enabled = !taken) {
                                            st.team[slot] = a.name
                                            pickSlot = null
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AgentImage(
                                        a.icon,
                                        Modifier.size(52.dp)
                                            .border(
                                                if (st.team[slot] == a.name) 2.dp else 0.dp,
                                                RED, CircleShape
                                            ),
                                        a.name
                                    )
                                    Text(a.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

// ============================================================
//  PESTAÑA ARMAS
// ============================================================

@Composable
fun WeaponsTab(st: AppState, shown: Weapon?, spinning: Boolean, waiting: Boolean, onSpin: () -> Unit) {
    val all = weaponList.value
    val pool = st.weapons()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (all.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Conéctate a internet una vez\npara descargar las armas.",
                    textAlign = TextAlign.Center, color = Color.Gray
                )
            }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(WEAPON_CATS.filter { c -> all.any { it.cat == c } }) { c ->
                FilterChip(
                    selected = c !in st.catOff,
                    onClick = { if (c in st.catOff) st.catOff.remove(c) else st.catOff.add(c) },
                    label = { Text(catEs(c)) }
                )
            }
        }

        // Resultado
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp)).background(SURFACE)
                .border(1.dp, if (shown != null && !spinning) RED else OUTLINE, RoundedCornerShape(20.dp))
                .padding(16.dp).heightIn(min = 190.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (shown == null) {
                Text("?", fontSize = 64.sp, fontWeight = FontWeight.Black, color = OUTLINE)
                Text("Pulsa sortear para elegir un arma", color = Color.Gray, fontSize = 13.sp)
            } else {
                AsyncImage(
                    model = shown.icon, contentDescription = shown.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(shown.name.uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Black)
                val price = when (shown.cost) {
                    null -> ""
                    0 -> " · Gratis"
                    else -> " · ${shown.cost} créditos"
                }
                Text(catEs(shown.cat) + price, color = Color.Gray, fontSize = 13.sp)
            }
        }

        BigButton("SORTEAR ARMA", enabled = !spinning && pool.isNotEmpty(), loading = waiting, onClick = onSpin)

        SectionTitle("Armas en el sorteo")
        if (pool.isEmpty()) {
            Text(
                "Activa al menos una categoría arriba.", color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pool.forEach { w ->
                    val on = shown?.name == w.name
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.width(78.dp).height(52.dp).clip(shape)
                            .background(if (on) RED.copy(alpha = 0.2f) else SURFACE)
                            .border(if (on) 2.dp else 1.dp, if (on) RED else OUTLINE, shape)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = w.icon, contentDescription = w.name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  AJUSTES
// ============================================================

@Composable
fun SettingsDialog(st: AppState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = { st.resetSettings() }) { Text("Restablecer") } },
        title = { Text("Ajustes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Color del puntero")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    POINTER_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).background(c, CircleShape)
                                .border(if (i == st.pointerIdx) 3.dp else 0.dp, Color.Gray, CircleShape)
                                .clickable { st.pointerIdx = i }
                        )
                    }
                }
                Text("Duración de la ruleta: ${st.singleSec}s")
                Slider(
                    value = st.singleSec.toFloat(), onValueChange = { st.singleSec = it.roundToInt() },
                    valueRange = 2f..20f, steps = 17
                )
                Text("Intensidad del giro: ${st.intensity}")
                Slider(
                    value = st.intensity.toFloat(), onValueChange = { st.intensity = it.roundToInt() },
                    valueRange = 1f..10f, steps = 8
                )
                Text("Duración del sorteo de armas: ${st.weaponSec}s")
                Slider(
                    value = st.weaponSec.toFloat(), onValueChange = { st.weaponSec = it.roundToInt() },
                    valueRange = 2f..10f, steps = 7
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
