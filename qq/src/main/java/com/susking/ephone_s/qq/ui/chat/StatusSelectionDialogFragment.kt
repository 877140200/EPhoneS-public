package com.susking.ephone_s.qq.ui.chat

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.qq.R

class StatusSelectionDialogFragment : DialogFragment() {

    interface StatusSelectionListener {
        fun onStatusSelected(status: String, statusIconUri: String?)
    }

    private var listener: StatusSelectionListener? = null
    private var selectedStatusIconUri: Uri? = null
    private lateinit var cropImage: ActivityResultLauncher<CropImageContractOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
        cropImage = registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                selectedStatusIconUri = result.uriContent
                view?.findViewById<ImageView>(R.id.iv_custom_status_icon)?.let {
                    Glide.with(this).load(selectedStatusIconUri).placeholder(R.drawable.ic_add_photo).into(it)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_status_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusRadioGroup: RadioGroup = view.findViewById(R.id.status_radio_group)
        val radioOnline: RadioButton = view.findViewById(R.id.radio_online)
        val radioBusy: RadioButton = view.findViewById(R.id.radio_busy)
        val ivCustomStatusIcon: ImageView = view.findViewById(R.id.iv_custom_status_icon)
        val etCustomStatusText: EditText = view.findViewById(R.id.et_custom_status_text)
        val btnSelectCustomIcon: Button = view.findViewById(R.id.btn_select_custom_icon)
        val btnCancel: Button = view.findViewById(R.id.btn_cancel)
        val btnSave: Button = view.findViewById(R.id.btn_save)

        // 设置初始状态
        val currentStatus = arguments?.getString(ARG_CURRENT_STATUS) ?: "在线"
        val currentStatusIconUri = arguments?.getString(ARG_CURRENT_STATUS_ICON_URI)?.toUri()

        when (currentStatus) {
            "在线" -> radioOnline.isChecked = true
            "忙碌" -> radioBusy.isChecked = true
            else -> {
                // 自定义状态
                etCustomStatusText.setText(currentStatus)
                selectedStatusIconUri = currentStatusIconUri
                currentStatusIconUri?.let {
                    Glide.with(this).load(it).placeholder(R.drawable.ic_add_photo).into(ivCustomStatusIcon)
                }
            }
        }

        statusRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_online -> {
                    etCustomStatusText.text.clear()
                    selectedStatusIconUri = null
                    ivCustomStatusIcon.setImageResource(R.drawable.ic_add_photo)
                }
                R.id.radio_busy -> {
                    etCustomStatusText.text.clear()
                    selectedStatusIconUri = null
                    ivCustomStatusIcon.setImageResource(R.drawable.ic_add_photo)
                }
            }
        }

        btnSelectCustomIcon.setOnClickListener {
            val cropOptions = CropImageOptions(fixAspectRatio = true, aspectRatioX = 1, aspectRatioY = 1)
            val options = CropImageContractOptions(null, cropOptions)
            cropImage.launch(options)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val status: String
            val statusIconUri: String?

            if (radioOnline.isChecked) {
                status = "在线"
                statusIconUri = null
            } else if (radioBusy.isChecked) {
                status = "忙碌"
                statusIconUri = null
            } else {
                status = etCustomStatusText.text.toString().ifEmpty { "自定义状态" }
                statusIconUri = selectedStatusIconUri?.toString()
            }
            listener?.onStatusSelected(status, statusIconUri)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun setStatusSelectionListener(listener: StatusSelectionListener) {
        this.listener = listener
    }

    companion object {
        const val TAG = "StatusSelectionDialogFragment"
        private const val ARG_CURRENT_STATUS = "current_status"
        private const val ARG_CURRENT_STATUS_ICON_URI = "current_status_icon_uri"

        fun newInstance(currentStatus: String, currentStatusIconUri: String?): StatusSelectionDialogFragment {
            val fragment = StatusSelectionDialogFragment()
            val args = Bundle().apply {
                putString(ARG_CURRENT_STATUS, currentStatus)
                putString(ARG_CURRENT_STATUS_ICON_URI, currentStatusIconUri)
            }
            fragment.arguments = args
            return fragment
        }
    }
}