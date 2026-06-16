package com.susking.ephone_s.health.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.HealthDailyRecordEntity
import com.susking.ephone_s.health.databinding.ItemHealthDayBinding
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 每日健康卡片列表适配器。展示步数/睡眠/心率主指标 + 距离/消耗/静息心率次要行。
 * 点击卡片跳转到睡眠详情页。
 */
class HealthDayAdapter(
    private val onCardClick: (String) -> Unit
) : ListAdapter<HealthDailyRecordEntity, HealthDayAdapter.DayViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemHealthDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DayViewHolder(binding, onCardClick)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemHealthDayBinding,
        private val onCardClick: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: HealthDailyRecordEntity) {
            // 设置卡片点击事件，传入日期
            binding.root.setOnClickListener {
                onCardClick(record.date)
            }
            binding.textDate.text = formatDate(record.date)
            binding.textStepsValue.text = record.steps.toString()

            // 睡眠：显示时间段（上）+ 总时长（下）
            val sleepStart = record.sleepStartTime
            val sleepEnd = record.sleepEndTime
            if (sleepStart != null && sleepEnd != null) {
                binding.textSleepTime.text = formatSleepTimeRange(sleepStart, sleepEnd)
                binding.textSleepDuration.text = formatSleepDuration(record.sleepTotalMinutes)
            } else {
                binding.textSleepTime.text = "--"
                binding.textSleepDuration.text = "--"
            }

            binding.textHeartValue.text = record.heartRateAvg?.toString() ?: "--"
            binding.textSecondary.text = buildSecondaryLine(record)
        }

        /** 把 yyyy-MM-dd 转成 MM-dd，并对今天/昨天加中文标签。 */
        private fun formatDate(date: String): String {
            return runCatching {
                val day: LocalDate = LocalDate.parse(date)
                val today: LocalDate = LocalDate.now()
                val suffix: String = when (day) {
                    today -> " 今天"
                    today.minusDays(1) -> " 昨天"
                    else -> ""
                }
                "${day.monthValue.pad()}-${day.dayOfMonth.pad()}$suffix"
            }.getOrDefault(date)
        }

        /** 睡眠时间段："14:02 - 19:40"。 */
        private fun formatSleepTimeRange(startMillis: Long, endMillis: Long): String {
            return runCatching {
                val zone = java.time.ZoneId.systemDefault()
                val start = java.time.Instant.ofEpochMilli(startMillis).atZone(zone)
                val end = java.time.Instant.ofEpochMilli(endMillis).atZone(zone)
                "${start.hour.pad()}:${start.minute.pad()} - ${end.hour.pad()}:${end.minute.pad()}"
            }.getOrDefault("--")
        }

        /** 分钟数转 "Xh Ym"；无睡眠返回 "--"。 */
        private fun formatSleepDuration(totalMinutes: Long): String {
            if (totalMinutes <= 0) return "--"
            val hours: Long = totalMinutes / 60
            val minutes: Long = totalMinutes % 60
            return "${hours}h${minutes}m"
        }

        /** 次要指标行：距离/消耗/静息心率，缺项跳过。 */
        private fun buildSecondaryLine(record: HealthDailyRecordEntity): String {
            val parts: MutableList<String> = mutableListOf()
            if (record.distanceMeters > 0) {
                val km: Double = record.distanceMeters / 1000.0
                parts.add("距离 ${(km * 10).roundToInt() / 10.0}km")
            }
            if (record.activeCaloriesKcal > 0) {
                parts.add("消耗 ${record.activeCaloriesKcal.roundToInt()}kcal")
            }
            record.restingHeartRate?.let { parts.add("静息心率 $it") }
            return if (parts.isEmpty()) "暂无更多数据" else parts.joinToString(" · ")
        }

        private fun Int.pad(): String = toString().padStart(2, '0')
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HealthDailyRecordEntity>() {
            override fun areItemsTheSame(
                oldItem: HealthDailyRecordEntity,
                newItem: HealthDailyRecordEntity,
            ): Boolean = oldItem.date == newItem.date

            override fun areContentsTheSame(
                oldItem: HealthDailyRecordEntity,
                newItem: HealthDailyRecordEntity,
            ): Boolean = oldItem == newItem
        }
    }
}
