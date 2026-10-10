package com.akif.temizlik

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    data class AppItem(val label: String, val pkg: String, val system: Boolean, val icon: Drawable)

    private var all: List<AppItem> = emptyList()
    private val appAdapter = Adapter()
    private lateinit var search: EditText
    private lateinit var showSys: CheckBox
    private lateinit var count: TextView
    private lateinit var appsView: LinearLayout
    private lateinit var cleanView: LinearLayout
    private lateinit var tab1: Button
    private lateinit var tab2: Button

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
    }

    private fun tabBtn(t: String) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#262C45"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1220"))
        }
        val title = txt("Temizlik Ustası", 22f, Color.WHITE).apply { setPadding(20.dp(), 14.dp(), 20.dp(), 8.dp()) }
        tab1 = tabBtn("Uygulamalar"); tab2 = tabBtn("Temizlik")
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp(), 0, 12.dp(), 8.dp())
            addView(tab1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(tab2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // ---- Uygulamalar sekmesi ----
        search = EditText(this).apply {
            hint = "Uygulama ara..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { applyFilter() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        showSys = CheckBox(this).apply {
            text = "Sistem uygulamalarını göster"; setTextColor(Color.LTGRAY)
            setOnCheckedChangeListener { _, _ -> applyFilter() }
        }
        count = txt("", 12f, Color.GRAY)
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }
        appsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 0, 16.dp(), 0)
            addView(search); addView(showSys); addView(count)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        // ---- Temizlik sekmesi ----
        val info = txt(
            "Tüm uygulamaların önbelleğini tek dokunuşla temizler.\n\n" +
            "Telefon boş alan açmak için diğer uygulamaların önbelleğini siler. " +
            "Kişisel verilerine, hesaplarına veya ayarlarına dokunulmaz.",
            15f, Color.LTGRAY
        ).apply { gravity = Gravity.CENTER }
        val cleanBtn = Button(this).apply {
            text = "Tüm önbellekleri temizle"; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C5CFF"))
        }
        val result = txt("", 16f, Color.parseColor("#34D399")).apply { gravity = Gravity.CENTER }
        cleanBtn.setOnClickListener { cleanAll(cleanBtn, result) }
        cleanView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28.dp(), 0, 28.dp(), 0)
            addView(info)
            addView(cleanBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()).apply { topMargin = 28.dp() })
            addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20.dp() })
        }

        val content = FrameLayout(this).apply {
            addView(appsView, FrameLayout.LayoutParams(-1, -1))
            addView(cleanView, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(title); root.addView(tabs)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        tab1.setOnClickListener { selectTab(true) }
        tab2.setOnClickListener { selectTab(false) }
        selectTab(true)
    }

    private fun selectTab(apps: Boolean) {
        appsView.visibility = if (apps) android.view.View.VISIBLE else android.view.View.GONE
        cleanView.visibility = if (apps) android.view.View.GONE else android.view.View.VISIBLE
        tab1.setBackgroundColor(Color.parseColor(if (apps) "#7C5CFF" else "#262C45"))
        tab2.setBackgroundColor(Color.parseColor(if (apps) "#262C45" else "#7C5CFF"))
    }

    override fun onResume() {
        super.onResume()
        loadApps() // kaldırma sonrası liste güncellensin
    }

    private fun loadApps() {
        thread {
            val pm = packageManager
            val items = pm.getInstalledApplications(0)
                .filter { it.packageName != packageName }
                .map {
                    AppItem(
                        it.loadLabel(pm).toString(), it.packageName,
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0, it.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { all = items; applyFilter() }
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        appAdapter.data = all.filter {
            (showSys.isChecked || !it.system) &&
                (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q))
        }
        appAdapter.notifyDataSetChanged()
        count.text = "${appAdapter.data.size} uygulama"
    }

    private fun showActions(a: AppItem) {
        val opts = arrayOf("Önbellek / verileri temizle (ayar sayfası)", "Kaldır", "Aç")
        AlertDialog.Builder(this).setTitle(a.label).setItems(opts) { _, which ->
            when (which) {
                0 -> {
                    Toast.makeText(this, "Depolama bölümünden 'Önbelleği temizle' veya 'Verileri temizle'ye dokun", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                }
                1 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                2 -> {
                    val i = packageManager.getLaunchIntentForPackage(a.pkg)
                    if (i != null) startActivity(i) else Toast.makeText(this, "Bu uygulama açılamıyor", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    /** Sistemden yer isteyerek diğer uygulamaların önbelleğini boşaltır (root gerekmez). */
    private fun cleanAll(btn: Button, out: TextView) {
        btn.isEnabled = false
        out.setTextColor(Color.LTGRAY)
        out.text = "Temizleniyor, birkaç saniye sürebilir..."
        thread {
            var ok = true
            val msg = try {
                val sm = getSystemService(StorageManager::class.java)
                val uuid = sm.getUuidForPath(filesDir)
                val before = StatFs(filesDir.path).availableBytes
                val want = sm.getAllocatableBytes(uuid) - 4L * 1024 * 1024
                if (want <= before) {
                    "Temizlenecek önemli bir önbellek bulunamadı."
                } else {
                    val f = File(filesDir, "tmp.bin")
                    try {
                        FileOutputStream(f).use { sm.allocateBytes(it.fd, want) }
                    } finally {
                        f.delete()
                    }
                    val freed = (StatFs(filesDir.path).availableBytes - before).coerceAtLeast(0)
                    "Temizlendi! Açılan alan: " + Formatter.formatShortFileSize(this, freed)
                }
            } catch (e: Exception) {
                ok = false
                "Başarısız: " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                out.setTextColor(if (ok) Color.parseColor("#34D399") else Color.parseColor("#F87171"))
                out.text = msg
                btn.isEnabled = true
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var data: List<AppItem> = emptyList()

        inner class VH(val row: LinearLayout, val icon: ImageView, val name: TextView, val sub: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = this@MainActivity
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10.dp(), 0, 10.dp())
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            val icon = ImageView(ctx).apply { layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()) }
            val name = txt("", 16f, Color.WHITE)
            val sub = txt("", 12f, Color.GRAY)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14.dp(), 0, 0, 0)
                addView(name); addView(sub)
            }
            row.addView(icon); row.addView(col)
            return VH(row, icon, name, sub)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val a = data[i]
            h.icon.setImageDrawable(a.icon)
            h.name.text = a.label
            h.sub.text = a.pkg + if (a.system) "  •  SİSTEM" else ""
            h.row.setOnClickListener { showActions(a) }
        }
    }
}
