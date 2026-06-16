package com.susking.ephone_s.qq.ui.chat.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.susking.ephone_s.qq.databinding.FragmentPhotoViewerBinding

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoUriString = arguments?.getString(ARG_PHOTO_URI)
        photoUriString?.let {
            val photoUri = Uri.parse(it)
            Glide.with(this)
                .load(photoUri)
                .into(binding.photoView)
        }

        binding.closeButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PHOTO_URI = "photo_uri"

        fun newInstance(photoUri: String): PhotoViewerFragment {
            return PhotoViewerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PHOTO_URI, photoUri)
                }
            }
        }
    }
}