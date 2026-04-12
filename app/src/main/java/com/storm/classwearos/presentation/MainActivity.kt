package com.storm.classwearos.presentation

import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var scheduleJson = mutableStateOf<JSONObject?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) { loadData() }
        }, IntentFilter("com.storm.classwearos.UPDATE_UI"), RECEIVER_EXPORTED)

        setContent {
            var currentScreen by remember { mutableStateOf("home") }
            var selectedDay by remember { mutableStateOf("1") }
            val swipeState = rememberSwipeToDismissBoxState()
            val transitionState = remember { MutableTransitionState(false) }
            LaunchedEffect(Unit) { transitionState.targetState = true }

            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    primary = Color(0xFF00E5FF),
                    background = Color.Black,
                    surface = Color(0xFF121212)
                )
            ) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(tween(500, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(500)),
                    exit = fadeOut(tween(300))
                ) {
                    var lastNavTime by remember { mutableStateOf(0L) }
                    SwipeToDismissBox(
                        state = swipeState,
                        onDismissed = {
                            val now = System.currentTimeMillis()
                            if (now - lastNavTime > 300) {
                                lastNavTime = now
                                when (currentScreen) {
                                    "settings" -> currentScreen = "home"
                                    "detail" -> currentScreen = "list"
                                    "list" -> currentScreen = "home"
                                    else -> finish()
                                }
                            }
                        }
                    ) { isBackground ->
                        if (!isBackground) {
                            Scaffold(
                                timeText = { TimeText() },
                                vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
                            ) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        (fadeIn(tween(400, delayMillis = 100)) +
                                                slideInHorizontally(
                                                    initialOffsetX = { it / 8 },
                                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                                ))
                                            .togetherWith(
                                                fadeOut(tween(300)) +
                                                        slideOutHorizontally(
                                                            targetOffsetX = { -it / 8 },
                                                            animationSpec = tween(300)
                                                        )
                                            )
                                    }, label = "nav"
                                ) { screen ->
                                    when (screen) {
                                        "home" -> HomeScreen(
                                            json = scheduleJson.value,
                                            onNav = { currentScreen = it }
                                        )
                                        "list" -> WeeklyListScreen(
                                            onSelectDay = { day ->
                                                selectedDay = day
                                                currentScreen = "detail"
                                            }
                                        )
                                        "detail" -> DayDetailScreen(day = selectedDay, json = scheduleJson.value)
                                        "settings" -> SettingsScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadData() {
        val file = File(filesDir, "schedule.json")
        if (file.exists()) {
            try { scheduleJson.value = JSONObject(file.readText()) } catch (_: Exception) {}
        }
    }
}

@Composable
fun HomeScreen(json: JSONObject?, onNav: (String) -> Unit) {
    val cal = Calendar.getInstance()
    val todayNum = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // 周一=1 ... 周日=7
    val dayKey = todayNum.toString()
    val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    val allCourses = remember(json, dayKey) {
        val list = mutableListOf<Course>()
        json?.optJSONArray(dayKey)?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(Course(o.getString("name"), o.getString("startTime"), o.getString("endTime"), o.getString("room")))
            }
        }
        list.sortedBy { it.startTime }
    }

    val current = allCourses.find { nowTime >= it.startTime && nowTime <= it.endTime }
    val next = allCourses.find { it.startTime > nowTime }

    val countdownText = remember(next?.startTime) {
        next?.startTime?.let { calcCountdown(it) } ?: ""
    }
    LaunchedEffect(countdownText) {
        while (next != null) {
            delay(60000)
        }
    }

    val itemsVisible = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        itemsVisible["header"] = true
        delay(80)
        itemsVisible["status"] = true
        delay(80)
        itemsVisible["actions"] = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAngle by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "glowAngle")

    Box(Modifier.fillMaxSize().drawBehind {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.6f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.03f), Color.Transparent), center, r), radius = r, center = center)
        val rad = glowAngle * (PI / 180).toFloat()
        drawCircle(Color(0xFF00E5FF).copy(alpha = 0.02f), radius = r * 0.3f,
            center = Offset(center.x + cos(rad) * r * 0.8f, center.y + sin(rad) * r * 0.8f))
    }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.35f, edgeAlpha = 0.15f),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = AutoCenteringParams(itemIndex = 1)
        ) {
            item {
                AnimatedVisibility(
                    visible = itemsVisible["header"] == true,
                    enter = fadeIn() + expandVertically()
                ) {
                    Text(
                        "周${"一二三四五六日"[todayNum-1]} · 今日",
                        color = Color(0xFF00E5FF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = itemsVisible["status"] == true,
                    enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.6f)) + fadeIn()
                ) {
                    when {
                        current != null -> EnhancedStatusCard(current!!, "正在进行", true, endTime = current!!.endTime)
                        next != null -> EnhancedStatusCard(next!!, "即将开始", false, startTime = next!!.startTime, countdown = countdownText)
                        else -> Text("今日课程已结束", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                    }
                }
            }

            if (allCourses.isNotEmpty()) {
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(600))) {
                        StatsCard(allCourses)
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = itemsVisible["actions"] == true,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        ActionButton("≡", { onNav("list") }, "课表列表")
                        ActionButton("⚙", { onNav("settings") }, "设置", highlight = true)
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

fun calcCountdown(startTimeStr: String): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = Date()
        val start = sdf.parse(startTimeStr) ?: return ""
        val calNow = Calendar.getInstance().apply { time = now }
        val calStart = Calendar.getInstance().apply {
            time = start
            set(Calendar.YEAR, calNow.get(Calendar.YEAR))
            set(Calendar.MONTH, calNow.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, calNow.get(Calendar.DAY_OF_MONTH))
        }
        val diffMinutes = (calStart.timeInMillis - calNow.timeInMillis) / 60000
        when {
            diffMinutes <= 0 -> "即将开始"
            diffMinutes < 60 -> "${diffMinutes}分钟后开始"
            else -> {
                val hours = diffMinutes / 60
                val mins = diffMinutes % 60
                if (mins == 0L) "${hours}小时后开始" else "${hours}小时${mins}分钟后开始"
            }
        }
    } catch (e: Exception) { "" }
}

@Composable
fun StatsCard(courses: List<Course>) {
    val totalMinutes = remember(courses) {
        courses.sumOf { c ->
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            try {
                val start = sdf.parse(c.startTime)!!
                val end = sdf.parse(c.endTime)!!
                (end.time - start.time) / 60000
            } catch (e: Exception) { 0 }
        }
    }
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF1A2A2F))
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${courses.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                Text("节课", fontSize = 10.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                Text(if (hours > 0) "${hours}h${mins}m" else "${mins}min", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                Text("总时长", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun WeeklyListScreen(onSelectDay: (String) -> Unit) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val visibleItems = remember { mutableStateListOf<Boolean>().apply { repeat(days.size) { add(false) } } }
    LaunchedEffect(Unit) { days.indices.forEach { i -> delay(i * 60L); visibleItems[i] = true } }
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 })) {
                Text("一周课表", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        items(days.size) { i ->
            AnimatedVisibility(
                visible = visibleItems[i],
                enter = fadeIn(tween(400, delayMillis = i * 30)) + slideInHorizontally(initialOffsetX = { it / 4 })
            ) {
                Chip(
                    label = { Text(days[i], Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 15.sp) },
                    onClick = { onSelectDay((i + 1).toString()) },
                    modifier = Modifier.fillMaxWidth(0.8f).height(44.dp).padding(vertical = 3.dp),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF202020)),
                    shape = RoundedCornerShape(30.dp)
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun DayDetailScreen(day: String, json: JSONObject?) {
    val list = remember(day, json) {
        val courses = mutableListOf<Course>()
        json?.optJSONArray(day)?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                courses.add(Course(o.getString("name"), o.getString("startTime"), o.getString("endTime"), o.getString("room")))
            }
        }
        courses.sortedBy { it.startTime }
    }
    val itemsVisible = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(list) { list.indices.forEach { i -> delay(i * 80L); itemsVisible[i] = true } }
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "周${"一二三四五六日"[day.toInt() - 1]} 课程详情",
                color = Color(0xFF00E5FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(list.size) { i ->
            AnimatedVisibility(
                visible = itemsVisible[i] == true,
                enter = slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()
            ) {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF1A1A1A))
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(list[i].name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${list[i].startTime} - ${list[i].endTime}", fontSize = 11.sp, color = Color.Gray)
                        if (list[i].room.isNotEmpty()) Text(list[i].room, fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var isSyncRunning by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    LaunchedEffect(showHint) {
        if (showHint) {
            delay(2000)
            showHint = false
        }
    }

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("同步与设置", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp)) }
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF1A1A1A))
            ) {
                Column(Modifier.padding(16.dp)) {
                    ToggleChip(
                        checked = isSyncRunning,
                        onCheckedChange = {
                            isSyncRunning = it
                            if (it) {
                                context.startForegroundService(Intent(context, SyncService::class.java))
                                showHint = true
                            } else context.stopService(Intent(context, SyncService::class.java))
                        },
                        label = { Text("编辑器连接", fontSize = 14.sp) },
                        toggleControl = {
                            Switch(
                                checked = isSyncRunning,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E5FF),
                                    checkedTrackColor = Color(0xFF003344)
                                )
                            )
                        }
                    )
                    AnimatedVisibility(visible = isSyncRunning, enter = expandVertically() + fadeIn()) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("IP 地址", fontSize = 10.sp, color = Color.Gray)
                            Text(getDeviceIp() + ":8080", fontSize = 16.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                            Text("在电脑浏览器输入以上地址", fontSize = 9.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = showHint, enter = fadeIn() + slideInVertically()) {
                Text("✓ 服务已启动", color = Color(0xFF00E5FF), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun Modifier.pulsate(duration: Int = 1500, minAlpha: Float = 0.3f): Modifier {
    val alpha by rememberInfiniteTransition().animateFloat(
        1f, minAlpha,
        infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    return this.alpha(alpha)
}

@Composable
fun ActionButton(icon: String, onClick: () -> Unit, desc: String, highlight: Boolean = false) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            onClick()
            scope.launch {
                scale.animateTo(0.8f, tween(100))
                scale.animateTo(1f, spring())
            }
        },
        modifier = Modifier.size(56.dp).scale(scale.value),
        colors = if (highlight) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
        shape = CircleShape
    ) {
        Text(icon, fontSize = 26.sp, color = if (highlight) Color.Black else Color(0xFF00E5FF))
    }
}

@Composable
fun EnhancedStatusCard(
    course: Course,
    label: String,
    isActive: Boolean,
    endTime: String? = null,
    startTime: String? = null,
    countdown: String = ""
) {
    val context = LocalContext.current
    val timeText = if (isActive && endTime != null) "结束于 $endTime" else if (!isActive && startTime != null) "开始于 $startTime" else ""
    val progress = remember { Animatable(0f) }
    if (isActive && endTime != null) {
        LaunchedEffect(endTime) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val start = sdf.parse(course.startTime)!!
            val end = sdf.parse(endTime)!!
            val total = end.time - start.time
            while (true) {
                val elapsed = Date().time - start.time
                progress.animateTo((elapsed.toFloat() / total).coerceIn(0f, 1f), tween(1000))
                delay(1000)
            }
        }
    }
    Card(
        onClick = {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(VibratorManager::class.java)?.defaultVibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        },
        modifier = Modifier.fillMaxWidth(0.9f).padding(8.dp).animateContentSize(),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = if (isActive) Color(0xFF00222B) else Color(0xFF181818)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) Box(Modifier.size(8.dp).background(Color(0xFF00E5FF), CircleShape).pulsate())
                Text(label, fontSize = 11.sp, color = if (isActive) Color(0xFF00E5FF) else Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(course.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text("${course.startTime} - ${course.endTime}", fontSize = 12.sp, color = Color.LightGray)
            if (course.room.isNotEmpty()) Text(course.room, fontSize = 12.sp, color = Color(0xFF00E5FF))
            if (isActive && endTime != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth(0.8f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress.value).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00E5FF))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(timeText, fontSize = 10.sp, color = Color.Gray)
            } else if (!isActive) {
                if (countdown.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(countdown, fontSize = 10.sp, color = Color(0xFF00E5FF))
                }
                if (timeText.isNotEmpty()) {
                    Text(timeText, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

data class Course(val name: String, val startTime: String, val endTime: String, val room: String)

fun getDeviceIp(): String {
    try {
        NetworkInterface.getNetworkInterfaces().toList().forEach { intf ->
            intf.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: ""
            }
        }
    } catch (_: Exception) {}
    return "未联网"
}