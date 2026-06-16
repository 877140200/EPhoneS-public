package com.susking.ephone_s.health.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.susking.ephone_s.health.databinding.FragmentHealthBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 健康页 Fragment。
 *
 * 职责：渲染四种 UI 状态（加载/不可用/缺权限/就绪）、发起 Health Connect 权限请求、
 * 在 onResume（打开 + 切回前台 + 应用内亮屏）触发一次权限复检与同步。
 * 数据始终来自 ViewModel 观察的 Room Flow。
 */
@AndroidEntryPoint
class HealthFragment : Fragment() {

    private var binding: FragmentHealthBinding? = null
    private val viewModel: HealthViewModel by viewModels()
    private val dayAdapter = HealthDayAdapter { date ->
        // 点击卡片跳转到睡眠详情页
        val intent = SleepDetailActivity.createIntent(requireContext(), date)
        startActivity(intent)
    }

    // Health Connect 权限请求启动器：授权返回后复检状态并同步。
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        viewModel.refreshPermissionState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val newBinding = FragmentHealthBinding.inflate(inflater, container, false)
        binding = newBinding
        return newBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowInsets()
        binding?.recyclerDays?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dayAdapter
        }
        observeUiState()
    }

    /** 适配系统栏：根布局加 padding 避让状态栏+导航栏，背景延伸至导航栏下。 */
    private fun setupWindowInsets() {
        val b = binding ?: return
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view: View, insets: WindowInsetsCompat ->
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

    override fun onResume() {
        super.onResume()
        // 打开/切回前台/应用内亮屏后回到本页时，复检权限并触发同步。
        viewModel.refreshPermissionState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: HealthUiState) {
        val b = binding ?: return
        when (state) {
            is HealthUiState.Loading -> {
                b.recyclerDays.visibility = View.GONE
                showState(message = "正在加载健康数据…", actionText = null)
            }
            is HealthUiState.Unavailable -> {
                b.recyclerDays.visibility = View.GONE
                showState(
                    message = "本设备未提供 Health Connect，\n无法读取健康数据。",
                    actionText = null,
                )
            }
            is HealthUiState.NeedPermission -> {
                b.recyclerDays.visibility = View.GONE
                showState(
                    message = "需要授权读取你的健康数据\n（步数 / 睡眠 / 心率等）",
                    actionText = "授权",
                ) {
                    permissionLauncher.launch(viewModel.requiredPermissions)
                }
            }
            is HealthUiState.Ready -> {
                b.layoutState.visibility = View.GONE
                b.textSyncInfo.text = state.syncInfo
                if (state.records.isEmpty()) {
                    b.recyclerDays.visibility = View.GONE
                    showState(message = "暂无健康数据，\n佩戴手表并同步后再来看看。", actionText = null)
                } else {
                    b.recyclerDays.visibility = View.VISIBLE
                    dayAdapter.submitList(state.records)
                }
            }
        }
    }

    /** 显示中央状态视图；actionText 为空则隐藏按钮。 */
    private fun showState(message: String, actionText: String?, onAction: (() -> Unit)? = null) {
        val b = binding ?: return
        b.layoutState.visibility = View.VISIBLE
        b.textStateMessage.text = message
        if (actionText == null) {
            b.buttonStateAction.visibility = View.GONE
        } else {
            b.buttonStateAction.visibility = View.VISIBLE
            b.buttonStateAction.text = actionText
            b.buttonStateAction.setOnClickListener { onAction?.invoke() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.recyclerDays?.adapter = null
        binding = null
    }

    companion object {
        fun newInstance(): HealthFragment = HealthFragment()
    }
}
