package com.susking.ephone_s.alipay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.alipay.R
import com.susking.ephone_s.alipay.databinding.FragmentWorkClockBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 上班打卡Fragment
 * 显示工作状态和打卡按钮
 */
class WorkClockFragment : Fragment() {
    
    private var _binding: FragmentWorkClockBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: WorkClockViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkClockBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[WorkClockViewModel::class.java]
        
        setupUI()
        observeViewModel()
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        binding.buttonClock.setOnClickListener {
            viewModel.toggleWorkStatus()
        }
    }
    
    /**
     * 观察ViewModel数据变化
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察工作状态
            viewModel.workStatus.collect { status ->
                updateUI(status)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察今日收入
            viewModel.todayIncome.collect { income ->
                binding.textTodayIncome.text = "¥ %.2f".format(income)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察错误消息
            viewModel.errorMessage.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    /**
     * 更新UI显示
     */
    private fun updateUI(status: com.susking.ephone_s.aidata.domain.model.WorkStatus) {
        when {
            !status.hasWorkedToday -> {
                // 今天还未上班
                binding.textWorkStatus.text = "未上班"
                binding.textWorkDuration.visibility = View.GONE
                binding.textEstimatedIncome.visibility = View.GONE
                binding.buttonClock.text = "上班打卡"
                binding.buttonClock.setBackgroundColor(
                    resources.getColor(R.color.alipay_primary, null)
                )
                binding.textClockHint.text = "点击按钮开始上班\n每小时可获得¥30工资"
            }
            status.isWorking -> {
                // 正在上班
                binding.textWorkStatus.text = "上班中"
                binding.textWorkDuration.visibility = View.VISIBLE
                binding.textEstimatedIncome.visibility = View.VISIBLE
                
                val duration = System.currentTimeMillis() - status.workStartTime
                val hours = TimeUnit.MILLISECONDS.toHours(duration)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                binding.textWorkDuration.text = "已工作: ${hours}小时${minutes}分钟"
                
                val estimatedIncome = hours * 30
                binding.textEstimatedIncome.text = "预计收入: ¥%.2f".format(estimatedIncome.toDouble())
                
                if (status.canFinishWork) {
                    binding.buttonClock.text = "下班打卡"
                    binding.buttonClock.setBackgroundColor(
                        resources.getColor(R.color.income_green, null)
                    )
                    binding.textClockHint.text = "工作满1小时，可以下班了"
                } else {
                    binding.buttonClock.text = "上班中..."
                    binding.buttonClock.setBackgroundColor(
                        resources.getColor(android.R.color.darker_gray, null)
                    )
                    binding.textClockHint.text = "需要工作满1小时才能下班"
                }
            }
            else -> {
                // 今天已下班
                binding.textWorkStatus.text = "已下班"
                val duration = status.workEndTime - status.workStartTime
                val hours = TimeUnit.MILLISECONDS.toHours(duration)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                binding.textWorkDuration.visibility = View.VISIBLE
                binding.textWorkDuration.text = "今日工作: ${hours}小时${minutes}分钟"
                binding.textEstimatedIncome.visibility = View.GONE
                binding.buttonClock.text = "今日已下班"
                binding.buttonClock.isEnabled = false
                binding.buttonClock.setBackgroundColor(
                    resources.getColor(android.R.color.darker_gray, null)
                )
                binding.textClockHint.text = "明天再来吧"
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}