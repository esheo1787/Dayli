package com.silverwest.dayli.ddaywidget

import com.silverwest.dayli.R

// 생성 파일: tools/gen_calendar_widget_layouts.py 로 다시 만들 것 (직접 수정 금지)

data class AgendaRowIds(val row: Int, val dot: Int, val time: Int, val title: Int)

object CalendarWidgetIds {
    val cells = intArrayOf(R.id.cal_cell_0, R.id.cal_cell_1, R.id.cal_cell_2, R.id.cal_cell_3, R.id.cal_cell_4, R.id.cal_cell_5, R.id.cal_cell_6, R.id.cal_cell_7, R.id.cal_cell_8, R.id.cal_cell_9, R.id.cal_cell_10, R.id.cal_cell_11, R.id.cal_cell_12, R.id.cal_cell_13, R.id.cal_cell_14, R.id.cal_cell_15, R.id.cal_cell_16, R.id.cal_cell_17, R.id.cal_cell_18, R.id.cal_cell_19, R.id.cal_cell_20, R.id.cal_cell_21, R.id.cal_cell_22, R.id.cal_cell_23, R.id.cal_cell_24, R.id.cal_cell_25, R.id.cal_cell_26, R.id.cal_cell_27, R.id.cal_cell_28, R.id.cal_cell_29, R.id.cal_cell_30, R.id.cal_cell_31, R.id.cal_cell_32, R.id.cal_cell_33, R.id.cal_cell_34, R.id.cal_cell_35, R.id.cal_cell_36, R.id.cal_cell_37, R.id.cal_cell_38, R.id.cal_cell_39, R.id.cal_cell_40, R.id.cal_cell_41)
    val nums = intArrayOf(R.id.cal_num_0, R.id.cal_num_1, R.id.cal_num_2, R.id.cal_num_3, R.id.cal_num_4, R.id.cal_num_5, R.id.cal_num_6, R.id.cal_num_7, R.id.cal_num_8, R.id.cal_num_9, R.id.cal_num_10, R.id.cal_num_11, R.id.cal_num_12, R.id.cal_num_13, R.id.cal_num_14, R.id.cal_num_15, R.id.cal_num_16, R.id.cal_num_17, R.id.cal_num_18, R.id.cal_num_19, R.id.cal_num_20, R.id.cal_num_21, R.id.cal_num_22, R.id.cal_num_23, R.id.cal_num_24, R.id.cal_num_25, R.id.cal_num_26, R.id.cal_num_27, R.id.cal_num_28, R.id.cal_num_29, R.id.cal_num_30, R.id.cal_num_31, R.id.cal_num_32, R.id.cal_num_33, R.id.cal_num_34, R.id.cal_num_35, R.id.cal_num_36, R.id.cal_num_37, R.id.cal_num_38, R.id.cal_num_39, R.id.cal_num_40, R.id.cal_num_41)
    val todayBgs = intArrayOf(R.id.cal_today_0, R.id.cal_today_1, R.id.cal_today_2, R.id.cal_today_3, R.id.cal_today_4, R.id.cal_today_5, R.id.cal_today_6, R.id.cal_today_7, R.id.cal_today_8, R.id.cal_today_9, R.id.cal_today_10, R.id.cal_today_11, R.id.cal_today_12, R.id.cal_today_13, R.id.cal_today_14, R.id.cal_today_15, R.id.cal_today_16, R.id.cal_today_17, R.id.cal_today_18, R.id.cal_today_19, R.id.cal_today_20, R.id.cal_today_21, R.id.cal_today_22, R.id.cal_today_23, R.id.cal_today_24, R.id.cal_today_25, R.id.cal_today_26, R.id.cal_today_27, R.id.cal_today_28, R.id.cal_today_29, R.id.cal_today_30, R.id.cal_today_31, R.id.cal_today_32, R.id.cal_today_33, R.id.cal_today_34, R.id.cal_today_35, R.id.cal_today_36, R.id.cal_today_37, R.id.cal_today_38, R.id.cal_today_39, R.id.cal_today_40, R.id.cal_today_41)
    val rows = intArrayOf(R.id.cal_row_0, R.id.cal_row_1, R.id.cal_row_2, R.id.cal_row_3, R.id.cal_row_4, R.id.cal_row_5)
    val weekdays = intArrayOf(R.id.cal_week_0, R.id.cal_week_1, R.id.cal_week_2, R.id.cal_week_3, R.id.cal_week_4, R.id.cal_week_5, R.id.cal_week_6)
    val dots = arrayOf(
        intArrayOf(R.id.cal_dot_0_0, R.id.cal_dot_0_1, R.id.cal_dot_0_2),
        intArrayOf(R.id.cal_dot_1_0, R.id.cal_dot_1_1, R.id.cal_dot_1_2),
        intArrayOf(R.id.cal_dot_2_0, R.id.cal_dot_2_1, R.id.cal_dot_2_2),
        intArrayOf(R.id.cal_dot_3_0, R.id.cal_dot_3_1, R.id.cal_dot_3_2),
        intArrayOf(R.id.cal_dot_4_0, R.id.cal_dot_4_1, R.id.cal_dot_4_2),
        intArrayOf(R.id.cal_dot_5_0, R.id.cal_dot_5_1, R.id.cal_dot_5_2),
        intArrayOf(R.id.cal_dot_6_0, R.id.cal_dot_6_1, R.id.cal_dot_6_2),
        intArrayOf(R.id.cal_dot_7_0, R.id.cal_dot_7_1, R.id.cal_dot_7_2),
        intArrayOf(R.id.cal_dot_8_0, R.id.cal_dot_8_1, R.id.cal_dot_8_2),
        intArrayOf(R.id.cal_dot_9_0, R.id.cal_dot_9_1, R.id.cal_dot_9_2),
        intArrayOf(R.id.cal_dot_10_0, R.id.cal_dot_10_1, R.id.cal_dot_10_2),
        intArrayOf(R.id.cal_dot_11_0, R.id.cal_dot_11_1, R.id.cal_dot_11_2),
        intArrayOf(R.id.cal_dot_12_0, R.id.cal_dot_12_1, R.id.cal_dot_12_2),
        intArrayOf(R.id.cal_dot_13_0, R.id.cal_dot_13_1, R.id.cal_dot_13_2),
        intArrayOf(R.id.cal_dot_14_0, R.id.cal_dot_14_1, R.id.cal_dot_14_2),
        intArrayOf(R.id.cal_dot_15_0, R.id.cal_dot_15_1, R.id.cal_dot_15_2),
        intArrayOf(R.id.cal_dot_16_0, R.id.cal_dot_16_1, R.id.cal_dot_16_2),
        intArrayOf(R.id.cal_dot_17_0, R.id.cal_dot_17_1, R.id.cal_dot_17_2),
        intArrayOf(R.id.cal_dot_18_0, R.id.cal_dot_18_1, R.id.cal_dot_18_2),
        intArrayOf(R.id.cal_dot_19_0, R.id.cal_dot_19_1, R.id.cal_dot_19_2),
        intArrayOf(R.id.cal_dot_20_0, R.id.cal_dot_20_1, R.id.cal_dot_20_2),
        intArrayOf(R.id.cal_dot_21_0, R.id.cal_dot_21_1, R.id.cal_dot_21_2),
        intArrayOf(R.id.cal_dot_22_0, R.id.cal_dot_22_1, R.id.cal_dot_22_2),
        intArrayOf(R.id.cal_dot_23_0, R.id.cal_dot_23_1, R.id.cal_dot_23_2),
        intArrayOf(R.id.cal_dot_24_0, R.id.cal_dot_24_1, R.id.cal_dot_24_2),
        intArrayOf(R.id.cal_dot_25_0, R.id.cal_dot_25_1, R.id.cal_dot_25_2),
        intArrayOf(R.id.cal_dot_26_0, R.id.cal_dot_26_1, R.id.cal_dot_26_2),
        intArrayOf(R.id.cal_dot_27_0, R.id.cal_dot_27_1, R.id.cal_dot_27_2),
        intArrayOf(R.id.cal_dot_28_0, R.id.cal_dot_28_1, R.id.cal_dot_28_2),
        intArrayOf(R.id.cal_dot_29_0, R.id.cal_dot_29_1, R.id.cal_dot_29_2),
        intArrayOf(R.id.cal_dot_30_0, R.id.cal_dot_30_1, R.id.cal_dot_30_2),
        intArrayOf(R.id.cal_dot_31_0, R.id.cal_dot_31_1, R.id.cal_dot_31_2),
        intArrayOf(R.id.cal_dot_32_0, R.id.cal_dot_32_1, R.id.cal_dot_32_2),
        intArrayOf(R.id.cal_dot_33_0, R.id.cal_dot_33_1, R.id.cal_dot_33_2),
        intArrayOf(R.id.cal_dot_34_0, R.id.cal_dot_34_1, R.id.cal_dot_34_2),
        intArrayOf(R.id.cal_dot_35_0, R.id.cal_dot_35_1, R.id.cal_dot_35_2),
        intArrayOf(R.id.cal_dot_36_0, R.id.cal_dot_36_1, R.id.cal_dot_36_2),
        intArrayOf(R.id.cal_dot_37_0, R.id.cal_dot_37_1, R.id.cal_dot_37_2),
        intArrayOf(R.id.cal_dot_38_0, R.id.cal_dot_38_1, R.id.cal_dot_38_2),
        intArrayOf(R.id.cal_dot_39_0, R.id.cal_dot_39_1, R.id.cal_dot_39_2),
        intArrayOf(R.id.cal_dot_40_0, R.id.cal_dot_40_1, R.id.cal_dot_40_2),
        intArrayOf(R.id.cal_dot_41_0, R.id.cal_dot_41_1, R.id.cal_dot_41_2)
    )
    val todayRows = arrayOf(
        AgendaRowIds(R.id.today_row_0, R.id.today_dot_0, R.id.today_time_0, R.id.today_title_0),
        AgendaRowIds(R.id.today_row_1, R.id.today_dot_1, R.id.today_time_1, R.id.today_title_1),
        AgendaRowIds(R.id.today_row_2, R.id.today_dot_2, R.id.today_time_2, R.id.today_title_2),
        AgendaRowIds(R.id.today_row_3, R.id.today_dot_3, R.id.today_time_3, R.id.today_title_3)
    )
    val tomorrowRows = arrayOf(
        AgendaRowIds(R.id.tomorrow_row_0, R.id.tomorrow_dot_0, R.id.tomorrow_time_0, R.id.tomorrow_title_0),
        AgendaRowIds(R.id.tomorrow_row_1, R.id.tomorrow_dot_1, R.id.tomorrow_time_1, R.id.tomorrow_title_1),
        AgendaRowIds(R.id.tomorrow_row_2, R.id.tomorrow_dot_2, R.id.tomorrow_time_2, R.id.tomorrow_title_2)
    )
}
