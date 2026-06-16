package com.susking.ephone_s.tavern.ui.jinnang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.repository.PromptStorageRepository
import com.susking.ephone_s.tavern.R
import com.susking.ephone_s.tavern.databinding.FragmentPromptStorageBinding
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 提示词储存器页面。
 *
 * 句子与词语两类对称管理：手动新增、长按删除、随机抽取（句子 1 条；词语按自定义范围抽 N 个）、
 * 随机结果支持重新随机与复制。词语数量范围可自定义，存于仓库（随完整备份导入导出）。
 */
class PromptStorageFragment : Fragment() {

    private var _binding: FragmentPromptStorageBinding? = null
    private val binding get() = _binding!!

    private val repository: PromptStorageRepository by lazy { AiDataApi.getPromptStorageRepository() }

    private lateinit var sentenceAdapter: PromptItemAdapter
    private lateinit var wordAdapter: PromptItemAdapter

    // 当前列表缓存，供随机抽取使用
    private var currentSentences: List<PromptListItem> = emptyList()
    private var currentWords: List<PromptListItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPromptStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setupRecyclers()
        setupButtons()
        observeData()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v: View, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun setupRecyclers() {
        sentenceAdapter = PromptItemAdapter { item -> confirmDelete(item, isSentence = true) }
        wordAdapter = PromptItemAdapter { item -> confirmDelete(item, isSentence = false) }
        binding.sentenceRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sentenceAdapter
        }
        binding.wordRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = wordAdapter
        }
    }

    private fun setupButtons() {
        binding.promptBackButton.setOnClickListener { parentFragmentManager.popBackStack() }

        // 句子：添加 / 随机 / 重新随机 / 复制
        binding.sentenceAddButton.setOnClickListener {
            showInputDialog(R.string.prompt_add_dialog_title_sentence, R.string.prompt_add_sentence_hint) { text ->
                lifecycleScope.launch { repository.addSentence(text) }
            }
        }
        binding.sentenceRandomButton.setOnClickListener { randomSentence() }
        binding.sentenceReRandomButton.setOnClickListener { randomSentence() }
        binding.sentenceCopyButton.setOnClickListener {
            copyToClipboard(binding.sentenceResultText.text?.toString().orEmpty())
        }

        // 词语：添加 / 随机 / 重新随机 / 复制 / 数量范围
        binding.wordAddButton.setOnClickListener {
            showInputDialog(R.string.prompt_add_dialog_title_word, R.string.prompt_add_word_hint) { text ->
                lifecycleScope.launch { repository.addWord(text) }
            }
        }
        binding.wordRandomButton.setOnClickListener { randomWords() }
        binding.wordReRandomButton.setOnClickListener { randomWords() }
        binding.wordCopyButton.setOnClickListener {
            copyToClipboard(binding.wordResultText.text?.toString().orEmpty())
        }
        binding.wordRangeButton.setOnClickListener { showWordRangeDialog() }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.getAllSentences().collect { list ->
                        currentSentences = list.map { PromptListItem(it.id, it.content) }
                        sentenceAdapter.submitList(currentSentences)
                        binding.sentenceEmptyHint.visibility =
                            if (currentSentences.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    repository.getAllWords().collect { list ->
                        currentWords = list.map { PromptListItem(it.id, it.content) }
                        wordAdapter.submitList(currentWords)
                        binding.wordEmptyHint.visibility =
                            if (currentWords.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    /** 句子随机：抽 1 条。 */
    private fun randomSentence() {
        if (currentSentences.isEmpty()) {
            Toast.makeText(requireContext(), R.string.prompt_random_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val picked = currentSentences.random().content
        binding.sentenceResultText.text = picked
        binding.sentenceResultCard.visibility = View.VISIBLE
    }

    /** 词语随机：在 [min, max] 内随机数量（不超过现有词语数）抽取，逗号分隔。 */
    private fun randomWords() {
        if (currentWords.isEmpty()) {
            Toast.makeText(requireContext(), R.string.prompt_random_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val range: PromptStorageRepository.WordCountRange = repository.getWordCountRange()
        val upperBound: Int = minOf(range.max, currentWords.size)
        val lowerBound: Int = minOf(range.min, upperBound)
        val count: Int = if (upperBound <= lowerBound) {
            upperBound
        } else {
            lowerBound + Random.nextInt(upperBound - lowerBound + 1)
        }
        val picked: String = currentWords.shuffled().take(count).joinToString("，") { it.content }
        binding.wordResultText.text = picked
        binding.wordResultCard.visibility = View.VISIBLE
    }

    private fun confirmDelete(item: PromptListItem, isSentence: Boolean) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.prompt_delete)
            .setMessage(R.string.prompt_delete_confirm)
            .setPositiveButton(R.string.prompt_delete) { _, _ ->
                lifecycleScope.launch {
                    if (isSentence) repository.deleteSentence(item.id) else repository.deleteWord(item.id)
                }
            }
            .setNegativeButton(R.string.save_chat_cancel, null)
            .show()
    }

    /** 通用输入弹窗：标题 + 提示语 + 确认回调。 */
    private fun showInputDialog(titleRes: Int, hintRes: Int, onConfirm: (String) -> Unit) {
        val context = requireContext()
        val input = EditText(context).apply { setHint(hintRes) }
        val paddingPx = (resources.displayMetrics.density * 20).toInt()
        val container = LinearLayout(context).apply {
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(R.string.prompt_add) { _, _ ->
                val text = input.text?.toString().orEmpty().trim()
                if (text.isNotEmpty()) onConfirm(text)
            }
            .setNegativeButton(R.string.save_chat_cancel, null)
            .show()
    }

    /** 词语随机数量范围设置弹窗：两个数字输入。 */
    private fun showWordRangeDialog() {
        val context = requireContext()
        val current = repository.getWordCountRange()
        val paddingPx = (resources.displayMetrics.density * 20).toInt()

        val minInput = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.prompt_word_count_min)
            setText(current.min.toString())
        }
        val maxInput = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.prompt_word_count_max)
            setText(current.max.toString())
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            addView(TextView(context).apply { setText(R.string.prompt_word_count_min) })
            addView(minInput)
            addView(TextView(context).apply { setText(R.string.prompt_word_count_max) })
            addView(maxInput)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.prompt_word_count_setting)
            .setView(container)
            .setPositiveButton(R.string.prompt_word_count_save) { _, _ ->
                val min = minInput.text?.toString()?.toIntOrNull() ?: current.min
                val max = maxInput.text?.toString()?.toIntOrNull() ?: current.max
                if (min < 1 || max < min) {
                    Toast.makeText(context, R.string.prompt_word_count_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    repository.saveWordCountRange(min, max)
                    Toast.makeText(context, R.string.prompt_word_count_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.save_chat_cancel, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("prompt", text))
        Toast.makeText(requireContext(), R.string.prompt_copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
