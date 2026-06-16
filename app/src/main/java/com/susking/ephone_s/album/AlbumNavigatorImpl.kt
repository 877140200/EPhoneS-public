package com.susking.ephone_s.album

import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.R
import com.susking.ephone_s.album.api.AlbumNavigator
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.album.ui.PhotoViewerFragment
import com.susking.ephone_s.album.ui.photogrid.PhotoGridFragment

/**
 * AlbumNavigator接口的实现类
 * 在app模块中实现，提供导航能力
 */
class AlbumNavigatorImpl : AlbumNavigator {
    
    override fun getContainerId(): Int = R.id.main_fragment_container
    
    override fun navigateToPhotoViewer(
        fragmentManager: FragmentManager,
        photos: ArrayList<Photo>,
        position: Int
    ) {
        val fragment = PhotoViewerFragment.newInstance(photos, position)
        fragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    override fun navigateToPhotoGrid(
        fragmentManager: FragmentManager,
        albumId: Long,
        albumName: String,
        isFavorites: Boolean
    ) {
        val fragment = if (isFavorites) {
            PhotoGridFragment.newInstanceForFavorites()
        } else {
            PhotoGridFragment.newInstance(albumId, albumName)
        }
        fragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    override fun navigateBack() {
        // 由调用方处理
    }
}