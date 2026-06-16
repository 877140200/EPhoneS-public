package com.susking.ephone_s.qq.ui.memories

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.qq.R
import java.util.Calendar

class CreateAppointmentDialogFragment : DialogFragment() {

    interface AppointmentCreationListener {
        fun onAppointmentCreated(contactId: String, title: String, date: Long)
    }

    var listener: AppointmentCreationListener? = null
    private var contactId: String = ""

    private val calendar: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID, "")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_create_appointment, null)

        val titleEditText = view.findViewById<EditText>(R.id.et_appointment_title)
        val dateEditText = view.findViewById<EditText>(R.id.et_appointment_date)
        val timeEditText = view.findViewById<EditText>(R.id.et_appointment_time)

        dateEditText.setOnClickListener {
            showDatePicker(dateEditText)
        }

        timeEditText.setOnClickListener {
            showTimePicker(timeEditText)
        }

        builder.setView(view)
            .setTitle("新建约定")
            .setPositiveButton("创建") { _, _ ->
                val title = titleEditText.text.toString()
                if (title.isNotEmpty()) {
                    listener?.onAppointmentCreated(contactId, title, calendar.timeInMillis)
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.cancel()
            }

        return builder.create()
    }

    private fun showDatePicker(editText: EditText) {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            editText.setText(String.format("%d-%02d-%02d", year, month + 1, dayOfMonth))
        }
        DatePickerDialog(requireContext(), dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(editText: EditText) {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            editText.setText(String.format("%02d:%02d", hourOfDay, minute))
        }
        TimePickerDialog(requireContext(), timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE), true).show()
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): CreateAppointmentDialogFragment {
            val fragment = CreateAppointmentDialogFragment()
            val args = Bundle()
            args.putString(ARG_CONTACT_ID, contactId)
            fragment.arguments = args
            return fragment
        }
    }
}