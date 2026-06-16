package com.susking.ephone_s.cphone.ui.music

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.aidata.domain.model.MusicTrack
import com.susking.ephone_s.aidata.domain.model.PlayableSong
import com.susking.ephone_s.aidata.domain.service.MusicService
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneMusicBinding
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CPhone音乐播放器App主界面
 * 模拟网易云音乐
 */
@AndroidEntryPoint
class CPhoneMusicFragment : Fragment() {

    private var _binding: FragmentCphoneMusicBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()
    
    @Inject
    lateinit var musicService: MusicService

    private lateinit var adapter: CPhoneMusicAdapter
    private var contactId: String = ""
    private val songList = mutableListOf<MusicTrack>()
    
    // MediaPlayer用于播放音乐
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayableSong: PlayableSong? = null
    private var currentSong: MusicTrack? = null
    private var isPlaying: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        setupMiniPlayer()
        observeData()
        observeRefreshState()
        loadSongs()
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        val toolbar = view?.findViewById<MaterialToolbar>(R.id.toolbar)

        toolbar?.apply {
            title = "音乐"
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        
        // 设置刷新按钮点击事件
        view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
            refreshSongs()
        }
    }


    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneMusicAdapter(
            onSongClick = { song ->
                playSong(song)
            },
            onMoreClick = { song ->
                // TODO: 显示更多选项(收藏、下载、分享等)
            }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneMusicFragment.adapter
        }
    }

    /**
     * 设置迷你播放器
     */
    private fun setupMiniPlayer() {
        val miniPlayer = binding.miniPlayer
        
        // 点击迷你播放器展开完整播放器(TODO: 实现BottomSheet播放器)
        miniPlayer.root.setOnClickListener {
            // TODO: 打开完整播放器BottomSheet
        }
        
        // 播放/暂停按钮
        miniPlayer.root.findViewById<AppCompatImageButton>(R.id.btn_play_pause)?.setOnClickListener {
            togglePlayPause()
        }
        
        // 下一首按钮
        miniPlayer.root.findViewById<AppCompatImageButton>(R.id.btn_next)?.setOnClickListener {
            playNextSong()
        }
    }

    /**
     * 观察音乐数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMusicData(contactId).collect { musicTracks ->
                songList.clear()
                songList.addAll(musicTracks)
                
                if (songList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(songList.toList())
                }
            }
        }
    }
    
    /**
     * 观察刷新状态
     */
    private fun observeRefreshState() {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CPhoneAppViewModel.RefreshState.Idle -> {
                    // 空闲状态
                }
                is CPhoneAppViewModel.RefreshState.Loading -> {
                    showLoading()
                }
                is CPhoneAppViewModel.RefreshState.Success -> {
                    if (state.appType == "music") {
                        Snackbar.make(binding.root, "音乐列表刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "music") {
                        showContent()
                        Snackbar.make(
                            binding.root,
                            "刷新失败: ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                        viewModel.resetRefreshState()
                    }
                }
            }
        }
    }
    
    /**
     * 加载歌曲
     */
    private fun loadSongs() {
        // 数据通过observeData自动加载
    }

    /**
     * 刷新歌曲
     * 调用AI接口生成10-20首歌曲
     */
    private fun refreshSongs() {
        viewModel.refreshAppData(contactId, "music")
    }

    /**
     * 播放歌曲
     * 从网络获取播放链接并开始播放
     */
    private fun playSong(song: MusicTrack) {
        currentSong = song
        
        // 如果歌曲已有播放URL，直接播放
        val playUrl = song.playUrl
        if (!playUrl.isNullOrEmpty()) {
            playWithUrl(playUrl, song)
            return
        }
        
        // 否则需要从网络获取
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 显示加载提示
                Snackbar.make(binding.root, "正在获取歌曲资源...", Snackbar.LENGTH_SHORT).show()
                
                // 构造搜索结果对象
                val searchResult = com.susking.ephone_s.aidata.domain.model.MusicSearchResult(
                    id = song.id,
                    song = song.songName,
                    singer = song.artistName,
                    cover = song.coverUrl ?: "",
                    source = com.susking.ephone_s.aidata.domain.model.MusicSource.NETEASE
                )
                
                // 获取可播放的歌曲详情
                val playableSong = musicService.getPlayableSongDetails(searchResult)
                
                if (playableSong != null) {
                    currentPlayableSong = playableSong
                    playWithUrl(playableSong.src, song)
                    
                    // 更新歌曲信息，保存播放URL和封面
                    song.playUrl = playableSong.src
                    if (song.coverUrl.isNullOrEmpty()) {
                        song.coverUrl = playableSong.cover
                    }
                } else {
                    Snackbar.make(
                        binding.root,
                        "无法获取歌曲播放资源",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(
                    binding.root,
                    "获取歌曲失败: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 使用URL播放歌曲
     */
    private fun playWithUrl(url: String, song: MusicTrack) {
        try {
            // 释放之前的MediaPlayer
            mediaPlayer?.release()
            
            // 创建新的MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                
                setDataSource(url)
                
                setOnPreparedListener {
                    start()
                    this@CPhoneMusicFragment.isPlaying = true
                    updateMiniPlayer()
                    showMiniPlayer()
                }
                
                setOnCompletionListener {
                    // 播放完成，自动播放下一首
                    playNextSong()
                }
                
                setOnErrorListener { _, what, extra ->
                    Snackbar.make(
                        binding.root,
                        "播放出错: $what, $extra",
                        Snackbar.LENGTH_LONG
                    ).show()
                    true
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(
                binding.root,
                "播放失败: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
            updateMiniPlayerPlayButton()
        }
    }

    /**
     * 播放下一首
     */
    private fun playNextSong() {
        val currentIndex = songList.indexOfFirst { it.id == currentSong?.id }
        if (currentIndex != -1 && currentIndex < songList.size - 1) {
            playSong(songList[currentIndex + 1])
        } else if (songList.isNotEmpty()) {
            // 循环播放,回到第一首
            playSong(songList[0])
        }
    }

    /**
     * 更新迷你播放器信息
     */
    private fun updateMiniPlayer() {
        val miniPlayer = binding.miniPlayer
        currentSong?.let { song ->
            miniPlayer.root.findViewById<TextView>(R.id.tv_song_name)?.text = song.songName
            miniPlayer.root.findViewById<TextView>(R.id.tv_artist)?.text = song.artistName
            
            // 使用Coil加载专辑封面
            miniPlayer.root.findViewById<ImageView>(R.id.iv_album_cover)?.load(song.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_album_placeholder)
                error(R.drawable.ic_album_placeholder)
                transformations(RoundedCornersTransformation(8f))
            }
        }
        
        updateMiniPlayerPlayButton()
    }

    /**
     * 更新迷你播放器播放按钮
     */
    private fun updateMiniPlayerPlayButton() {
        val playButton = binding.miniPlayer.root.findViewById<AppCompatImageButton>(R.id.btn_play_pause)
        playButton?.setImageResource(
            if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_arrow
        )
    }

    /**
     * 显示迷你播放器
     */
    private fun showMiniPlayer() {
        binding.miniPlayer.root.visibility = View.VISIBLE
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvSongs.visibility = View.GONE
            progressBar.visibility = View.GONE
            miniPlayer.root.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text = 
                "暂无歌曲\n点击刷新生成AI歌单"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvSongs.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 显示加载中
     */
    private fun showLoading() {
        binding.apply {
            rvSongs.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放MediaPlayer资源
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停播放
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            updateMiniPlayerPlayButton()
        }
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String) = CPhoneMusicFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}