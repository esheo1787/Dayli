"""
캘린더 위젯 레이아웃 생성기 (손으로 쓰면 42칸 ID가 틀리기 쉬워서 생성)
  python tools/gen_calendar_widget_layouts.py
생성: res/layout/widget_calendar_grid.xml, widget_calendar_agenda_block.xml,
      widget_calendar_month.xml, widget_calendar_agenda.xml, widget_calendar_combined.xml
      java/.../ddaywidget/CalendarWidgetIds.kt
위젯은 Android 12 미만에서 일반 View를 못 쓰므로 FrameLayout/LinearLayout/TextView/ImageView만 사용
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
LAYOUT = os.path.join(ROOT, "res", "layout")
KOTLIN = os.path.join(ROOT, "java", "com", "silverwest", "dayli", "ddaywidget")

CELLS = 42
DOTS = 3
TODAY_ROWS = 4
TOMORROW_ROWS = 3
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- 생성 파일: tools/gen_calendar_widget_layouts.py 로 다시 만들 것 (직접 수정 금지) -->\n'
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def grid():
    rows = []
    for r in range(6):
        cells = []
        for c in range(7):
            i = r * 7 + c
            dots = "\n".join(
                f'''                <ImageView android:id="@+id/cal_dot_{i}_{k}" android:layout_width="4dp" android:layout_height="4dp"
                    android:layout_marginHorizontal="1dp" android:src="@drawable/widget_dot" android:visibility="gone"
                    android:importantForAccessibility="no" />'''
                for k in range(DOTS)
            )
            cells.append(f'''        <LinearLayout android:id="@+id/cal_cell_{i}" android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:orientation="vertical" android:gravity="center_horizontal">
            <FrameLayout android:layout_width="24dp" android:layout_height="22dp">
                <ImageView android:id="@+id/cal_today_{i}" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:scaleType="fitXY" android:src="@drawable/widget_shape_r6" android:visibility="invisible"
                    android:importantForAccessibility="no" />
                <TextView android:id="@+id/cal_num_{i}" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:gravity="center" android:textSize="12sp" android:includeFontPadding="false" />
            </FrameLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="5dp" android:orientation="horizontal"
                android:gravity="center" android:layout_marginTop="1dp">
{dots}
            </LinearLayout>
        </LinearLayout>''')
        rows.append(f'''    <LinearLayout android:id="@+id/cal_row_{r}" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:layout_marginTop="1dp">
{chr(10).join(cells)}
    </LinearLayout>''')
    week = "\n".join(
        f'''        <TextView android:id="@+id/cal_week_{c}" android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:gravity="center" android:textSize="10sp" android:text="{n}" />'''
        for c, n in enumerate("일월화수목금토")
    )
    return HEAD + f'''<LinearLayout {NS}
    android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical" android:paddingHorizontal="4dp" android:paddingBottom="4dp">
        <TextView android:id="@+id/cal_title" android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:textSize="15sp" android:textStyle="bold" />
        <ImageView android:id="@+id/cal_legend_dayli_dot" android:layout_width="6dp" android:layout_height="6dp"
            android:src="@drawable/widget_dot" android:importantForAccessibility="no" />
        <TextView android:id="@+id/cal_legend_dayli" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginStart="3dp" android:textSize="10sp" android:text="D-Day" />
        <ImageView android:id="@+id/cal_legend_phone_dot" android:layout_width="6dp" android:layout_height="6dp"
            android:layout_marginStart="8dp" android:src="@drawable/widget_dot" android:importantForAccessibility="no" />
        <TextView android:id="@+id/cal_legend_phone" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginStart="3dp" android:textSize="10sp" android:text="캘린더" />
    </LinearLayout>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
{week}
    </LinearLayout>
{chr(10).join(rows)}
</LinearLayout>
'''


def agenda_rows(prefix, n):
    return "\n".join(
        f'''    <LinearLayout android:id="@+id/{prefix}_row_{i}" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="5dp" android:visibility="gone">
        <ImageView android:id="@+id/{prefix}_dot_{i}" android:layout_width="7dp" android:layout_height="7dp"
            android:src="@drawable/widget_dot" android:importantForAccessibility="no" />
        <TextView android:id="@+id/{prefix}_time_{i}" android:layout_width="60dp" android:layout_height="wrap_content"
            android:layout_marginStart="8dp" android:textSize="12sp" android:maxLines="1" />
        <TextView android:id="@+id/{prefix}_title_{i}" android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:textSize="12.5sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end" />
    </LinearLayout>'''
        for i in range(n)
    )


def agenda_block():
    def day(prefix, n):
        return f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"
        android:gravity="bottom">
        <TextView android:id="@+id/{prefix}_label" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="13.5sp" android:textStyle="bold" />
        <TextView android:id="@+id/{prefix}_date" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginStart="6dp" android:textSize="11.5sp" />
    </LinearLayout>
{agenda_rows(prefix, n)}
    <TextView android:id="@+id/{prefix}_empty" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="5dp" android:textSize="12sp" android:text="일정 없음" android:visibility="gone" />
    <TextView android:id="@+id/{prefix}_more" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="4dp" android:textSize="11sp" android:visibility="gone" />'''
    return HEAD + f'''<LinearLayout {NS}
    android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical"
    android:paddingHorizontal="4dp">
{day("today", TODAY_ROWS)}
    <ImageView android:id="@+id/agenda_divider" android:layout_width="match_parent" android:layout_height="1dp"
        android:layout_marginVertical="8dp" android:scaleType="fitXY" android:src="@drawable/widget_line"
        android:importantForAccessibility="no" />
{day("tomorrow", TOMORROW_ROWS)}
</LinearLayout>
'''


def shell(name, body):
    # 둥근 바탕(ImageView) + 내용. FrameLayout은 match_parent 자식이 2개 이상일 때 바탕을 다시 재므로 내용도 match_parent
    return HEAD + f'''<FrameLayout {NS}
    android:id="@+id/cal_widget_root" android:layout_width="match_parent" android:layout_height="match_parent">
    <ImageView android:id="@+id/cal_widget_bg" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:src="@drawable/widget_shape_r16" android:importantForAccessibility="no" />
    <LinearLayout android:id="@+id/cal_widget_content" android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:padding="12dp">
{body}
    </LinearLayout>
    <!-- 프리미엄이 아닐 때 안내 -->
    <TextView android:id="@+id/cal_widget_locked" android:layout_width="match_parent" android:layout_height="match_parent"
        android:gravity="center" android:padding="16dp" android:textSize="13sp" android:visibility="gone"
        android:text="캘린더 위젯은 Dayli 프리미엄 기능이에요\\n눌러서 알아보기" />
</FrameLayout>
'''


def kotlin_ids():
    def arr(fmt, n):
        return ", ".join(fmt.format(i) for i in range(n))
    dots = ",\n        ".join(f"intArrayOf({', '.join(f'R.id.cal_dot_{i}_{k}' for k in range(DOTS))})" for i in range(CELLS))

    def rows(prefix, n):
        return ",\n        ".join(
            f"AgendaRowIds(R.id.{prefix}_row_{i}, R.id.{prefix}_dot_{i}, R.id.{prefix}_time_{i}, R.id.{prefix}_title_{i})"
            for i in range(n)
        )
    return f'''package com.silverwest.dayli.ddaywidget

import com.silverwest.dayli.R

// 생성 파일: tools/gen_calendar_widget_layouts.py 로 다시 만들 것 (직접 수정 금지)

data class AgendaRowIds(val row: Int, val dot: Int, val time: Int, val title: Int)

object CalendarWidgetIds {{
    val cells = intArrayOf({arr("R.id.cal_cell_{}", CELLS)})
    val nums = intArrayOf({arr("R.id.cal_num_{}", CELLS)})
    val todayBgs = intArrayOf({arr("R.id.cal_today_{}", CELLS)})
    val rows = intArrayOf({arr("R.id.cal_row_{}", 6)})
    val weekdays = intArrayOf({arr("R.id.cal_week_{}", 7)})
    val dots = arrayOf(
        {dots}
    )
    val todayRows = arrayOf(
        {rows("today", TODAY_ROWS)}
    )
    val tomorrowRows = arrayOf(
        {rows("tomorrow", TOMORROW_ROWS)}
    )
}}
'''


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


write(os.path.join(LAYOUT, "widget_calendar_grid.xml"), grid())
write(os.path.join(LAYOUT, "widget_calendar_agenda_block.xml"), agenda_block())
write(os.path.join(LAYOUT, "widget_calendar_month.xml"),
      shell("month", '        <include layout="@layout/widget_calendar_grid" />'))
write(os.path.join(LAYOUT, "widget_calendar_agenda.xml"),
      shell("agenda", '        <include layout="@layout/widget_calendar_agenda_block" />'))
write(os.path.join(LAYOUT, "widget_calendar_combined.xml"),
      shell("combined", '''        <include layout="@layout/widget_calendar_grid" />
        <ImageView android:id="@+id/combined_divider" android:layout_width="match_parent" android:layout_height="1dp"
            android:layout_marginVertical="8dp" android:scaleType="fitXY" android:src="@drawable/widget_line"
            android:importantForAccessibility="no" />
        <include layout="@layout/widget_calendar_agenda_block" />'''))
write(os.path.join(KOTLIN, "CalendarWidgetIds.kt"), kotlin_ids())
