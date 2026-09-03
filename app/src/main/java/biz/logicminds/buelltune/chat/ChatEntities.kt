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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One rider-created chat conversation (R15, KTD5). [providerId] (a
 * [ProviderId.name]) and [modelId] are set once here at creation and never
 * updated afterward (KD5) - [ChatRepository] exposes no method that mutates
 * either column after [ChatRepository.createConversation] returns.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
)

/**
 * One turn of a conversation (R15, R16). [role] is a [Role.name]
 * (`"USER"`/`"ASSISTANT"`). [toolCallsJson] is populated only on assistant
 * rows, as a JSON-encoded `List<String>` of tool names the agent called
 * while producing [content] - for on-screen display only (R12). It is
 * deliberately never read back into the model-bound turn list
 * [ChatRepository] builds for [ChatAgent.send] (KTD7, R17): only [role] and
 * [content] cross that boundary.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val toolCallsJson: String?,
    val createdAt: Long,
)
