package com.oudi.voiceai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
                    Spacer(Modifier.height(30.dp))
                    Text("مساعدك الصوتي الذكي", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { startListening() }, modifier = Modifier.size(145.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087EBD))) {
                        Text("🎙", fontSize = 48.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(status, color = Color(0xFFB8C7DA), fontSize = 15.sp)
                    Spacer(Modifier.height(22.dp))
                    InfoCard("آخر أمر", lastCommand)
                    Spacer(Modifier.height(12.dp))
                    InfoCard("أوامر الصوت", "شغّل الفلاش • اقفل الفلاش • علي الصوت • وطي الصوت • اكتم الصوت • افتح البلوتوث • افتح الموقع • افتح الكاميرا • افتح الإعدادات")
                    Spacer(Modifier.weight(1f))
                    Text("OUDI Voice AI — إصدار تجريبي", color = Color(0xFF73849C), fontSize = 12.sp)
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
        val c = raw.lowercase(Locale("ar", "EG")).replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        when {
            c.contains("شغل الفلاش") || c.contains("افتح الفلاش") || c.contains("نور الفلاش") || c.contains("ولع الفلاش") -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setTorch(true)
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            c.contains("اقفل الفلاش") || c.contains("اطفئ الفلاش") || c.contains("طفي الفلاش") || c.contains("اطفي الفلاش") -> setTorch(false)

            c.contains("علي الصوت") || c.contains("اعلي الصوت") || c.contains("زود الصوت") || c.contains("ارفع الصوت") -> changeVolume(AudioManager.ADJUST_RAISE)
            c.contains("وطي الصوت") || c.contains("قلل الصوت") || c.contains("خفض الصوت") || c.contains("انزل الصوت") -> changeVolume(AudioManager.ADJUST_LOWER)
            c.contains("اكتم الصوت") || c.contains("اسكت الصوت") -> muteVolume()
            c.contains("الغاء كتم الصوت") || c.contains("افتح الصوت") -> changeVolume(AudioManager.ADJUST_UNMUTE)
            c.contains("اعلى صوت") || c.contains("اقصى صوت") -> setVolumeToMax()
            c.contains("اقل صوت") || c.contains("صفر الصوت") -> setVolumeToMin()

            c.contains("بلوتوث") -> openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            c.contains("واي فاي") || c.contains("wifi") -> openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS)
            c.contains("الموقع") || c.contains("gps") -> openSettings(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            c.contains("عدم الازعاج") || c.contains("عدم الإزعاج") -> openSettings("android.settings.ZEN_MODE_SETTINGS")
            c.contains("الشاشه") || c.contains("الشاشة") || c.contains("سطوع") -> openSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
            c.contains("الصوت والاهتزاز") || c.contains("اهتزاز") || c.contains("نغمه") || c.contains("نغمة") -> openSettings(android.provider.Settings.ACTION_SOUND_SETTINGS)
            c.contains("الاشعارات") || c.contains("اشعارات") -> openSettings("android.settings.APP_NOTIFICATION_SETTINGS")
            c.contains("انتظار المكالمات") || c.contains("انتظار المكالمه") || c.contains("اعدادات المكالمات") || c.contains("المكالمات") -> openSettings("android.intent.action.CALL_SETTINGS")
            c.contains("اعدادات التطبيقات") || c.contains("التطبيقات") -> openSettings(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
            c.contains("اعدادات") -> openSettings(android.provider.Settings.ACTION_SETTINGS)

            c.contains("افتح الكاميرا") || c == "الكاميرا" -> launchApp(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), "الكاميرا")
            c.contains("افتح الساعه") || c.contains("افتح الساعة") || c == "الساعة" -> launchCategory("android.intent.category.APP_CLOCK", "الساعة")
            c.contains("افتح جهات الاتصال") || c.contains("افتح الاسماء") -> launchCategory(Intent.CATEGORY_APP_CONTACTS, "جهات الاتصال")
            c.contains("افتح الرسائل") -> launchCategory(Intent.CATEGORY_APP_MESSAGING, "الرسائل")
            c.contains("اتصل") || c.contains("افتح الاتصال") || c.contains("افتح الهاتف") -> launchApp(Intent(Intent.ACTION_DIAL), "الهاتف")
            else -> statusUpdate?.invoke("مش فاهم الأمر ده لسه. جرّب أمر من قائمة الأوامر")
        }
    }

    private fun changeVolume(direction: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        statusUpdate?.invoke("تم تعديل مستوى صوت الوسائط")
    }

    private fun muteVolume() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        statusUpdate?.invoke("تم كتم صوت الوسائط")
    }

    private fun setVolumeToMax() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        statusUpdate?.invoke("تم رفع صوت الوسائط للحد الأقصى")
    }

    private fun setVolumeToMin() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        statusUpdate?.invoke("تم خفض صوت الوسائط للصفر")
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

    private fun launchCategory(category: String, label: String) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        launchApp(intent, label)
    }

    private fun launchApp(intent: Intent, label: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            statusUpdate?.invoke("تم فتح $label")
        } catch (_: Exception) { statusUpdate?.invoke("مش قادر أفتح $label على الجهاز") }
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
