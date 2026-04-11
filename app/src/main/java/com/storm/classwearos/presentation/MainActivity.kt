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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            // 核心状态：当前页面和选中的日期
            var currentScreen by remember { mutableStateOf("home") }
            var selectedDay by remember { mutableStateOf("1") }

            // 1. 创建滑动返回的状态
            val swipeState = rememberSwipeToDismissBoxState()

            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    primary = Color(0xFF00E5FF),
                    background = Color.Black
                )
            ) {
                // 2. 使用 SwipeToDismissBox 包裹整个导航逻辑
                SwipeToDismissBox(
                    state = swipeState,
                    // 当用户向右滑动完成时触发
                    onDismissed = {
                        when (currentScreen) {
                            "settings" -> currentScreen = "home"
                            "detail" -> currentScreen = "list"
                            "list" -> currentScreen = "home"
                            else -> finish() // 在主页滑动则退出
                        }
                    }
                ) { isBackground ->
                    // isBackground 为 true 时显示的是“下面一层”的内容，
                    // 这里我们简单处理，只渲染当前层，Scaffold 会处理背景
                    if (!isBackground) {
                        Scaffold(
                            timeText = { TimeText() },
                            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
                        ) {
                            // 3. 页面切换动画
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

// ---------------- 页面逻辑与组件 ----------------

@Composable
fun HomeScreen(json: JSONObject?, onNav: (String) -> Unit) {
    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val cal = Calendar.getInstance()
    var dayNum = cal.get(Calendar.DAY_OF_WEEK) - 1
    if (dayNum <= 0) dayNum = 7
    val dayKey = dayNum.toString()

    var current: Course? = null
    var next: Course? = null
    json?.optJSONArray(dayKey)?.let { array ->
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
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.4f, edgeAlpha = 0.2f),
        horizontalAlignment = Alignment.CenterHorizontally,
        autoCentering = AutoCenteringParams(itemIndex = 1)
    ) {
        item { Text("星期" + "一二三四五六日"[dayNum - 1], color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

        item {
            if (current != null) StatusCard(current!!, "现在是", true)
            else if (next != null) StatusCard(next!!, "下节课", false)
            else Text("今日已结课", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }

        item {
            Row(modifier = Modifier.padding(top = 16.dp)) {
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
                modifier = Modifier.fillMaxWidth(0.75f).height(38.dp).padding(vertical = 2.dp),
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF161616))
            )
        }
        item { BottomReturnArc(onBack) }
    }
}

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
        item { Text("周${"一二三四五六日"[day.toInt()-1]} 详情", color = Color(0xFF00E5FF), modifier = Modifier.padding(bottom = 10.dp)) }
        items(list.sortedBy { it.startTime }) { c ->
            Card(onClick = {}, modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 4.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${c.startTime}-${c.endTime}", fontSize = 10.sp, color = Color.Gray)
                    if (c.room.isNotEmpty()) Text(c.room, fontSize = 10.sp, color = Color(0xFF00E5FF))
                }
            }
        }
        item { BottomReturnArc(onBack) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRunning by remember { mutableStateOf(false) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("同步设置", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 15.dp)) }
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
                    Text("IP 地址", fontSize = 10.sp, color = Color.Gray)
                    Text(getDeviceIp() + ":8080", fontSize = 14.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { BottomReturnArc(onBack) }
    }
}

// ---------------- 自定义通用组件 ----------------

@Composable
fun BottomReturnArc(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
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
            Text(course.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
    return "未联网"
}