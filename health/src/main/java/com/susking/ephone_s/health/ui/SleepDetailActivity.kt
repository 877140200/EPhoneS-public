package com.susking.ephone_s.health.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.health.SleepSessionDetail
import com.susking.ephone_s.aidata.data.health.SleepStageDetail
import com.susking.ephone_s.health.databinding.ActivitySleepDetailBinding
import com.susking.ephone_s.health.databinding.ItemSleepSessionBinding
import com.susking.ephone_s.health.databinding.ItemSleepStageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 睡眠详情页：显示指定日期的所有睡眠会话及其分期段详情。
 *
 * 从健康页卡片点击进入，传入日期参数（格式 yyyy-MM-dd）。
 */
@AndroidEntryPoint
class SleepDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySleepDetailBinding
    private val viewModel: SleepDetailViewModel by viewModels()
    private val adapter = SleepSessionAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySleepDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        observeUiState()

        // 从 Intent 获取日期参数并加载数据
        val date = intent.getStringExtra(EXTRA_DATE) ?: run {
            finish()
            return
        }
        viewModel.loadSleepSessions(date)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = adapter
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: SleepDetailUiState) {
        when (state) {
            is SleepDetailUiState.Loading -> {
                binding.recyclerSessions.visibility = View.GONE
                binding.layoutState.visibility = View.VISIBLE
                binding.textStateMessage.text = "Loading sleep data..."
            }
            is SleepDetailUiState.Empty -> {
                binding.recyclerSessions.visibility = View.GONE
                binding.layoutState.visibility = View.VISIBLE
                binding.textStateMessage.text = "No sleep sessions for this day"
            }
            is SleepDetailUiState.Success -> {
                binding.layoutState.visibility = View.GONE
                binding.recyclerSessions.visibility = View.VISIBLE
                adapter.submitList(state.sessions)
            }
        }
    }

    companion object {
        private const val EXTRA_DATE = "date"

        fun createIntent(context: Context, date: String): Intent {
            return Intent(context, SleepDetailActivity::class.java).apply {
                putExtra(EXTRA_DATE, date)
            }
        }
    }
}

/**
 * 睡眠会话适配器：每个会话显示为一个卡片，内部包含分期段列表。
 */
class SleepSessionAdapter : RecyclerView.Adapter<SleepSessionAdapter.SessionViewHolder>() {

    private var sessions: List<SleepSessionDetail> = emptyList()

    fun submitList(newSessions: List<SleepSessionDetail>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSleepSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    class SessionViewHolder(
        private val binding: ItemSleepSessionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: SleepSessionDetail) {
            // 会话标题：时间段 + 总时长
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(session.startTime).atZone(zone)
            val end = Instant.ofEpochMilli(session.endTime).atZone(zone)
            val timeRange = "${start.hour.pad()}:${start.minute.pad()} - ${end.hour.pad()}:${end.minute.pad()}"
            val duration = formatDuration(session.totalMinutes)
            binding.textSessionTitle.text = "$timeRange  $duration"

            // 动态添加分期段视图
            binding.layoutStages.removeAllViews()
            session.stages.forEach { stage ->
                val stageView = ItemSleepStageBinding.inflate(
                    LayoutInflater.from(binding.root.context), binding.layoutStages, false
                )
                bindStage(stageView, stage)
                binding.layoutStages.addView(stageView.root)
            }
        }

        private fun bindStage(stageBinding: ItemSleepStageBinding, stage: SleepStageDetail) {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(stage.startTime).atZone(zone)
            val end = Instant.ofEpochMilli(stage.endTime).atZone(zone)
            stageBinding.textStageTime.text = "${start.hour.pad()}:${start.minute.pad()} - ${end.hour.pad()}:${end.minute.pad()}"
            stageBinding.textStageType.text = stage.type
            stageBinding.textStageDuration.text = "${stage.durationMinutes} min"
        }

        private fun formatDuration(totalMinutes: Long): String {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return "${hours}h ${minutes}m"
        }

        private fun Int.pad(): String = toString().padStart(2, '0')
    }
}
