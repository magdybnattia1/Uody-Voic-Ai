package com.oudi.voiceai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var statusUpdate: ((String) -> Unit)? = null
    private var commandUpdate: ((String) -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listenNow() else statusUpdate?.invoke("اسمح باستخدام الميكروفون أولاً")
    }
    private val speechResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) {
            commandUpdate?.invoke(words)
            runCommand(words)
        } else statusUpdate?.invoke("لم أسمع الأمر بوضوح، حاول مرة أخرى")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setTorch(true) else statusUpdate?.invoke("اسمح بإذن الكاميرا لتشغيل الفلاش")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by remember { mutableStateOf("جاهز — اضغط الميكروفون وقل أمرك") }
            var lastCommand by remember { mutableStateOf("لا يوجد أمر بعد") }
            statusUpdate = { status = it }
            commandUpdate = { lastCommand = it }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF38BDF8), background = Color(0xFF080D18), surface = Color(0xFF142039))) {
                Column(Modifier.fillMaxSize().background(Color(0xFF080D18)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))
                    Text("OUDI", color = Color(0xFF38BDF8), fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text("VOICE AI", color = Color.White, fontSize = 16.sp, letterSpacing = 5.sp)
                    Spacer(Modifier.height(38.dp))
                    Text("مساعدك الصوتي الذكي", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(26.dp))
                    Button(onClick = { startListening() }, modifier = Modifier.size(145.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087EBD))) {
                        Text("🎙", fontSize = 48.sp)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(status, color = Color(0xFFB8C7DA), fontSize = 15.sp)
                    Spacer(Modifier.height(26.dp))
                    InfoCard("آخر أمر", lastCommand)
                    Spacer(Modifier.height(12.dp))
                    InfoCard("أوامر تجريبية", "شغّل الفلاش • اقفل الفلاش • افتح البلوتوث • افتح الواي فاي • افتح إعدادات الإشعارات • افتح إعدادات المكالمات")
                    Spacer(Modifier.weight(1f))
                    Text("الإصدار الأولي 1.0", color = Color(0xFF73849C), fontSize = 12.sp)
                }
            }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listenNow()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listenNow() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "قول الأمر اللي عايز OUDI ينفذه")
        }
        try { speechResult.launch(intent) } catch (_: Exception) { statusUpdate?.invoke("خدمة التعرف على الصوت غير متاحة على جهازك") }
    }

    private fun runCommand(raw: String) {
        val c = raw.lowercase(Locale("ar", "EG"))
        when {
            c.contains("شغل الفلاش") || c.contains("افتح الفلاش") || c.contains("نور الفلاش") -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setTorch(true)
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            c.contains("اقفل الفلاش") || c.contains("اطفئ الفلاش") || c.contains("طفي الفلاش") -> setTorch(false)
            c.contains("بلوتوث") -> openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            c.contains("واي فاي") || c.contains("wifi") -> openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS)
            c.contains("الإشعارات") || c.contains("الاشعارات") -> openSettings("android.settings.APP_NOTIFICATION_SETTINGS")
            c.contains("انتظار المكالمات") || c.contains("انتظار المكالمه") || c.contains("المكالمات") -> openSettings("android.intent.action.CALL_SETTINGS")
            c.contains("إعدادات") || c.contains("الاعدادات") -> openSettings(android.provider.Settings.ACTION_SETTINGS)
            else -> statusUpdate?.invoke("الأمر ده لسه مش مضاف. جرّب أمر من القائمة")
        }
    }

    private fun setTorch(enabled: Boolean) {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (id == null) { statusUpdate?.invoke("الفلاش غير متاح على الهاتف"); return }
            manager.setTorchMode(id, enabled)
            statusUpdate?.invoke(if (enabled) "الفلاش اشتغل بنجاح" else "الفلاش اتقفل")
        } catch (e: Exception) { statusUpdate?.invoke("تعذر التحكم في الفلاش: ${e.localizedMessage ?: "خطأ"}") }
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)); statusUpdate?.invoke("تم فتح الإعدادات المطلوبة") }
        catch (_: Exception) { statusUpdate?.invoke("الإعداد ده غير متاح على جهازك") }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF142039))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(7.dp))
            Text(body, color = Color.White, fontSize = 14.sp)
        }
    }
}
