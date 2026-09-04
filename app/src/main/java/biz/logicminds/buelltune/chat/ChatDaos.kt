/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2012 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune.chat

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A [ConversationDao.observeAllWithPreview] row (buelltune-1vt): [preview]
 * is the conversation's first user-turn content, `null` for a brand-new
 * conversation with no turns yet - the rider-browsable list's short summary,
 * standing in for [ConversationEntity.title] alone (which is just a
 * creation-time date/time label, see [ChatFragment.createConversationEntity]).
 */
data class ConversationWithPreview(
    @Embedded val conversation: ConversationEntity,
    val preview: String?,
)

@Dao
interface ConversationDao {
    /** The rider-browsable conversation list (R15), newest first. */
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    /**
     * [observeAll] plus each row's first user turn as [ConversationWithPreview.preview]
     * (buelltune-1vt) - a correlated subquery rather than a second observed
     * `Flow`/join in Kotlin, so a single emission always reflects a single,
     * consistent snapshot of both tables.
     */
    @Query(
        "SELECT c.*, (" +
            "SELECT content FROM chat_messages m " +
            "WHERE m.conversationId = c.id AND m.role = 'USER' " +
            "ORDER BY m.id ASC LIMIT 1" +
            ") AS preview " +
            "FROM conversations c ORDER BY c.createdAt DESC",
    )
    fun observeAllWithPreview(): Flow<List<ConversationWithPreview>>

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    /**
     * Cascades to every row in `chat_messages` whose `conversationId`
     * matches, via [ChatMessageEntity]'s `onDelete = CASCADE` foreign key.
     */
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: Long)
}

@Dao
interface ChatMessageDao {
    /** One conversation's turns, oldest first - the order both the transcript UI and [ChatRepository] replay in. */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long
}
