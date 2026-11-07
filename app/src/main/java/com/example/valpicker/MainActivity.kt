@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.valpicker

import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
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
data class MapInfo(val name: String, val icon: String?)
data class WR(val wr: Float, val n: Int)
data class MapStats(val data: Map<String, WR>, val live: Boolean)

val WEAPON_CATS = listOf("Sidearm", "SMG", "Shotgun", "Rifle", "Sniper", "Heavy")

fun catEs(c: String) = when (c) {
    "Sidearm" -> "Pistolas"
    "SMG" -> "Subfusiles"
    "Shotgun" -> "Escopetas"
    "Rifle" -> "Rifles"
    "Sniper" -> "Francotiradores"
    "Heavy" -> "Pesadas"
    else -> c
}

// Mapas del pool competitivo actual (parche 13.09)
val POOL = listOf("Abyss", "Bind", "Corrode", "Haven", "Lotus", "Summit", "Sunset")

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
val mapList = mutableStateOf(POOL.map { MapInfo(it, null) })

// Porcentajes de victoria por mapa (solo en memoria, se piden al abrir cada mapa)
val STATS = mutableStateMapOf<String, MapStats>()

val POINTER_COLORS = listOf(RED, Color.White, Color(0xFFFFD54F), Color(0xFF4FC3F7))

val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

// Respaldo: solo se usa si MetaBot no responde (MetaBot.gg, todos los rangos)
const val SNAPSHOT_DATE = "6 nov 2026"
val SNAPSHOT = mapOf(
    "Bind" to "Viper:53.4:805,Fade:53.0:2492,Clove:52.7:5756,Skye:52.6:3131,Phoenix:52.0:540,Cypher:51.6:1089,Raze:51.3:3257,Deadlock:51.2:699,Reyna:51.2:2306,Yoru:51.1:2219,Neon:51.0:1510,Chamber:50.9:4390,Waylay:50.8:1974,Brimstone:50.2:1155,Jett:50.2:2978,Vyse:65.8:76,Killjoy:59.4:69,Hush:53.1:350,Tejo:52.0:271,KAY/O:51.4:70,Veto:51.0:239,Sova:49.5:299,Gekko:49.1:108,Omen:48.3:118,Miks:47.4:346,Iso:47.3:264,Sage:45.5:222,Astra:43.3:60,Breach:40.5:42,Harbor:30.4:46",
    "Haven" to "Killjoy:53.7:3414,Phoenix:53.2:4385,Neon:52.8:4608,Clove:52.7:10990,Cypher:52.1:3732,Yoru:52.0:2290,Sova:51.9:11982,Reyna:50.3:5220,Jett:49.8:11602,Chamber:48.8:5552,Omen:48.8:2731,Waylay:48.4:1352,Iso:47.4:891,Hush:53.5:312,Fade:52.5:259,Deadlock:52.0:127,Raze:51.7:437,Tejo:51.2:123,Gekko:51.0:98,Brimstone:50.7:142,Viper:50.2:213,Vyse:49.5:101,Breach:48.8:256,Miks:48.4:560,Sage:48.1:432,Skye:48.1:374,Astra:47.3:129,Veto:47.2:214,KAY/O:44.8:154,Harbor:37.7:69",
    "Lotus" to "Cypher:53.8:530,Waylay:52.8:1394,Clove:52.0:6818,Vyse:51.4:1844,Fade:51.2:6738,Phoenix:51.1:1132,Killjoy:51.0:551,Neon:51.0:3619,Iso:50.6:981,Chamber:50.1:5753,Reyna:50.0:3111,Jett:49.4:5446,Raze:48.6:4016,Miks:47.4:673,Yoru:46.9:559,Omen:45.2:1891,Skye:42.5:510,Deadlock:54.5:224,Sage:51.6:312,Tejo:51.1:268,Viper:50.9:397,Astra:49.4:79,Veto:49.0:210,Hush:48.5:326,Breach:47.8:433,Sova:47.5:295,Gekko:46.6:206,Brimstone:45.9:109,KAY/O:44.9:98,Harbor:34.1:44",
    "Sunset" to "Cypher:53.1:1656,Hush:52.8:290,Sova:52.4:3802,Fade:52.3:600,Clove:52.2:4474,Yoru:52.1:493,Phoenix:52.1:1579,Neon:51.8:2701,Vyse:51.4:496,Reyna:50.8:1991,Iso:50.8:327,Waylay:49.4:629,Jett:49.3:2877,Omen:48.0:827,Raze:47.8:1047,Chamber:47.6:2611,Sage:46.4:405,Brimstone:55.9:68,Viper:55.2:29,Killjoy:52.3:44,Gekko:51.7:205,Deadlock:51.6:273,Harbor:50.0:24,Skye:49.4:178,Veto:48.7:189,Tejo:46.5:86,Miks:45.9:159,KAY/O:44.9:89,Breach:43.4:145,Astra:40.6:32"
)

fun snapshotFor(map: String): Map<String, WR>? =
    SNAPSHOT[map]?.split(",")?.mapNotNull { e ->
        val p = e.split(":")
        val wr = p.getOrNull(1)?.toFloatOrNull()
        val n = p.getOrNull(2)?.toIntOrNull()
        if (p.size == 3 && wr != null && n != null) p[0] to WR(wr, n) else null
    }?.toMap()

// ============================================================
//  API Y CACHÉ
// ============================================================

fun JSONObject.str(k: String): String? {
    if (isNull(k)) return null
    return optString(k).ifBlank { null }
}

suspend fun httpGet(url: String, ua: String? = null): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 15000
    if (ua != null) {
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    }
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

fun parseMaps(text: String): List<MapInfo> {
    val arr = JSONObject(text).getJSONArray("data")
    val out = mutableListOf<MapInfo>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val name = o.str("displayName") ?: continue
        if (o.str("tacticalDescription") == null) continue   // solo mapas de partida (con sitios)
        out.add(MapInfo(name, o.str("listViewIcon") ?: o.str("splash")))
    }
    return out.distinctBy { it.name }.sortedBy { it.name }
}

// ---- Porcentajes de victoria por mapa (lectura en vivo de MetaBot.gg) ----

val AGENT_LINK = Regex("""<a\s[^>]*?href="[^"]*?/valorant/agent/([^"/]+)/overview"[^>]*>""")
val HTML_TAG = Regex("""<[^>]+>""")
val MARKER = Regex("""§([^§]+)§""")
val STATS_ROW = Regex(
    """(?:Controller|Duelist|Initiator|Sentinel)\s+\d\.\d\d\s+([\d,]+)\s+(\d{1,3}\.\d)\s*%\s+\d{1,3}\.\d\s*%"""
)

fun parseMapStats(html: String): Map<String, WR> {
    val withMarkers = AGENT_LINK.replace(html) { " §${it.groupValues[1]}§ " }
    val text = HTML_TAG.replace(withMarkers, " ")
    val marks = MARKER.findAll(text).toList()
    val out = LinkedHashMap<String, WR>()
    for (m in STATS_ROW.findAll(text)) {
        val prev = marks.lastOrNull { it.range.first < m.range.first } ?: continue
        val raw = prev.groupValues[1]
        val name = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) {
            raw
        }
        if (name == "Unknown" || name in out) continue
        val n = m.groupValues[1].replace(",", "").toIntOrNull() ?: continue
        val wr = m.groupValues[2].toFloatOrNull() ?: continue
        out[name] = WR(wr, n)
    }
    return out
}

suspend fun fetchMapStats(map: String): Map<String, WR> =
    parseMapStats(httpGet("https://metabot.gg/en/valorant/map/$map/agents", BROWSER_UA))

// ---- Caché de listas (nombres, fotos, armas, mapas) ----

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
        val cat = o.getString("c")
        if (cat !in WEAPON_CATS) continue
        out.add(Weapon(o.getString("n"), cat, o.getString("i").ifBlank { null }, o.getInt("k").takeIf { it >= 0 }))
    }
    return out
}

fun mapsToJson(l: List<MapInfo>): String {
    val arr = JSONArray()
    l.forEach { arr.put(JSONObject().put("n", it.name).put("i", it.icon ?: "")) }
    return arr.toString()
}

fun mapsFromJson(s: String): List<MapInfo> {
    val arr = JSONArray(s)
    val out = mutableListOf<MapInfo>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(MapInfo(o.getString("n"), o.getString("i").ifBlank { null }))
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
    try {
        prefs.getString("maps_json", null)?.let { s ->
            val l = mapsFromJson(s)
            if (l.isNotEmpty()) mapList.value = l
        }
    } catch (e: Exception) {
    }
}

// ============================================================
//  ESTADO GUARDADO
// ============================================================

class AppState(prefs: SharedPreferences) {
    val roleOff = mutableStateListOf<Role>()
    val team = mutableStateListOf<String?>(null, null, null, null, null)
    var mapName by mutableStateOf("Haven")
    var singleSec by mutableStateOf(8)
    var weaponSec by mutableStateOf(4)
    var intensity by mutableStateOf(5)
    var pointerIdx by mutableStateOf(0)
    var credits by mutableStateOf(9000)

    init {
        prefs.getString("state3", null)?.let { decode(it) }
    }

    fun available(): List<Agent> = AGENTS.filter { it.role !in roleOff }
    fun weapons(): List<Weapon> = weaponList.value
    fun canBuy(w: Weapon): Boolean = (w.cost ?: Int.MAX_VALUE) <= credits
    fun weaponPool(): List<Weapon> = weaponList.value.filter { canBuy(it) }

    fun resetSettings() {
        singleSec = 8; weaponSec = 4; intensity = 5; pointerIdx = 0
    }

    fun encode(): String = listOf(
        roleOff.joinToString(",") { it.name },
        singleSec, weaponSec, intensity, pointerIdx,
        team.joinToString(",") { it ?: "-" },
        mapName,
        credits
    ).joinToString("\n")

    private fun decode(s: String) {
        try {
            val p = s.split("\n")
            if (p.size < 7) return
            roleOff.clear()
            roleOff.addAll(p[0].split(",").mapNotNull { r -> Role.values().firstOrNull { it.name == r } })
            singleSec = p[1].toInt(); weaponSec = p[2].toInt(); intensity = p[3].toInt()
            pointerIdx = p[4].toInt().coerceIn(0, POINTER_COLORS.size - 1)
            p[5].split(",").forEachIndexed { i, n -> if (i < 5) team[i] = if (n == "-") null else n }
            if (p[6].isNotBlank()) mapName = p[6]
            if (p.size >= 8) credits = p[7].toInt().coerceIn(0, 9000)
        } catch (e: Exception) {
        }
    }
}

// ============================================================
//  RECOMENDACIONES SEGÚN % DE VICTORIA EN EL MAPA
// ============================================================

data class Advice(val text: String, val ok: Boolean)
data class Pick(val agent: Agent, val wr: WR, val adj: Float, val score: Float, val bonus: Float)

val PRIORITY = listOf(Role.Controller, Role.Initiator, Role.Duelist, Role.Sentinel)

fun fmt(f: Float): String = "%.1f%%".format(Locale.US, f)

// Acerca hacia 50% los porcentajes con pocas partidas, para no fiarse de muestras pequeñas
fun adjWr(w: WR): Float = 50f + (w.wr - 50f) * w.n / (w.n + 400f)

fun roleBonus(role: Role, count: Int): Float = when {
    count == 0 -> when (role) {
        Role.Controller -> 3f
        Role.Initiator -> 2f
        Role.Duelist -> 2f
        Role.Sentinel -> 1.5f
    }
    count == 1 -> 0f
    count == 2 -> if (role == Role.Duelist) -1.5f else -2.5f
    else -> -5f
}

fun recommend(team: List<Agent>, stats: Map<String, WR>): List<Pick> {
    val names = team.map { it.name }.toSet()
    return AGENTS.filter { it.name !in names }.mapNotNull { a ->
        val w = stats[a.name] ?: return@mapNotNull null
        if (w.n < 30) return@mapNotNull null
        val adj = adjWr(w)
        val bonus = roleBonus(a.role, team.count { it.role == a.role })
        Pick(a, w, adj, adj + bonus, bonus)
    }.sortedByDescending { it.score }
}

fun reasonFor(p: Pick, team: List<Agent>, map: String): String {
    val base = "${fmt(p.wr.wr)} de victorias en $map con ${p.wr.n} partidas"
    val same = team.count { it.role == p.agent.role }
    return when {
        p.bonus > 0f -> "$base. Cubre el rol que le falta a tu equipo: ${p.agent.role.es.lowercase()}."
        p.bonus < 0f -> "$base. Ojo: ya tienes $same de este rol."
        else -> "$base."
    }
}

fun plural(s: String) = if (s.endsWith("r")) s + "es" else s + "s"

fun missingReason(r: Role) = when (r) {
    Role.Controller -> "sin humos te costará controlar las zonas"
    Role.Initiator -> "sin información ni apoyo para entrar a los sitios"
    Role.Duelist -> "nadie que abra espacio en los enfrentamientos"
    Role.Sentinel -> "nadie que asegure los flancos y el retake"
}

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
        else Advice("Vas bien: ya cubres todos los roles.", true)
    }
    return out
}

// ============================================================
//  ACTIVIDAD
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wheel", MODE_PRIVATE)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .respectCacheHeaders(false)
                .memoryCache { MemoryCache.Builder(this@MainActivity).maxSizePercent(0.3).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(filesDir.resolve("img_cache"))
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .build()
        )
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
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(), fontSize = 12.sp, color = Color.Gray, letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).weight(1f)
        )
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun MapThumb(url: String?, name: String) {
    val shape = RoundedCornerShape(8.dp)
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp, 40.dp).clip(shape)
        )
    } else {
        Box(Modifier.size(72.dp, 40.dp).clip(shape).background(OUTLINE))
    }
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
fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun LoadingScreen(status: String, done: Int, total: Int, canSkip: Boolean, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BG).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("VALORANT", fontSize = 14.sp, color = RED, letterSpacing = 8.sp, fontWeight = FontWeight.Bold)
        Text("PICKER", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Spacer(Modifier.height(36.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = RED, trackColor = OUTLINE
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = RED, trackColor = OUTLINE
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(status, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(40.dp)) {
            if (canSkip) TextButton(onClick = onSkip) { Text("Omitir") }
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
    var winner by remember { mutableStateOf<Agent?>(null) }
    var shownWeapon by remember { mutableStateOf<Weapon?>(null) }
    val rot = remember { Animatable(0f) }

    // Pantalla de carga
    var ready by remember {
        mutableStateOf(
            prefs.getString("agents_json", null) != null &&
                (prefs.getStringSet("preloaded", null)?.isNotEmpty() == true)
        )
    }
    var status by remember { mutableStateOf("Conectando con la API…") }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var canSkip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(6000)
        canSkip = true
    }

    // Guardado automático de ajustes
    LaunchedEffect(Unit) {
        snapshotFlow { st.encode() }.collect { prefs.edit().putString("state3", it).apply() }
    }

    // Al iniciar: actualiza agentes, armas y mapas, y precarga TODAS las imágenes
    LaunchedEffect(Unit) {
        status = "Actualizando agentes, armas y mapas…"
        coroutineScope {
            val a = async {
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
            }
            val w = async {
                try {
                    val l = parseWeapons(httpGet("https://valorant-api.com/v1/weapons"))
                    if (l.isNotEmpty()) {
                        weaponList.value = l
                        prefs.edit().putString("weapons_json", weaponsToJson(l)).apply()
                    }
                } catch (e: Exception) {
                }
            }
            val m = async {
                try {
                    val l = parseMaps(httpGet("https://valorant-api.com/v1/maps"))
                    if (l.isNotEmpty()) {
                        mapList.value = l
                        prefs.edit().putString("maps_json", mapsToJson(l)).apply()
                    }
                } catch (e: Exception) {
                }
            }
            a.await(); w.await(); m.await()
        }

        val urls = (AGENTS.mapNotNull { it.icon } +
            AGENTS.flatMap { a -> a.abilities.mapNotNull { it.icon } } +
            roleInfo.value.values.mapNotNull { it.icon } +
            weaponList.value.mapNotNull { it.icon } +
            mapList.value.mapNotNull { it.icon }).distinct()
        // Solo se descargan las imágenes que aún no están guardadas en el dispositivo
        val saved = prefs.getStringSet("preloaded", emptySet()) ?: emptySet()
        val missing = urls.filter { it !in saved }
        total = missing.size
        done = 0
        status = "Descargando imágenes…"
        val loader = ctx.imageLoader
        val ok = mutableListOf<String>()
        withTimeoutOrNull(45000) {
            val sem = Semaphore(12)
            coroutineScope {
                missing.map { u ->
                    async {
                        sem.withPermit {
                            try {
                                val res = loader.execute(ImageRequest.Builder(ctx).data(u).build())
                                if (res is SuccessResult) ok.add(u)
                            } catch (e: Exception) {
                            }
                            done++
                        }
                    }
                }.awaitAll()
            }
        }
        if (ok.isNotEmpty()) prefs.edit().putStringSet("preloaded", HashSet(saved + ok)).apply()
        ready = true
    }

    val avail = st.available()

    fun spinAgent() {
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
            spinning = false
        }
    }

    fun spinWeapon() {
        if (spinning) return
        val list = st.weaponPool()
        if (list.isEmpty()) return
        scope.launch {
            spinning = true
            val n = list.size
            val win = Random.nextInt(n)
            val steps = ((st.weaponSec * 1000) / 160).coerceAtLeast(8)
            for (k in 0 until steps) {
                val idx = (((win - (steps - 1 - k)) % n) + n) % n
                shownWeapon = list[idx]
                val f = k.toFloat() / (steps - 1)
                delay((40 + 360 * f * f).toLong())
            }
            spinning = false
        }
    }

    if (!ready) {
        LoadingScreen(
            status = if (total > 0) "Descargando imágenes $done/$total\nSolo la primera vez: después quedan guardadas" else status,
            done = done, total = total, canSkip = canSkip, onSkip = { ready = true }
        )
        return
    }

    Scaffold(
        containerColor = BG,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    when (tab) {
                        0 -> "AGENTES"
                        1 -> "TU EQUIPO"
                        else -> "ARMAS"
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
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
                    val sel = tab == i
                    val scale by animateFloatAsState(
                        if (sel) 1.15f else 1f,
                        spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "navScale"
                    )
                    val tint by animateColorAsState(if (sel) RED else Color.Gray, tween(220), label = "navTint")
                    NavigationBarItem(
                        selected = sel,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                icon, contentDescription = desc, tint = tint,
                                modifier = Modifier.size(28.dp).scale(scale)
                            )
                        },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = RED.copy(alpha = 0.16f))
                    )
                }
            }
        }
    ) { pad ->
        Crossfade(
            targetState = tab,
            animationSpec = tween(220),
            modifier = Modifier.padding(pad).fillMaxSize(),
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> AgentsTab(st, rot, avail, spinning) { spinAgent() }
                1 -> TeamTab(st)
                else -> WeaponsTab(st, shownWeapon, spinning) { spinWeapon() }
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
    onSpin: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).alpha(if (spinning) 0.5f else 1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Role.values().forEach { role ->
                val on = role !in st.roleOff
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.weight(1f).height(52.dp).clip(shape)
                        .background(if (on) role.color.copy(alpha = 0.18f) else SURFACE)
                        .border(2.dp, if (on) role.color else OUTLINE, shape)
                        .clickable(enabled = !spinning) {
                            if (on) st.roleOff.add(role) else st.roleOff.remove(role)
                        },
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
        BigButton("GIRAR", enabled = !spinning && avail.isNotEmpty(), onClick = onSpin)
    }
}

// ============================================================
//  PESTAÑA RECOMENDACIONES (EQUIPO + MAPA + % DE VICTORIA)
// ============================================================

@Composable
fun PickCard(p: Pick, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(128.dp).clip(shape).background(SURFACE)
            .border(1.dp, p.agent.role.color.copy(alpha = 0.5f), shape)
            .clickable { onAdd() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AgentImage(p.agent.icon, Modifier.size(60.dp), p.agent.name)
        Spacer(Modifier.height(6.dp))
        Text(p.agent.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleIcon(p.agent.role, 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(p.agent.role.es, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            fmt(p.wr.wr), fontSize = 18.sp, fontWeight = FontWeight.Black,
            color = if (p.wr.wr >= 52f) GREEN else if (p.wr.wr >= 50f) Color.White else RED
        )
        Text("${p.wr.n} partidas", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun TeamTab(st: AppState) {
    var pickSlot by remember { mutableStateOf<Int?>(null) }
    var reload by remember { mutableStateOf(0) }
    var loadingFor by remember { mutableStateOf<String?>(null) }
    val map = st.mapName
    val stats = STATS[map]
    val team = st.team.mapNotNull { n -> AGENTS.firstOrNull { it.name == n } }

    // Pide los porcentajes de victoria del mapa elegido (en vivo)
    LaunchedEffect(map, reload) {
        if (STATS[map]?.live == true) return@LaunchedEffect
        loadingFor = map
        try {
            val live = try {
                fetchMapStats(map)
            } catch (e: Exception) {
                emptyMap()
            }
            if (live.size >= 8) {
                STATS[map] = MapStats(live, true)
            } else if (STATS[map] == null) {
                snapshotFor(map)?.let { STATS[map] = MapStats(it, false) }
            }
        } finally {
            if (loadingFor == map) loadingFor = null
        }
    }

    fun addToTeam(a: Agent) {
        val free = st.team.indexOfFirst { it == null }
        if (free >= 0) st.team[free] = a.name
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionTitle("Mapa")
        var mapMenu by remember { mutableStateOf(false) }
        val current = mapList.value.firstOrNull { it.name == st.mapName }
        Box(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SURFACE)
                    .border(1.5.dp, if (mapMenu) RED else OUTLINE, RoundedCornerShape(14.dp))
                    .clickable { mapMenu = true }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapThumb(current?.icon, st.mapName)
                Spacer(Modifier.width(12.dp))
                Text(st.mapName, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (st.mapName in POOL) {
                    Text("En rotación", fontSize = 11.sp, color = GREEN)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar mapa", tint = Color.Gray)
            }
            DropdownMenu(
                expanded = mapMenu,
                onDismissRequest = { mapMenu = false },
                modifier = Modifier.width(320.dp).heightIn(max = 380.dp)
            ) {
                mapList.value.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MapThumb(m.icon, m.name)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    m.name, modifier = Modifier.weight(1f),
                                    fontWeight = if (m.name == st.mapName) FontWeight.Bold else FontWeight.Normal
                                )
                                if (m.name in POOL) Text("En rotación", fontSize = 11.sp, color = GREEN)
                            }
                        },
                        onClick = { st.mapName = m.name; mapMenu = false }
                    )
                }
            }
        }

        SectionTitle("Tu equipo") {
            IconButton(
                onClick = { for (i in 0 until 5) st.team[i] = null },
                enabled = team.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.RestartAlt, contentDescription = "Reiniciar equipo",
                    tint = if (team.isNotEmpty()) Color.White else OUTLINE
                )
            }
        }

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

        // Estado de los datos
        when {
            stats == null && loadingFor == map -> {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = RED)
                    Spacer(Modifier.width(10.dp))
                    Text("Cargando porcentajes de victoria de $map…", color = Color.Gray, fontSize = 13.sp)
                }
            }
            stats == null -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        "No se pudieron cargar los porcentajes de victoria de $map. Revisa tu conexión.",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    TextButton(onClick = { reload++ }) { Text("Reintentar") }
                }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (stats.live) "En vivo · MetaBot.gg · todos los rangos"
                        else "Sin conexión con MetaBot: datos de respaldo del $SNAPSHOT_DATE",
                        fontSize = 11.sp, color = if (stats.live) GREEN else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { reload++ }) { Text("Actualizar", fontSize = 12.sp) }
                }

                val picks = recommend(team, stats.data)
                if (team.isEmpty()) {
                    SectionTitle("Mejores agentes en $map")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(picks.sortedByDescending { it.adj }.take(8)) { p -> PickCard(p) { addToTeam(p.agent) } }
                    }
                    Text(
                        "Toca un círculo y elige tus agentes: te diré cuál conviene más en $map según los porcentajes de victoria.",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else if (team.size < 5) {
                    val best = picks.firstOrNull()
                    if (best != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(20.dp)).background(SURFACE)
                                .border(1.5.dp, RED, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Text("MEJOR OPCIÓN EN ${map.uppercase()}", fontSize = 11.sp, color = RED, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AgentImage(best.agent.icon, Modifier.size(84.dp), best.agent.name)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(best.agent.name.uppercase(), fontSize = 26.sp, fontWeight = FontWeight.Black, color = best.agent.role.color)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RoleIcon(best.agent.role, 18.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(best.agent.role.es, color = Color.Gray, fontSize = 13.sp)
                                    }
                                    Text(
                                        fmt(best.wr.wr) + " victorias", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        color = if (best.wr.wr >= 52f) GREEN else if (best.wr.wr >= 50f) Color.White else RED
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(reasonFor(best, team, map), fontSize = 13.sp, color = Color.Gray)
                            if (best.agent.abilities.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    best.agent.abilities.take(4).forEach { b ->
                                        AsyncImage(model = b.icon, contentDescription = b.name, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { addToTeam(best.agent) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Añadir al equipo") }
                        }
                    }
                    SectionTitle("Otras buenas opciones")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(picks.drop(1).take(7)) { p -> PickCard(p) { addToTeam(p.agent) } }
                    }
                } else {
                    // Equipo completo: revisión y cambios sugeridos
                    val members = team.mapNotNull { a -> stats.data[a.name]?.let { a to it } }
                    SectionTitle("Tu equipo en $map")
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)).background(SURFACE).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        team.forEach { a ->
                            val w = stats.data[a.name]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AgentImage(a.icon, Modifier.size(40.dp), a.name)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, fontWeight = FontWeight.Bold)
                                    Text(a.role.es, fontSize = 11.sp, color = Color.Gray)
                                }
                                if (w != null) {
                                    Text(
                                        fmt(w.wr), fontWeight = FontWeight.Black,
                                        color = if (w.wr >= 52f) GREEN else if (w.wr >= 50f) Color.White else RED
                                    )
                                } else {
                                    Text("sin datos", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                        if (members.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Promedio del equipo: " + fmt(members.map { it.second.wr }.average().toFloat()),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    SectionTitle("Cambios que te convienen")
                    val swaps = members.mapNotNull { (a, w) ->
                        val better = picks.filter { it.agent.role == a.role && it.adj >= adjWr(w) + 1.5f }
                            .maxByOrNull { it.adj }
                        if (better != null) Triple(a, w, better) else null
                    }
                    if (swaps.isEmpty()) {
                        Text(
                            "No hay cambios claros: tu equipo ya usa buenas opciones para $map.",
                            color = Color.Gray, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            swaps.forEach { (a, w, b) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(SURFACE).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AgentImage(a.icon, Modifier.size(36.dp), a.name)
                                    Text(
                                        "  →  ", color = Color.Gray, fontWeight = FontWeight.Bold
                                    )
                                    AgentImage(b.agent.icon, Modifier.size(36.dp), b.agent.name)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Cambia ${a.name} (${fmt(w.wr)}) por ${b.agent.name} (${fmt(b.wr.wr)})",
                                        fontSize = 13.sp, modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                val advice = teamAdvice(team)
                if (advice.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Composición")
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
                                val w = stats?.data?.get(a.name)
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
                                    if (w != null) {
                                        Text(
                                            fmt(w.wr), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (w.wr >= 52f) GREEN else if (w.wr >= 50f) Color.White else RED
                                        )
                                    }
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
fun WeaponsTab(st: AppState, shown: Weapon?, spinning: Boolean, onSpin: () -> Unit) {
    val all = st.weapons()
    val pool = st.weaponPool()
    var text by remember { mutableStateOf(st.credits.toString()) }
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

        SectionTitle("Tus créditos")
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(5)
                val num = d.toIntOrNull() ?: 0
                st.credits = num.coerceAtMost(9000)
                text = if (num > 9000) "9000" else d
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("créditos", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(800, 1600, 2900, 3900, 9000).forEach { c ->
                FilterChip(
                    selected = st.credits == c,
                    onClick = { st.credits = c; text = c.toString() },
                    label = { Text("$c") }
                )
            }
        }

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

        BigButton("SORTEAR ARMA", enabled = !spinning && pool.isNotEmpty(), onClick = onSpin)

        Text(
            "${pool.size} de ${all.size} armas entran al sorteo con ${st.credits} créditos",
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        SectionTitle("Armas en el sorteo")
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            all.forEach { w ->
                val on = shown?.name == w.name
                val buyable = st.canBuy(w)
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.width(78.dp).height(52.dp).alpha(if (buyable) 1f else 0.2f).clip(shape)
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
            }
        }
    )
}
