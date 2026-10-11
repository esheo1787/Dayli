package com.silverwest.dayli.ddaywidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 설정 > 프리미엄 카드: 무엇이 열리는지 + 구매 / 구매 복원 */
@Composable
fun PremiumSettingsCard() {
    val context = LocalContext.current
    SettingsCard {
        if (Premium.unlocked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("프리미엄 사용 중", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "광고 없음 · 캘린더 연동 · 캘린더 위젯",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dayli 프리미엄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("한 번 결제하면 계속 쓸 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "광고 제거",
                    "구글·삼성 캘린더 일정을 Dayli 캘린더에 함께 보기",
                    "캘린더 위젯 3종 (월간 · 오늘·내일 · 합친 위젯)"
                ).forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null || !Premium.purchase(activity)) {
                            Toast.makeText(context, "결제 정보를 불러오는 중이에요. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(Premium.priceText?.let { "프리미엄 열기 · $it" } ?: "프리미엄 열기")
                }
                TextButton(
                    onClick = {
                        Premium.refreshPurchases(context)
                        Toast.makeText(context, "구매 내역을 확인하고 있어요", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("구매 복원") }
            }
        }
    }
}

/** 설정 > 캘린더 연동: 켜기(권한 요청) + 보여줄 캘린더 고르기. 프리미엄 전용 */
@Composable
fun CalendarSyncSettingsCard(onChanged: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DdaySettings.isPhoneCalendarEnabled(context)) }
    var hasPermission by remember { mutableStateOf(PhoneCalendar.hasPermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf(PhoneCalendar.calendars(context)) }
    var selected by remember { mutableStateOf(PhoneCalendar.selectedCalendarIds(context)) }

    fun turnOn() {
        enabled = true
        DdaySettings.setPhoneCalendarEnabled(context, true)
        calendars = PhoneCalendar.calendars(context)
        selected = PhoneCalendar.selectedCalendarIds(context)
        onChanged()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) turnOn()
    }

    val active = Premium.unlocked && enabled && hasPermission

    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("구글·삼성 캘린더 일정 함께 보기", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (Premium.unlocked) "폰에 동기화된 캘린더를 읽기만 해요. 일정은 기기 밖으로 보내지 않아요."
                    else "프리미엄에서 쓸 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = active,
                enabled = Premium.unlocked,
                onCheckedChange = { on ->
                    if (!on) {
                        enabled = false
                        DdaySettings.setPhoneCalendarEnabled(context, false)
                        onChanged()
                    } else if (PhoneCalendar.hasPermission(context)) {
                        hasPermission = true
                        turnOn()
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                }
            )
        }

        if (permissionDenied) {
            Text(
                "캘린더 권한이 꺼져 있어요. 설정에서 Dayli의 캘린더 권한을 허용해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("설정으로 이동") }
        }

        if (active) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                "보여줄 캘린더",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            if (calendars.isEmpty()) {
                Text(
                    "폰에 동기화된 캘린더가 없어요. 구글 계정의 캘린더 동기화가 켜져 있는지 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            calendars.forEach { cal ->
                val checked = cal.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            selected = if (checked) selected - cal.id else selected + cal.id
                            DdaySettings.setPhoneCalendarIds(context, selected)
                            onChanged()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(cal.color)))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cal.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${cal.sourceLabel} · ${cal.accountName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (Premium.unlocked) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("캘린더 위젯 추가", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "월간" to CalendarWidgetKind.MONTH,
                    "오늘·내일" to CalendarWidgetKind.AGENDA,
                    "합친 위젯" to CalendarWidgetKind.COMBINED
                ).forEach { (label, kind) ->
                    OutlinedButton(
                        onClick = { CalendarWidgets.requestPin(context, kind) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) { Text(label, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }

        Text(
            "네이버 캘린더는 폰 캘린더에 들어오지 않아 바로 연결되지 않아요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
