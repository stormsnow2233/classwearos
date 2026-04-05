package com.storm.classwearos.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class Course(val time: String, val name: String, val room: String)

class MainActivity : ComponentActivity() {
    private var statusMessage by mutableStateOf("服务运行中")
    private var lastSyncTime by mutableStateOf("尚未同步")
    private val fullSchedule = mutableStateMapOf<String, List<Course>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动同步服务
        startSyncForegroundService()
        refreshScheduleData()

        val filter = IntentFilter("com.storm.classwearos.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiUpdateReceiver, filter)
        }

        setContent {
            val pagerState = rememberPagerState(pageCount = { 7 })

            MaterialTheme {
                AppScaffold {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState) { page ->
                            val dayIndex = (page + 1).toString()
                            val dayCourses = fullSchedule[dayIndex] ?: emptyList()

                            ScreenScaffold {
                                ScalingLazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp)
                                ) {
                                    item {
                                        ListHeader {
                                            Text("星期" + "一二三四五六日"[page], color = Color(0xFF7FCFFF))
                                        }
                                    }

                                    if (dayCourses.isEmpty()) {
                                        item {
                                            Text("暂无课程，请同步", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    } else {
                                        items(dayCourses) { course ->
                                            CourseCard(course)
                                        }
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("$statusMessage | $lastSyncTime", fontSize = 8.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalPageIndicator(
                            pagerState = pagerState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
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
            subtitle = { Text(course.room, color = Color(0xFF7FCFFF), fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        ) {
            Text(text = course.time, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
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
                        list.add(Course(obj.optString("time"), obj.optString("name"), obj.optString("room")))
                    }
                    fullSchedule[dayKey] = list.sortedBy { it.time }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startSyncForegroundService() {
        val intent = Intent(this, SyncService::class.java)
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(uiUpdateReceiver)
    }
}