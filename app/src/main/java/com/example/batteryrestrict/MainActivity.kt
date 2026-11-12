package com.example.batteryrestrict

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.SparseBooleanArray
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

data class AppItem(val pkg: String, val label: String, val isSystem: Boolean)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_CODE = 1001
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    }

    private lateinit var txtStatus: TextView
    private lateinit var txtSteps: TextView
    private lateinit var setupPanel: View
    private lateinit var readyPanel: View
    private lateinit var btnDevOptions: Button
    private lateinit var btnInstall: Button
    private lateinit var btnOpenShizuku: Button
    private lateinit var btnPerm: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnClearAll: Button
    private lateinit var btnApplySel: Button
    private lateinit var btnRestoreSel: Button
    private lateinit var listApps: ListView

    private var apps: List<AppItem> = emptyList()
    private var appsLoaded = false

    private val onPermResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == REQ_CODE) refreshUi()
    }
    private val onBinder = Shizuku.OnBinderReceivedListener { refreshUi() }
    private val onBinderDead = Shizuku.OnBinderDeadListener { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtSteps = findViewById(R.id.txtSteps)
        setupPanel = findViewById(R.id.setupPanel)
        readyPanel = findViewById(R.id.readyPanel)
        btnDevOptions = findViewById(R.id.btnDevOptions)
        btnInstall = findViewById(R.id.btnInstall)
        btnOpenShizuku = findViewById(R.id.btnOpenShizuku)
        btnPerm = findViewById(R.id.btnPerm)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnApplySel = findViewById(R.id.btnApplySel)
        btnRestoreSel = findViewById(R.id.btnRestoreSel)
        listApps = findViewById(R.id.listApps)

        btnDevOptions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        btnInstall.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG")))
            } catch (e: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PKG")
                    )
                )
            }
        }
        btnOpenShizuku.setOnClickListener {
            val i = packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
            if (i != null) startActivity(i)
            else Toast.makeText(this, "شيزوكو مش متثبت", Toast.LENGTH_SHORT).show()
        }
        btnPerm.setOnClickListener {
            if (isShizukuRunning()) Shizuku.requestPermission(REQ_CODE)
        }

        btnSelectAll.setOnClickListener {
            for (i in 0 until listApps.count) listApps.setItemChecked(i, true)
        }
        btnClearAll.setOnClickListener {
            for (i in 0 until listApps.count) listApps.setItemChecked(i, false)
        }
        btnApplySel.setOnClickListener { runSelected(true) }
        btnRestoreSel.setOnClickListener { runSelected(false) }

        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermResult)
        refreshUi()
    }

    override fun onStop() {
        super.onStop()
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermResult)
    }

    private fun isInstalled(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    private fun hasPermission(): Boolean = try {
        isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    private fun refreshUi() {
        val installed = isInstalled()
        val running = isShizukuRunning()
        val granted = hasPermission()

        txtStatus.text = when {
            !installed -> "شيزوكو مش متثبت ❌"
            !running -> "شيزوكو متثبت لكنه مش شغّال ⚠️"
            !granted -> "شيزوكو شغّال، محتاج صلاحية ⚠️"
            else -> "جاهز ✅ اختار التطبيقات واضغط تقييد"
        }

        btnInstall.visibility = if (installed) View.GONE else View.VISIBLE
        btnOpenShizuku.isEnabled = installed
        btnPerm.isEnabled = running && !granted

        txtSteps.text = when {
            !installed ->
                "الخطوة 1: اضغط «تنزيل شيزوكو» وثبّته من متجر التطبيقات.\n\n" +
                    "بعد ما يتثبت ارجع لهنا."

            !running ->
                "الخطوة 2: شغّل شيزوكو.\n\n" +
                    "1) اضغط «فتح شيزوكو».\n" +
                    "2) لو أول مرة: من «خيارات المطور» فعّل «تصحيح الأخطاء اللاسلكي»، " +
                    "واضغط «إقران الجهاز برمز الإقران» وادخل الرمز في شيزوكو.\n" +
                    "3) في شيزوكو اضغط «Start via Wireless debugging» أو «تشغيل عبر تصحيح الأخطاء اللاسلكي».\n" +
                    "4) لما يبقى مكتوب إن شيزوكو شغّال، ارجع لهنا.\n\n" +
                    "لو الموبايل عمل restart لازم تكرر الخطوة 3.\n\n" +
                    "💡 شاومي: لو ما اشتغلش، فعّل «تصحيح USB (إعدادات الأمان)» من خيارات المطور."

            !granted ->
                "الخطوة 3: اضغط «منح صلاحية لتطبيقي».\n\n" +
                    "هيظهر طلب من شيزوكو، اختار «السماح»."

            else -> ""
        }

        setupPanel.visibility = if (granted) View.GONE else View.VISIBLE
        readyPanel.visibility = if (granted) View.VISIBLE else View.GONE

        if (granted) loadApps()
    }

    private fun loadApps() {
        if (appsLoaded) return
        appsLoaded = true
        thread {
            val list = packageManager.getInstalledApplications(0)
                .filter { it.packageName != packageName }
                .map {
                    AppItem(
                        it.packageName,
                        it.loadLabel(packageManager).toString(),
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedWith(compareBy<AppItem>({ it.isSystem }, { it.label.lowercase() }))

            runOnUiThread {
                apps = list
                listApps.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_multiple_choice,
                    list.map { (if (it.isSystem) "[نظام] " else "") + it.label + "\n" + it.pkg }
                )
            }
        }
    }

    private fun getSelected(): List<AppItem> {
        val sb: SparseBooleanArray = listApps.checkedItemPositions
        val out = mutableListOf<AppItem>()
        for (i in 0 until sb.size()) {
            if (sb.valueAt(i)) apps.getOrNull(sb.keyAt(i))?.let { out.add(it) }
        }
        return out
    }

    private fun runSelected(restrict: Boolean) {
        val selected = getSelected()
        if (selected.isEmpty()) {
            Toast.makeText(this, "اختار تطبيق الأول", Toast.LENGTH_SHORT).show()
            return
        }
        val sysCount = selected.count { it.isSystem }
        val title = if (restrict) "تقييد ${selected.size} تطبيق؟" else "إرجاع ${selected.size} تطبيق؟"
        var msg = "هيتغير إعداد البطارية فعلاً للتطبيقات اللي اخترتها."
        if (sysCount > 0) {
            msg += "\n\n⚠️ منهم $sysCount من تطبيقات النظام. تقييدها ممكن يأثر على الإشعارات أو المكالمات أو الشاشة. كمّل بس لو متأكد."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("أيوه") { _, _ -> execute(selected, restrict) }
            .setNegativeButton("لا", null)
            .show()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnApplySel.isEnabled = enabled
        btnRestoreSel.isEnabled = enabled
    }

    private fun execute(selected: List<AppItem>, restrict: Boolean) {
        setButtonsEnabled(false)
        Toast.makeText(this, "جاري التنفيذ...", Toast.LENGTH_SHORT).show()
        thread {
            val ok = mutableListOf<String>()
            val failed = mutableListOf<String>()
            for (app in selected) {
                if (setAppMode(app.pkg, restrict)) ok.add(app.label) else failed.add(app.label)
            }
            runOnUiThread {
                setButtonsEnabled(true)
                showResult(restrict, ok, failed)
            }
        }
    }

    private fun setAppMode(pkg: String, restrict: Boolean): Boolean {
        val mode = if (restrict) "deny" else "allow"
        runShell("cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode")
        runShell("cmd appops set $pkg RUN_IN_BACKGROUND $mode")
        val out = runShell("cmd appops get $pkg RUN_ANY_IN_BACKGROUND") ?: return false
        return if (restrict) {
            out.contains("deny") || out.contains("ignore")
        } else {
            out.contains("allow") || out.contains("default")
        }
    }

    private fun showResult(restrict: Boolean, ok: List<String>, failed: List<String>) {
        val action = if (restrict) "التقييد" else "الإرجاع"
        var msg = "تم ${action} على: ${ok.size}\n"
        if (failed.isNotEmpty()) {
            msg += "\nفشل على: ${failed.size}\n" + failed.joinToString("\n") { "• $it" }
            msg += "\n\nلو فيه فشل، شيزوكو لازم يكون شغّال، وشاومي ممكن يكون عنده قيود إضافية."
        }
        AlertDialog.Builder(this)
            .setTitle("النتيجة")
            .setMessage(msg)
            .setPositiveButton("تمام", null)
            .show()
    }

    private fun runShell(cmd: String): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.errorStream.bufferedReader().readText()
        if (p.waitFor() == 0) out.trim() else null
    } catch (e: Exception) {
        null
    }
}
