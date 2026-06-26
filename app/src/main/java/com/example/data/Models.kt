package com.example.data

import com.google.firebase.database.PropertyName

data class TelegramUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val username: String = "",
    val archivedChats: Map<String, Boolean> = emptyMap()
)

data class TelegramChat(
    val id: String = "",
    val name: String = "",
    val type: String = "DIRECT", // "DIRECT", "GROUP", "CHANNEL", "SAVED"
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val creatorId: String = "",
    val members: Map<String, Boolean> = emptyMap(),
    val username: String = "",
    val isPrivate: Boolean = false,
    val inviteKey: String = "",
    val admins: Map<String, Boolean> = emptyMap(),
    val adminPermissions: Map<String, String> = emptyMap(), // Key: userId, Value: comma-separated list like "canWrite,canPin,canAddAdmins"
    val pinnedMessageId: String = ""
)

data class TelegramMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val scheduledTime: Long = 0L,
    val webAppUrl: String = "",
    val webAppName: String = ""
)

data class CustomWebApp(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val creatorId: String = "",
    val creatorName: String = ""
)

