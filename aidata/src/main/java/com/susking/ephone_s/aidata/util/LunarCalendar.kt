package com.susking.ephone_s.aidata.util

import java.util.Calendar

/**
 * 农历日历工具类
 * 用于公历与农历之间的转换，以及农历节日的判断
 * 
 * 算法参考：中国科学院紫金山天文台的农历算法
 * 支持范围：1900年 - 2100年
 */
object LunarCalendar {
    
    /**
     * 农历日期数据类
     * @param year 农历年份
     * @param month 农历月份 (1-12)
     * @param day 农历日 (1-30)
     * @param isLeapMonth 是否为闰月
     */
    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean = false
    )
    
    // 农历1900-2100年每年的天数信息
    // 前12位表示12个月（1为大月30天，0为小月29天）
    // 后4位表示闰月月份（0表示无闰月）
    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )
    
    // 1900年1月31日是农历1900年正月初一
    private const val BASE_YEAR = 1900
    private const val BASE_MONTH = 1
    private const val BASE_DAY = 31
    
    /**
     * 将公历日期转换为农历日期
     * @param year 公历年份
     * @param month 公历月份 (1-12)
     * @param day 公历日 (1-31)
     * @return 农历日期，如果超出支持范围则返回null
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate? {
        if (year < BASE_YEAR || year > 2100) {
            return null
        }
        
        // 计算从基准日期到目标日期的天数差
        val baseCalendar = Calendar.getInstance().apply {
            set(BASE_YEAR, BASE_MONTH - 1, BASE_DAY)
        }
        val targetCalendar = Calendar.getInstance().apply {
            set(year, month - 1, day)
        }
        
        val offsetDays = ((targetCalendar.timeInMillis - baseCalendar.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        
        // 从1900年开始累加，找到对应的农历年月日
        var lunarYear = BASE_YEAR
        var lunarMonth = 1
        var lunarDay = 1
        var isLeapMonth = false
        var remainingDays = offsetDays
        
        // 逐年递减天数
        while (remainingDays > 0) {
            val yearDays = getLunarYearDays(lunarYear)
            if (remainingDays < yearDays) {
                break
            }
            remainingDays -= yearDays
            lunarYear++
        }
        
        // 获取该年的闰月
        val leapMonth = getLeapMonth(lunarYear)
        var isAfterLeap = false
        
        // 逐月递减天数
        for (m in 1..12) {
            // 如果有闰月且当前月份在闰月之后
            if (leapMonth > 0 && m == leapMonth + 1 && !isAfterLeap) {
                isAfterLeap = true
                lunarMonth = m - 1
                isLeapMonth = true
                val leapMonthDays = getLeapMonthDays(lunarYear)
                if (remainingDays < leapMonthDays) {
                    break
                }
                remainingDays -= leapMonthDays
                isLeapMonth = false
            }
            
            lunarMonth = if (isAfterLeap) m else m
            val monthDays = getLunarMonthDays(lunarYear, lunarMonth)
            if (remainingDays < monthDays) {
                break
            }
            remainingDays -= monthDays
        }
        
        lunarDay = remainingDays + 1
        
        return LunarDate(lunarYear, lunarMonth, lunarDay, isLeapMonth)
    }
    
    /**
     * 获取农历某年的总天数
     */
    private fun getLunarYearDays(year: Int): Int {
        var sum = 348 // 12个月，每月最少29天
        for (i in 0x8000 downTo 0x8) {
            sum += if (LUNAR_INFO[year - BASE_YEAR] and i.toLong() != 0L) 1 else 0
        }
        return sum + getLeapMonthDays(year)
    }
    
    /**
     * 获取农历某年的闰月天数
     */
    private fun getLeapMonthDays(year: Int): Int {
        if (getLeapMonth(year) == 0) {
            return 0
        }
        return if (LUNAR_INFO[year - BASE_YEAR] and 0x10000L != 0L) 30 else 29
    }
    
    /**
     * 获取农历某年的闰月月份（0表示无闰月）
     */
    private fun getLeapMonth(year: Int): Int {
        return (LUNAR_INFO[year - BASE_YEAR] and 0xfL).toInt()
    }
    
    /**
     * 获取农历某年某月的天数
     */
    private fun getLunarMonthDays(year: Int, month: Int): Int {
        return if (LUNAR_INFO[year - BASE_YEAR] and (0x10000 shr month).toLong() != 0L) 30 else 29
    }
    
    /**
     * 判断是否为指定的农历节日
     * @param solarYear 公历年份
     * @param solarMonth 公历月份
     * @param solarDay 公历日
     * @param lunarMonth 农历月份
     * @param lunarDay 农历日
     * @return 是否匹配
     */
    fun isLunarFestival(solarYear: Int, solarMonth: Int, solarDay: Int, lunarMonth: Int, lunarDay: Int): Boolean {
        val lunar = solarToLunar(solarYear, solarMonth, solarDay) ?: return false
        return lunar.month == lunarMonth && lunar.day == lunarDay && !lunar.isLeapMonth
    }
    
    /**
     * 判断是否为农历除夕（腊月最后一天）
     */
    fun isLunarNewYearsEve(solarYear: Int, solarMonth: Int, solarDay: Int): Boolean {
        val lunar = solarToLunar(solarYear, solarMonth, solarDay) ?: return false
        if (lunar.month != 12) return false
        
        // 判断是否为腊月最后一天
        val monthDays = getLunarMonthDays(lunar.year, 12)
        return lunar.day == monthDays
    }
}