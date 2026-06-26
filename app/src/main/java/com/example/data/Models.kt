package com.example.data

data class TelegramUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "offline",
    val lastSeen: Long = 0L
)

data class TelegramChat(
    val id: String = "",
    val name: String = "",
    val type: String = "DIRECT", // "DIRECT", "GROUP", "CHANNEL"
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val creatorId: String = "",
    val members: Map<String, Boolean> = emptyMap()
)

data class TelegramMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false
)
