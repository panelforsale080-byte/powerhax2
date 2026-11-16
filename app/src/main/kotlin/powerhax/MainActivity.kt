package com.accel.loader

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private companion object {
        const val SERVER_LIST_URL = "https://potpot.x10.mx/Loader/index.php"
        const val PANEL_URL = "https://potpot.x10.mx/connect"
        const val DEFAULT_GAME_TYPE = "CODM"
        const val DEFAULT_GAME_PACKAGE = "com.garena.game.codm"
        const val LIBRARY_FILE_NAME = "libcodm.so"
        private const val TAG = "AstralMainActivity"
        /** Raw URL of the .so chosen from PasswordAndLicense. */
        var libraryDownloadUrl: String = ""
    }

    init {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private lateinit var licenseKeyInput: TextInputEditText
    private lateinit var licenseKeyInputLayout: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var librarySpinner: Spinner
    private lateinit var delaySpinner: Spinner
    private lateinit var methodSpinner: Spinner
    private lateinit var loaderPreferences: LoaderPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioAuto: RadioButton
    private lateinit var radioManual: RadioButton
    private lateinit var rootStatusView: TextView
    private lateinit var appBanner: ImageView
    private lateinit var mainScroll: View
    private lateinit var libraryDrawer: View
    private lateinit var drawerScrim: View
    private lateinit var librarySearch: EditText
    private lateinit var libraryList: LinearLayout
    private lateinit var libraryCount: TextView
    private lateinit var injectLogView: TextView
    private lateinit var injectLogScroll: ScrollView
    private lateinit var libraryDownloadStatus: TextView
    private lateinit var libraryDownloadProgress: ProgressBar
    private lateinit var libraryDownloadPercent: TextView
    private lateinit var moduleUpdateRow: View
    private lateinit var moduleUpdateText: TextView
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val injectLogBuffer = StringBuilder()
    private val injectLogLines = ArrayDeque<String>(6)
    private lateinit var injectUiLog: InjectUiLog
    
    private val commandRunner = CommandRunner()
    private val catalogClient = LibraryCatalogClient(SERVER_LIST_URL)
    private var isReadyToInject: Boolean = false
    private var isLoadingLibrary: Boolean = false
    private var remoteLibrarySize: Int = 0
    private var selectedLibrary = SelectedLibrary()
    private var delaySeconds: Int = 0
    private var injectionMethod: String = "1"
    private var showTestLibraries = false
    private val allLibraries = mutableListOf<JSONObject>()
    private var githubLibraries: List<GithubSoSource.Hit> = emptyList()
    private var githubListed = false
    private val filteredLibraries = mutableListOf<JSONObject>()
    private var isDrawerOpen = false
    private var moduleUpdatePending = false
    private var menuKeyValid = false
    private lateinit var menuKeyInput: EditText
    private lateinit var menuKeyValidity: TextView
    private var payloadFileName: String = LIBRARY_FILE_NAME
        
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_main)
        loaderPreferences = LoaderPreferences(this)

        mainScroll = findViewById(R.id.main_scroll)
        libraryDrawer = findViewById(R.id.library_drawer)
        drawerScrim = findViewById(R.id.drawer_scrim)
        librarySearch = findViewById(R.id.library_search)
        libraryList = findViewById(R.id.library_list)
        libraryCount = findViewById(R.id.library_count)
        setupLibraryDrawer()
        
        val versionCode = getAppVersionCode()
        
        progressBar = findViewById(R.id.progressBar)
        
        librarySpinner = findViewById(R.id.my_spinner)
        delaySpinner = findViewById(R.id.delay_spinner)
        methodSpinner = findViewById(R.id.method_spinner)
        licenseKeyInput = findViewById(R.id.input_key)
        licenseKeyInputLayout = findViewById(R.id.input_layout)
        findViewById<View>(R.id.license_key_label).visibility = View.GONE
        licenseKeyInputLayout.visibility = View.GONE
        menuKeyInput = findViewById(R.id.menu_key_input)
        menuKeyValidity = findViewById(R.id.menu_key_validity)
        findViewById<View>(R.id.menu_key_check).setOnClickListener {
            checkMenuKey(menuKeyInput.text?.toString().orEmpty())
        }
        refreshMenuKeyFromDevice()
        loginButton = findViewById(R.id.login_button)
        radioGroup = findViewById(R.id.radioGroup)
        radioAuto = findViewById(R.id.radio_auto)
        radioManual = findViewById(R.id.radio_manual)
        rootStatusView = findViewById(R.id.root_status)
        injectLogView = findViewById(R.id.inject_log)
        injectLogScroll = findViewById(R.id.inject_log_scroll)
        libraryDownloadStatus = findViewById(R.id.library_download_status)
        libraryDownloadProgress = findViewById(R.id.library_download_progress)
        libraryDownloadPercent = findViewById(R.id.library_download_percent)
        moduleUpdateRow = findViewById(R.id.module_update_row)
        moduleUpdateText = findViewById(R.id.module_update_text)
        findViewById<View>(R.id.module_update_action).setOnClickListener {
            if (!isLoadingLibrary) beginLibraryLoad()
        }
        NotificationPermissionRequester(this, notificationPermissionLauncher).request()
        injectUiLog = InjectUiLog { line -> appendInjectLogLine(line) }
        appBanner = findViewById(R.id.app_banner)
        appBanner.setImageResource(R.drawable.app_logo)
        findViewById<TextView>(R.id.header_build).text =
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        playEntrance()

        findViewById<MaterialButton>(R.id.telegram_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/axcelLOki")))
        }

        val savedMode = loaderPreferences.getMode()
        if (savedMode == "auto") {
            radioAuto.isChecked = true
        } else {
            radioManual.isChecked = true
        }

        setupDelaySpinner()
        setupMethodSpinner()
        
        val savedDelay = loaderPreferences.getDelaySeconds()
        val savedMethod = loaderPreferences.getInjectionMethod()
        
        delaySpinner.setSelection(savedDelay)
        injectionMethod = "1"
        loaderPreferences.saveInjectionMethod("1")
        methodSpinner.setSelection(0)
        
        delaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                delaySeconds = position
                loaderPreferences.saveDelaySeconds(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radio_auto) "auto" else "manual"
            loaderPreferences.saveMode(mode)
        }

        loginButton.setOnClickListener {
            if (isReadyToInject) {
                if (!menuKeyValid) {
                    showToast("Menu key is not valid")
                    return@setOnClickListener
                }
                injectLibrary(payloadFileName)
                showToast("Injecting, please wait...")
                loginButton.isEnabled = false
                radioGroup.isEnabled = false
                delaySpinner.isEnabled = false
                methodSpinner.isEnabled = false
            } else if (!isLoadingLibrary) {
                beginLibraryLoad()
            }
        }
        
        appBanner.setOnLongClickListener {
            appBanner.alpha = 0.7f
            Handler(Looper.getMainLooper()).postDelayed({
                appBanner.alpha = 1.0f
            }, 500)
            
            filterLibraries(!showTestLibraries)
            
            val message = if (showTestLibraries) {
                "Test libraries shown"
            } else {
                "Test libraries hidden"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            
            true
        }
        
        startCodmLoad()
        loadLibraryCatalog()
        updateRootStatus()
    }

    private fun userLog(message: String) {
        injectUiLog.line(message)
    }

    private fun appendInjectLogLine(line: String) {
        val safe = sanitizeLogLine(line) ?: return
        runOnUiThread {
            injectLogLines.addLast(safe)
            while (injectLogLines.size > 4) injectLogLines.removeFirst()
            injectLogView.text = injectLogLines.joinToString("\n")
        }
    }

    private fun sanitizeLogLine(raw: String): String? {
        if (raw.isBlank()) return null
        val lower = raw.lowercase()
        if (lower.contains("http") || lower.contains("github") || lower.contains("passwordandlicense")) return null
        if (raw.contains("/data/") || raw.contains("/proc/") || raw.contains(".so/")) return null
        var s = raw.replace(Regex("""\[\d{2}:\d{2}:\d{2}\]\s*"""), "")
        s = s.replace(Regex("com\\.garena\\.game\\.codm[^\\s]*"), "CODM")
        s = s.replace(Regex("""pid=\d+"""), "game")
        s = s.replace(Regex("""\d{4,}"""), "#")
        if (s.length > 72) s = s.take(69) + "…"
        return s.trim()
    }

    private fun clearInjectLog() {
        injectLogBuffer.clear()
        injectLogLines.clear()
        injectLogView.text = "—"
    }

    private fun setupDelaySpinner() {
        val delayOptions = arrayOf("NO DELAY", "1S DELAY", "2S DELAY", "3S DELAY", "4S DELAY", "5S DELAY")
        delaySpinner.adapter = SimpleSpinnerAdapter(this, delayOptions)
    }
    
    private fun setupMethodSpinner() {
        methodSpinner.adapter = SimpleSpinnerAdapter(this, arrayOf("METHOD 1"))
    }
    
    private fun applyLibrarySelection(position: Int) {
        if (position !in filteredLibraries.indices) return

        val selectedLibraryJson = filteredLibraries[position]
        handleSelectedJsonData(selectedLibraryJson)
    }
    
    private fun refreshMenuKeyFromDevice() {
        lifecycleScope.launch(Dispatchers.IO) {
            val fromMenu = MenuKeyFiles.read()
            val saved = loaderPreferences.getMenuKey()
            val key = when {
                !fromMenu.isNullOrBlank() -> fromMenu
                saved.isNotBlank() -> saved
                else -> ""
            }
            if (key.isBlank()) return@launch
            withContext(Dispatchers.Main) { menuKeyInput.setText(key) }
            showMenuKey(MenuKeyClient.check(key), key)
        }
    }

    private fun checkMenuKey(typed: String) {
        menuKeyValidity.text = "Checking..."
        lifecycleScope.launch(Dispatchers.IO) {
            showMenuKey(MenuKeyClient.check(typed), typed)
        }
    }

    private suspend fun showMenuKey(result: MenuKeyList.Result, typed: String) {
        val token = typed.trim().split(Regex("\\s+")).firstOrNull { it.contains('-') && it.length >= 6 } ?: typed.trim()
        if (token.isNotBlank() && result.status != MenuKeyList.Status.OFFLINE && result.status != MenuKeyList.Status.EMPTY) {
            loaderPreferences.saveMenuKey(token)
        }
        if (result.status == MenuKeyList.Status.VALID) MenuKeyFiles.publish(token)
        withContext(Dispatchers.Main) {
            menuKeyValid = result.status == MenuKeyList.Status.VALID
            menuKeyValidity.text = MenuKeyList.label(result)
            menuKeyValidity.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (menuKeyValid) R.color.loader_status_active else R.color.loader_status_maintenance
                )
            )
        }
    }

    private fun loadLibraryCatalog() {
        lifecycleScope.launch {
            val root = catalogClient.load().getOrElse { _ ->
                Toast.makeText(this@MainActivity, "Failed to load servers data", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val meta = root.optJSONObject("META")
            if (meta != null) {
                val requiredCode = meta.optInt("APP_VERSION_CODE", -1)
                val reason = meta.optString("VERSION_REASON", "Update required.")
                val updateUrl = meta.optString("UPDATE_URL")

                val currentCode = getAppVersionCode()
                if (requiredCode != -1 && currentCode < requiredCode) {
                    showVersionMismatchDialog(reason, updateUrl)
                    return@launch
                }
            }

        }
    }
    
    private fun drawerClosedOffset(): Float {
        val panel = findViewById<View>(R.id.library_panel)
        return if (panel.width > 0) panel.width.toFloat() else 268f * resources.displayMetrics.density
    }

    private fun setupLibraryDrawer() {
        libraryDrawer.visibility = View.VISIBLE
        libraryDrawer.post {
            if (!isDrawerOpen) libraryDrawer.translationX = drawerClosedOffset()
        }

        findViewById<View>(R.id.library_menu_button).setOnClickListener {
            setLibraryDrawerOpen(!isDrawerOpen)
        }
        findViewById<View>(R.id.drawer_close).setOnClickListener {
            setLibraryDrawerOpen(false)
        }
        drawerScrim.setOnClickListener {
            setLibraryDrawerOpen(false)
        }

        librarySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                refreshLibraryDrawer(text?.toString().orEmpty())
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        })

        findViewById<View>(R.id.drawer_about).setOnClickListener {
            setLibraryDrawerOpen(false)
            AboutDialog(this).show()
        }

        refreshLibraryDrawer()
    }

    private fun setLibraryDrawerOpen(open: Boolean) {
        if (isDrawerOpen == open) return
        isDrawerOpen = open

        val closed = drawerClosedOffset()
        if (open) {
            drawerScrim.visibility = View.VISIBLE
            drawerScrim.alpha = 0f
            drawerScrim.animate().alpha(1f).setDuration(180L).start()

            libraryDrawer.visibility = View.VISIBLE
            libraryDrawer.animate().cancel()
            libraryDrawer.animate()
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            setMainContentBlur(true)
        } else {
            libraryDrawer.animate().cancel()
            libraryDrawer.animate()
                .translationX(closed)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            drawerScrim.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { drawerScrim.visibility = View.GONE }
                .start()
            setMainContentBlur(false)
        }
    }

    @Suppress("NewApi")
    private fun setMainContentBlur(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        mainScroll.setRenderEffect(
            if (enabled) {
                RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
    }

    private fun refreshLibraryDrawer(query: String = librarySearch.text?.toString().orEmpty()) {
        if (!::libraryList.isInitialized) return

        val normalizedQuery = query.trim()
        val visibleLibraries = githubLibraries.filter { hit ->
            normalizedQuery.isBlank() || hit.fileName.contains(normalizedQuery, ignoreCase = true)
        }

        libraryList.removeAllViews()
        val local = localModuleFile()
        val savedSha = loaderPreferences.getModuleSha()
        libraryCount.text = when {
            !githubListed -> "loading"
            visibleLibraries.size == 1 -> "1 library"
            else -> "${visibleLibraries.size} libraries"
        }

        if (visibleLibraries.isEmpty()) {
            val emptyState = TextView(this).apply {
                text = if (!githubListed) "Checking GitHub..." else "No CODM module on GitHub"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.loader_text_muted))
                textSize = 11f
                setPadding(12, 18, 12, 18)
            }
            libraryList.addView(emptyState)
            return
        }

        visibleLibraries.forEach { hit ->
            val saved = local != null && !moduleUpdatePending && (
                local.name.equals(hit.fileName, ignoreCase = true) ||
                    (savedSha.isNotBlank() && savedSha.equals(hit.sha, ignoreCase = true))
                )
            val kb = (if (hit.size > 0) hit.size else local?.length() ?: 0L) / 1024
            val status = when {
                moduleUpdatePending -> "UPDATE"
                saved -> "SAVED"
                else -> "GITHUB"
            }
            addDrawerRow(
                name = hit.fileName,
                meta = "CODM · ${kb.coerceAtLeast(1)}kb",
                packageName = "Call of Duty Mobile",
                status = status,
                available = true,
                selected = saved,
                onClick = {
                    setLibraryDrawerOpen(false)
                    if (!saved && !isLoadingLibrary) beginLibraryLoad()
                }
            )
        }
    }

    private fun addDrawerRow(
        name: String,
        meta: String,
        packageName: String,
        status: String,
        available: Boolean,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val row = layoutInflater.inflate(R.layout.item_library_drawer, libraryList, false)
        row.findViewById<TextView>(R.id.drawer_name).text = name
        row.findViewById<TextView>(R.id.drawer_meta).text = meta
        row.findViewById<TextView>(R.id.drawer_package).apply {
            text = packageName
            visibility = if (packageName.isBlank()) View.GONE else View.VISIBLE
        }
        row.findViewById<TextView>(R.id.drawer_status).apply {
            text = status
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (available && status != "UPDATE") R.color.loader_status_active else R.color.loader_status_maintenance
                )
            )
            background = ContextCompat.getDrawable(
                this@MainActivity,
                if (available && status != "UPDATE") R.drawable.status_pill else R.drawable.maintenance_pill
            )
        }
        row.background = ContextCompat.getDrawable(
            this,
            if (selected) R.drawable.drawer_item_selected else R.drawable.drawer_item
        )
        row.findViewById<ImageView>(R.id.drawer_logo).setImageResource(R.drawable.app_logo)
        row.setOnClickListener { onClick() }
        libraryList.addView(row)
    }

    private fun selectLibraryFromDrawer(library: JSONObject) {
        if (!LibraryMetadata.isActive(library)) {
            showToast("${library.optString("NAME", "Library")} is under maintenance")
            return
        }
        handleSelectedJsonData(library)
        val filteredPosition = filteredLibraries.indexOfFirst {
            it.optString("NAME") == library.optString("NAME")
        }
        if (filteredPosition >= 0) {
            librarySpinner.setSelection(filteredPosition, false)
        }
        saveSelectedLibrary(selectedLibrary.name)
        setLibraryDrawerOpen(false)
    }

    private fun filterLibraries(includeTestLibraries: Boolean) {
        showTestLibraries = includeTestLibraries
        filteredLibraries.clear()
        if (includeTestLibraries) {
            filteredLibraries.addAll(allLibraries.filter { library ->
                library.optString("NAME").contains("TEST", ignoreCase = true) &&
                    LibraryMetadata.isActive(library)
            })
        } else {
            filteredLibraries.addAll(allLibraries.filter { library ->
                !library.optString("NAME").contains("TEST", ignoreCase = true) &&
                    LibraryMetadata.isActive(library)
            })
        }
        updateSpinnerWithFilteredData()
    }
    
    private fun updateSpinnerWithFilteredData() {
        val nameList = mutableListOf<String>()
        val logoList = mutableListOf<String>()
        
        for (item in filteredLibraries) {
            val name = item.optString("NAME")
            val logo = item.optString("LOGO")
            nameList.add(name)
            logoList.add(logo)
        }
        
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, nameList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    view = inflater.inflate(R.layout.spinner_item, parent, false)
                }
                val nameTextView = view!!.findViewById<TextView>(R.id.spinnerText)
                val logoImageView = view.findViewById<ImageView>(R.id.logoImage)
                nameTextView.text = " ${nameList[position]}"
                logoImageView.setImageResource(R.drawable.app_logo)

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return getView(position, convertView, parent)
            }
        }
        
        librarySpinner.adapter = adapter

        librarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View?, position: Int, id: Long) {
                applyLibrarySelection(position)
            }
            override fun onNothingSelected(parentView: AdapterView<*>) {
            }
        }
        
        val savedLibraryName = loaderPreferences.licenses.getSelectedLibrary()
        val savedPosition = filteredLibraries.indexOfFirst {
            it.optString("NAME") == savedLibraryName
        }
        val initialPosition = if (savedPosition >= 0) savedPosition else 0

        if (filteredLibraries.isNotEmpty()) {
            librarySpinner.setSelection(initialPosition, false)
            val initialName = filteredLibraries[initialPosition].optString("NAME")
            if (selectedLibrary.name != initialName || selectedLibrary.connectUrl.isBlank()) {
            applyLibrarySelection(initialPosition)
            }
        }
    }
    
    fun handleSelectedJsonData(jsonObject: JSONObject) {
        selectedLibrary = SelectedLibrary.fromJson(jsonObject)
        val maintenance = !LibraryMetadata.isActive(jsonObject)
        findViewById<TextView>(R.id.library_name).text = selectedLibrary.name
        findViewById<TextView>(R.id.library_game).text = LibraryMetadata.gameLabel(jsonObject)
        findViewById<TextView>(R.id.library_version).apply {
            text = LibraryMetadata.versionLabel(jsonObject)
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.library_package).apply {
            text = selectedLibrary.gamePackage
            visibility = if (selectedLibrary.gamePackage.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.library_status).apply {
            text = if (maintenance) "● Maintenance" else "● Ready"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (maintenance) R.color.loader_status_maintenance else R.color.loader_status_active
                )
            )
            background = ContextCompat.getDrawable(
                this@MainActivity,
                if (maintenance) R.drawable.maintenance_pill else R.drawable.status_pill
            )
        }
        if (maintenance) {
            isReadyToInject = false
            isLoadingLibrary = false
            loginButton.text = "UNAVAILABLE"
            loginButton.isEnabled = false
        } else {
            isReadyToInject = false
            loginButton.isEnabled = true
            loginButton.text = if (isLoadingLibrary) "…" else "LOAD"
            beginLibraryLoad()
        }
        appBanner.setImageResource(R.drawable.app_logo)
        refreshLibraryDrawer()
    }
    
    private fun showVersionMismatchDialog(message: String, updateUrl: String?) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
                .setTitle("Update Required")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("Exit") { _, _ ->
                    finishAffinity()
                }

            if (!updateUrl.isNullOrEmpty()) {
                builder.setPositiveButton("Update") { _, _ ->
                        try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(updateUrl)
                            )
                        )
                        finishAffinity()
                    } catch (e: Exception) {
                        finishAffinity()
                    }
                }
            }

            builder.show()
        }
    }
 
    private fun getAppVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun beginLibraryLoad() {
        if (isLoadingLibrary || isReadyToInject) return
        val detectedVpn = SplashActivity.getDetectedVPN()
        if (detectedVpn != null) {
            SplashActivity.showDialog(
                this,
                "VPN DETECTED",
                "Detected VPN Interface:\n$detectedVpn\n\nPlease disable your VPN and try again."
            )
            return
        }
        isLoadingLibrary = true
        setLibraryChip("load")
        libraryDownloadStatus.text = "downloading module…"
        setDownloadProgressUi(0, showBar = true)
        loginButton.isEnabled = false
        librarySpinner.isEnabled = false
        delaySpinner.isEnabled = false
        methodSpinner.isEnabled = false
        radioGroup.isEnabled = false
        saveSelectedLibrary(selectedLibrary.name)
        loginButton.text = "…"
        proceedWithDownload()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun saveSelectedLibrary(libraryName: String) {
        loaderPreferences.licenses.saveSelectedLibrary(libraryName)
    }

    private fun resetLoginState(message: String) {
        showToast(message)
        isLoadingLibrary = false
        loginButton.text = if (moduleUpdatePending) "UPDATE" else "RETRY LOAD"
        loginButton.isEnabled = true
        librarySpinner.isEnabled = true
        delaySpinner.isEnabled = true
        methodSpinner.isEnabled = true
        radioGroup.isEnabled = true
        isReadyToInject = false
    }

    private fun playEntrance() {
        listOf(R.id.card_header, R.id.card_profile, R.id.card_module).forEachIndexed { index, id ->
            findViewById<View>(id).apply {
                alpha = 0f
                translationY = 36f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L * index)
                    .setDuration(460)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        appBanner.scaleX = 0.86f
        appBanner.scaleY = 0.86f
        appBanner.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(520)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startCodmLoad() {
        selectedLibrary = SelectedLibrary(
            name = "CODM",
            architecture = "64bit",
            gamePackage = DEFAULT_GAME_PACKAGE,
            type = DEFAULT_GAME_TYPE
        )
        findViewById<TextView>(R.id.library_name).text = "CODM"
        findViewById<TextView>(R.id.library_game).text = "Call of Duty Mobile"
        findViewById<TextView>(R.id.library_version).text = "1.6.59"
        setLibraryChip("idle")
        lifecycleScope.launch(Dispatchers.IO) {
            val payloads = GithubSoSource.listPayloads()
            githubLibraries = payloads.orEmpty()
            githubListed = true
            val hit = payloads?.maxByOrNull { it.size }
            val local = localModuleFile()
            val savedSha = loaderPreferences.getModuleSha()
            val localSha = if (
                savedSha.isBlank() &&
                local != null &&
                hit?.fileName?.endsWith(".so", ignoreCase = true) == true
            ) {
                runCatching { ModuleUpdatePolicy.gitBlobSha1(local) }.getOrDefault("")
            } else {
                ""
            }
            val decision = ModuleUpdatePolicy.decide(
                localExists = local != null,
                localSize = local?.length() ?: 0L,
                localSha = localSha,
                savedSha = savedSha,
                remoteSha = hit?.sha.orEmpty(),
                remoteSize = hit?.size ?: 0L
            )
            withContext(Dispatchers.Main) {
                when (decision) {
                    ModuleUpdatePolicy.Decision.KEEP -> {
                        if (local != null) payloadFileName = local.name
                        if (hit != null && hit.sha.isNotBlank() && localSha.equals(hit.sha, ignoreCase = true)) {
                            loaderPreferences.saveModuleSha(hit.sha)
                        }
                        hideModuleUpdate()
                        applyLibraryReadyUi()
                        isReadyToInject = true
                        loginButton.text = "INJECT"
                    }
                    ModuleUpdatePolicy.Decision.DROP_AND_PROMPT -> {
                        discardLocalModule()
                        loaderPreferences.clearModuleSha()
                        isReadyToInject = false
                        isLoadingLibrary = false
                        showModuleUpdate(hit?.fileName.orEmpty())
                        if (hit != null && hit.sha.isNotBlank() && hit.sha != loaderPreferences.getNotifiedModuleSha()) {
                            ModuleUpdateNotifier.show(this@MainActivity)
                            loaderPreferences.setNotifiedModuleSha(hit.sha)
                        }
                        loginButton.text = "UPDATE"
                        loginButton.isEnabled = true
                    }
                    ModuleUpdatePolicy.Decision.MISSING -> {
                        hideModuleUpdate()
                        libraryDownloadStatus.text = "fetching module…"
                        setDownloadProgressUi(0, showBar = true)
                        beginLibraryLoad()
                    }
                }
                refreshLibraryDrawer()
            }
        }
    }

    private fun localModuleFile(): File? {
        return listOf(payloadFileName, LIBRARY_FILE_NAME)
            .distinct()
            .map { File(cacheDir, it) }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun discardLocalModule() {
        localModuleFile()?.delete()
        File(cacheDir, payloadFileName).delete()
        File(cacheDir, LIBRARY_FILE_NAME).delete()
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".so", ignoreCase = true)) file.delete()
        }
    }

    private fun showModuleUpdate(remoteName: String) {
        moduleUpdatePending = true
        val label = remoteName.ifBlank { "module" }
        moduleUpdateText.text = "$label changed. The saved copy was removed."
        moduleUpdateRow.visibility = View.VISIBLE
        libraryDownloadStatus.text = "new module. not saved yet."
        setDownloadProgressUi(0, showBar = false)
        setLibraryChip("update")
        findViewById<TextView>(R.id.library_package).text = "update"
    }

    private fun hideModuleUpdate() {
        moduleUpdatePending = false
        moduleUpdateRow.visibility = View.GONE
    }

    private fun setLibraryChip(state: String) {
        findViewById<TextView>(R.id.library_status).text = state
    }

    private fun applyLibraryReadyUi() {
        hideModuleUpdate()
        val f = File(cacheDir, payloadFileName)
        val kb = (f.length() / 1024).coerceAtLeast(1)
        libraryDownloadStatus.text = "module ready"
        setDownloadProgressUi(100, showBar = false)
        findViewById<TextView>(R.id.library_package).text = "$payloadFileName · ${kb}kb"
        setLibraryChip("ready")
    }

    private fun setDownloadProgressUi(percent: Int, showBar: Boolean) {
        val clamped = percent.coerceIn(0, 100)
        libraryDownloadPercent.text = "$clamped%"
        libraryDownloadProgress.progress = clamped
        libraryDownloadProgress.visibility = if (showBar) View.VISIBLE else View.GONE
        if (!showBar && clamped >= 100) {
            libraryDownloadPercent.text = "100%"
        }
    }

    private fun proceedWithDownload() {
        val detectedVpn = SplashActivity.getDetectedVPN()
        if (detectedVpn == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                libraryDownloadUrl = LibraryDownloadUrls.resolvePrimary()
                payloadFileName = LibraryDownloadUrls.resolvedName
                if (libraryDownloadUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        resetLoginState("No .so in PasswordAndLicense")
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    libraryDownloadStatus.text = "downloading module…"
                    setDownloadProgressUi(0, showBar = true)
                }
                downloadLibraryIfNeeded(this@MainActivity, libraryDownloadUrl, payloadFileName) { success ->
                if (success) {
                    val saved = GithubSoSource.materialize(File(cacheDir, payloadFileName))
                    payloadFileName = saved.name
                    remoteLibrarySize = saved.length().toInt()
                    GithubSoSource.lastHit?.sha?.takeIf { it.isNotBlank() }?.let {
                        loaderPreferences.saveModuleSha(it)
                    }
                    showToast("Module saved")
                    if (!isLibraryReady()) {
                        resetLoginState("FAILED TO LOAD RUNTIME LIBRARY")
                        isReadyToInject = false
                    } else {
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(400)
                            isLoadingLibrary = false
                            applyLibraryReadyUi()
                            refreshLibraryDrawer()
                            loginButton.text = "INJECT"
                            userLog("module ok")
                            loginButton.isEnabled = true
                            radioGroup.isEnabled = true
                            delaySpinner.isEnabled = true
                            methodSpinner.isEnabled = true
                            librarySpinner.isEnabled = true
                            isReadyToInject = true
                        }
                    }
                } else {
                    resetLoginState("FAILED TO DOWNLOAD, \nRETRY AGAIN.")
                    isReadyToInject = false
                }
                }
            }
        } else {
            resetLoginState("VPN detected — disable VPN to download")
        }
    }

    private fun isLibraryReady(): Boolean {
        val libraryFile = File(cacheDir, payloadFileName)
        return libraryFile.isFile && libraryFile.length() > 0L
    }
    
    private fun injectLibrary(libraryFileName: String) {
        val appCacheDir = cacheDir.absolutePath
        val outputFile = File(appCacheDir, libraryFileName)
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            return
        }
        if (remoteLibrarySize > 0 && outputFile.length().toInt() != remoteLibrarySize) {
            return
        }
        remoteLibrarySize = outputFile.length().toInt()
        
        val cacheDirectory = File(cacheDir.absolutePath)
        cacheDirectory.setReadable(true, false)
        cacheDirectory.setWritable(true, false)
        cacheDirectory.setExecutable(true, false)
        cacheDirectory.listFiles()?.forEach { file ->
            file.setExecutable(true, false)
        }
        if (!isLibraryReady()) {
            resetLoginState("FAILED TO LOAD RUNTIME LIBRARY")
            return
        }
        val injectLibrary = libraryForInjection()
        val gamePackage = DEFAULT_GAME_PACKAGE
        clearInjectLog()
        userLog("inject…")
        loginButton.text = "…"
        lifecycleScope.launch {
            if (delaySeconds > 0) delay(delaySeconds * 1000L)
            runInjectPipeline(
                libraryFile = outputFile,
                cacheDirectory = cacheDirectory,
                libraryFileName = libraryFileName,
                gamePackage = gamePackage,
                library = injectLibrary
            )
        }
    }

    private suspend fun runInjectPipeline(
        libraryFile: File,
        cacheDirectory: File,
        libraryFileName: String,
        gamePackage: String,
        library: SelectedLibrary
    ) {
        val gspace = withContext(Dispatchers.IO) { ProcessFinder.inVirtualSpace() }
        userLog("waiting…")
        val targetUnity = ensureCodmRunning(gamePackage, maxAttempts = 120)
        if (targetUnity == null) {
            userLog("no game")
            withContext(Dispatchers.Main) {
                showToast("Install CODM")
                finishInjectUi(success = false)
            }
            return
        }

        var activeUnity = targetUnity

        if (gspace) {
            delay(maxOf(delaySeconds, 12) * 1000L)
            val refreshed = withContext(Dispatchers.IO) { ProcessFinder.findCodm() }
                ?: ensureCodmRunning(gamePackage, maxAttempts = 60)
            if (refreshed != null) activeUnity = refreshed
            else {
                userLog("game lost")
                withContext(Dispatchers.Main) {
                    finishInjectUi(success = false)
                }
                return
            }
        }

        val preferred = loaderPreferences.getPreferredInjectTarget()
        val targets = InjectionCommandBuilder.injectTargetAttempts(
            unity = activeUnity,
            gamePackage = gamePackage,
            inGspace = gspace,
            preferredTarget = preferred
        )
        var loaded = false
        var lastExit = -1
        var winningTarget: String? = null
        val libPath = libraryFile.absolutePath
        for (target in targets) {
            userLog("inject…")
            lastExit = withContext(Dispatchers.IO) {
                AccelInject.nativeInject(target, libPath)
            }
            if (lastExit == -100) {
                userLog("need arm64")
                break
            }
            if (lastExit == 0) userLog("rc ok") else userLog("rc $lastExit")
            delay(1200)
            val verifyPid = withContext(Dispatchers.IO) {
                ProcessFinder.findCodm()?.pid ?: activeUnity.pid
            }
            if (!proc_alive(verifyPid)) {
                userLog("game stopped")
                break
            }
            loaded = withContext(Dispatchers.IO) { InjectVerifier.isLibLoaded(verifyPid, libraryFileName) }
            if (loaded) {
                winningTarget = target
                userLog("loaded")
                break
            }
        }

        winningTarget?.let { loaderPreferences.savePreferredInjectTarget(it) }

        withContext(Dispatchers.Main) {
            when {
                loaded -> {
                    userLog("done")
                    showToast("Inject OK")
                    launchGame(gamePackage)
                    loginButton.text = "RE-INJECT"
                }
                lastExit != 0 -> {
                    userLog("failed")
                    showToast("Inject failed")
                    loginButton.text = "RETRY"
                }
                else -> {
                    userLog("retry?")
                    showToast("Try again in match")
                    loginButton.text = "RE-INJECT"
                }
            }
            finishInjectUi(success = loaded)
        }
    }

    private fun finishInjectUi(success: Boolean) {
        loginButton.isEnabled = true
        radioGroup.isEnabled = true
        delaySpinner.isEnabled = true
        if (!success) {
            loginButton.text = "RETRY"
        }
    }

    private suspend fun ensureCodmRunning(
        gamePackage: String,
        maxAttempts: Int
    ): ProcessFinder.Target? {
        for (attempt in 0 until maxAttempts) {
            val found = withContext(Dispatchers.IO) { ProcessFinder.findCodm() }
            if (found != null) {
                userLog("game ok")
                return found
            }
            if (attempt == 0 || attempt % 6 == 0) {
                withContext(Dispatchers.Main) {
                    val launched = launchGame(gamePackage)
                    if (!launched) {
                        userLog("not installed")
                    } else {
                        userLog("open game…")
                        bringAccelToFront()
                    }
                }
            }
            if (attempt % 10 == 0 && attempt > 0) {
                userLog("waiting…")
            }
            delay(500)
        }
        return null
    }

    private fun bringAccelToFront() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }, 350)
    }

    private fun proc_alive(pid: Int): Boolean {
        if (pid <= 0) return false
        return File("/proc/$pid").exists()
    }

    private fun libraryForInjection(): SelectedLibrary {
        val pkg = DEFAULT_GAME_PACKAGE
        val arch = SelectedLibrary.resolveArchitecture(selectedLibrary.architecture)
        return selectedLibrary.copy(gamePackage = pkg, architecture = arch)
    }

    
    private fun launchGame(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                true
            } else {
                Toast.makeText(this, "Game not installed!", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start game!", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    private fun downloadLibraryIfNeeded(
        context: Context,
        url: String,
        libraryFileName: String,
        callback: (Boolean) -> Unit
    ) {
        val libraryDownloader = LibraryDownloader(context)
        val outputFile = File(context.cacheDir, libraryFileName)
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val connection = URL(url).openConnection()
                try {
                    connection.connect()
                    val reportedSize = connection.contentLength
                    remoteLibrarySize = if (reportedSize > 0) reportedSize else 0
                    val needToDownload = moduleUpdatePending || if (outputFile.exists() && outputFile.length() > 0L) {
                        remoteLibrarySize > 0 && outputFile.length() != remoteLibrarySize.toLong()
                    } else {
                        true
                    }

                    if (!needToDownload) {
                        remoteLibrarySize = outputFile.length().toInt()
                        success = true
                    } else {
                        withContext(Dispatchers.Main) {
                            setDownloadProgressUi(0, showBar = true)
                        }
                        success = libraryDownloader.download(url, libraryFileName) { progress ->
                            runOnUiThread {
                                setDownloadProgressUi(progress, showBar = true)
                            }
                        }
                        if (success && outputFile.exists()) {
                            remoteLibrarySize = outputFile.length().toInt()
                        }
                    }
                } finally {
                    (connection as? HttpURLConnection)?.disconnect()
                }
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }
    
    private fun updateRootStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val isRooted = commandRunner.hasRootAccess()
            withContext(Dispatchers.Main) {
                if (isRooted) {
                    rootStatusView.text = "rooted · ready"
                    rootStatusView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.loader_status_active))
                } else {
                    rootStatusView.text = "no root"
                    rootStatusView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.loader_status_maintenance))
                }
                rootStatusView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun cleanUp() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        LibraryDownloader.cancelNotification(this)
    }
    
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            cleanUp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUp()
    }
}
