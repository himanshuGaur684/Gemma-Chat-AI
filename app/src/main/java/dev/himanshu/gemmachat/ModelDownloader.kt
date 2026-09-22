package dev.himanshu.gemmachat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ModelDownloader {

    fun modelFile(context: Context) = File(context.filesDir, "model.litertlm")

    suspend fun ensureModel(context: Context): String = withContext(Dispatchers.IO) {

        val target = modelFile(context)

        if (target.exists().not() && target.length() == 0L) {
            context.assets.open("model.litertlm").use { input ->

                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        target.absolutePath

    }


}