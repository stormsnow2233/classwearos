package com.storm.classwearos.presentation

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.TitleCard
import androidx.wear.compose.material.Card as MaterialCard
import androidx.wear.compose.material.CardDefaults as MaterialCardDefaults
import androidx.wear.compose.material.Switch as MaterialSwitch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Card as Card3
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.TextButton
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.net.Inet4Address
import java.net.NetworkInterface

data class Course(val startTime: String, val endTime: String, val name: String, val room: String)
data class ScheduleStatus(val currentCourse: Course? = null, val nextCourse: Course? = null)

class MainActivity : ComponentActivity() {
    private var statusMessage by mutableStateOf("待开启")
    private var lastSyncTime by mutableStateOf("尚未同步")
    private val fullSchedule = mutableStateMapOf<String, List<Course>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshScheduleData()

        val filter = IntentFilter("com.storm.classwearos.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiUpdateReceiver, filter)
        }

        setContent {
            val pagerState = rememberPagerState(initialPage = 1, pageCount = { 8 })
            val pageIndicatorState = remember {
                object : PageIndicatorState {
                    override val pageCount: Int get() = 8
                    override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
                    override val selectedPage: Int get() = pagerState.currentPage
                }
            }

            MaterialTheme {
                AppScaffold {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState) { page ->
                            if (page == 0) {
                                ControlScreen()
                            } else {
                                SchedulePage(page - 1)
                            }
                        }
                        HorizontalPageIndicator(
                            pageIndicatorState = pageIndicatorState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(viewModel: MainViewModel = viewModel()) {
        val context = LocalContext.current
        val isServiceRunning by viewModel.isServiceRunning.collectAsState()
        val deviceIp = remember { getIpAddress() }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ClassWear 同步助手", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(20.dp))

            Card3(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("网页端同步服务")
                        Spacer(modifier = Modifier.weight(1f))
                        MaterialSwitch(
                            checked = isServiceRunning,
                            onCheckedChange = { viewModel.toggleService(context, it) }
                        )
                    }

                    if (isServiceRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "浏览器输入: http://$deviceIp:8080",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card3(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchButton(
                        checked = viewModel.reminderEnabled,
                        onCheckedChange = { viewModel.toggleReminder(it) },
                        label = { Text("上课提醒") },
                        secondaryLabel = { Text("课前 5 分钟震动提醒") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("手动刷新课表")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text("Device IP: $deviceIp", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }

    fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "未知"
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return "127.0.0.1"
    }

    @Composable
    fun ControlScreen() {
        val context = LocalContext.current
        var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, SyncService::class.java)) }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("网页同步服务", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))

            ToggleChip(
                checked = isServiceRunning,
                onCheckedChange = { checked ->
                    isServiceRunning = checked
                    val intent = Intent(context, SyncService::class.java)
                    if (checked) {
                        statusMessage = "服务运行中"
                        context.startForegroundService(intent)
                    } else {
                        statusMessage = "服务已停止"
                        context.stopService(intent)
                    }
                },
                label = { Text(if (isServiceRunning) "正在接收" else "已关闭") },
                toggleControl = {
                    Icon(
                        imageVector = if (isServiceRunning) ToggleChipDefaults.switchIcon(isServiceRunning) else ToggleChipDefaults.radioIcon(isServiceRunning),
                        contentDescription = "Switch"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("开启后浏览器访问端口 8080", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }

    @Composable
    fun SchedulePage(dayIdx: Int) {
        val dayIndex = (dayIdx + 1).toString()
        val dayCourses = fullSchedule[dayIndex] ?: emptyList()
        val context = LocalContext.current
        var scheduleStatus by remember { mutableStateOf(getActiveSchedule(dayCourses)) }
        val listState = rememberScalingLazyListState() // 绑定滚动状态

        LaunchedEffect(dayCourses) {
            while(true) {
                scheduleStatus = getActiveSchedule(dayCourses)
                if (dayIdx + 1 == getTodayDayOfWeek()) checkAndApplyMuteMode(context, scheduleStatus)
                kotlinx.coroutines.delay(60000)
            }
        }

        ScreenScaffold(scrollState = listState) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 30.dp)
            ) {
                item { ListHeader { Text("星期" + "一二三四五六日"[dayIdx], color = Color(0xFF7FCFFF)) } }

                if (scheduleStatus.currentCourse != null || scheduleStatus.nextCourse != null) {
                    item {
                        MaterialCard(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            backgroundPainter = MaterialCardDefaults.cardBackgroundPainter(
                                startBackgroundColor = if (scheduleStatus.currentCourse != null) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                endBackgroundColor = Color(0xFF121212)
                            )
                        ) {
                            Column {
                                if (scheduleStatus.currentCourse != null) {
                                    val c = scheduleStatus.currentCourse!!
                                    Text("正在上课", fontSize = 10.sp, color = Color.White)
                                    Text(c.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("教室: ${c.room} | 结束: ${c.endTime}", fontSize = 10.sp, color = Color.White)
                                } else if (scheduleStatus.nextCourse != null) {
                                    val n = scheduleStatus.nextCourse!!
                                    Text("下一节", fontSize = 10.sp, color = Color.LightGray)
                                    Text(n.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${n.startTime} - ${n.endTime} | ${n.room}", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                if (dayCourses.isEmpty()) {
                    item { Text("暂无课程", fontSize = 12.sp, color = Color.Gray) }
                } else {
                    items(dayCourses) { course -> CourseCard(course) }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("$statusMessage | $lastSyncTime", fontSize = 8.sp, color = Color.Gray)
                    }
                }
            }
        }
    }

    @Composable
    fun CourseCard(course: Course) {
        TitleCard(
            onClick = { },
            title = { Text(course.name, modifier = Modifier.basicMarquee(), maxLines = 1) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        ) {
            Text(course.room, color = Color(0xFF7FCFFF), fontSize = 12.sp)
            Text(text = "${course.startTime} - ${course.endTime}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    private fun getActiveSchedule(courses: List<Course>): ScheduleStatus {
        val now = Calendar.getInstance()
        val currentTime = String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        var current: Course? = null
        var next: Course? = null

        for (course in courses) {
            val start = course.startTime
            val end = course.endTime
            if (start.isEmpty()) continue

            if (currentTime in start..end) {
                current = course
            } else if (currentTime < start) {
                if (next == null || start < next.startTime) next = course
            }
        }
        return ScheduleStatus(current, next)
    }

    private fun checkAndApplyMuteMode(context: Context, status: ScheduleStatus) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (status.currentCourse != null) {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_VIBRATE) audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getTodayDayOfWeek(): Int {
        var day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        if (day == 0) day = 7
        return day
    }

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusMessage = "✅ 同步成功"
            lastSyncTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            refreshScheduleData()
        }
    }

    private fun refreshScheduleData() {
        val file = File(filesDir, "schedule.json")
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                for (i in 1..7) {
                    val dayKey = i.toString()
                    val array = json.optJSONArray(dayKey) ?: org.json.JSONArray()
                    val list = mutableListOf<Course>()
                    for (j in 0 until array.length()) {
                        val obj = array.getJSONObject(j)

                        // 🔥 5. 兼容老数据格式，同时读取新的 startTime 和 endTime 🔥
                        val timeStr = obj.optString("time", "")
                        val start = obj.optString("startTime", if (timeStr.contains("-")) timeStr.split("-")[0] else timeStr)
                        val end = obj.optString("endTime", if (timeStr.contains("-")) timeStr.split("-")[1] else "")

                        list.add(Course(start, end, obj.optString("name"), obj.optString("room")))
                    }
                    list.sortBy { it.startTime }
                    fullSchedule[dayKey] = list
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(uiUpdateReceiver) } catch (e: Exception) {}
    }
}