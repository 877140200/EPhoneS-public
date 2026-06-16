package com.susking.ephone_s.album.data.local.mapper

import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.aidata.data.local.entity.PhotoEntity

/**
 * Photo 和 PhotoEntity 之间的转换器
 */
object PhotoMapper {
    
    /**
     * 将 PhotoEntity 转换为 Photo domain model
     */
    fun toDomain(entity: PhotoEntity): Photo {
        return Photo(
            id = entity.id,
            uri = entity.uri,
            albumId = entity.albumId,
            dateAdded = entity.dateAdded,
            isFavorited = entity.isFavorited
        )
    }
    
    /**
     * 将 Photo domain model 转换为 PhotoEntity
     */
    fun toEntity(domain: Photo): PhotoEntity {
        return PhotoEntity(
            id = domain.id,
            uri = domain.uri,
            albumId = domain.albumId,
            dateAdded = domain.dateAdded,
            isFavorited = domain.isFavorited
        )
    }
    
    /**
     * 批量转换 Entity 列表为 Domain 列表
     */
    fun toDomainList(entities: List<PhotoEntity>): List<Photo> {
        return entities.map { toDomain(it) }
    }
}