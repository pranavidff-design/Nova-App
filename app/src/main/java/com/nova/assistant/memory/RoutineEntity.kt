package com.nova.assistant.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * A routine is either:
 *  - taught directly ("when I say X, do A, B, C") -> isUserTaught = true, active immediately
 *  - noticed by pattern-tracking (see RoutineLearner) -> isUserTaught = false,
 *    starts as a SUGGESTION only. Nova never silently turns a pattern into an
 *    active routine — the spec is explicit: "Nova should suggest, not silently change behavior."
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerPhrase: String,
    val actions: String,       // comma-separated action descriptions, kept simple for MVP
    val isUserTaught: Boolean,
    val isActive: Boolean = true,
    val timesObserved: Int = 1
)

@Dao
interface RoutineDao {
    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Query("SELECT * FROM routines WHERE isActive = 1")
    suspend fun getActive(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE triggerPhrase = :phrase LIMIT 1")
    suspend fun findByTrigger(phrase: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE isUserTaught = 0 AND isActive = 1")
    suspend fun getPendingSuggestions(): List<RoutineEntity>
}
