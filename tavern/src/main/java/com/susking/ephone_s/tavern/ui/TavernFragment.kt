package com.susking.ephone_s.tavern.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.TavernForegroundEvent
import com.susking.ephone_s.tavern.R
import com.susking.ephone_s.tavern.databinding.FragmentTavernBinding
import com.susking.ephone_s.tavern.ui.jinnang.JinnangHost
import com.susking.ephone_s.tavern.ui.jinnang.JinnangHomeFragment
import com.susking.ephone_s.tavern.ui.jinnang.PromptStorageFragment
import com.susking.ephone_s.tavern.ui.jinnang.TavernChatSaver
import com.susking.ephone_s.tavern.ui.jinnang.TavernRegexImporter
import kotlin.math.abs

/**
 * 酒馆入口界面。
 *
 * 用 WebView 承载运行在 Termux 中的 SillyTavern 前端（默认 [SERVER_URL]）。
 * 本模块只负责「外壳」：把酒馆网页嵌进小手机；记忆库、脚本助手等扩展仍在网页内原生运行。
 *
 * WebView 实例由 [TavernWebViewHolder] 进程级缓存，跨 Fragment 重建存活，
 * 以支持「挂后台不重新加载」——返回键弹对话框让用户选择真正退出还是挂后台。
 */
class TavernFragment : Fragment(), JinnangHost {

    private var _binding: FragmentTavernBinding? = null
    private val binding get() = _binding!!

    // 当前挂入布局的 WebView（来自 TavernWebViewHolder 缓存）
    private var webView: WebView? = null

    // 文件选择回调：酒馆导入角色卡/图片时由 WebChromeClient 触发，结果回传给网页
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // 主框架是否加载失败，用于决定是否展示引导层
    private var hasLoadError: Boolean = false

    // 本次进入酒馆是否已自动拉起过 Termux：仅自动拉起一次，避免用户来回切换被反复拉走
    private var hasAutoLaunchedTermux: Boolean = false

    // 返回键回调：WebView 可回退则回退，否则弹「退出/挂后台」对话框
    private var backPressedCallback: OnBackPressedCallback? = null

    // 背景色采样：周期性读取酒馆网页 body 背景色，同步到根布局，使系统栏区域随酒馆背景变化
    private val backgroundSampler: Handler = Handler(Looper.getMainLooper())
    private val backgroundSampleRunnable: Runnable = object : Runnable {
        override fun run() {
            sampleTavernBackgroundColor()
            backgroundSampler.postDelayed(this, BACKGROUND_SAMPLE_INTERVAL_MS)
        }
    }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            deliverFileChooserResult(result)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTavernBinding.inflate(inflater, container, false)
        attachWebView()
        applyWindowInsets()
        binding.tavernRetryButton.setOnClickListener { loadTavern() }
        binding.tavernOpenTermuxButton.setOnClickListener { openTermux() }
        setupJinnangFab()
        setupBackNavigation()
        return binding.root
    }

    /**
     * 从缓存取出 WebView 挂入容器：首次创建并加载，复用时直接挂回（不重新加载）。
     */
    private fun attachWebView() {
        val (view, isNewlyCreated) = TavernWebViewHolder.obtain(requireContext()) { configureWebView(it) }
        webView = view
        // 复用前先从可能存在的旧父布局摘下，避免重复 addView 抛异常
        (view.parent as? ViewGroup)?.removeView(view)
        binding.tavernWebViewContainer.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // 每次进场重新绑定文件选择回调到当前 Fragment（旧 Fragment 已销毁）
        view.webChromeClient = createWebChromeClient()
        view.onResume()
        if (isNewlyCreated) {
            loadTavern()
        } else {
            // 复用缓存的 WebView：说明此前已加载成功（能挂后台），直接显示锦囊 FAB
            binding.jinnangFab.show()
        }
    }

    /**
     * 避让系统栏与键盘（bug2/bug3）。
     *
     * 应用为边到边布局（BaseActivity 关闭了 decorFitsSystemWindows），故需手动消费 insets：
     * 顶部与左右留系统栏，底部取「导航栏」与「输入法」较大值——键盘弹出时容器收缩，输入框随之上移可见。
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }
    }

    /**
     * 判断宿主 app 是否为可调试构建。
     * 用 ApplicationInfo 的 FLAG_DEBUGGABLE，避免库模块依赖宿主 BuildConfig。
     */
    private fun isHostAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * 读取酒馆网页 body 的背景色并同步到根布局背景。
     *
     * 边到边布局下状态栏/导航栏透明，其下透出的是根布局背景；据此让系统栏区域随酒馆背景色变化。
     * 注意：当酒馆背景是图片或半透明时，computed background-color 多为透明，此时保持主题默认背景不变。
     */
    private fun sampleTavernBackgroundColor() {
        val target = webView ?: return
        if (hasLoadError) return
        target.evaluateJavascript(
            "(function(){return window.getComputedStyle(document.body).backgroundColor;})();"
        ) { result: String? ->
            val color: Int = parseCssColor(result) ?: return@evaluateJavascript
            _binding?.root?.setBackgroundColor(color)
        }
    }

    /**
     * 解析 evaluateJavascript 回传的 CSS 颜色字符串（形如 "rgb(r, g, b)" 或 "rgba(r, g, b, a)"）。
     * 完全透明（alpha 为 0）视为无有效背景色，返回 null 以保留主题默认背景。
     */
    private fun parseCssColor(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        // evaluateJavascript 回传值带引号转义，先去除首尾引号
        val value: String = raw.trim().trim('"')
        val numbers: List<Float> = Regex("[0-9.]+").findAll(value)
            .map { it.value.toFloat() }
            .toList()
        if (numbers.size < 3) return null
        val red: Int = numbers[0].toInt().coerceIn(COLOR_MIN, COLOR_MAX)
        val green: Int = numbers[1].toInt().coerceIn(COLOR_MIN, COLOR_MAX)
        val blue: Int = numbers[2].toInt().coerceIn(COLOR_MIN, COLOR_MAX)
        // rgba 第四位为 alpha（0-1），缺省视为不透明
        val alpha: Float = numbers.getOrNull(3) ?: 1f
        if (alpha <= 0f) return null
        return Color.rgb(red, green, blue)
    }

    /**
     * 配置 WebView 以满足酒馆前端的运行需求（仅首次创建时调用一次）。
     * 仅开启酒馆必需的能力，关闭 file:// 相关访问以收敛安全面。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        // 仅在可调试构建开启远程调试（chrome://inspect），正式包关闭以收敛安全面
        if (isHostAppDebuggable(target.context)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        target.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // 允许音频等媒体自动播放，供脚本助手类扩展使用
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            // 收敛安全面：禁止访问本地 file:// 资源
            allowFileAccess = false
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
        }
        target.webViewClient = createWebViewClient()
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            // 所有跳转都留在 WebView 内，不外抛到系统浏览器
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // 仅主框架加载失败才视为「服务未连接」，子资源失败忽略
                if (request.isForMainFrame) {
                    hasLoadError = true
                    handleLoadFailure()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                // 主框架成功加载完成则隐藏引导层，并显示锦囊 FAB
                if (!hasLoadError) {
                    hideErrorOverlay()
                    binding.jinnangFab.show()
                }
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // 取消上一个未完成的选择，避免网页端回调被阻塞
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return launchFileChooser(fileChooserParams)
            }
        }
    }

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams): Boolean {
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "文件选择器启动失败", e)
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            false
        }
    }

    private fun deliverFileChooserResult(result: ActivityResult) {
        val callback = fileChooserCallback ?: return
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        callback.onReceiveValue(uris)
        fileChooserCallback = null
    }

    /**
     * 加载酒馆首页：重置错误标记并隐藏引导层后发起加载。
     */
    private fun loadTavern() {
        hasLoadError = false
        hideErrorOverlay()
        // 加载期间先隐藏锦囊 FAB，待 onPageFinished 成功后再显示
        binding.jinnangFab.hide()
        webView?.loadUrl(SERVER_URL)
    }

    private fun showErrorOverlay() {
        binding.tavernErrorOverlay.visibility = View.VISIBLE
        binding.tavernWebViewContainer.visibility = View.INVISIBLE
    }

    private fun hideErrorOverlay() {
        binding.tavernErrorOverlay.visibility = View.GONE
        binding.tavernWebViewContainer.visibility = View.VISIBLE
    }

    /**
     * 主框架加载失败时的处理（任务1）。
     *
     * 探测到酒馆服务不可用时：
     * - 已安装 Termux：自动拉起 Termux（每个 Fragment 生命周期仅一次，避免与用户来回切换反复横跳），
     *   并展示「打开 Termux」+「重试」引导页；用户在 Termux 启动 SillyTavern 后回来点重试。
     * - 未安装 Termux：直接展示错误页（提示安装并启动服务），仅保留「重试」。
     *
     * 注：Android 拉起他应用需在 manifest 声明对 com.termux 的包可见性（已在本模块 manifest 声明）；
     * 自动在 Termux 内跑启动命令需 RUN_COMMAND 权限且用户在 Termux 端额外配置，未采用，仅拉起 App。
     */
    private fun handleLoadFailure() {
        // 加载失败：隐藏锦囊 FAB，若锦囊浮层已展开也一并关闭（无可用对话场景）
        binding.jinnangFab.hide()
        if (isJinnangOpen()) {
            closeJinnang()
        }
        val isTermuxInstalled: Boolean = isTermuxInstalled()
        if (isTermuxInstalled) {
            binding.tavernErrorMessage.setText(R.string.tavern_error_message)
            binding.tavernOpenTermuxButton.visibility = View.VISIBLE
            // 自动拉起 Termux：本次进入酒馆仅尝试一次，避免用户在两端来回切换被反复拉走
            if (!hasAutoLaunchedTermux) {
                hasAutoLaunchedTermux = true
                openTermux()
            }
        } else {
            binding.tavernErrorMessage.setText(R.string.tavern_error_message_no_termux)
            binding.tavernOpenTermuxButton.visibility = View.GONE
        }
        showErrorOverlay()
    }

    /** 判断是否安装了 Termux（能取到其启动 Intent 即视为已安装）。 */
    private fun isTermuxInstalled(): Boolean {
        return requireContext().packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null
    }

    /** 拉起 Termux App；用户需自行在其中启动 SillyTavern 服务，再回来点重试。 */
    private fun openTermux() {
        val launchIntent: Intent? =
            requireContext().packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launchIntent == null) {
            // 极端情况下（拉起前被卸载）退回未安装态
            handleLoadFailure()
            return
        }
        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.tavern_termux_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 返回键处理：优先在 WebView 内回退（如酒馆内二级面板）；
     * 无可回退时弹出对话框，让用户选择真正退出（销毁 WebView）还是挂后台（保留复用）。
     */
    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 优先处理锦囊覆盖层：有子页面回退栈先回退，否则关闭锦囊
                if (isJinnangOpen()) {
                    if (childFragmentManager.backStackEntryCount > 0) {
                        childFragmentManager.popBackStack()
                    } else {
                        closeJinnang()
                    }
                    return
                }
                val currentWebView = webView
                if (currentWebView != null && currentWebView.canGoBack()) {
                    currentWebView.goBack()
                } else {
                    showExitOptionsDialog()
                }
            }
        }
        backPressedCallback = callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    /**
     * 弹出「挂后台 / 退出」选择框（bug4）。
     * - 挂后台：仅离开页面，WebView 留在缓存，下次进入复用、不重新加载。
     * - 退出酒馆：销毁缓存的 WebView，下次进入重新加载。
     */
    private fun showExitOptionsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tavern_exit_title)
            .setMessage(R.string.tavern_exit_message)
            .setPositiveButton(R.string.tavern_exit_background) { _, _ ->
                // 挂后台：保留 WebView，仅退出当前 Fragment
                navigateBack()
            }
            .setNegativeButton(R.string.tavern_exit_quit) { _, _ ->
                // 真正退出：销毁缓存的 WebView
                TavernWebViewHolder.destroy()
                webView = null
                navigateBack()
            }
            .setNeutralButton(R.string.tavern_exit_cancel, null)
            .show()
    }

    /**
     * 退出当前酒馆 Fragment，交还系统默认返回处理（弹出返回栈）。
     */
    private fun navigateBack() {
        val callback = backPressedCallback ?: return
        callback.isEnabled = false
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        backgroundSampler.removeCallbacks(backgroundSampleRunnable)
        // 离开酒馆：恢复 brain 悬浮窗
        EventBus.post(TavernForegroundEvent(isForeground = false))
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        // 前台时周期采样酒馆背景色，离开页面即停止
        backgroundSampler.post(backgroundSampleRunnable)
        // 进入酒馆：隐藏 brain 悬浮窗，改由「锦囊」接管
        EventBus.post(TavernForegroundEvent(isForeground = true))
    }

    override fun onDestroyView() {
        backgroundSampler.removeCallbacks(backgroundSampleRunnable)
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        // 解绑文件选择回调，把 WebView 从容器摘下保留到缓存（不销毁，供下次复用）
        webView?.webChromeClient = null
        TavernWebViewHolder.detach()
        webView = null
        backPressedCallback = null
        super.onDestroyView()
        _binding = null
    }

    // ==================== 「锦囊」伪悬浮窗（任务2/任务3）====================

    /**
     * 配置锦囊 FAB：可拖拽贴边 + 点击展开锦囊主面板。
     * 拖拽逻辑参照 BrainFragment：移动距离小于阈值视为点击，松手后吸附到最近边。
     */
    private fun setupJinnangFab() {
        var initialX = 0f
        var initialY = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f

        binding.jinnangFab.setOnClickListener { openJinnang() }
        // 点击遮罩空白处关闭锦囊（卡片自身 clickable 已拦截点击，不会穿透）
        binding.jinnangOverlay.setOnClickListener { closeJinnang() }
        binding.jinnangFab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = view.x
                    initialY = view.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = initialX + (event.rawX - initialTouchX)
                    view.y = initialY + (event.rawY - initialTouchY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) < CLICK_THRESHOLD_PX && abs(dy) < CLICK_THRESHOLD_PX) {
                        view.performClick()
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val targetX = if (view.x + view.width / 2 < screenWidth / 2) {
                            0f
                        } else {
                            (screenWidth - view.width).toFloat()
                        }
                        view.animate().x(targetX).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 锦囊覆盖层是否正在显示。 */
    private fun isJinnangOpen(): Boolean = _binding?.jinnangOverlay?.visibility == View.VISIBLE

    /** 展开锦囊：显示浮层并装入主面板，隐藏 FAB 避免遮挡。 */
    private fun openJinnang() {
        if (isJinnangOpen()) return
        binding.jinnangOverlay.visibility = View.VISIBLE
        binding.jinnangFab.hide()
        if (childFragmentManager.findFragmentById(R.id.jinnang_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.jinnang_container, JinnangHomeFragment(), JINNANG_HOME_TAG)
                .commit()
        }
    }

    // ==================== JinnangHost 实现 ====================

    override fun jinnangNavigateToPromptStorage() {
        childFragmentManager.beginTransaction()
            .replace(R.id.jinnang_container, PromptStorageFragment(), JINNANG_PROMPT_STORAGE_TAG)
            .addToBackStack(JINNANG_PROMPT_STORAGE_TAG)
            .commit()
    }

    override fun jinnangRequestSaveChat() {
        extractAndSaveCurrentChat()
    }

    override fun jinnangRequestPullRegex() {
        pullTavernRegex()
    }

    override fun jinnangClose() {
        closeJinnang()
    }

    /** 关闭锦囊：清空子页面回退栈、隐藏浮层、恢复 FAB（仅在加载成功时恢复）。 */
    private fun closeJinnang() {
        // 清空锦囊内部回退栈，回到干净状态
        childFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        binding.jinnangOverlay.visibility = View.GONE
        // 仅当酒馆加载成功时才恢复 FAB；加载失败时保持隐藏
        if (!hasLoadError) {
            binding.jinnangFab.show()
        }
    }

    /**
     * 任务3：提取当前 SillyTavern 对话并保存到酒馆记录。
     *
     * 提取含异步步骤（fetch 头像转 base64），`evaluateJavascript` 无法等待 Promise，
     * 故先启动 [TavernChatSaver.EXTRACT_AND_SAVE_JS]（结果写入网页全局变量），
     * 再轮询 [TavernChatSaver.POLL_RESULT_JS] 读取结果。
     * 解析失败（酒馆未加载/版本过旧/超时）给提示不崩溃。成功则弹命名框，确认后写入 chat_records。
     */
    private fun extractAndSaveCurrentChat() {
        val target: WebView? = webView
        if (target == null || hasLoadError) {
            Toast.makeText(requireContext(), R.string.save_chat_extract_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // 启动异步提取任务（结果稍后写入网页全局变量）
        target.evaluateJavascript(TavernChatSaver.EXTRACT_AND_SAVE_JS, null)
        pollExtractResult(target, attempt = 0)
    }

    /**
     * 轮询读取提取结果。
     *
     * 返回空串表示异步任务尚未完成，继续轮询；超过 [EXTRACT_MAX_ATTEMPTS] 次仍为空视为超时失败。
     * 拿到非空结果即交给 [TavernChatSaver.parseExtractResult] 解析。
     */
    private fun pollExtractResult(target: WebView, attempt: Int) {
        if (!isAdded || _binding == null) return
        target.evaluateJavascript(TavernChatSaver.POLL_RESULT_JS) { rawResult ->
            // evaluateJavascript 回传带引号转义：空字符串结果会是 "\"\"" 或 "null"
            val isEmpty: Boolean = rawResult.isNullOrBlank() ||
                rawResult == "null" || rawResult == "\"\""
            if (isEmpty) {
                if (attempt >= EXTRACT_MAX_ATTEMPTS) {
                    Toast.makeText(requireContext(), R.string.save_chat_extract_failed, Toast.LENGTH_SHORT).show()
                } else {
                    backgroundSampler.postDelayed(
                        { pollExtractResult(target, attempt + 1) },
                        EXTRACT_POLL_INTERVAL_MS
                    )
                }
                return@evaluateJavascript
            }
            val extracted = TavernChatSaver.parseExtractResult(rawResult)
            when {
                extracted == null ->
                    Toast.makeText(requireContext(), R.string.save_chat_extract_failed, Toast.LENGTH_SHORT).show()
                extracted.messageCount == 0 ->
                    Toast.makeText(requireContext(), R.string.save_chat_empty, Toast.LENGTH_SHORT).show()
                else -> showSaveChatNameDialog(extracted)
            }
        }
    }

    /** 弹命名框：预填「角色名_时间」，可改名后保存。 */
    private fun showSaveChatNameDialog(extracted: TavernChatSaver.ExtractedChat) {
        val context = requireContext()
        val input = android.widget.EditText(context).apply {
            setText(TavernChatSaver.buildDefaultName(extracted.name2))
            setSelection(text.length)
        }
        val paddingPx = (resources.displayMetrics.density * 20).toInt()
        val container = android.widget.FrameLayout(context).apply {
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.save_chat_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save_chat_confirm) { _, _ ->
                val name = input.text?.toString().orEmpty()
                saveExtractedChat(extracted, name)
            }
            .setNegativeButton(R.string.save_chat_cancel, null)
            .show()
    }

    private fun saveExtractedChat(extracted: TavernChatSaver.ExtractedChat, fileName: String) {
        try {
            val file = TavernChatSaver.save(requireContext(), fileName, extracted)
            Toast.makeText(
                requireContext(),
                getString(R.string.save_chat_success) + "：" + file.name,
                Toast.LENGTH_SHORT
            ).show()
            closeJinnang()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "保存对话到酒馆记录失败", e)
            Toast.makeText(requireContext(), R.string.save_chat_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 一键拉取酒馆全局正则到酒馆记录（clouddreams）。
     *
     * 读 `SillyTavern.getContext().extensionSettings.regex` → 转 clouddreams RegexRule → 合并写入
     * 其全局正则 prefs。之后酒馆记录打开任意记录自动套用。提取失败/无正则均给提示不崩溃。
     */
    private fun pullTavernRegex() {
        val target: WebView? = webView
        if (target == null || hasLoadError) {
            Toast.makeText(requireContext(), R.string.pull_regex_failed, Toast.LENGTH_SHORT).show()
            return
        }
        target.evaluateJavascript(TavernRegexImporter.EXTRACT_JS) { rawResult ->
            val result = TavernRegexImporter.importFromExtractResult(requireContext(), rawResult)
            when {
                !result.parsedOk ->
                    Toast.makeText(requireContext(), R.string.pull_regex_failed, Toast.LENGTH_SHORT).show()
                result.importedCount == 0 ->
                    Toast.makeText(requireContext(), R.string.pull_regex_empty, Toast.LENGTH_SHORT).show()
                else -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pull_regex_success, result.importedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    closeJinnang()
                }
            }
        }
    }

    companion object {
        private const val TAG: String = "TavernFragment"

        // 酒馆服务默认地址（Termux 中 SillyTavern 默认监听 127.0.0.1:8000）。
        // TODO: 后续接入设置模块以支持自定义主机与端口。
        private const val SERVER_URL: String = "http://127.0.0.1:8000"

        // Termux 应用包名，用于探测安装与拉起
        private const val TERMUX_PACKAGE: String = "com.termux"

        // 锦囊子页面在 childFragmentManager 中的 tag / 回退栈名
        private const val JINNANG_HOME_TAG: String = "jinnang_home"
        private const val JINNANG_PROMPT_STORAGE_TAG: String = "jinnang_prompt_storage"

        // FAB 拖拽判定阈值：移动小于此像素视为点击
        private const val CLICK_THRESHOLD_PX: Int = 10

        // 背景色采样间隔，兼顾「酒馆内切换主题后及时跟随」与「低开销」
        private const val BACKGROUND_SAMPLE_INTERVAL_MS: Long = 1500L

        // 保存对话提取结果轮询：间隔与最大次数（约 8 秒超时，覆盖头像 fetch 慢的场景）
        private const val EXTRACT_POLL_INTERVAL_MS: Long = 200L
        private const val EXTRACT_MAX_ATTEMPTS: Int = 40

        // 颜色通道取值范围
        private const val COLOR_MIN: Int = 0
        private const val COLOR_MAX: Int = 255

        @JvmStatic
        fun newInstance(): TavernFragment = TavernFragment()
    }
}
