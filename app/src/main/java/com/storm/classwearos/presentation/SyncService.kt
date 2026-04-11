package com.storm.classwearos.presentation

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread

class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "SyncServiceChannel"
        val channel = NotificationChannel(channelId, "课程同步服务", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Class OS 服务运行中")
            .setContentText("可以通过网页更新课表")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()

        startForeground(1, notification)
        startWatchServer()
        return START_STICKY
    }

    private fun startWatchServer() {
        thread {
            try {
                val server = ServerSocket(8080)
                while (true) {
                    val socket = server.accept()
                    val reader = socket.getInputStream().bufferedReader()
                    val firstLine = reader.readLine() ?: continue

                    if (firstLine.contains("data=")) {
                        val data = URLDecoder.decode(firstLine.substringAfter("data=").substringBefore(" "), "UTF-8")
                        File(filesDir, "schedule.json").writeText(data)
                        // 发送广播通知 Activity 刷新
                        sendBroadcast(Intent("com.storm.classwearos.UPDATE_UI").setPackage(packageName))

                        val response = "HTTP/1.1 200 OK\r\n\r\nOK"
                        socket.getOutputStream().write(response.toByteArray())
                    } else {
                        val localData = File(filesDir, "schedule.json").let { if(it.exists()) it.readText() else "{}" }
                        val html = EDITOR_HTML.replace("VAR_LOCAL_DATA", localData)
                        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n$html"
                        socket.getOutputStream().write(response.toByteArray())
                    }
                    socket.close()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 网页编辑器：深色模式 + Vant UI 组件库
    private val EDITOR_HTML = """
        <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <link rel="stylesheet" href="https://fastly.jsdelivr.net/npm/vant@4/lib/index.css">
        <script src="https://fastly.jsdelivr.net/npm/vue@3"></script><script src="https://fastly.jsdelivr.net/npm/vant@4/lib/vant.min.js"></script>
        <style>
            :root { --van-primary-color: #00e5ff; --van-background-2: #1a1a1a; }
            body { background: #0a0a0a; color: #fff; padding-bottom: 80px; font-family: -apple-system, sans-serif; }
            .header { background: linear-gradient(135deg, #00c2ff, #00e5ff); padding: 30px 20px; border-radius: 0 0 20px 20px; margin-bottom: 20px; }
            .card { background: #1e1e1e; margin: 12px; padding: 15px; border-radius: 12px; border: 1px solid #333; }
            .van-field { background: transparent !important; --van-field-input-text-color: #fff; }
            .save-btn { position: fixed; bottom: 20px; left: 15px; right: 15px; box-shadow: 0 4px 12px rgba(0,229,255,0.4); }
        </style>
        </head><body><div id="app">
            <div class="header"><h2>Class OS 编辑器</h2><p>由 d 开发 · 实时同步至手表</p></div>
            <van-tabs v-model:active="activeTab" background="transparent" color="#00e5ff" title-active-color="#00e5ff">
                <van-tab v-for="day in 7" :title="'周'+'一二三四五六日'[day-1]" :name="String(day)">
                    <div v-for="(item, i) in schedule[day]" :key="i" class="card">
                        <van-row gutter="10">
                            <van-col span="12"><van-field v-model="item.startTime" label="开始" type="time"></van-field></van-col>
                            <van-col span="12"><van-field v-model="item.endTime" label="结束" type="time"></van-field></van-col>
                        </van-row>
                        <van-field v-model="item.name" placeholder="课程名称" label="课程"></van-field>
                        <van-field v-model="item.room" placeholder="教室地点" label="地点"></van-field>
                        <div style="text-align:right"><van-button size="mini" plain type="danger" @click="schedule[day].splice(i,1)">删除</van-button></div>
                    </div>
                    <div style="padding:15px"><van-button block dashed type="primary" @click="add(day)">+ 添加课程</van-button></div>
                </van-tab>
            </van-tabs>
            <van-button class="save-btn" block type="primary" round @click="save">保存并同步</van-button>
        </div><script>
            const { createApp, ref } = Vue;
            createApp({
                setup() {
                    const activeTab = ref("1"); const schedule = ref({});
                    const raw = VAR_LOCAL_DATA;
                    for(let i=1; i<=7; i++) schedule.value[i] = raw[i] || [];
                    const add = (day) => schedule.value[day].push({startTime:'08:00', endTime:'09:40', name:'', room:''});
                    const save = async () => {
                        const t = vant.showLoadingToast({message:'同步中...'});
                        try {
                            const res = await fetch('/?data=' + encodeURIComponent(JSON.stringify(schedule.value)));
                            if(res.ok) vant.showSuccessToast('同步成功');
                        } catch(e) { vant.showFailToast('失败'); }
                    };
                    return { activeTab, schedule, add, save };
                }
            }).use(vant).mount('#app');
        </script></body></html>
    """.trimIndent()
}
