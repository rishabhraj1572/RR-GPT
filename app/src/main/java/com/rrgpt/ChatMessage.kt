package com.rrgpt

import android.net.Uri
import java.io.Serializable

data class ChatMessage (
    var text: String? = null,
    val images: List<Uri> = emptyList(),
    val isUser: Boolean = true
): Serializable
