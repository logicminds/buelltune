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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for rider-generated conversation/message persistence (R15,
 * KTD5) - a normal, mutable, growing, coroutine-queried database, versioned
 * independently of the bundled reference database
 * ([biz.logicminds.buelltune.data.EcmDefinitionsDatabase]). Unlike that
 * database, this one is created and migrated by Room itself: no
 * `createFromAsset`, no `allowMainThreadQueries` (every access here goes
 * through [ChatRepository]'s suspend/`Flow` API). `fallbackToDestructiveMigration`
 * is the deliberate v1 migration strategy for this local-only, easily
 * re-creatable conversation store - there is no user-visible data loss risk
 * worth a hand-written [androidx.room.migration.Migration] yet.
 */
@Database(
    entities = [ConversationEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DB_NAME = "chat.db"

        @Volatile
        private var instance: ChatDatabase? = null

        /** Resolve the process-wide singleton, building it on first access - mirrors [biz.logicminds.buelltune.data.EcmDefinitionsDatabase.getInstance]'s lazy/synchronized shape. */
        @JvmStatic
        fun getInstance(context: Context): ChatDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
