package com.susking.ephone_s.album.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.album.api.AlbumNavigator
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.databinding.FragmentAlbumListBinding
import com.susking.ephone_s.album.domain.model.Album
import com.susking.ephone_s.album.ui.photogrid.PhotoGridFragment
import kotlinx.coroutines.launch

class AlbumListFragment : Fragment() {

    private var _binding: FragmentAlbumListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumViewModel by activityViewModels {
        val provider = requireContext().applicationContext as AlbumDatabaseProvider
        val albumRepository = AlbumRepositoryImpl(provider.getAlbumDao(), provider.getPhotoDao())
        AlbumViewModelFactory(albumRepository)
    }
    private lateinit var albumAdapter: AlbumAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeAlbums()

        binding.fabAddAlbum.setOnClickListener {
            showCreateAlbumDialog()
        }
    }

    private fun showCreateAlbumDialog() {
        val editText = EditText(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建新相册")
            .setMessage("请输入相册名称")
            .setView(editText)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val albumName = editText.text.toString()
                if (albumName.isNotBlank()) {
                    viewModel.createAlbum(albumName)
                }
            }
            .show()
    }

    private fun showRenameAlbumDialog(album: Album) {
        val editText = EditText(requireContext())
        editText.setText(album.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重命名相册")
            .setView(editText)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    viewModel.renameAlbum(album, newName)
                }
            }
            .show()
    }

    private fun setupRecyclerView() {
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                val navigator = requireContext().applicationContext as? AlbumNavigator
                navigator?.navigateToPhotoGrid(
                    requireActivity().supportFragmentManager,
                    album.id,
                    album.name,
                    album.name == "收藏夹"
                )
            },
            onAlbumLongClick = { album ->
                if (album.name != "默认相册" && album.name != "收藏夹") {
                    showRenameAlbumDialog(album)
                }
            }
        )
        binding.albumListRecyclerView.adapter = albumAdapter


    }
    private fun observeAlbums() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.albums.collect { albums ->
                albumAdapter.submitList(albums)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AlbumListFragment()
    }
}
