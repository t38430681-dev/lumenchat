package app.lumen.chat

import android.content.Context
import androidx.room.Room
import app.lumen.chat.data.ChatRepository
import app.lumen.chat.data.SettingsStore
import app.lumen.chat.data.api.AnthropicClient
import app.lumen.chat.data.db.AppDatabase

class AppContainer(context: Context) {
    val settings = SettingsStore(context)

    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "lumen.db",
    ).build()

    val repository = ChatRepository(database.chatDao(), AnthropicClient())
}
