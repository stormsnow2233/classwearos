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
import androidx.compose.foundation.clickable
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
                    enter = fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(500, easing = FastOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
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
                                        (fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                                                slideInHorizontally(
                                                    initialOffsetX = { it / 8 },
                                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                                ))
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(300)) +
                                                        slideOutHorizontally(
                                                            targetOffsetX = { -it / 8 },
                                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                                        )
                                            )
                                    },
                                    label = "navigation"
                                ) { screen ->
                                    when (screen) {
                                        "home" -> HomeScreen(
                                            json = scheduleJson.value,
                                            onNav = { currentScreen = it }
                                        )
                                        "list" -> WeeklyListScreen(
                                            onBack = { currentScreen = "home" },
                                            onSelectDay = { day ->
                                                selectedDay = day
                                                currentScreen = "detail"
                                            }
                                        )
                                        "detail" -> DayDetailScreen(
                                            day = selectedDay,
                                            json = scheduleJson.value,
                                            onBack = { currentScreen = "list" }
                                        )
                                        "settings" -> SettingsScreen(
                                            onBack = { currentScreen = "home" }
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

    private fun loadData() {
        val file = File(filesDir, "schedule.json")
        if (file.exists()) {
            try { scheduleJson.value = JSONObject(file.readText()) } catch (e: Exception) {}
        }
    }
}

@Composable
fun HomeScreen(json: JSONObject?, onNav: (String) -> Unit) {
    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val cal = Calendar.getInstance()
    var dayNum = cal.get(Calendar.DAY_OF_WEEK) - 1
    if (dayNum <= 0) dayNum = 7
    val dayKey = dayNum.toString()

    var current: Course? = null
    var next: Course? = null
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

    current = allCourses.find { now >= it.startTime && now <= it.endTime }
    next = allCourses.find { it.startTime > now }

    val itemsVisible = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        itemsVisible["header"] = true
        delay(100)
        itemsVisible["status"] = true
        delay(100)
        itemsVisible["actions"] = true
    }

    // 背景微光动画
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "glowAngle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // 动态光晕背景
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension * 0.6f
                val brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                )
                drawCircle(brush = brush, radius = radius, center = center)

                // 旋转的微光线条
                val angleRad = glowAngle * (PI / 180).toFloat()
                val xOffset = cos(angleRad) * radius * 0.8f
                val yOffset = sin(angleRad) * radius * 0.8f
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.02f),
                    radius = radius * 0.3f,
                    center = Offset(center.x + xOffset, center.y + yOffset)
                )
            }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.35f,
                edgeAlpha = 0.15f
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = AutoCenteringParams(itemIndex = 1)
        ) {
            item {
                AnimatedVisibility(
                    visible = itemsVisible["header"] == true,
                    enter = fadeIn() + expandVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            "星期" + "一二三四五六日"[dayNum - 1],
                            color = Color(0xFF00E5FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // 添加一个小点表示今天
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00E5FF), CircleShape)
                                .pulsate()
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = itemsVisible["status"] == true,
                    enter = scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                    ) + fadeIn()
                ) {
                    when {
                        current != null -> EnhancedStatusCard(
                            course = current!!,
                            label = "正在进行",
                            isActive = true,
                            endTime = current!!.endTime
                        )
                        next != null -> EnhancedStatusCard(
                            course = next!!,
                            label = "即将开始",
                            isActive = false,
                            startTime = next!!.startTime
                        )
                        else -> Text(
                            "今日课程已结束",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = itemsVisible["actions"] == true,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        ActionButton(
                            icon = "≡",
                            onClick = { onNav("list") },
                            contentDescription = "课表列表"
                        )
                        ActionButton(
                            icon = "⚙",
                            onClick = { onNav("settings") },
                            contentDescription = "设置",
                            highlight = true
                        )
                    }
                }
            }

            // 底部留白
            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}

// 脉冲动画修饰符
@Composable
fun Modifier.pulsate(
    durationMillis: Int = 1500,
    minAlpha: Float = 0.3f
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )
    return this.alpha(alpha)
}

@Composable
fun ActionButton(
    icon: String,
    onClick: () -> Unit,
    contentDescription: String,
    highlight: Boolean = false
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch {
                // 点击动画
                scale.animateTo(0.8f, animationSpec = tween(100))
                scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            onClick()
        },
        modifier = Modifier
            .size(56.dp)
            .scale(scale.value),
        colors = if (highlight) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
        shape = CircleShape
    ) {
        Text(
            icon,
            fontSize = 26.sp,
            color = if (highlight) Color.Black else Color(0xFF00E5FF)
        )
    }
}

@Composable
fun EnhancedStatusCard(
    course: Course,
    label: String,
    isActive: Boolean,
    endTime: String? = null,
    startTime: String? = null
) {
    val context = LocalContext.current
    val timeText = if (isActive && endTime != null) {
        "结束于 $endTime"
    } else if (!isActive && startTime != null) {
        "开始于 $startTime"
    } else ""

    // 活动进度条动画
    val progress = remember { Animatable(0f) }
    if (isActive && endTime != null) {
        LaunchedEffect(endTime) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = Date()
            val end = sdf.parse(endTime)
            val start = sdf.parse(course.startTime)
            if (end != null && start != null) {
                val total = end.time - start.time
                val elapsed = now.time - start.time
                val target = (elapsed.toFloat() / total).coerceIn(0f, 1f)
                progress.snapTo(target)
                // 每秒更新一次进度
                while (true) {
                    delay(1000)
                    val newNow = Date()
                    val newElapsed = newNow.time - start.time
                    val newTarget = (newElapsed.toFloat() / total).coerceIn(0f, 1f)
                    progress.animateTo(newTarget, animationSpec = tween(1000))
                }
            }
        }
    }

    Card(
        onClick = {
            // 轻触卡片时的触感反馈
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        },
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(vertical = 8.dp)
            .animateContentSize(),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = if (isActive) Color(0xFF00222B) else Color(0xFF181818),
            endBackgroundColor = if (isActive) Color(0xFF003344) else Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标签行
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                            .pulsate(minAlpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (isActive) Color(0xFF00E5FF) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 课程名称（带缩放效果）
            Text(
                course.name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 时间与教室
            Text(
                "${course.startTime} - ${course.endTime}",
                fontSize = 12.sp,
                color = Color.LightGray
            )

            if (course.room.isNotEmpty()) {
                Text(
                    course.room,
                    fontSize = 12.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Medium
                )
            }

            // 进度条（仅活动中显示）
            if (isActive && endTime != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00E5FF))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    timeText,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            } else if (!isActive && startTime != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    timeText,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ---------------- 周课表页面 ----------------
@Composable
fun WeeklyListScreen(onBack: () -> Unit, onSelectDay: (String) -> Unit) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    // 入场动画
    val visibleItems = remember { mutableStateListOf<Boolean>().apply { repeat(days.size) { add(false) } } }
    LaunchedEffect(Unit) {
        visibleItems.forEachIndexed { index, _ ->
            delay((index * 60).toLong())
            visibleItems[index] = true
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            edgeScale = 0.4f,
            edgeAlpha = 0.2f
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 })
            ) {
                Text(
                    "一周课表",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        items(days.size) { index ->
            AnimatedVisibility(
                visible = visibleItems.getOrElse(index) { false },
                enter = fadeIn(animationSpec = tween(400, delayMillis = index * 30)) +
                        slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        )
            ) {
                Chip(
                    label = {
                        Text(
                            days[index],
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = { onSelectDay((index + 1).toString()) },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(44.dp)
                        .padding(vertical = 3.dp),
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF202020)
                    ),
                    shape = RoundedCornerShape(30.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            BottomReturnArc(onBack)
        }
    }
}

// ---------------- 详情页面 (带进入动画) ----------------
@Composable
fun DayDetailScreen(day: String, json: JSONObject?, onBack: () -> Unit) {
    val list = remember(day, json) {
        val courses = mutableListOf<Course>()
        json?.optJSONArray(day)?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                courses.add(
                    Course(
                        o.getString("name"),
                        o.getString("startTime"),
                        o.getString("endTime"),
                        o.getString("room")
                    )
                )
            }
        }
        courses.sortedBy { it.startTime }
    }

    // 每项依次出现
    val itemsVisible = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(list) {
        list.indices.forEach { index ->
            delay((index * 80).toLong())
            itemsVisible[index] = true
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
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

        items(list.size) { index ->
            AnimatedVisibility(
                visible = itemsVisible[index] == true,
                enter = slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn()
            ) {
                val course = list[index]
                Card(
                    onClick = { /* 可以扩展为编辑/提醒等 */ },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color(0xFF1A1A1A),
                        endBackgroundColor = Color(0xFF222222)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            course.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${course.startTime} - ${course.endTime}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (course.room.isNotEmpty()) {
                            Text(
                                course.room,
                                fontSize = 11.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            BottomReturnArc(onBack)
        }
    }
}

// ---------------- 设置页面 (扩展功能) ----------------
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isSyncRunning by remember { mutableStateOf(false) }
    var showVibrationHint by remember { mutableStateOf(false) }

    // 读取当前服务状态（简化，可扩展）
    LaunchedEffect(Unit) {
        // 实际项目中应查询服务状态
        isSyncRunning = false
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "同步与设置",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF1A1A1A)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ToggleChip(
                        checked = isSyncRunning,
                        onCheckedChange = { checked ->
                            isSyncRunning = checked
                            if (checked) {
                                context.startForegroundService(Intent(context, SyncService::class.java))
                                showVibrationHint = true
                            } else {
                                context.stopService(Intent(context, SyncService::class.java))
                            }
                        },
                        label = {
                            Text(
                                "编辑器连接",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        toggleControl = {
                            Switch(
                                checked = isSyncRunning,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E5FF),
                                    checkedTrackColor = Color(0xFF003344)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(
                        visible = isSyncRunning,
                        enter = expandVertically() + fadeIn()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                "IP 地址",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                getDeviceIp() + ":8080",
                                fontSize = 16.sp,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "在电脑浏览器输入以上地址",
                                fontSize = 9.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        // 触感反馈提示
        item {
            AnimatedVisibility(
                visible = showVibrationHint,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Text(
                    "✓ 服务已启动",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            LaunchedEffect(showVibrationHint) {
                if (showVibrationHint) {
                    delay(2000)
                    showVibrationHint = false
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            BottomReturnArc(onBack)
        }
    }
}

// ---------------- 通用组件 (动画增强) ----------------
@Composable
fun BottomReturnArc(onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(42.dp)
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(topStartPercent = 100, topEndPercent = 100))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1E1E), Color(0xFF121212))
                )
            )
            .clickable {
                // 点击返回动画
                scope.launch {
                    scale.animateTo(0.9f, animationSpec = tween(80))
                    scale.animateTo(1f, animationSpec = spring())
                }
                onClick()
            }
            .scale(scale.value),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "返回",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.offset(y = (-2).dp)
        )
    }
}

// ---------------- 数据类与工具函数 ----------------
data class Course(val name: String, val startTime: String, val endTime: String, val room: String)

fun getDeviceIp(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: ""
                }
            }
        }
    } catch (e: Exception) {}
    return "未联网"
}

// 扩展函数：简单的振动反馈
fun vibrate(context: Context, durationMs: Long = 20) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}