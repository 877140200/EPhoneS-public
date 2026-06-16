package com.susking.ephone_s.aidata.data.local.converters

import androidx.room.TypeConverter
import com.susking.ephone_s.aidata.domain.model.MemoryType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallPurpose
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipChangeAction
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallSceneType
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel

class MemoryTypeConverters {
    @TypeConverter
    fun fromSummaryLevel(level: SummaryLevel?): String? {
        return level?.name
    }

    @TypeConverter
    fun toSummaryLevel(name: String?): SummaryLevel? {
        return name?.let { SummaryLevel.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryEventType(type: MemoryEventType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMemoryEventType(name: String?): MemoryEventType? {
        return name?.let { MemoryEventType.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryEventStatus(status: MemoryEventStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toMemoryEventStatus(name: String?): MemoryEventStatus? {
        return name?.let { MemoryEventStatus.valueOf(it) }
    }

    @TypeConverter
    fun fromRelationshipChangeAction(action: RelationshipChangeAction?): String? {
        return action?.name
    }

    @TypeConverter
    fun toRelationshipChangeAction(name: String?): RelationshipChangeAction? {
        return name?.let { RelationshipChangeAction.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryIndexedObjectType(type: MemoryIndexedObjectType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMemoryIndexedObjectType(name: String?): MemoryIndexedObjectType? {
        return name?.let { MemoryIndexedObjectType.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryRecallSceneType(type: MemoryRecallSceneType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMemoryRecallSceneType(name: String?): MemoryRecallSceneType? {
        return name?.let { MemoryRecallSceneType.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryRecallPurpose(purpose: MemoryRecallPurpose?): String? {
        return purpose?.name
    }

    @TypeConverter
    fun toMemoryRecallPurpose(name: String?): MemoryRecallPurpose? {
        return name?.let { MemoryRecallPurpose.valueOf(it) }
    }

    @TypeConverter
    fun fromMemoryType(type: MemoryType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMemoryType(name: String?): MemoryType? {
        return name?.let { MemoryType.valueOf(it) }
    }
}
