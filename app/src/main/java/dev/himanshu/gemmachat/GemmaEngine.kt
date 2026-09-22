package dev.himanshu.gemmachat

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GemmaEngine(
    private val engine: Engine,
    private val conversation: Conversation
) {

    fun reply(prompt: String): Flow<String> {
        return conversation.sendMessageAsync(prompt).map { it.toString() }
    }

    fun close() {
        conversation.close()
        engine.close()
    }

    companion object {
        suspend fun create(context: Context): GemmaEngine = withContext(Dispatchers.IO) {

            val config = EngineConfig(
                modelPath = ModelDownloader.modelFile(context).absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )

            val engine = Engine(config)

            engine.initialize()


            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of("You are a helpful assistant")
                )
            )

            GemmaEngine(
                engine = engine,
                conversation = conversation
            )


        }
    }


}