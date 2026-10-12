package app.lumen.chat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun allConversations(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE title LIKE '%' || :q || '%' ESCAPE '\'
           OR id IN (
               SELECT conversationId FROM messages
               WHERE content LIKE '%' || :q || '%' ESCAPE '\'
           )
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun searchConversations(q: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun conversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversationOnce(id: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    fun messages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    suspend fun messagesOnce(id: String): List<MessageEntity>

    @Query("SELECT MAX(createdAt) FROM messages WHERE conversationId = :id")
    suspend fun lastMessageTime(id: String): Long?

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, isError = :isError WHERE id = :id")
    suspend fun updateMessage(id: String, content: String, isError: Boolean)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET model = :model WHERE id = :id")
    suspend fun setModel(id: String, model: String)

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE role = 'assistant' AND content = ''")
    suspend fun deleteEmptyAssistantMessages()

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
