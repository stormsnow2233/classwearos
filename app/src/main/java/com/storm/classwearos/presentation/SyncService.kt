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
        val channel = NotificationChannel(channelId, "课程同步", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ClassWear服务运行中")
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

    private val EDITOR_HTML = """
        <!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
    <link rel="stylesheet" href="https://fastly.jsdelivr.net/npm/vant@4/lib/index.css">
    <script src="https://fastly.jsdelivr.net/npm/vue@3"></script>
    <script src="https://fastly.jsdelivr.net/npm/vant@4/lib/vant.min.js"></script>
    <style>
        :root {
            --van-primary-color: #00b4d8;
            --bg-color: #f5f7fa;
            --card-bg: rgba(255, 255, 255, 0.85);
            --text-primary: #1e293b;
            --text-secondary: #64748b;
            --border-light: rgba(0, 0, 0, 0.05);
        }

        body {
            background: linear-gradient(145deg, #eef2f6 0%, #f8fafc 100%);
            color: var(--text-primary);
            padding-bottom: 80px;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            margin: 0;
            min-height: 100vh;
            backdrop-filter: blur(10px);
        }

        .header {
            background: rgba(255, 255, 255, 0.7);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            padding: 28px 20px;
            border-radius: 0 0 32px 32px;
            margin-bottom: 16px;
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.02);
            border-bottom: 1px solid rgba(255, 255, 255, 0.5);
        }

        .header h2 {
            margin: 0;
            font-weight: 600;
            background: linear-gradient(135deg, #0077b6, #00b4d8);
            -webkit-background-clip: text;
            background-clip: text;
            color: transparent;
        }

        .card {
            background: var(--card-bg);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            margin: 12px 16px;
            padding: 16px;
            border-radius: 20px;
            border: 1px solid var(--border-light);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
        }

        .van-field {
            background: transparent !important;
            --van-field-input-text-color: var(--text-primary);
            --van-field-label-color: var(--text-secondary);
        }

        .van-tabs__wrap {
            background: transparent !important;
        }

        .van-tab {
            color: var(--text-secondary) !important;
        }

        .van-tab--active {
            color: var(--van-primary-color) !important;
            font-weight: 500;
        }

        .save-btn {
            position: fixed;
            bottom: 20px;
            left: 16px;
            right: 16px;
            box-shadow: 0 8px 20px rgba(0, 180, 216, 0.2);
            border-radius: 60px !important;
            height: 52px;
            font-weight: 600;
            backdrop-filter: blur(10px);
            background: rgba(0, 180, 216, 0.9) !important;
            border: 1px solid rgba(255, 255, 255, 0.3);
        }

        .toolbar {
            display: flex;
            gap: 8px;
            padding: 8px 16px;
            flex-wrap: wrap;
        }

        .toolbar .van-button {
            background: rgba(255, 255, 255, 0.6) !important;
            backdrop-filter: blur(8px);
            border: 1px solid rgba(0, 0, 0, 0.05);
            border-radius: 40px;
            font-size: 13px;
            color: var(--text-primary);
        }

        .conflict-badge {
            background: #ff6b6b;
            color: white;
            border-radius: 40px;
            padding: 2px 10px;
            font-size: 12px;
            margin-left: 8px;
        }

        .status-dot {
            display: inline-block;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            margin-right: 6px;
        }

        .status-online {
            background: #10b981;
            box-shadow: 0 0 12px #10b981;
        }

        .status-offline {
            background: #94a3b8;
        }

        /* 浅色模式下的标签优化 */
        .van-tag--primary {
            background: rgba(0, 180, 216, 0.15) !important;
            color: #0077b6 !important;
            backdrop-filter: blur(4px);
        }

        /* 弹窗背景 */
        .van-popup, .van-action-sheet {
            background: rgba(255, 255, 255, 0.95) !important;
            backdrop-filter: blur(20px);
        }
    </style>
</head>
<body>
<div id="app">
    <div class="header">
        <div style="display: flex; align-items: center; justify-content: space-between;">
            <h2>📱 Class OS</h2>
            <van-tag round size="large">
                <span :class="['status-dot', isOnline ? 'status-online' : 'status-offline']"></span>
                {{ isOnline ? '已连接' : '未连接' }}
            </van-tag>
        </div>
        <p style="margin:6px 0 0; opacity:0.7; font-size:14px; color: var(--text-secondary);">实时同步 · 轻触编辑</p>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
        <van-button size="small" plain @click="copyToday">📋 复制今日</van-button>
        <van-button size="small" plain @click="exportData">💾 导出</van-button>
        <van-button size="small" plain @click="importData">📂 导入</van-button>
        <van-button size="small" plain @click="checkConflicts">⚠️ 冲突</van-button>
        <van-button size="small" plain @click="showDefaultSettings = true">⏱️ 默认</van-button>
    </div>

    <!-- 课表标签页 -->
    <van-tabs v-model:active="activeTab" background="transparent" color="#00b4d8" title-active-color="#0077b6" swipeable>
        <van-tab v-for="day in 7" :title="'周'+'一二三四五六日'[day-1]" :name="String(day)">
            <div v-for="(item, i) in schedule[day]" :key="i" class="card">
                <van-row gutter="10">
                    <van-col span="12"><van-field v-model="item.startTime" label="开始" type="time"></van-field></van-col>
                    <van-col span="12"><van-field v-model="item.endTime" label="结束" type="time"></van-field></van-col>
                </van-row>
                <van-field v-model="item.name" placeholder="课程名称" label="课程"></van-field>
                <van-field v-model="item.room" placeholder="教室地点" label="地点"></van-field>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 8px;">
                    <van-tag v-if="hasConflict(day, i)" type="danger" size="medium">⚠️ 时间冲突</van-tag>
                    <van-button size="mini" plain type="danger" @click="schedule[day].splice(i,1)">删除</van-button>
                </div>
            </div>
            <div style="padding:8px 16px 16px">
                <van-button block dashed @click="add(day)">+ 添加课程</van-button>
            </div>
        </van-tab>
    </van-tabs>

    <van-button class="save-btn" block round @click="save" :loading="saving">保存并同步到手表</van-button>

    <!-- 复制今日弹窗 -->
    <van-action-sheet v-model:show="showCopySheet" title="复制到星期">
        <div style="padding: 20px;">
            <van-checkbox-group v-model="copyTargetDays" direction="horizontal">
                <van-checkbox v-for="d in 7" :name="d" shape="square">{{ '周'+'一二三四五六日'[d-1] }}</van-checkbox>
            </van-checkbox-group>
            <div style="margin-top: 24px; display: flex; gap: 12px;">
                <van-button block @click="showCopySheet = false">取消</van-button>
                <van-button block type="primary" @click="doCopyToday">确认复制</van-button>
            </div>
        </div>
    </van-action-sheet>

    <!-- 默认时长设置弹窗 -->
    <van-popup v-model:show="showDefaultSettings" round position="bottom" :style="{ height: '30%' }">
        <div style="padding: 24px;">
            <h4 style="margin-top:0">默认课程时长</h4>
            <van-field v-model="defaultDuration" type="number" placeholder="例如 45" label="分钟">
                <template #button>
                    <van-button size="small" type="primary" @click="setDefaultDuration">保存</van-button>
                </template>
            </van-field>
            <p style="color: var(--text-secondary); font-size: 13px;">添加课程时自动计算结束时间</p>
        </div>
    </van-popup>

    <input type="file" ref="fileInput" style="display: none" accept=".json,application/json" @change="handleFileImport">
</div>

<script>
    const { createApp, ref, reactive } = Vue;
    // 服务端注入初始数据（若未注入则默认为空对象）
    const LOCAL_DATA = (typeof VAR_LOCAL_DATA !== 'undefined') ? VAR_LOCAL_DATA : {};

    const app = createApp({
        setup() {
            const activeTab = ref("1");
            const schedule = reactive({});
            const saving = ref(false);
            const isOnline = ref(false);
            const showCopySheet = ref(false);
            const copyTargetDays = ref([]);
            const showDefaultSettings = ref(false);
            const defaultDuration = ref(localStorage.getItem('defaultDuration') || '45');
            const fileInput = ref(null);

            // 初始化数据
            for (let i = 1; i <= 7; i++) {
                schedule[i] = (LOCAL_DATA[i] || []).map(c => ({ ...c }));
            }

            // 检查手表连接状态
            const checkOnline = async () => {
                try {
                    const res = await fetch('/ping', { method: 'HEAD', cache: 'no-cache' });
                    isOnline.value = res.ok;
                } catch { isOnline.value = false; }
            };
            checkOnline();
            setInterval(checkOnline, 5000);

            // 添加课程（使用默认时长）
            const add = (day) => {
                const newCourse = { startTime: '08:00', endTime: '', name: '', room: '' };
                const mins = parseInt(defaultDuration.value) || 45;
                const [h, m] = newCourse.startTime.split(':').map(Number);
                const totalMins = h * 60 + m + mins;
                const endH = Math.floor(totalMins / 60) % 24;
                const endM = totalMins % 60;
                newCourse.endTime = `${'$'}{String(endH).padStart(2,'0')}:${'$'}{String(endM).padStart(2,'0')}`;
                schedule[day].push(newCourse);
            };

            // 冲突检测（单个课程）
            const hasConflict = (day, index) => {
                const courses = schedule[day];
                const c = courses[index];
                if (!c.startTime || !c.endTime) return false;
                for (let i = 0; i < courses.length; i++) {
                    if (i === index) continue;
                    const o = courses[i];
                    if (!o.startTime || !o.endTime) continue;
                    if (c.startTime < o.endTime && c.endTime > o.startTime) return true;
                }
                return false;
            };

            // 全局冲突检查
            const checkConflicts = () => {
                let conflicts = [];
                for (let d = 1; d <= 7; d++) {
                    schedule[d].forEach((c, i) => {
                        if (hasConflict(d, i)) conflicts.push(`周${'$'}{'一二三四五六日'[d-1]} ${'$'}{c.name || '未命名'} (${'$'}{c.startTime}-${'$'}{c.endTime})`);
                    });
                }
                if (conflicts.length) {
                    vant.showDialog({
                        title: '时间冲突警告',
                        message: conflicts.join('\n'),
                        confirmButtonText: '知道了'
                    });
                } else {
                    vant.showToast({ message: '✅ 没有时间冲突', position: 'top' });
                }
            };

            // 复制今日
            const copyToday = () => {
                const today = new Date().getDay() || 7;
                if (!schedule[today]?.length) {
                    vant.showToast('今日没有课程可复制');
                    return;
                }
                copyTargetDays.value = [];
                showCopySheet.value = true;
            };
            const doCopyToday = () => {
                const today = new Date().getDay() || 7;
                const source = schedule[today];
                copyTargetDays.value.forEach(d => {
                    if (d !== today) {
                        schedule[d] = source.map(c => ({ ...c }));
                    }
                });
                showCopySheet.value = false;
                vant.showToast('复制成功');
            };

            // 导出
            const exportData = () => {
                const dataStr = JSON.stringify(schedule, null, 2);
                const blob = new Blob([dataStr], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `schedule_${'$'}{new Date().toISOString().slice(0,10)}.json`;
                a.click();
                URL.revokeObjectURL(url);
            };

            // 导入
            const importData = () => fileInput.value.click();
            const handleFileImport = (e) => {
                const file = e.target.files[0];
                if (!file) return;
                const reader = new FileReader();
                reader.onload = (ev) => {
                    try {
                        const imported = JSON.parse(ev.target.result);
                        for (let i = 1; i <= 7; i++) {
                            if (imported[i]) schedule[i] = imported[i].map(c => ({ ...c }));
                        }
                        vant.showToast('导入成功');
                    } catch (ex) { vant.showToast('文件格式错误'); }
                };
                reader.readAsText(file);
                fileInput.value.value = '';
            };

            // 保存
            const save = async () => {
                saving.value = true;
                const toast = vant.showLoadingToast({ message: '同步中...', forbidClick: true });
                try {
                    const res = await fetch('/?data=' + encodeURIComponent(JSON.stringify(schedule)), { method: 'POST' });
                    if (res.ok) vant.showSuccessToast('同步成功');
                    else throw new Error();
                } catch { vant.showFailToast('同步失败'); }
                finally { toast.close(); saving.value = false; }
            };

            // 默认时长设置
            const setDefaultDuration = () => {
                localStorage.setItem('defaultDuration', defaultDuration.value);
                showDefaultSettings.value = false;
                vant.showToast('默认时长已保存');
            };

            return {
                activeTab, schedule, saving, isOnline,
                showCopySheet, copyTargetDays, showDefaultSettings, defaultDuration, fileInput,
                add, hasConflict, checkConflicts, copyToday, doCopyToday,
                exportData, importData, handleFileImport, save, setDefaultDuration
            };
        }
    });

    app.use(vant).mount('#app');
</script>
</body>
</html>
    """.trimIndent()
}
