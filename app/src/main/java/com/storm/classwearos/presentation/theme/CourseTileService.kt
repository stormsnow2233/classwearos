package com.storm.classwearos.presentation

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CourseTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val nextCourse = getNextCourse()

        val rootLayout = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(nextCourse.first)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(nextCourse.second)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(13f)).build())
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder().setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(rootLayout).build()
                    ).build()
                ).build()
            ).build()

        return Futures.immediateFuture(tile)
    }

    private fun getNextCourse(): Pair<String, String> {
        val file = File(filesDir, "schedule.json")
        if (!file.exists()) return "无数据" to "请先在网页编辑"

        try {
            val json = JSONObject(file.readText())
            val cal = Calendar.getInstance()
            val dayKey = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "1"; Calendar.TUESDAY -> "2"; Calendar.WEDNESDAY -> "3"
                Calendar.THURSDAY -> "4"; Calendar.FRIDAY -> "5"; Calendar.SATURDAY -> "6"
                Calendar.SUNDAY -> "7"; else -> "1"
            }

            val courses = json.optJSONArray(dayKey) ?: return "今天没课" to "享受时光吧"
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            var foundCourse: JSONObject? = null
            for (i in 0 until courses.length()) {
                val c = courses.getJSONObject(i)
                if (c.optString("startTime") > now) {
                    foundCourse = c
                    break
                }
            }

            return if (foundCourse != null) {
                foundCourse.optString("name") to "${foundCourse.optString("startTime")} @ ${foundCourse.optString("room")}"
            } else {
                "下课啦" to "今天的课已结束"
            }
        } catch (e: Exception) {
            return "解析错误" to "请检查格式"
        }
    }
}