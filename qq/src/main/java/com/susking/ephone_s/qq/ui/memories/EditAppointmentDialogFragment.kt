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

class EditAppointmentDialogFragment : DialogFragment() {

    interface AppointmentEditListener {
        fun onAppointmentUpdated(id: Long, contactId: String, title: String, date: Long)
    }

    var listener: AppointmentEditListener? = null

    private val calendar: Calendar = Calendar.getInstance()
    private var appointmentId: Long = 0
    private var appointmentContactId: String = ""
    private var appointmentTitle: String = ""
    private var appointmentDate: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appointmentId = it.getLong(ARG_APPOINTMENT_ID)
            appointmentContactId = it.getString(ARG_APPOINTMENT_CONTACT_ID, "")
            appointmentTitle = it.getString(ARG_APPOINTMENT_TITLE, "")
            appointmentDate = it.getLong(ARG_APPOINTMENT_DATE)
        }
        calendar.timeInMillis = appointmentDate
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_create_appointment, null)

        val titleEditText = view.findViewById<EditText>(R.id.et_appointment_title)
        val dateEditText = view.findViewById<EditText>(R.id.et_appointment_date)
        val timeEditText = view.findViewById<EditText>(R.id.et_appointment_time)

        // 预填充现有数据
        titleEditText.setText(appointmentTitle)
        val cal = Calendar.getInstance()
        cal.timeInMillis = appointmentDate
        dateEditText.setText(String.format("%d-%02d-%02d", 
            cal.get(Calendar.YEAR), 
            cal.get(Calendar.MONTH) + 1, 
            cal.get(Calendar.DAY_OF_MONTH)))
        timeEditText.setText(String.format("%02d:%02d", 
            cal.get(Calendar.HOUR_OF_DAY), 
            cal.get(Calendar.MINUTE)))

        dateEditText.setOnClickListener {
            showDatePicker(dateEditText)
        }

        timeEditText.setOnClickListener {
            showTimePicker(timeEditText)
        }

        builder.setView(view)
            .setTitle("编辑约定")
            .setPositiveButton("保存") { _, _ ->
                val title = titleEditText.text.toString()
                if (title.isNotEmpty()) {
                    listener?.onAppointmentUpdated(appointmentId, appointmentContactId, title, calendar.timeInMillis)
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
        private const val ARG_APPOINTMENT_ID = "appointment_id"
        private const val ARG_APPOINTMENT_CONTACT_ID = "appointment_contact_id"
        private const val ARG_APPOINTMENT_TITLE = "appointment_title"
        private const val ARG_APPOINTMENT_DATE = "appointment_date"

        fun newInstance(id: Long, contactId: String, title: String, date: Long): EditAppointmentDialogFragment {
            val fragment = EditAppointmentDialogFragment()
            val args = Bundle()
            args.putLong(ARG_APPOINTMENT_ID, id)
            args.putString(ARG_APPOINTMENT_CONTACT_ID, contactId)
            args.putString(ARG_APPOINTMENT_TITLE, title)
            args.putLong(ARG_APPOINTMENT_DATE, date)
            fragment.arguments = args
            return fragment
        }
    }
}