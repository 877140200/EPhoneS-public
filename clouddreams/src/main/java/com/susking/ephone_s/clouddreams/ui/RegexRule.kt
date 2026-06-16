package com.susking.ephone_s.clouddreams.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.susking.ephone_s.clouddreams.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

// 数据类，表示一个正则规则
data class RegexRule(
    val id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var findPattern: String = "",
    var replacePattern: String = "",
    var trimStrings: List<String> = emptyList(),
    var enabled: Boolean = true,
    var runOnEdit: Boolean = false,
    var onlyFormatDisplay: Boolean = false,
    var onlyFormatPrompt: Boolean = false,
    var affectsInput: Boolean = true,
    var affectsOutput: Boolean = true,
    var affectsCommands: Boolean = true,
    var affectsWorldInfo: Boolean = true,
    var minDepth: Int? = null,
    var maxDepth: Int? = null,
    var isGlobal: Boolean = true // 区分全局和局部规则
)

class RegexEditorActivity : AppCompatActivity() {
    private lateinit var currentRule: RegexRule
    private var isTestMode = false
    private var isEditingExisting = false

    // UI元素声明
    private lateinit var regexTestModeToggle: Button
    private lateinit var regexTestMode: LinearLayout
    private lateinit var regexTestInput: EditText
    private lateinit var regexTestOutput: EditText
    private lateinit var regexScriptName: EditText
    private lateinit var findRegex: EditText
    private lateinit var regexReplaceString: EditText
    private lateinit var regexTrimStrings: EditText
    private lateinit var replacePosition1: CheckBox
    private lateinit var replacePosition2: CheckBox
    private lateinit var replacePosition3: CheckBox
    private lateinit var replacePosition5: CheckBox
    private lateinit var disabled: CheckBox
    private lateinit var runOnEdit: CheckBox
    private lateinit var onlyFormatDisplay: CheckBox
    private lateinit var onlyFormatPrompt: CheckBox
    private lateinit var minDepth: EditText
    private lateinit var maxDepth: EditText
    private lateinit var editorSaveBtn: Button
    private lateinit var editorCloseBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.oo_regex_editor) // 使用regex.xml布局

        // 初始化UI元素
        initViews()

        // 获取传递的参数
        val ruleId = intent.getStringExtra("rule_id")
        val isGlobal = intent.getBooleanExtra("is_global", true)
        val cardId = intent.getStringExtra("card_id") // 获取卡片ID


        // 加载规则或创建新规则
        if (ruleId != null) {
            currentRule = loadRuleById(ruleId, isGlobal, cardId) // 传递卡片ID
            isEditingExisting = true
        } else {
            currentRule = RegexRule(isGlobal = isGlobal)
        }

        setupUI()
        populateForm()
        setupTestMode()
    }

    private fun initViews() {
        regexTestModeToggle = findViewById(R.id.regex_test_mode_toggle)
        regexTestMode = findViewById(R.id.regex_test_mode)
        regexTestInput = findViewById(R.id.regex_test_input)
        regexTestOutput = findViewById(R.id.regex_test_output)
        regexScriptName = findViewById(R.id.regex_script_name)
        findRegex = findViewById(R.id.find_regex)
        regexReplaceString = findViewById(R.id.regex_replace_string)
        regexTrimStrings = findViewById(R.id.regex_trim_strings)
        replacePosition1 = findViewById(R.id.replace_position_1)
        replacePosition2 = findViewById(R.id.replace_position_2)
        replacePosition3 = findViewById(R.id.replace_position_3)
        replacePosition5 = findViewById(R.id.replace_position_5)
        disabled = findViewById(R.id.disabled)
        runOnEdit = findViewById(R.id.run_on_edit)
        onlyFormatDisplay = findViewById(R.id.only_format_display)
        onlyFormatPrompt = findViewById(R.id.only_format_prompt)
        minDepth = findViewById(R.id.min_depth)
        maxDepth = findViewById(R.id.max_depth)
        editorSaveBtn = findViewById(R.id.editorSaveBtn)
        editorCloseBtn = findViewById(R.id.editorCloseBtn)
    }

    private fun setupUI() {
        // 设置测试模式切换
        regexTestModeToggle.setOnClickListener {
            isTestMode = !isTestMode
            updateTestModeVisibility()
        }

        // 设置保存按钮
        editorSaveBtn.setOnClickListener {
            if (saveRule()) {
                // 发送广播通知规则列表需要刷新
                sendBroadcast(Intent("REFRESH_REGEX_RULES"))
                finish()
            }
        }

        // 设置取消按钮
        editorCloseBtn.setOnClickListener {
            finish()
        }

        // 实时测试功能
        regexTestInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runTest()
            }
        })
    }

    private fun populateForm() {
        regexScriptName.setText(currentRule.name)
        findRegex.setText(currentRule.findPattern)
        regexReplaceString.setText(currentRule.replacePattern)
        regexTrimStrings.setText(currentRule.trimStrings.joinToString("\n"))

        replacePosition1.isChecked = currentRule.affectsInput
        replacePosition2.isChecked = currentRule.affectsOutput
        replacePosition3.isChecked = currentRule.affectsCommands
        replacePosition5.isChecked = currentRule.affectsWorldInfo

        disabled.isChecked = !currentRule.enabled
        runOnEdit.isChecked = currentRule.runOnEdit
        onlyFormatDisplay.isChecked = currentRule.onlyFormatDisplay
        onlyFormatPrompt.isChecked = currentRule.onlyFormatPrompt

        currentRule.minDepth?.let { minDepth.setText(it.toString()) }
        currentRule.maxDepth?.let { maxDepth.setText(it.toString()) }
    }

    private fun setupTestMode() {
        updateTestModeVisibility()
    }

    private fun updateTestModeVisibility() {
        regexTestMode.visibility = if (isTestMode) LinearLayout.VISIBLE else LinearLayout.GONE
        regexTestModeToggle.text = if (isTestMode) "Edit Mode" else "Test Mode"

        if (isTestMode) {
            runTest()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun runTest() {
        val input = regexTestInput.text.toString()
        if (input.isEmpty()) {
            regexTestOutput.setText("")
            return
        }

        try {
            val findPattern = findRegex.text.toString()
            val replacePattern = regexReplaceString.text.toString()
            val trimStrings = regexTrimStrings.text.toString().split("\n").filter { it.isNotBlank() }

            var result = input
            if (findPattern.isNotEmpty()) {
                val pattern = Pattern.compile(findPattern)
                val matcher = pattern.matcher(result)

                // 应用trim操作
                trimStrings.forEach { trimStr ->
                    result = result.replace(trimStr, "")
                }

                // 应用替换
                result = if (replacePattern.contains("{{match}}")) {
                    // 使用{{match}}占位符 - 兼容旧版本API
                    val sb = StringBuffer()
                    while (matcher.find()) {
                        matcher.appendReplacement(sb, replacePattern.replace("{{match}}", matcher.group()))
                    }
                    matcher.appendTail(sb)
                    sb.toString()
                } else {
                    // 使用标准替换模式
                    matcher.replaceAll(replacePattern)
                }
            }

            regexTestOutput.setText(result)
        } catch (e: PatternSyntaxException) {
            regexTestOutput.setText("正则表达式语法错误: ${e.message}")
        } catch (e: Exception) {
            regexTestOutput.setText("处理错误: ${e.message}")
        }
    }

    private fun saveRule(): Boolean {
        // 验证必要字段
        val name = regexScriptName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入规则名称", Toast.LENGTH_SHORT).show()
            return false
        }

        val findPattern = findRegex.text.toString().trim()
        if (findPattern.isEmpty()) {
            Toast.makeText(this, "请输入查找正则表达式", Toast.LENGTH_SHORT).show()
            return false
        }

        // 验证正则表达式语法
        try {
            Pattern.compile(findPattern)
        } catch (e: PatternSyntaxException) {
            Toast.makeText(this, "正则表达式语法错误: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }

        // 更新规则对象
        currentRule.name = name
        currentRule.findPattern = findPattern
        currentRule.replacePattern = regexReplaceString.text.toString()
        currentRule.trimStrings = regexTrimStrings.text.toString().split("\n").filter { it.isNotBlank() }

        currentRule.affectsInput = replacePosition1.isChecked
        currentRule.affectsOutput = replacePosition2.isChecked
        currentRule.affectsCommands = replacePosition3.isChecked
        currentRule.affectsWorldInfo = replacePosition5.isChecked

        currentRule.enabled = !disabled.isChecked
        currentRule.runOnEdit = runOnEdit.isChecked
        currentRule.onlyFormatDisplay = onlyFormatDisplay.isChecked
        currentRule.onlyFormatPrompt = onlyFormatPrompt.isChecked

        currentRule.minDepth = minDepth.text.toString().toIntOrNull()
        currentRule.maxDepth = maxDepth.text.toString().toIntOrNull()

        // 保存规则
        if (isEditingExisting) {
            updateRule(currentRule)
        } else {
            addRule(currentRule)
        }

        Toast.makeText(this, "规则已保存", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun loadRuleById(ruleId: String, isGlobal: Boolean, cardId: String?): RegexRule {
        val ruleManager = RegexRuleManager(this)
        return if (isGlobal) {
            ruleManager.getGlobalRules().find { it.id == ruleId } ?: RegexRule()
        } else {
            // 使用传递的卡片ID
            val targetCardId = cardId ?: ""
            ruleManager.getScopedRules(targetCardId).find { it.id == ruleId } ?: RegexRule()
        }
    }

    private fun addRule(rule: RegexRule) {
        val ruleManager = RegexRuleManager(this)
        if (rule.isGlobal) {
            ruleManager.saveGlobalRule(rule)
        } else {
            // 从Intent获取卡片ID
            val cardId = intent.getStringExtra("card_id") ?: ""
            ruleManager.saveScopedRule(rule, cardId)
        }
    }

    private fun updateRule(rule: RegexRule) {
        addRule(rule) // 更新规则与添加规则使用相同的方法
    }
}

// 扩展RegexDropdownActivity以支持打开编辑器
class RegexDropdownActivity : AppCompatActivity() {
    // 用于导入的JSON数据类
    data class ImportedRegexRule(
        val id: String? = null,
        val scriptName: String? = null,
        val findRegex: String? = null,
        val replaceString: String? = null,
        val trimStrings: List<String>? = null,
        val placement: List<Int>? = null,
        val disabled: Boolean? = null,
        val markdownOnly: Boolean? = null,
        val promptOnly: Boolean? = null,
        val runOnEdit: Boolean? = null,
        val substituteRegex: Int? = null,
        val minDepth: Int? = null,
        val maxDepth: Int? = null
    )
    private lateinit var importRuleLauncher: ActivityResultLauncher<Intent>


    private lateinit var ruleManager: RegexRuleManager
    private var currentCardId: String? = null // 需要从Intent获取或根据上下文确定
    private lateinit var globalListView: ListView
    private lateinit var scopedListView: ListView

    // 只读模式：true=仅显示 appliedRegexIds 匹配的规则，隐藏所有编辑按钮；false=正常编辑模式
    private var readOnlyMode: Boolean = false
    private var appliedRegexIds: List<String>? = null

    // 添加一个标志位来跟踪是否有规则变化
    private var rulesChanged = false

    // 在规则发生变化的方法中设置标志位
    private fun setRulesChanged() {
        rulesChanged = true
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.oo_regex_dropdown)

        // 初始化规则管理器
        ruleManager = RegexRuleManager(this)

        // 读取只读模式标志与应用的正则 id 列表
        readOnlyMode = intent.getBooleanExtra("read_only_mode", false)
        appliedRegexIds = intent.getStringArrayListExtra("applied_regex_ids")

        // 获取当前卡片ID（这里需要根据您的应用逻辑获取）
        currentCardId = intent.getStringExtra("card_id") ?: "default_card_id"

        // 初始化ListView
        globalListView = findViewById(R.id.saved_regex_scripts)
        scopedListView = findViewById(R.id.saved_scoped_scripts)

        // 设置关闭按钮点击事件
        val closeButton = findViewById<ImageButton>(R.id.dropdownCloseBtn)
        closeButton.setOnClickListener {
            finish()
        }

        // 只读模式：隐藏所有编辑按钮
        val openRegexEditorButton = findViewById<Button>(R.id.open_regex_editor)
        val openScopedEditorButton = findViewById<Button>(R.id.open_scoped_editor)
        val importRegexButton = findViewById<Button>(R.id.import_regex)
        val scopedToggle = findViewById<Switch>(R.id.regex_scoped_toggle)

        if (readOnlyMode) {
            openRegexEditorButton.visibility = View.GONE
            openScopedEditorButton.visibility = View.GONE
            importRegexButton.visibility = View.GONE
            scopedToggle.visibility = View.GONE
            scopedListView.visibility = View.GONE
        } else {
            // 正常编辑模式：设置新建全局正则按钮点击事件
            openRegexEditorButton.setOnClickListener {
                val intent = Intent(this, RegexEditorActivity::class.java).apply {
                    putExtra("is_global", true)
                }
                startActivity(intent)
            }

            // 设置新建局部正则按钮点击事件
            openScopedEditorButton.setOnClickListener {
                val intent = Intent(this, RegexEditorActivity::class.java).apply {
                    putExtra("is_global", false)
                    putExtra("card_id", currentCardId)
                }
                startActivity(intent)
            }

            // 初始化 importRuleLauncher
            importRuleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        importRuleFromJson(uri)
                    }
                }
            }

            // 设置导入正则按钮点击事件
            importRegexButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importRuleLauncher.launch(intent)
            }

            // 设置局部规则开关的监听器
            scopedToggle.setOnCheckedChangeListener { _, isChecked ->
                scopedListView.visibility = if (isChecked && currentCardId != null) View.VISIBLE else View.GONE
            }
        }

        // 加载并显示已保存的规则
        loadSavedRules()
    }

    override fun onResume() {
        super.onResume()
        // 当Activity恢复时刷新列表
        loadSavedRules()
    }

    private fun loadSavedRules() {
        // 加载全局规则
        val allGlobalRules = ruleManager.getGlobalRules()

        // 只读模式：按 appliedRegexIds 过滤规则（保留顺序）
        val globalRules = if (readOnlyMode && appliedRegexIds != null) {
            val rulesById = allGlobalRules.associateBy { it.id }
            appliedRegexIds!!.mapNotNull { id -> rulesById[id] }
        } else {
            allGlobalRules
        }

        val globalAdapter = RegexRuleAdapter(this, globalRules, true)
        globalListView.adapter = globalAdapter

        if (readOnlyMode) {
            // 只读模式：禁止点击编辑
            globalListView.setOnItemClickListener(null)
        } else {
            // 编辑模式：设置全局规则项的点击事件
            globalListView.setOnItemClickListener { _, _, position, _ ->
                val rule = globalRules[position]
                val intent = Intent(this, RegexEditorActivity::class.java).apply {
                    putExtra("rule_id", rule.id)
                    putExtra("is_global", true)
                }
                startActivity(intent)
            }
        }

        // 只读模式不显示局部规则
        if (!readOnlyMode) {
            // 加载局部规则（如果有当前卡片ID）
            currentCardId?.let { cardId ->
                val scopedRules = if (currentCardId != null) {
                    ruleManager.getScopedRules(currentCardId!!)
                } else {
                    emptyList()
                }
                val scopedAdapter = RegexRuleAdapter(this, scopedRules, false)
                scopedListView.adapter = scopedAdapter

                // 设置局部规则项的点击事件
                scopedListView.setOnItemClickListener { _, _, position, _ ->
                    val rule = scopedRules[position]
                    val intent = Intent(this, RegexEditorActivity::class.java).apply {
                        putExtra("rule_id", rule.id)
                        putExtra("is_global", false)
                        putExtra("card_id", currentCardId) // 添加卡片ID传递
                    }
                    startActivity(intent)
                }
            }
        }
    }

    inner class RegexRuleAdapter(
        context: Context,
        private val rules: List<RegexRule>,
        private val isGlobalList: Boolean
    ) : ArrayAdapter<RegexRule>(context, R.layout.oo_regex_list_item, rules) {
        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = LayoutInflater.from(context).inflate(R.layout.oo_regex_list_item, parent, false)
            val rule = rules[position]

            // 设置规则名称
            view.findViewById<TextView>(R.id.rule_name).text = rule.name

            // 设置启用/禁用按钮的图标
            val toggleButton = view.findViewById<ImageButton>(R.id.toggle_enable)
            toggleButton.setImageResource(
                if (rule.enabled) R.drawable.baseline_toggle_on_24 else R.drawable.round_toggle_off_24
            )

            // 为按钮设置点击事件
            toggleButton.setOnClickListener {
                // 切换规则启用状态
                rule.enabled = !rule.enabled
                // 更新图标
                toggleButton.setImageResource(
                    if (rule.enabled) R.drawable.baseline_toggle_on_24 else R.drawable.round_toggle_off_24
                )
                // 保存更改
                if (isGlobalList) {
                    ruleManager.saveGlobalRule(rule)
                } else {
                    currentCardId?.let { cardId -> ruleManager.saveScopedRule(rule, cardId) }
                }
                // 设置规则变化标志
                setRulesChanged()
            }

            view.findViewById<ImageButton>(R.id.edit_rule).setOnClickListener {
                val intent = Intent(context, RegexEditorActivity::class.java).apply {
                    putExtra("rule_id", rule.id)
                    putExtra("is_global", isGlobalList)
                }
                context.startActivity(intent)
            }

            view.findViewById<ImageButton>(R.id.move_rule).setOnClickListener {
                // 移动规则：从全局到局部或从局部到全局
                if (isGlobalList) {
                    // 当前是全局规则，要移动到局部
                    ruleManager.deleteGlobalRule(rule.id)
                    rule.isGlobal = false
                    currentCardId?.let { cardId -> ruleManager.saveScopedRule(rule, cardId) }
                } else {
                    // 当前是局部规则，要移动到全局
                    currentCardId?.let { cardId -> ruleManager.deleteScopedRule(rule.id, cardId) }
                    rule.isGlobal = true
                    ruleManager.saveGlobalRule(rule)
                }
                // 设置规则变化标志
                setRulesChanged()
                // 刷新列表
                loadSavedRules()
            }
            val moveButton = view.findViewById<ImageButton>(R.id.move_rule)
            if (isGlobalList) {
                moveButton.setImageResource(R.drawable.baseline_arrow_downward_24) // 全局规则使用向下箭头
            } else {
                moveButton.setImageResource(R.drawable.baseline_arrow_upward_24) // 局部规则使用向上箭头
            }

            view.findViewById<ImageButton>(R.id.export_rule).setOnClickListener {
                // 导出规则：将规则转换为字符串（例如JSON）并分享或保存
                val gson = Gson()
                val ruleJson = gson.toJson(rule)
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, ruleJson)
                context.startActivity(Intent.createChooser(shareIntent, "导出正则规则"))
            }

            view.findViewById<ImageButton>(R.id.delete_rule).setOnClickListener {
                // 创建确认删除对话框
                AlertDialog.Builder(context)
                    .setTitle("确认删除")
                    .setMessage("确定要删除规则 '${rule.name}' 吗？")
                    .setPositiveButton("删除") { dialog, which ->
                        // 确认删除
                        if (isGlobalList) {
                            ruleManager.deleteGlobalRule(rule.id)
                        } else {
                            currentCardId?.let { cardId -> ruleManager.deleteScopedRule(rule.id, cardId) }
                        }
                        // 设置规则变化标志
                        setRulesChanged()
                        // 刷新列表
                        loadSavedRules()
                        Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null) // 点击取消不做任何操作
                    .show()
            }

        return view
        }
    }

    // 重写onDestroy方法，在Activity销毁时通知ChatActivity
    override fun onDestroy() {
        super.onDestroy()

        // 如果有规则变化，通知ChatActivity需要刷新
        if (rulesChanged) {
            // 通过Intent或广播通知ChatActivity
            val resultIntent = Intent()
            resultIntent.putExtra("need_refresh", true)
            setResult(Activity.RESULT_OK, resultIntent)
        }
    }

    // 导入规则 from JSON file
    private fun importRuleFromJson(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val gson = Gson()
                val importedRule = gson.fromJson(jsonString, ImportedRegexRule::class.java)

                // 转换为 RegexRule
                val rule = RegexRule(
                    id = importedRule.id ?: System.currentTimeMillis().toString(),
                    name = importedRule.scriptName ?: "",
                    findPattern = importedRule.findRegex ?: "",
                    replacePattern = importedRule.replaceString ?: "",
                    trimStrings = importedRule.trimStrings ?: emptyList(),
                    enabled = !(importedRule.disabled ?: false),
                    runOnEdit = importedRule.runOnEdit ?: false,
                    onlyFormatDisplay = importedRule.markdownOnly ?: false,
                    onlyFormatPrompt = importedRule.promptOnly ?: false,
                    affectsInput = importedRule.placement?.contains(1) ?: false,
                    affectsOutput = importedRule.placement?.contains(2) ?: false,
                    affectsCommands = importedRule.placement?.contains(3) ?: false,
                    affectsWorldInfo = importedRule.placement?.contains(5) ?: false,
                    minDepth = importedRule.minDepth,
                    maxDepth = importedRule.maxDepth,
                    isGlobal = true // 默认导入为全局规则
                )

                // 保存规则
                ruleManager.saveGlobalRule(rule)
                Toast.makeText(this, "规则已导入", Toast.LENGTH_SHORT).show()
                loadSavedRules() // 刷新列表
            }
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}