package com.storm.classwearos.presentation

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import java.io.File
import java.net.ServerSocket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import kotlin.concurrent.thread

class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSync()
        startWatchServer()
        startReminderTimer()
        return START_STICKY
    }


    private fun startReminderTimer() {
        thread {
            while (true) {
                try {
                    checkAndNotifyIncomingCourse()
                    Thread.sleep(60000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkAndNotifyIncomingCourse() {
        val file = File(filesDir, "schedule.json")
        if (!file.exists()) return

        val calendar = Calendar.getInstance()
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "1"
            Calendar.TUESDAY -> "2"
            Calendar.WEDNESDAY -> "3"
            Calendar.THURSDAY -> "4"
            Calendar.FRIDAY -> "5"
            Calendar.SATURDAY -> "6"
            Calendar.SUNDAY -> "7"
            else -> "1"
        }

        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        calendar.add(Calendar.MINUTE, 5)
        val targetTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

        try {
            val json = JSONObject(file.readText())
            val todayCourses = json.optJSONArray(dayOfWeek) ?: return

            for (i in 0 until todayCourses.length()) {
                val course = todayCourses.getJSONObject(i)
                val courseTime = course.getString("time") // 格式如 "08:00"

                if (courseTime == targetTime) {
                    sendCourseNotification(
                        course.getString("name"),
                        course.getString("room"),
                        courseTime
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendCourseNotification(name: String, room: String, time: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "course_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("快上课啦！")
                .setContentText("${time} 在 ${room} 上 ${name}")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("快上课啦！")
                .setContentText("${time} 在 ${room} 上 ${name}")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        }

        notificationManager.notify(name.hashCode(), notification)
    }

    private fun startForegroundSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("sync", "同步保活", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
            
            val notification = Notification.Builder(this, "sync")
                .setContentTitle("课表同步中")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, notification)
            }
        }
    }

    private fun startWatchServer() {
        thread {
            var server: ServerSocket? = null
            try {
                server = ServerSocket(8080)
                while (true) {
                    val socket = server.accept()
                    thread {
                        try {
                            val inputStream = socket.getInputStream()
                            val reader = inputStream.bufferedReader()

                            val firstLine = reader.readLine() ?: ""
                            android.util.Log.d("SyncServer", "收到请求: ${firstLine.take(50)}...")

                            if (firstLine.startsWith("GET / ")) {
                                // 发送网页编辑器
                                val localFile = File(filesDir, "schedule.json")
                                val currentData = if (localFile.exists()) localFile.readText() else "{}"
                                val responseHtml = EDITOR_HTML.replace("VAR_LOCAL_DATA", currentData)
                                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$responseHtml"
                                socket.getOutputStream().write(response.toByteArray())
                            }
                            else if (firstLine.contains("data=")) {
                                // 提取数据
                                val dataPart = firstLine.substringAfter("data=").substringBefore(" ")
                                val json = URLDecoder.decode(dataPart, "UTF-8")

                                android.util.Log.d("SyncServer", "正在保存数据...")
                                File(filesDir, "schedule.json").writeText(json)

                                val updateIntent = Intent("com.storm.classwearos.UPDATE_UI").apply {
                                    putExtra("json", json)
                                    setPackage(packageName) // 关键：指定自己包名
                                }
                                sendBroadcast(updateIntent)

                                // 给手机反馈
                                val okResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK"
                                socket.getOutputStream().write(okResponse.toByteArray())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            socket.close()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncServer", "服务器崩溃: ${e.message}")
                server?.close()
                Thread.sleep(3000)
                startWatchServer() // 崩溃重启
            }
        }
    }

    private fun handleGetRequest(socket: java.net.Socket) {
        val localFile = File(filesDir, "schedule.json")
        val currentData = if (localFile.exists()) localFile.readText() else "{}"

        // 注入数据到网页
        val finalHtml = EDITOR_HTML.replace("VAR_LOCAL_DATA", currentData)

        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$finalHtml"
        socket.getOutputStream().write(response.toByteArray())
    }

    companion object {
        private val EDITOR_HTML = """
        <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <title>课表同步</title>
        <link rel="stylesheet" href="https://fastly.jsdelivr.net/npm/vant@4/lib/index.css">
        <script src="https://fastly.jsdelivr.net/npm/vue@3"></script>
        <script src="https://fastly.jsdelivr.net/npm/vant@4/lib/vant.min.js"></script>
        <style>
            body{background:#f7f8fa;padding-bottom:100px;font-family: sans-serif;}
            .card{margin:10px;background:#fff;padding:15px;border-radius:12px;box-shadow:0 2px 5px rgba(0,0,0,0.05)}
            .time-input{border:1px solid #eee;border-radius:4px;padding:5px;font-size:14px;width:80px;}
            .van-nav-bar{background:#1989fa !important;}
            .van-nav-bar__title{color:white !important;}
        </style>
        </head>
        <body>
        <div id="app">
            <van-nav-bar title="课表编辑器" sticky></van-nav-bar>
            
            <van-tabs v-model:active="activeTab" color="#1989fa" sticky offset-top="46" animated swipeable>
                <van-tab v-for="day in 7" :title="'周'+'一二三四五六日'[day-1]" :name="day">
                    <div v-for="(course, index) in schedule[day]" :key="index" class="card">
                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                            <input type="time" v-model="course.time" class="time-input">
                            <van-button size="mini" type="danger" icon="delete-o" @click="schedule[day].splice(index,1)">删除</van-button>
                        </div>
                        <van-field v-model="course.name" label="课程" placeholder="输入课程名" label-width="50px" clearable></van-field>
                        <van-field v-model="course.room" label="教室" placeholder="输入教师名字" label-width="50px" clearable></van-field>
                    </div>
                    
                    <div v-if="!schedule[day] || schedule[day].length === 0" style="text-align:center;padding:40px;color:#999;">
                        点下面添加课程吧
                    </div>

                    <div style="padding:20px">
                        <van-button block plain type="primary" icon="plus" @click="addCourse(day)">添加课程</van-button>
                    </div>
                </van-tab>
            </van-tabs>

            <div style="position:fixed;bottom:0;left:0;right:0;padding:15px;background:white;box-shadow:0 -2px 10px rgba(0,0,0,0.05);z-index:999">
                <van-button block type="primary" @click="saveData" round shadow icon="upgrade">保存并同步到手表</van-button>
            </div>
        </div>

        <script>
        const { createApp, ref } = Vue;
        createApp({
            setup() {
                const activeTab = ref(1);
                const rawData = VAR_LOCAL_DATA || {};
                
                const initialSchedule = {};
                for (let i = 1; i <= 7; i++) {
                    initialSchedule[i] = rawData[i] || [];
                }
                const schedule = ref(initialSchedule);
                
                const addCourse = (day) => {
                    if (!schedule.value[day]) {
                        schedule.value[day] = [];
                    }
                    schedule.value[day].push({ time: '08:00', name: '', room: '' });
                };

                const saveData = async () => {
                    const toast = vant.showLoadingToast({
                        message: '同步中...',
                        forbidClick: true,
                        duration: 0
                    });

                    try {
                        const dataStr = encodeURIComponent(JSON.stringify(schedule.value));
                        const response = await fetch('/?data=' + dataStr);
                        if (response.ok) {
                            vant.showSuccessToast('同步成功！');
                        } else {
                            throw new Error('Server Error');
                        }
                    } catch (error) {
                        vant.showFailToast('同步失败，请检查网络');
                        console.error(error);
                    } finally {
                        toast.close();
                    }
                };

                return { activeTab, schedule, addCourse, saveData };
            }
        }).use(vant).mount('#app');
        </script>
        </body></html>
    """.trimIndent()
    }
}
