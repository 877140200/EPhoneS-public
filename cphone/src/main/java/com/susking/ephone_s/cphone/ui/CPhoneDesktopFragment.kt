package com.susking.ephone_s.cphone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneDesktopBinding
import com.susking.ephone_s.cphone.domain.model.CPhoneApp
import com.susking.ephone_s.cphone.domain.model.CPhoneAppType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone第二层界面：AI手机桌面
 * 显示AI角色的模拟手机桌面，包含壁纸、时间日期和App图标
 */
class CPhoneDesktopFragment : Fragment() {

    private var _binding: FragmentCphoneDesktopBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CPhoneAppAdapter
    private var contactId: String = ""
    private var contactNickname: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID) ?: ""
            contactNickname = it.getString(ARG_CONTACT_NICKNAME) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneDesktopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupTimeDate()
        setupApps()
    }

    /**
     * 设置时间和日期
     */
    private fun setupTimeDate() {
        val currentTime = Date()
        
        // 设置时间（24小时制）
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvTime.text = timeFormat.format(currentTime)
        
        // 设置日期
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(currentTime)
    }

    /**
     * 设置App图标列表
     */
    private fun setupApps() {
        val apps = listOf(
            CPhoneApp(CPhoneAppType.ALBUM, "相册", R.drawable.ic_cphone_album),
            CPhoneApp(CPhoneAppType.BROWSER, "浏览器", R.drawable.ic_cphone_browser),
            CPhoneApp(CPhoneAppType.TAOBAO, "淘宝", R.drawable.ic_cphone_taobao),
            CPhoneApp(CPhoneAppType.MEMO, "备忘录", R.drawable.ic_cphone_memo),
            CPhoneApp(CPhoneAppType.DIARY, "日记", R.drawable.ic_cphone_diary),
            CPhoneApp(CPhoneAppType.AMAP, "地图", R.drawable.ic_cphone_amap),
            CPhoneApp(CPhoneAppType.USAGE, "使用记录", R.drawable.ic_cphone_usage),
            CPhoneApp(CPhoneAppType.MUSIC, "音乐", R.drawable.ic_cphone_music),
            CPhoneApp(CPhoneAppType.QQ, "QQ", R.drawable.ic_cphone_qq)
        )

        adapter = CPhoneAppAdapter { app ->
            onAppClick(app)
        }

        binding.rvApps.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = this@CPhoneDesktopFragment.adapter
        }

        adapter.submitList(apps)
    }

    /**
     * 处理App点击事件
     */
    private fun onAppClick(app: CPhoneApp) {
        val fragment = when (app.type) {
            CPhoneAppType.ALBUM -> {
                com.susking.ephone_s.cphone.ui.album.CPhoneAlbumFragment.newInstance(contactId)
            }
            CPhoneAppType.BROWSER -> {
                com.susking.ephone_s.cphone.ui.browser.CPhoneBrowserFragment.newInstance(contactId)
            }
            CPhoneAppType.MEMO -> {
                com.susking.ephone_s.cphone.ui.memo.CPhoneMemoFragment.newInstance(contactId)
            }
            CPhoneAppType.TAOBAO -> {
                com.susking.ephone_s.cphone.ui.taobao.CPhoneTaobaoFragment.newInstance(contactId)
            }
            CPhoneAppType.DIARY -> {
                com.susking.ephone_s.cphone.ui.diary.CPhoneDiaryFragment.newInstance(contactId)
            }
            CPhoneAppType.AMAP -> {
                com.susking.ephone_s.cphone.ui.amap.CPhoneAmapFragment.newInstance(contactId)
            }
            CPhoneAppType.USAGE -> {
                com.susking.ephone_s.cphone.ui.usage.CPhoneUsageFragment.newInstance(contactId)
            }
            CPhoneAppType.MUSIC -> {
                com.susking.ephone_s.cphone.ui.music.CPhoneMusicFragment.newInstance(contactId)
            }
            CPhoneAppType.QQ -> {
                com.susking.ephone_s.cphone.ui.qq.CPhoneSimulatedQQFragment.newInstance(contactId)
            }
        }
        
        // 跳转到第三层：具体App界面
        // 获取当前Fragment所在的容器ID
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_CONTACT_NICKNAME = "contact_nickname"

        fun newInstance(contactId: String, contactNickname: String) =
            CPhoneDesktopFragment().apply {
                arguments = bundleOf(
                    ARG_CONTACT_ID to contactId,
                    ARG_CONTACT_NICKNAME to contactNickname
                )
            }
    }
}