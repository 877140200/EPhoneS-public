package com.susking.ephone_s.qq.ui.chat.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.FragmentChatHistorySearchBinding
import com.susking.ephone_s.qq.ui.chat.QqChatFragment
import com.susking.ephone_s.qq.databinding.ItemCalendarDayBinding
import com.susking.ephone_s.qq.databinding.ItemCalendarHeaderBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@AndroidEntryPoint
class ChatHistorySearchFragment : Fragment() {

    private var _binding: FragmentChatHistorySearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatHistorySearchViewModel by viewModels()
    private lateinit var searchAdapter: ChatHistorySearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatHistorySearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupCalendar()
        setupSearchInput()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = ChatHistorySearchAdapter { message ->
            val contactId = requireArguments().getString(ARG_CONTACT_ID)
            if (contactId == null) {
                Toast.makeText(requireContext(), "错误: 缺少联系人 ID", Toast.LENGTH_SHORT).show()
                return@ChatHistorySearchAdapter
            }

            val chatFragment = QqChatFragment.newInstance(
                contactId = contactId,
                targetTimestamp = message.timestamp,
                isLaunchedFromSearch = true
            )

            (view?.parent as? ViewGroup)?.id?.let { containerId ->
                parentFragmentManager.beginTransaction()
                    .add(containerId, chatFragment)
                    .addToBackStack(null) // Add this transaction to the back stack
                    .commit()
            }
        }
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val daysOfWeek = daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY)

        binding.calendarView.setup(startMonth, endMonth, daysOfWeek.first())
        binding.calendarView.scrollToMonth(currentMonth)

        // Day Binder
        class DayViewContainer(view: View) : ViewContainer(view) {
            lateinit var day: CalendarDay
            val binding = ItemCalendarDayBinding.bind(view)

            init {
                view.setOnClickListener {
                    if (day.position == DayPosition.MonthDate) {
                        viewModel.onDateSelected(day.date)
                    }
                }
            }
        }

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                val textView = container.binding.calendarDayText
                val dotView = container.binding.calendarDayDot

                textView.text = day.date.dayOfMonth.toString()

                if (day.position == DayPosition.MonthDate) {
                    textView.setTextColor(requireContext().getColor(R.color.black))
                    
                    val state = viewModel.uiState.value
                    dotView.visibility = if (state.datesWithMessages.contains(day.date)) View.VISIBLE else View.INVISIBLE

                    if (state.selectedDate == day.date) {
                        textView.setBackgroundResource(R.drawable.bg_calendar_selected_day)
                        textView.setTextColor(requireContext().getColor(android.R.color.white))
                    } else {
                        textView.background = null
                    }
                } else {
                    textView.setTextColor(requireContext().getColor(R.color.gray_400))
                    dotView.visibility = View.INVISIBLE
                    textView.background = null
                }
            }
        }

        // Month Header Binder
        class MonthViewContainer(view: View) : ViewContainer(view) {
            val textView = ItemCalendarHeaderBinding.bind(view).calendarHeaderMonthText
        }

        binding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                val monthName = month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.CHINA)
                container.textView.text = "${month.yearMonth.year}年 $monthName"
            }
        }
    }

    private fun setupSearchInput() {
        binding.searchInputEditText.addTextChangedListener { text ->
            viewModel.onSearchQueryChanged(text.toString())
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    searchAdapter.updateProfiles(state.contact, state.userProfile)
                    searchAdapter.submitList(state.messages)
                    binding.noResultsTextView.visibility = if (!state.isLoading && state.messages.isEmpty()) View.VISIBLE else View.GONE
                    
                    // Invalidate calendar to re-bind dates
                    binding.calendarView.notifyCalendarChanged()

                    // Update toolbar subtitle
                    if (state.selectedDate != null) {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        val dateString = state.selectedDate.format(formatter)
                        val count = state.messages.size
                        binding.toolbarSubtitle.text = "($dateString 共 $count 条)"
                        binding.toolbarSubtitle.visibility = View.VISIBLE
                    } else {
                        binding.toolbarSubtitle.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): ChatHistorySearchFragment {
            return ChatHistorySearchFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}
