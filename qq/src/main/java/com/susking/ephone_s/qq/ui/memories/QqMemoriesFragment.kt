package com.susking.ephone_s.qq.ui.memories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.MemoryType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.qq.databinding.DialogMemoryDetailBinding
import com.susking.ephone_s.qq.databinding.FragmentQqMemoriesBinding
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QqMemoriesFragment : Fragment(),
    CreateAppointmentDialogFragment.AppointmentCreationListener,
    EditAppointmentDialogFragment.AppointmentEditListener {

    private var _binding: FragmentQqMemoriesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqMemoriesViewModel by viewModels()
    private val mainViewModel: QqViewModel by activityViewModels()

    private lateinit var adapter: QqMemoriesAdapter
    private var currentContactId: String? = null
    private var appointmentSource: LiveData<List<AppointmentEntity>>? = null
    private var generalMemorySource: LiveData<List<GeneralMemoryEntity>>? = null
    private var allLongTermMemories: List<LongTermMemory> = emptyList()
    private var currentAppointments: List<AppointmentEntity> = emptyList()
    private var currentGeneralMemories: List<GeneralMemoryEntity> = emptyList()
    private var currentLongTermMemories: List<LongTermMemory> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqMemoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = QqMemoriesAdapter(
            onItemLongClick = { memory: Memory -> handleMemoryLongClick(memory) },
            onDetailClick = { memory: Memory.GeneralMemory -> showMemoryDetail(memory) }
        )
        binding.recyclerViewMemories.adapter = adapter
        binding.recyclerViewMemories.layoutManager = LinearLayoutManager(requireContext())
        observeMemories()
        observeMemoryActionEvents()
//        binding.fabCreateAppointment.setOnClickListener {
//            val dialog = CreateAppointmentDialogFragment()
//            dialog.listener = this
//            dialog.show(childFragmentManager, "CreateAppointmentDialog")
//        }
    }

    private fun observeMemories() {
        mainViewModel.selectedContactId.observe(viewLifecycleOwner) { contactId: String? ->
            currentContactId = contactId
            observeContactScopedMemories(contactId)
            applyLongTermMemoryScope()
        }
        viewModel.allLongTermMemories.observe(viewLifecycleOwner) { longTermMemories: List<LongTermMemory> ->
            allLongTermMemories = longTermMemories
            applyLongTermMemoryScope()
        }
    }

    private fun observeContactScopedMemories(contactId: String?) {
        appointmentSource?.removeObservers(viewLifecycleOwner)
        generalMemorySource?.removeObservers(viewLifecycleOwner)
        if (contactId.isNullOrBlank()) {
            currentAppointments = emptyList()
            currentGeneralMemories = emptyList()
            updateMemoriesList()
            return
        }
        appointmentSource = viewModel.getAppointmentsByContactId(contactId).also { source: LiveData<List<AppointmentEntity>> ->
            source.observe(viewLifecycleOwner) { appointments: List<AppointmentEntity> ->
                currentAppointments = appointments
                updateMemoriesList()
            }
        }
        generalMemorySource = viewModel.getGeneralMemoriesByContactId(contactId).also { source: LiveData<List<GeneralMemoryEntity>> ->
            source.observe(viewLifecycleOwner) { generalMemories: List<GeneralMemoryEntity> ->
                currentGeneralMemories = generalMemories
                updateMemoriesList()
            }
        }
    }

    private fun applyLongTermMemoryScope() {
        val contactId: String? = currentContactId
        currentLongTermMemories = if (contactId.isNullOrBlank()) {
            emptyList()
        } else {
            allLongTermMemories.filter { memory: LongTermMemory -> memory.contactId == contactId }
        }
        updateMemoriesList()
    }

    private fun observeMemoryActionEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.memoryActionEvents.collect { event: QqMemoriesViewModel.MemoryActionEvent ->
                    val message: String = when (event) {
                        is QqMemoriesViewModel.MemoryActionEvent.Succeeded -> event.message
                        is QqMemoriesViewModel.MemoryActionEvent.Failed -> event.message
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleMemoryLongClick(memory: Memory) {
        when (memory) {
            is Memory.Appointment -> showAppointmentOptions(memory)
            is Memory.GeneralMemory -> showGeneralMemoryOptions(memory)
        }
    }

    private fun showAppointmentOptions(appointment: Memory.Appointment) {
        val options = arrayOf("编辑", "删除")
        AlertDialog.Builder(requireContext())
            .setTitle("选择操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editAppointment(appointment)
                    1 -> deleteAppointment(appointment)
                }
            }
            .show()
    }

    private fun showGeneralMemoryOptions(memory: Memory.GeneralMemory) {
        val options: Array<String> = if (memory.isLongTermMemory) {
            arrayOf("详情")
        } else {
            arrayOf("详情", "删除")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择操作")
            .setItems(options) { _, which: Int ->
                when (which) {
                    0 -> showMemoryDetail(memory)
                    1 -> deleteGeneralMemory(memory)
                }
            }
            .show()
    }

    private fun editAppointment(appointment: Memory.Appointment) {
        val dialog = EditAppointmentDialogFragment.newInstance(
            appointment.id,
            appointment.contactId,
            appointment.title,
            appointment.appointmentDate
        )
        dialog.listener = this
        dialog.show(childFragmentManager, "EditAppointmentDialog")
    }

    private fun deleteAppointment(appointment: Memory.Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除约定 \"${appointment.title}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                val entity = AppointmentEntity(
                    id = appointment.id,
                    contactId = appointment.contactId,
                    title = appointment.title,
                    appointmentDate = appointment.appointmentDate
                )
                viewModel.delete(entity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateMemoriesList() {
        val allMemories: MutableList<Memory> = mutableListOf()
        allMemories.addAll(currentGeneralMemories.map { memory: GeneralMemoryEntity ->
            Memory.GeneralMemory(
                id = memory.id.toString(),
                contactId = memory.contactId,
                date = Date(memory.createdDate),
                title = "珍藏回忆",
                content = memory.description,
                isLongTermMemory = false,
                isVectorized = false
            )
        })
        allMemories.addAll(currentAppointments.map { appointment: AppointmentEntity ->
            Memory.Appointment(appointment.id, appointment.contactId, appointment.title, appointment.appointmentDate)
        })
        allMemories.sortByDescending { memory: Memory ->
            when (memory) {
                is Memory.GeneralMemory -> memory.date.time
                is Memory.Appointment -> memory.appointmentDate
            }
        }
        adapter.submitList(allMemories)
    }

    private fun showMemoryDetail(memory: Memory.GeneralMemory) {
        if (!memory.isLongTermMemory) {
            showLegacyMemoryDetail(memory)
            return
        }
        val longTermMemory: LongTermMemory = currentLongTermMemories.firstOrNull { item: LongTermMemory -> item.id == memory.id } ?: return
        val dialogBinding: DialogMemoryDetailBinding = DialogMemoryDetailBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.etMemoryText.setText(longTermMemory.memoryText)
        dialogBinding.etMemoryText.isEnabled = false
        dialogBinding.btnManualVectorize.isEnabled = false
        dialogBinding.btnManualVectorize.text = "只读纪念"
        val dialog: AlertDialog = AlertDialog.Builder(requireContext())
            .setTitle("原子事件纪念记录")
            .setView(dialogBinding.root)
            .setPositiveButton("关闭", null)
            .create()
        var embeddingJob: Job? = null
        dialog.setOnShowListener {
            embeddingJob = viewLifecycleOwner.lifecycleScope.launch {
                viewModel.observeActiveEmbedding(longTermMemory.id).collect { embedding: MemoryEmbedding? ->
                    updateVectorDetail(dialogBinding, longTermMemory, embedding)
                }
            }
        }
        dialog.setOnDismissListener { embeddingJob?.cancel() }
        dialog.show()
    }

    private fun showLegacyMemoryDetail(memory: Memory.GeneralMemory) {
        AlertDialog.Builder(requireContext())
            .setTitle("回忆详情")
            .setMessage("这是一条旧普通回忆，暂未接入原子事件向量索引。\n\n${memory.content}")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun updateVectorDetail(
        dialogBinding: DialogMemoryDetailBinding,
        memory: LongTermMemory,
        embedding: MemoryEmbedding?
    ) {
        val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        dialogBinding.tvVectorStatus.text = if (embedding != null || memory.isVectorized) "状态：已向量化" else "状态：未向量化"
        dialogBinding.tvVectorModel.text = "模型：${embedding?.modelName ?: memory.embeddingVersion ?: "暂无"}"
        dialogBinding.tvVectorDimension.text = "维度：${embedding?.dimension?.toString() ?: "暂无"}"
        dialogBinding.tvVectorUpdatedAt.text = "更新时间：${embedding?.let { item: MemoryEmbedding -> formatter.format(Date(item.updatedAt)) } ?: "暂无"}"
        dialogBinding.tvVectorHash.text = "哈希：${embedding?.embeddingHash ?: "暂无"}"
        dialogBinding.btnManualVectorize.text = "只读纪念"
    }

    private fun deleteGeneralMemory(memory: Memory.GeneralMemory) {
        val legacyMemoryId: Long = memory.id.toLongOrNull() ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除这条回忆吗？\n\n${memory.content}")
            .setPositiveButton("删除") { _, _ ->
                val entity = GeneralMemoryEntity(
                    id = legacyMemoryId,
                    contactId = memory.contactId,
                    description = memory.content,
                    createdDate = memory.date.time
                )
                viewModel.deleteMemory(entity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = QqMemoriesFragment()
    }

    override fun onAppointmentCreated(contactId: String, title: String, date: Long) {
        val newAppointment = AppointmentEntity(contactId = contactId, title = title, appointmentDate = date)
        viewModel.insert(newAppointment)
    }

    override fun onAppointmentUpdated(id: Long, contactId: String, title: String, date: Long) {
        val updatedAppointment = AppointmentEntity(
            id = id,
            contactId = contactId,
            title = title,
            appointmentDate = date
        )
        viewModel.update(updatedAppointment)
    }
}
