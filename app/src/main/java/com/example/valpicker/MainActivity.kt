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
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ============================================================
//  DATOS
// ============================================================

val BG = Color(0xFF121214)
val SURFACE = Color(0xFF1C1C1F)
val OUTLINE = Color(0xFF2E2E32)
val RED = Color(0xFFF2432B)
val INK = Color(0xFF0B0B0C)
val TEXT = Color(0xFFEDEDED)
val DIM = Color(0xFF8E8E93)
val GREEN = Color(0xFF66D18F)

enum class Role(val label: String, val es: String, val color: Color) {
    Controller("Controller", "Controlador", Color(0xFF8FB8D6)),
    Sentinel("Sentinel", "Centinela", Color(0xFF9CC9A1)),
    Initiator("Initiator", "Iniciador", Color(0xFFE0B27A)),
    Duelist("Duelist", "Duelista", Color(0xFFFF4B21))
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

// Clave de HenrikDev (perfil). Puedes cambiarla desde Ajustes.
const val DEFAULT_API_KEY = "HDEV-8458d668-9612-44d4-b8c9-9e8de8ff6914"

val POINTER_COLORS = listOf(RED, Color.White, Color(0xFFFFD54F), Color(0xFF8FB8D6))

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

    var riotId by mutableStateOf("Sova o Nada#COSTA")
    var apiKey by mutableStateOf(DEFAULT_API_KEY)

    init {
        prefs.getString("state3", null)?.let { decode(it) }
        riotId = prefs.getString("riot", riotId) ?: riotId
        apiKey = prefs.getString("hkey", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_API_KEY
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
//  UTILIDADES
// ============================================================

val PRIORITY = listOf(Role.Controller, Role.Initiator, Role.Duelist, Role.Sentinel)

fun fmt(f: Float): String = "%.1f%%".format(Locale.US, f)

// Acerca hacia 50% los porcentajes con pocas partidas, para no fiarse de muestras pequeñas
fun adjWr(w: WR): Float = 50f + (w.wr - 50f) * w.n / (w.n + 400f)

fun wrColor(w: Float): Color = if (w >= 52f) GREEN else if (w >= 50f) TEXT else RED

fun topByRole(role: Role, stats: Map<String, WR>, k: Int = 3): List<Pair<Agent, WR>> =
    AGENTS.filter { it.role == role }
        .mapNotNull { a -> stats[a.name]?.takeIf { it.n >= 30 }?.let { a to it } }
        .sortedByDescending { adjWr(it.second) }
        .take(k)

fun catColor(c: String): Color = when (c) {
    "Sidearm" -> Color(0xFF9AA0A6)
    "SMG" -> Color(0xFF6CB4EE)
    "Shotgun" -> Color(0xFFF2B35E)
    "Rifle" -> Color(0xFFF2432B)
    "Sniper" -> Color(0xFF7BC98F)
    else -> Color(0xFFB48CF2)
}

fun rankEs(n: String): String = n
    .replace("Iron", "Hierro", true).replace("Bronze", "Bronce", true)
    .replace("Silver", "Plata", true).replace("Gold", "Oro", true)
    .replace("Platinum", "Platino", true).replace("Diamond", "Diamante", true)
    .replace("Ascendant", "Ascendente", true).replace("Immortal", "Inmortal", true)
    .replace("Radiant", "Radiante", true).replace("Unranked", "Sin rango", true)
    .replace("Unrated", "Sin rango", true)

fun modeEs(id: String): String = when (id.lowercase()) {
    "competitive" -> "Competitivo"
    "unrated" -> "Normal"
    "swiftplay" -> "Partida rápida"
    "spikerush" -> "Spike Rush"
    "deathmatch" -> "Combate a muerte"
    "premier" -> "Premier"
    "custom" -> "Personalizada"
    else -> id.replaceFirstChar { it.uppercase() }
}

fun Modifier.card(r: Dp = 20.dp, border: Color = OUTLINE): Modifier {
    val shape = RoundedCornerShape(r)
    return this.clip(shape).background(SURFACE).border(1.dp, border, shape)
}

// ============================================================
//  PERFIL (API de HenrikDev)
// ============================================================

class ApiError(msg: String) : Exception(msg)

data class MatchRow(
    val map: String, val mode: String, val agent: String,
    val k: Int, val d: Int, val a: Int, val won: Boolean?, val score: String, val acs: Int
)

data class Profile(
    val name: String, val tag: String, val level: Int, val region: String, val card: String?,
    val tierName: String?, val tierIcon: String?, val rr: Int?, val change: Int?,
    val peakName: String?, val peakIcon: String?,
    val matches: List<MatchRow>, val kd: Float?, val winPct: Float?, val hs: Float?, val adr: Int?
)

val profileState = mutableStateOf<Profile?>(null)
var profileLoadedFor = ""

suspend fun hGet(url: String, key: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 12000
    conn.readTimeout = 25000
    conn.setRequestProperty("Authorization", key.trim())
    conn.setRequestProperty("Accept", "application/json")
    try {
        val code = conn.responseCode
        if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            throw ApiError(
                when (code) {
                    401, 403 -> "API key inválida"
                    404 -> "Cuenta no encontrada"
                    429 -> "Demasiadas solicitudes. Espera un momento"
                    else -> "Error $code"
                }
            )
        }
    } finally {
        conn.disconnect()
    }
}

suspend fun fetchTierIcons(): Map<Int, String> {
    val arr = JSONObject(httpGet("https://valorant-api.com/v1/competitivetiers")).getJSONArray("data")
    val tiers = arr.getJSONObject(arr.length() - 1).getJSONArray("tiers")
    val m = mutableMapOf<Int, String>()
    for (i in 0 until tiers.length()) {
        val t = tiers.getJSONObject(i)
        val ic = t.str("largeIcon")
        if (ic != null) m[t.optInt("tier")] = ic
    }
    return m
}

suspend fun fetchProfile(riotId: String, key: String): Profile {
    val parts = riotId.split("#", limit = 2)
    if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) throw ApiError("Riot ID inválido (nombre#TAG)")
    fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")
    val n = enc(parts[0])
    val t = enc(parts[1])
    val base = "https://api.henrikdev.xyz/valorant"

    val acc = JSONObject(hGet("$base/v2/account/$n/$t", key)).getJSONObject("data")
    val region = acc.str("region") ?: "na"
    val puuid = acc.optString("puuid")
    val tiers = try {
        fetchTierIcons()
    } catch (e: Exception) {
        emptyMap()
    }

    var tierName: String? = null
    var tierIcon: String? = null
    var rr: Int? = null
    var change: Int? = null
    var peakName: String? = null
    var peakIcon: String? = null
    try {
        val mmr = JSONObject(hGet("$base/v3/mmr/$region/pc/$n/$t", key)).getJSONObject("data")
        val cur = mmr.getJSONObject("current")
        val ct = cur.getJSONObject("tier")
        tierName = ct.str("name")
        tierIcon = tiers[ct.optInt("id")]
        rr = cur.optInt("rr")
        change = cur.optInt("last_change")
        val peak = mmr.optJSONObject("peak")
        val pt = peak?.optJSONObject("tier")
        if (pt != null) {
            peakName = pt.str("name")
            peakIcon = tiers[pt.optInt("id")]
        }
    } catch (e: Exception) {
    }

    val rows = mutableListOf<MatchRow>()
    var kills = 0; var deaths = 0; var head = 0; var shots = 0; var dmg = 0; var rounds = 0
    var wins = 0; var counted = 0
    try {
        val arr = JSONObject(hGet("$base/v4/matches/$region/pc/$n/$t?size=10", key)).getJSONArray("data")
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val md = m.getJSONObject("metadata")
            val players = m.getJSONArray("players")
            var me: JSONObject? = null
            for (j in 0 until players.length()) {
                val pl = players.getJSONObject(j)
                if (pl.optString("puuid") == puuid) {
                    me = pl
                    break
                }
            }
            if (me == null) continue
            val ps = me.getJSONObject("stats")
            val teamId = me.optString("team_id")
            var won: Boolean? = null
            var rw = 0
            var rl = 0
            val teams = m.optJSONArray("teams")
            if (teams != null) {
                for (x in 0 until teams.length()) {
                    val tm = teams.getJSONObject(x)
                    if (tm.optString("team_id") == teamId) {
                        won = tm.optBoolean("won")
                        val r = tm.getJSONObject("rounds")
                        rw = r.optInt("won")
                        rl = r.optInt("lost")
                    }
                }
            }
            val tr = (rw + rl).coerceAtLeast(1)
            val k = ps.optInt("kills")
            val d = ps.optInt("deaths")
            val a = ps.optInt("assists")
            val hs = ps.optInt("headshots")
            val bs = ps.optInt("bodyshots")
            val ls = ps.optInt("legshots")
            val dealt = ps.optJSONObject("damage")?.optInt("dealt") ?: 0
            val queueId = md.optJSONObject("queue")?.optString("id") ?: ""
            rows.add(
                MatchRow(
                    map = md.optJSONObject("map")?.optString("name") ?: "",
                    mode = modeEs(queueId),
                    agent = me.optJSONObject("agent")?.optString("name") ?: "",
                    k = k, d = d, a = a, won = won,
                    score = "$rw–$rl",
                    acs = ps.optInt("score") / tr
                )
            )
            kills += k; deaths += d; head += hs; shots += hs + bs + ls; dmg += dealt; rounds += tr
            counted++
            if (won == true) wins++
        }
    } catch (e: Exception) {
    }

    return Profile(
        name = acc.str("name") ?: parts[0], tag = acc.str("tag") ?: parts[1],
        level = acc.optInt("account_level"), region = region, card = acc.str("card"),
        tierName = tierName, tierIcon = tierIcon, rr = rr, change = change,
        peakName = peakName, peakIcon = peakIcon, matches = rows,
        kd = if (counted > 0) kills.toFloat() / deaths.coerceAtLeast(1) else null,
        winPct = if (counted > 0) wins * 100f / counted else null,
        hs = if (shots > 0) head * 100f / shots else null,
        adr = if (counted > 0 && rounds > 0) dmg / rounds else null
    )
}

// ============================================================
//  ACTIVIDAD
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val prefs = getSharedPreferences("wheel", MODE_PRIVATE)
        loadCaches(prefs)
        val st = AppState(prefs)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = RED, onPrimary = INK,
                    background = BG, onBackground = TEXT,
                    surface = SURFACE, onSurface = TEXT
                )
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = FontFamily.Monospace))
                ) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0xFF2B2119), 0.5f to BG, 1f to BG)
                        )
                    ) {
                        App(st, prefs)
                    }
                }
            }
        }
    }
}

// ============================================================
//  COMPONENTES BÁSICOS
// ============================================================

@Composable
fun Label(text: String, color: Color = DIM) {
    Text(text.uppercase(), fontSize = 10.sp, color = color, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun AgentImage(url: String?, modifier: Modifier, fallback: String = "") {
    if (url == null) {
        Box(modifier.clip(CircleShape).background(OUTLINE), contentAlignment = Alignment.Center) {
            Text(fallback.take(1), fontWeight = FontWeight.Bold, color = DIM)
        }
    } else {
        AsyncImage(
            model = url, contentDescription = fallback,
            modifier = modifier.clip(CircleShape).background(OUTLINE)
        )
    }
}

@Composable
fun RoleIcon(role: Role, size: Dp) {
    val url = roleInfo.value[role]?.icon
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = role.es,
            colorFilter = ColorFilter.tint(role.color),
            modifier = Modifier.size(size)
        )
    } else {
        Box(Modifier.size(size * 0.6f).background(role.color, CircleShape))
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

data class WheelItem(val label: String, val tint: Color)

@Composable
fun Wheel(items: List<WheelItem>, rotation: () -> Float, pointer: Color, modifier: Modifier = Modifier) {
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
            color = 0xFFEDEDED.toInt()
        }
    }
    Canvas(modifier) {
        val n = items.size
        if (n == 0) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.minDimension / 2f
        val r = half * 0.93f
        val sweep = 360f / n
        paint.textSize = r * (if (n > 26) 0.062f else 0.076f)
        rotate(rotation(), pivot = Offset(cx, cy)) {
            items.forEachIndexed { i, it ->
                val start = -90f + i * sweep
                drawArc(
                    color = if (i % 2 == 0) Color(0xFF1F1F23) else Color(0xFF28282D),
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r)
                )
                drawArc(
                    color = it.tint.copy(alpha = 0.2f),
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
                    nc.drawText(it.label, cx + r - r * 0.07f, cy + paint.textSize / 3f, paint)
                    nc.restore()
                }
            }
            // anillo de puntos
            val dots = 90
            for (i in 0 until dots) {
                val ang = Math.toRadians(i * 360.0 / dots)
                val rr = half * 0.975f
                drawCircle(
                    color = if (i % 15 == 0) RED else Color(0xFF55555A),
                    radius = if (i % 15 == 0) 2.6f else 1.6f,
                    center = Offset(cx + (rr * Math.cos(ang)).toFloat(), cy + (rr * Math.sin(ang)).toFloat())
                )
            }
        }
        drawCircle(BG, radius = r * 0.13f, center = Offset(cx, cy))
        drawCircle(RED, radius = r * 0.035f, center = Offset(cx, cy))
        val tri = Path().apply {
            moveTo(cx - r * 0.06f, cy - half - r * 0.0f)
            lineTo(cx + r * 0.06f, cy - half - r * 0.0f)
            lineTo(cx, cy - half + r * 0.13f)
            close()
        }
        drawPath(tri, pointer)
    }
}

@Composable
fun FitWheel(items: List<WheelItem>, rotation: () -> Float, pointer: Color, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val s = if (maxWidth < maxHeight) maxWidth else maxHeight
        Wheel(items, rotation, pointer, Modifier.size(s))
    }
}

suspend fun spinWheel(rot: Animatable<Float, AnimationVector1D>, n: Int, seconds: Int, intensity: Int): Int {
    val idx = Random.nextInt(n)
    val sweep = 360f / n
    val jitter = 0.1f + Random.nextFloat() * 0.8f
    val residue = (((-(idx + jitter) * sweep) % 360f) + 360f) % 360f
    val cur = rot.value
    val curMod = ((cur % 360f) + 360f) % 360f
    val delta = ((residue - curMod) + 360f) % 360f
    val target = cur + 360f * (3 + intensity) + delta
    rot.animateTo(target, tween(seconds * 1000, easing = CubicBezierEasing(0.1f, 0.75f, 0.15f, 1f)))
    return idx
}

@Composable
fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(0.75f).height(54.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}

@Composable
fun LoadingScreen(status: String, done: Int, total: Int, canSkip: Boolean, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(RED, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("VALORANT", fontSize = 13.sp, color = DIM, letterSpacing = 6.sp)
        }
        Text("PICKER", fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Spacer(Modifier.height(36.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = RED, trackColor = OUTLINE
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = RED, trackColor = OUTLINE
            )
        }
        Spacer(Modifier.height(14.dp))
        Label(status)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(40.dp)) {
            if (canSkip) TextButton(onClick = onSkip) { Text("Omitir", color = DIM) }
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
    var weaponWinner by remember { mutableStateOf<Weapon?>(null) }
    val rot = remember { Animatable(0f) }
    val rotW = remember { Animatable(0f) }

    var ready by remember {
        mutableStateOf(
            prefs.getString("agents_json", null) != null &&
                (prefs.getStringSet("preloaded", null)?.isNotEmpty() == true)
        )
    }
    var status by remember { mutableStateOf("Conectando") }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var canSkip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(6000)
        canSkip = true
    }

    LaunchedEffect(Unit) {
        snapshotFlow { st.encode() + "\u0001" + st.riotId + "\u0001" + st.apiKey }.collect {
            prefs.edit()
                .putString("state3", st.encode())
                .putString("riot", st.riotId)
                .putString("hkey", st.apiKey)
                .apply()
        }
    }

    // Al iniciar: actualiza agentes, armas y mapas, y descarga las imágenes que falten
    LaunchedEffect(Unit) {
        status = "Actualizando datos"
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
        val saved = prefs.getStringSet("preloaded", emptySet()) ?: emptySet()
        val missing = urls.filter { it !in saved }
        total = missing.size
        done = 0
        status = "Descargando imágenes"
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

    fun spinAgent() {
        if (spinning) return
        val list = st.available()
        if (list.isEmpty()) return
        scope.launch {
            spinning = true
            val idx = spinWheel(rot, list.size, st.singleSec, st.intensity)
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
            val idx = spinWheel(rotW, list.size, st.weaponSec, st.intensity)
            weaponWinner = list[idx]
            spinning = false
        }
    }

    if (!ready) {
        LoadingScreen(
            status = if (total > 0) "Descargando imágenes $done/$total" else status,
            done = done, total = total, canSkip = canSkip, onSkip = { ready = true }
        )
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.fillMaxWidth().background(RED).padding(horizontal = 8.dp, vertical = 10.dp)) {
                Text(
                    when (tab) {
                        0 -> "AGENTES"
                        1 -> "EQUIPO"
                        2 -> "ARMAS"
                        else -> "PERFIL"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = INK,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = INK)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF151517)) {
                val items = listOf(
                    Icons.Filled.Casino to "Agentes",
                    Icons.Filled.Lightbulb to "Equipo",
                    Icons.Filled.GpsFixed to "Armas",
                    Icons.Filled.AccountCircle to "Perfil"
                )
                items.forEachIndexed { i, (icon, desc) ->
                    val sel = tab == i
                    val scale by animateFloatAsState(
                        if (sel) 1.15f else 1f,
                        spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "navScale"
                    )
                    val tint by animateColorAsState(if (sel) RED else DIM, tween(220), label = "navTint")
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
                0 -> AgentsTab(st, rot, spinning) { spinAgent() }
                1 -> TeamTab(st)
                2 -> WeaponsTab(st, rotW, spinning) { spinWeapon() }
                else -> ProfileTab(st)
            }
        }
    }

    winner?.let { w ->
        AlertDialog(
            onDismissRequest = { winner = null },
            containerColor = SURFACE,
            confirmButton = {
                TextButton(onClick = { winner = null; spinAgent() }) { Text("Girar otra vez", color = RED) }
            },
            dismissButton = { TextButton(onClick = { winner = null }) { Text("Cerrar", color = DIM) } },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AgentImage(w.icon, Modifier.size(150.dp), w.name)
                    Spacer(Modifier.height(10.dp))
                    Text(w.name.uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = w.role.color)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoleIcon(w.role, 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(w.role.es, color = DIM)
                    }
                }
            }
        )
    }

    weaponWinner?.let { w ->
        AlertDialog(
            onDismissRequest = { weaponWinner = null },
            containerColor = SURFACE,
            confirmButton = {
                TextButton(onClick = { weaponWinner = null; spinWeapon() }) { Text("Sortear otra vez", color = RED) }
            },
            dismissButton = { TextButton(onClick = { weaponWinner = null }) { Text("Cerrar", color = DIM) } },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = w.icon, contentDescription = w.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(w.name.uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Black)
                    val price = when (w.cost) {
                        null -> ""
                        0 -> " · Gratis"
                        else -> " · ${w.cost}"
                    }
                    Text(catEs(w.cat) + price, color = DIM)
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
fun AgentsTab(st: AppState, rot: Animatable<Float, AnimationVector1D>, spinning: Boolean, onSpin: () -> Unit) {
    val avail = st.available()
    val items = avail.map { WheelItem(it.name, it.role.color) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().alpha(if (spinning) 0.5f else 1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Role.values().forEach { role ->
                val on = role !in st.roleOff
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.size(width = 60.dp, height = 52.dp).clip(shape)
                        .background(if (on) role.color.copy(alpha = 0.16f) else SURFACE)
                        .border(1.5.dp, if (on) role.color else OUTLINE, shape)
                        .clickable(enabled = !spinning) {
                            if (on) st.roleOff.add(role) else st.roleOff.remove(role)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.alpha(if (on) 1f else 0.35f)) { RoleIcon(role, 28.dp) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("—", color = DIM, fontSize = 32.sp)
            }
        } else {
            FitWheel(items, { rot.value }, POINTER_COLORS[st.pointerIdx], Modifier.weight(1f).fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        BigButton("GIRAR", enabled = !spinning && items.isNotEmpty(), onClick = onSpin)
    }
}

// ============================================================
//  PESTAÑA EQUIPO: MEJORES POR ROL SEGÚN EL MAPA
// ============================================================

@Composable
fun TeamTab(st: AppState) {
    var reload by remember { mutableStateOf(0) }
    var loadingFor by remember { mutableStateOf<String?>(null) }
    var mapMenu by remember { mutableStateOf(false) }
    val map = st.mapName
    val stats = STATS[map]

    LaunchedEffect(map, reload) {
        if (STATS[map]?.live == true && reload == 0) return@LaunchedEffect
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val current = mapList.value.firstOrNull { it.name == st.mapName }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().card(14.dp, if (mapMenu) RED else OUTLINE)
                        .clickable { mapMenu = true }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapThumb(current?.icon, st.mapName)
                    Spacer(Modifier.width(12.dp))
                    Text(st.mapName.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    if (st.mapName in POOL) Text("●", color = GREEN, fontSize = 10.sp)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar mapa", tint = DIM)
                }
                DropdownMenu(
                    expanded = mapMenu,
                    onDismissRequest = { mapMenu = false },
                    modifier = Modifier.width(300.dp).heightIn(max = 380.dp)
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
                                    if (m.name in POOL) Text("●", color = GREEN, fontSize = 10.sp)
                                }
                            },
                            onClick = { st.mapName = m.name; mapMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            if (stats != null) {
                Box(Modifier.size(8.dp).background(if (stats.live) GREEN else Color(0xFFE0A030), CircleShape))
            }
            IconButton(onClick = { reload++ }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = DIM)
            }
        }

        if (stats == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (loadingFor == map) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = RED)
                } else {
                    Text("Sin datos de $map", color = DIM)
                }
            }
        } else {
            PRIORITY.forEachIndexed { i, role ->
                val picks = topByRole(role, stats.data)
                Row(
                    Modifier.weight(1f).fillMaxWidth().card(18.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label("0${i + 1}")
                        Spacer(Modifier.height(2.dp))
                        RoleIcon(role, 28.dp)
                        Spacer(Modifier.height(2.dp))
                        Text(role.es, fontSize = 10.sp, color = role.color, maxLines = 1)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        picks.forEachIndexed { j, (a, w) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AgentImage(
                                    a.icon,
                                    Modifier.size(if (j == 0) 54.dp else 44.dp)
                                        .border(if (j == 0) 2.dp else 0.dp, RED, CircleShape),
                                    a.name
                                )
                                Text(a.name, fontSize = 11.sp, maxLines = 1, color = TEXT)
                                Text(fmt(w.wr), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = wrColor(w.wr))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  PESTAÑA ARMAS
// ============================================================

@Composable
fun WeaponsTab(st: AppState, rot: Animatable<Float, AnimationVector1D>, spinning: Boolean, onSpin: () -> Unit) {
    val pool = st.weaponPool()
    val items = pool.map { WheelItem(it.name, catColor(it.cat)) }
    var text by remember { mutableStateOf(st.credits.toString()) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (weaponList.value.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Sin conexión", color = DIM)
            }
            return@Column
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(5)
                val num = d.toIntOrNull() ?: 0
                st.credits = num.coerceAtMost(9000)
                text = if (num > 9000) "9000" else d
            },
            enabled = !spinning,
            singleLine = true,
            label = { Text("CRÉDITOS") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RED, unfocusedBorderColor = OUTLINE,
                focusedLabelColor = RED, unfocusedLabelColor = DIM, cursorColor = RED
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(Modifier.height(10.dp))
        FitWheel(items, { rot.value }, POINTER_COLORS[st.pointerIdx], Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        BigButton("SORTEAR", enabled = !spinning && items.isNotEmpty(), onClick = onSpin)
    }
}

// ============================================================
//  PESTAÑA PERFIL
// ============================================================

@Composable
fun ProfileForm(st: AppState) {
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = RED, unfocusedBorderColor = OUTLINE,
        focusedLabelColor = RED, unfocusedLabelColor = DIM, cursorColor = RED
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = st.riotId, onValueChange = { st.riotId = it },
            label = { Text("RIOT ID") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = st.apiKey, onValueChange = { st.apiKey = it.trim() },
            label = { Text("API KEY · HenrikDev") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp), colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.card(16.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Label(label)
    }
}

@Composable
fun ProfileTab(st: AppState) {
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val profile = profileState.value

    LaunchedEffect(st.riotId, st.apiKey, reload) {
        if (st.apiKey.isBlank()) return@LaunchedEffect
        val keyNow = st.riotId + "|" + st.apiKey
        if (reload == 0 && profileLoadedFor == keyNow && profileState.value != null) return@LaunchedEffect
        delay(600)
        loading = true
        error = null
        try {
            profileState.value = fetchProfile(st.riotId, st.apiKey)
            profileLoadedFor = keyNow
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (st.apiKey.isBlank()) {
            Label("Conectar perfil")
            ProfileForm(st)
            return@Column
        }

        error?.let {
            Row(
                Modifier.fillMaxWidth().card(14.dp, RED).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(it, color = RED, modifier = Modifier.weight(1f), fontSize = 13.sp)
                IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Reintentar", tint = RED) }
            }
        }

        if (profile == null) {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = RED)
                }
            }
            return@Column
        }

        // 01 Jugador
        Box(Modifier.fillMaxWidth().card()) {
            if (profile.card != null) {
                AsyncImage(
                    model = "https://media.valorant-api.com/playercards/${profile.card}/wideart.png",
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(0.3f)
                )
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile.card != null) {
                    AsyncImage(
                        model = "https://media.valorant-api.com/playercards/${profile.card}/displayicon.png",
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(OUTLINE)
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Label("01 Jugador")
                    Text(profile.name.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Row {
                        Text("#${profile.tag}", color = RED, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text("NIV ${profile.level}", color = DIM)
                        Spacer(Modifier.width(10.dp))
                        Text(profile.region.uppercase(), color = DIM)
                    }
                }
                IconButton(onClick = { reload++ }) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = RED)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = TEXT)
                }
            }
        }

        // 02 Rango
        Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (profile.tierIcon != null) {
                AsyncImage(model = profile.tierIcon, contentDescription = null, modifier = Modifier.size(76.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Label("02 Rango")
                Text(rankEs(profile.tierName ?: "Sin rango").uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (profile.rr != null) {
                    Row {
                        Text("${profile.rr} RR", color = RED, fontWeight = FontWeight.Bold)
                        val ch = profile.change
                        if (ch != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                (if (ch > 0) "+" else "") + ch,
                                color = if (ch >= 0) GREEN else RED, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (profile.peakName != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Pico ")
                        if (profile.peakIcon != null) {
                            AsyncImage(model = profile.peakIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(rankEs(profile.peakName).uppercase(), fontSize = 12.sp, color = TEXT)
                    }
                }
            }
        }

        // 03 Estadísticas
        Label("03 Últimas ${profile.matches.size} partidas")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("K/D", profile.kd?.let { "%.2f".format(Locale.US, it) } ?: "—", Modifier.weight(1f))
            StatBox("Victorias", profile.winPct?.let { "${it.roundToInt()}%" } ?: "—", Modifier.weight(1f))
            StatBox("Headshot", profile.hs?.let { "${it.roundToInt()}%" } ?: "—", Modifier.weight(1f))
            StatBox("ADR", profile.adr?.toString() ?: "—", Modifier.weight(1f))
        }

        // 04 Partidas
        if (profile.matches.isNotEmpty()) {
            Label("04 Partidas")
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 6.dp)) {
                profile.matches.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = OUTLINE)
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AgentImage(
                            AGENTS.firstOrNull { it.name.equals(m.agent, true) }?.icon,
                            Modifier.size(38.dp), m.agent
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.map.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(m.mode, fontSize = 11.sp, color = DIM)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                m.score, fontWeight = FontWeight.Black,
                                color = when (m.won) {
                                    true -> GREEN
                                    false -> RED
                                    null -> DIM
                                }
                            )
                            Text("${m.k}/${m.d}/${m.a}", fontSize = 11.sp, color = DIM)
                        }
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
        containerColor = SURFACE,
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar", color = RED) } },
        dismissButton = { TextButton(onClick = { st.resetSettings() }) { Text("Restablecer", color = DIM) } },
        title = { Text("AJUSTES", letterSpacing = 3.sp, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label("Color del puntero")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    POINTER_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).background(c, CircleShape)
                                .border(if (i == st.pointerIdx) 3.dp else 0.dp, DIM, CircleShape)
                                .clickable { st.pointerIdx = i }
                        )
                    }
                }
                Label("Ruleta de agentes · ${st.singleSec}s")
                Slider(
                    value = st.singleSec.toFloat(), onValueChange = { st.singleSec = it.roundToInt() },
                    valueRange = 2f..20f, steps = 17
                )
                Label("Ruleta de armas · ${st.weaponSec}s")
                Slider(
                    value = st.weaponSec.toFloat(), onValueChange = { st.weaponSec = it.roundToInt() },
                    valueRange = 2f..20f, steps = 17
                )
                Label("Intensidad · ${st.intensity}")
                Slider(
                    value = st.intensity.toFloat(), onValueChange = { st.intensity = it.roundToInt() },
                    valueRange = 1f..10f, steps = 8
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = OUTLINE)
                Label("Perfil")
                Spacer(Modifier.height(8.dp))
                ProfileForm(st)
            }
        }
    )
}
