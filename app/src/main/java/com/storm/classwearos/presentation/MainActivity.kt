package com.storm.classwearos.presentation

import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var scheduleJson = mutableStateOf<JSONObject?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) { loadData() }
        }, IntentFilter("com.storm.classwearos.UPDATE_UI"), RECEIVER_EXPORTED)

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }

            // 状态：是否显示引导页
            var showOnboarding by remember {
                mutableStateOf(prefs.getBoolean("first_launch", true))
            }

            var currentScreen by remember { mutableStateOf("home") }
            var selectedDay by remember { mutableStateOf("1") }
            val swipeState = rememberSwipeToDismissBoxState()

            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    primary = Color(0xFF00E5FF),
                    background = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. 主程序逻辑
                    SwipeToDismissBox(
                        state = swipeState,
                        onDismissed = {
                            when (currentScreen) {
                                "settings" -> currentScreen = "home"
                                "detail" -> currentScreen = "list"
                                "list" -> currentScreen = "home"
                                else -> finish()
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
                                        (fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                                                scaleIn(initialScale = 0.85f, animationSpec = tween(350, easing = FastOutSlowInEasing)))
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(300)) +
                                                        scaleOut(targetScale = 1.15f, animationSpec = tween(300))
                                            )
                                    }, label = "navigation"
                                ) { screen ->
                                    when (screen) {
                                        "home" -> HomeScreen(scheduleJson.value, onNav = { currentScreen = it })
                                        "list" -> WeeklyListScreen(onBack = { currentScreen = "home" }, onSelectDay = {
                                            selectedDay = it
                                            currentScreen = "detail"
                                        })
                                        "detail" -> DayDetailScreen(selectedDay, scheduleJson.value, onBack = { currentScreen = "list" })
                                        "settings" -> SettingsScreen(onBack = { currentScreen = "home" })
                                    }
                                }
                            }
                        }
                    }

                    // 2. 首次进入的引导层
                    AnimatedVisibility(
                        visible = showOnboarding,
                        enter = fadeIn(),
                        exit = fadeOut() + scaleOut(targetScale = 1.2f)
                    ) {
                        OnboardingOverlay {
                            // 关闭引导并记录
                            showOnboarding = false
                            prefs.edit().putBoolean("first_launch", false).apply()
                        }
                    }
                }
            }
        }
    }

    private fun loadData() {
        val file = File(filesDir, "schedule.json")
        if (file.exists()) {
            try { scheduleJson.value = JSONObject(file.readText()) } catch (e: Exception) {}
        }
    }
}

// --- 引导页组件 ---
@Composable
fun OnboardingOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)) // 极深的背景增加沉浸感
            .clickable(enabled = false) {}, // 拦截点击
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            // 欢迎图标/Logo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⌚", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "欢迎使用 Class OS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "• 点击⚙开启网页同步\n• 右滑返回上一级\n• 适配圆屏 自动缩放",
                color = Color.LightGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 精致的“我知道了”按钮
            Chip(
                onClick = onDismiss,
                label = { Text("开始使用", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF00E5FF), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(0.7f).height(36.dp)
            )
        }
    }
}

// --- 主页 ---
@Composable
fun HomeScreen(json: JSONObject?, onNav: (String) -> Unit) {
    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val cal = Calendar.getInstance()
    var dayNum = cal.get(Calendar.DAY_OF_WEEK) - 1
    if (dayNum <= 0) dayNum = 7

    var current: Course? = null
    var next: Course? = null
    json?.optJSONArray(dayNum.toString())?.let { array ->
        val list = mutableListOf<Course>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(Course(o.getString("name"), o.getString("startTime"), o.getString("endTime"), o.getString("room")))
        }
        val sorted = list.sortedBy { it.startTime }
        current = sorted.find { now >= it.startTime && now <= it.endTime }
        next = sorted.find { it.startTime > now }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.45f, edgeAlpha = 0.2f),
        horizontalAlignment = Alignment.CenterHorizontally,
        autoCentering = AutoCenteringParams(itemIndex = 1)
    ) {
        item {
            Text(
                "星期" + "一二三四五六日"[dayNum - 1],
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            if (current != null) StatusCard(current!!, "现在是", true)
            else if (next != null) StatusCard(next!!, "下节预告", false)
            else Text("今日课程已结束", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(25.dp))
        }

        item {
            Row(modifier = Modifier.padding(top = 18.dp)) {
                Button(onClick = { onNav("list") }, modifier = Modifier.size(52.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("≡", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(24.dp))
                Button(onClick = { onNav("settings") }, modifier = Modifier.size(52.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("⚙", fontSize = 22.sp, color = Color(0xFF00E5FF))
                }
            }
        }
    }
}

// --- 周列表 ---
@Composable
fun WeeklyListScreen(onBack: () -> Unit, onSelectDay: (String) -> Unit) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Text("一周课表", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp)) }
        items(days.size) { i ->
            Chip(
                label = { Text(days[i], modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 13.sp) },
                onClick = { onSelectDay((i + 1).toString()) },
                modifier = Modifier.fillMaxWidth(0.72f).height(38.dp).padding(vertical = 2.dp),
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF161616))
            )
        }
        item { BottomReturnArc(onBack) }
    }
}

// --- 详情页 ---
@Composable
fun DayDetailScreen(day: String, json: JSONObject?, onBack: () -> Unit) {
    val list = mutableListOf<Course>()
    json?.optJSONArray(day)?.let { array ->
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(Course(o.getString("name"), o.getString("startTime"), o.getString("endTime"), o.getString("room")))
        }
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.5f)) {
        item { Text("星期${"一二三四五六日"[day.toInt()-1]} 详情", color = Color(0xFF00E5FF), modifier = Modifier.padding(bottom = 10.dp), fontSize = 13.sp) }
        if (list.isEmpty()) {
            item { Text("今日无课", color = Color.Gray, modifier = Modifier.padding(25.dp)) }
        } else {
            items(list.sortedBy { it.startTime }) { c ->
                Card(onClick = {}, modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${c.startTime}-${c.endTime}", fontSize = 10.sp, color = Color.Gray)
                        if (c.room.isNotEmpty()) Text(c.room, fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }
                }
            }
        }
        item { BottomReturnArc(onBack) }
    }
}

// --- 设置页 ---
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("同步服务", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 15.dp)) }
        item {
            ToggleChip(
                checked = isRunning,
                onCheckedChange = {
                    isRunning = it
                    if(it) context.startForegroundService(Intent(context, SyncService::class.java))
                    else context.stopService(Intent(context, SyncService::class.java))
                },
                label = { Text("编辑器连接") },
                toggleControl = { Switch(checked = isRunning, onCheckedChange = null) },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
        item {
            if (isRunning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 10.dp)) {
                    Text("访问地址", fontSize = 10.sp, color = Color.Gray)
                    Text(getDeviceIp() + ":8080", fontSize = 14.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { BottomReturnArc(onBack) }
    }
}

// --- 通用：半椭圆返回键 ---
@Composable
fun BottomReturnArc(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .height(38.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(topStartPercent = 100, topEndPercent = 100))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("返回", fontSize = 11.sp, color = Color.Gray)
    }
}

// --- 通用：状态卡片 ---
@Composable
fun StatusCard(course: Course, label: String, highlight: Boolean) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(0.88f),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = if (highlight) Color(0xFF00222B) else Color(0xFF111111)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 10.sp, color = if (highlight) Color(0xFF00E5FF) else Color.Gray)
            Text(course.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text("${course.startTime} - ${course.endTime}", fontSize = 11.sp, color = Color.LightGray)
            if (course.room.isNotEmpty()) Text(course.room, fontSize = 11.sp, color = Color(0xFF00E5FF))
        }
    }
}

data class Course(val name: String, val startTime: String, val endTime: String, val room: String)

fun getDeviceIp(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: ""
            }
        }
    } catch (e: Exception) {}
    return "未连接WiFi"
}