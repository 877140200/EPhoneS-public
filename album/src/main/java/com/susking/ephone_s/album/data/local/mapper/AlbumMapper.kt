package com.susking.ephone_s.album.data.local.mapper

import com.susking.ephone_s.album.domain.model.Album
import com.susking.ephone_s.aidata.data.local.entity.AlbumEntity

/**
 * Album 和 AlbumEntity 之间的转换器
 */
object AlbumMapper {
    
    /**
     * 将 AlbumEntity 转换为 Album domain model
     */
    fun toDomain(entity: AlbumEntity): Album {
        return Album(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt,
            coverImagePath = entity.coverImagePath,
            photoCount = entity.photoCount
        )
    }
    
    /**
     * 将 Album domain model 转换为 AlbumEntity
     */
    fun toEntity(domain: Album): AlbumEntity {
        return AlbumEntity(
            id = domain.id,
            name = domain.name,
            createdAt = domain.createdAt,
            coverImagePath = domain.coverImagePath,
            photoCount = domain.photoCount
        )
    }
    
    /**
     * 批量转换 Entity 列表为 Domain 列表
     */
    fun toDomainList(entities: List<AlbumEntity>): List<Album> {
        return entities.map { toDomain(it) }
    }
}