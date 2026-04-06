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
        startReminderLoop()
        return START_STICKY
    }

    private fun startReminderLoop() {
        thread {
            while (true) {
                val file = File(filesDir, "schedule.json")
                if (file.exists()) {
                    try {
                        val json = JSONObject(file.readText())
                        val now = Calendar.getInstance()

                        now.add(Calendar.MINUTE, 5)
                        val targetTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)

                        // 获取今天是周几
                        var dayNum = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
                        if (dayNum == 0) dayNum = 7

                        val todayCourses = json.optJSONArray(dayNum.toString())
                        if (todayCourses != null) {
                            for (i in 0 until todayCourses.length()) {
                                val course = todayCourses.getJSONObject(i)
                                if (course.optString("startTime") == targetTime) {
                                    sendNotification(course.optString("name"), course.optString("room"))
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                Thread.sleep(60000) // 每分钟检查一次
            }
        }
    }

    private fun sendNotification(name: String, room: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "course_alert"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "上课提醒", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("该去上课了！")
            .setContentText("下一节: $name ($room)")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // 震动反馈
            .build()

        manager.notify(1002, notification)
    }

    private fun startForegroundSync() {
        val chanId = "sync_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(chanId, "同步服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notification = Notification.Builder(this, chanId)
            .setContentTitle("课表同步中")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1001, notification)
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
                            val reader = socket.getInputStream().bufferedReader()
                            val firstLine = reader.readLine() ?: ""

                            if (firstLine.startsWith("GET / ")) {
                                val localFile = File(filesDir, "schedule.json")
                                val currentData = if (localFile.exists()) localFile.readText() else "{}"
                                val responseHtml = EDITOR_HTML.replace("VAR_LOCAL_DATA", currentData)
                                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$responseHtml"
                                socket.getOutputStream().write(response.toByteArray())
                            }
                            else if (firstLine.contains("data=")) {
                                val dataPart = firstLine.substringAfter("data=").substringBefore(" ")
                                val json = URLDecoder.decode(dataPart, "UTF-8")

                                File(filesDir, "schedule.json").writeText(json)

                                val updateIntent = Intent("com.storm.classwearos.UPDATE_UI")
                                updateIntent.setPackage(packageName) // 确保只发给自己的 APP
                                sendBroadcast(updateIntent)

                                val okResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK"
                                socket.getOutputStream().write(okResponse.toByteArray())
                            }
                        } finally { socket.close() }
                    }
                }
            } catch (e: Exception) {
                server?.close()
                Thread.sleep(2000)
                startWatchServer()
            }
        }
    }

    companion object {
        private val EDITOR_HTML = """
        <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <link rel="stylesheet" href="https://fastly.jsdelivr.net/npm/vant@4/lib/index.css">
        <script src="https://fastly.jsdelivr.net/npm/vue@3"></script>
        <script src="https://fastly.jsdelivr.net/npm/vant@4/lib/vant.min.js"></script>
        <style>body{background:#f7f8fa;padding-bottom:100px;}.card{margin:10px;background:#fff;padding:15px;border-radius:12px;box-shadow:0 2px 5px rgba(0,0,0,0.05)}</style>
        </head><body><div id="app">
            <van-nav-bar title="编辑器" sticky></van-nav-bar>
            <van-tabs v-model:active="activeTab" color="#1989fa" sticky>
                <van-tab v-for="day in 7" :title="'周'+'一二三四五六日'[day-1]" :name="String(day)">
                    <div v-for="(course, i) in schedule[day]" :key="i" class="card">
                        <van-row gutter="10"><van-col span="12"><van-field v-model="course.startTime" label="始" type="time"></van-field></van-col>
                        <van-col span="12"><van-field v-model="course.endTime" label="末" type="time"></van-field></van-col></van-row>
                        <van-field v-model="course.name" label="课名" placeholder="课名"></van-field>
                        <van-field v-model="course.room" label="教师" placeholder="教师"></van-field>
                        <div style="text-align:right"><van-button size="small" plain type="danger" @click="schedule[day].splice(i,1)">删除</van-button></div>
                    </div>
                    <div style="padding:20px"><van-button block plain type="primary" @click="add(day)">+ 添加</van-button></div>
                </van-tab>
            </van-tabs>
            <div style="position:fixed;bottom:0;left:0;right:0;padding:15px;background:#fff;z-index:99"><van-button block type="primary" round @click="save">保存到手表</van-button></div>
        </div><script>
            const { createApp, ref } = Vue;
            createApp({
                setup() {
                    const activeTab = ref("1");
                    const raw = VAR_LOCAL_DATA;
                    const schedule = ref({});
                    for(let i=1;i<=7;i++) schedule.value[i] = raw[i] || [];
                    const add = (day) => schedule.value[day].push({startTime:'08:00', endTime:'09:40', name:'', room:''});
                    const save = async () => {
                        vant.showLoadingToast({message:'同步中...'});
                        try {
                            const d = encodeURIComponent(JSON.stringify(schedule.value));
                            const res = await fetch('/?data=' + d);
                            if(res.ok) vant.showSuccessToast('同步成功！');
                        } catch(e) { vant.showFailToast('失败'); }
                    };
                    return { activeTab, schedule, add, save };
                }
            }).use(vant).mount('#app');
        </script></body></html>
        """.trimIndent()
    }
}