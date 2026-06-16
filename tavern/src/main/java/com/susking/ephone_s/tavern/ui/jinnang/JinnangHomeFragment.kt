package com.susking.ephone_s.tavern.ui.jinnang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.susking.ephone_s.tavern.databinding.FragmentJinnangHomeBinding

/**
 * 锦囊主面板（空白页）。
 *
 * 列出锦囊入口：提示词储存器、保存当前对话。导航与保存动作委托给 [JinnangHost]（即 TavernFragment）。
 * 作为浮层卡片内容展示（不占满全屏），无需系统栏避让。
 */
class JinnangHomeFragment : Fragment() {

    private var _binding: FragmentJinnangHomeBinding? = null
    private val binding get() = _binding!!

    private val host: JinnangHost? get() = parentFragment as? JinnangHost

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJinnangHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.jinnangCloseButton.setOnClickListener { host?.jinnangClose() }
        binding.entryPromptStorage.setOnClickListener { host?.jinnangNavigateToPromptStorage() }
        binding.entrySaveChat.setOnClickListener { host?.jinnangRequestSaveChat() }
        binding.entryPullRegex.setOnClickListener { host?.jinnangRequestPullRegex() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
