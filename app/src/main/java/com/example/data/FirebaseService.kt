package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseService {
    private const val TAG = "FirebaseService"
    private var isInitialized = false

    lateinit var auth: FirebaseAuth
    lateinit var database: FirebaseDatabase
    lateinit var usersRef: DatabaseReference
    lateinit var chatsRef: DatabaseReference
    lateinit var messagesRef: DatabaseReference

    fun init(context: Context) {
        if (isInitialized) return

        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAZXt07diZQKQu03n4QdhJe8_IvcLd5cBE")
                .setApplicationId("1:791368904590:android:08b5908e9e93ed24c6fedf")
                .setDatabaseUrl("https://menejer-ca10d-default-rtdb.firebaseio.com")
                .setProjectId("menejer-ca10d")
                .setStorageBucket("menejer-ca10d.firebasestorage.app")
                .build()

            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context, options)
            } else {
                FirebaseApp.getInstance()
            }

            auth = FirebaseAuth.getInstance(app)
            database = FirebaseDatabase.getInstance(app, "https://menejer-ca10d-default-rtdb.firebaseio.com")

            usersRef = database.getReference("users")
            chatsRef = database.getReference("chats")
            messagesRef = database.getReference("messages")

            isInitialized = true
            Log.d(TAG, "Firebase initialized successfully.")

            // Clear database once as requested by the user
            clearFirebaseDataOnce(context)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
        }
    }

    private fun clearFirebaseDataOnce(context: Context) {
        val prefs = context.getSharedPreferences("firebase_prefs", Context.MODE_PRIVATE)
        val hasCleared = prefs.getBoolean("has_cleared_rtdb_v3", false)
        if (!hasCleared) {
            Log.d(TAG, "PERFORMING ONE-TIME FIREBASE DATA CLEANUP...")
            usersRef.removeValue()
            chatsRef.removeValue()
            messagesRef.removeValue()
            prefs.edit().putBoolean("has_cleared_rtdb_v3", true).apply()
        }
    }

    val currentUid: String?
        get() = if (isInitialized) auth.currentUser?.uid else null

    fun saveUser(user: TelegramUser, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) return
        usersRef.child(user.uid).setValue(user)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun observeCurrentUser(uid: String, onUpdate: (TelegramUser?) -> Unit): ValueEventListener? {
        if (!isInitialized) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(TelegramUser::class.java)
                onUpdate(user)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User observe cancelled: ${error.message}")
            }
        }
        usersRef.child(uid).addValueEventListener(listener)
        return listener
    }

    fun getUserProfile(uid: String, onResult: (TelegramUser?) -> Unit) {
        if (!isInitialized) {
            onResult(null)
            return
        }
        usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(TelegramUser::class.java)
                onResult(user)
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(null)
            }
        })
    }

    fun searchUsers(query: String, onResult: (List<TelegramUser>) -> Unit) {
        if (!isInitialized || query.isBlank()) {
            onResult(emptyList())
            return
        }
        val cleanQuery = query.removePrefix("@").trim().lowercase()
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val results = mutableListOf<TelegramUser>()
                for (child in snapshot.children) {
                    val user = child.getValue(TelegramUser::class.java)
                    if (user != null && user.uid != currentUid) {
                        val matchesUsername = user.username.lowercase().contains(cleanQuery)
                        val matchesName = user.displayName.contains(query, ignoreCase = true)
                        if (matchesUsername || matchesName) {
                            results.add(user)
                        }
                    }
                }
                onResult(results)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Search users cancelled: ${error.message}")
                onResult(emptyList())
            }
        })
    }

    fun searchChats(query: String, onResult: (List<TelegramChat>) -> Unit) {
        if (!isInitialized || query.isBlank()) {
            onResult(emptyList())
            return
        }
        val cleanQuery = query.removePrefix("@").trim().lowercase()
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val results = mutableListOf<TelegramChat>()
                for (child in snapshot.children) {
                    val chat = child.getValue(TelegramChat::class.java)
                    if (chat != null && !chat.isPrivate) {
                        val matchesUsername = chat.username.lowercase().contains(cleanQuery)
                        val matchesName = chat.name.contains(query, ignoreCase = true)
                        if (matchesUsername || matchesName) {
                            results.add(chat)
                        }
                    }
                }
                onResult(results)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Search chats cancelled: ${error.message}")
                onResult(emptyList())
            }
        })
    }

    fun checkUsernameExists(username: String, onResult: (Boolean) -> Unit) {
        if (!isInitialized || username.isBlank()) {
            onResult(false)
            return
        }
        val cleanUsername = username.removePrefix("@").trim().lowercase()
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var exists = false
                for (child in snapshot.children) {
                    val user = child.getValue(TelegramUser::class.java)
                    if (user != null && user.username.lowercase().trim() == cleanUsername) {
                        exists = true
                        break
                    }
                }
                onResult(exists)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(false)
            }
        })
    }

    fun checkGroupUsernameExists(username: String, onResult: (Boolean) -> Unit) {
        if (!isInitialized || username.isBlank()) {
            onResult(false)
            return
        }
        val cleanUsername = username.removePrefix("@").trim().lowercase()
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var exists = false
                for (child in snapshot.children) {
                    val chat = child.getValue(TelegramChat::class.java)
                    if (chat != null && chat.username.lowercase().trim() == cleanUsername) {
                        exists = true
                        break
                    }
                }
                onResult(exists)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(false)
            }
        })
    }

    fun createChat(
        name: String,
        type: String,
        members: List<String>,
        username: String = "",
        isPrivate: Boolean = false,
        inviteKey: String = "",
        onComplete: (String?) -> Unit
    ) {
        if (!isInitialized) return
        val chatId = chatsRef.push().key ?: return
        val currentId = currentUid ?: return

        val chatMembers = (members + currentId).associateWith { true }
        val newChat = TelegramChat(
            id = chatId,
            name = name,
            type = type,
            creatorId = currentId,
            members = chatMembers,
            lastMessageTime = System.currentTimeMillis(),
            lastMessage = if (type == "DIRECT") "Чат создан" else "Группа создана",
            username = username.removePrefix("@").trim().lowercase(),
            isPrivate = isPrivate,
            inviteKey = inviteKey
        )

        chatsRef.child(chatId).setValue(newChat)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(chatId)
                } else {
                    onComplete(null)
                }
            }
    }

    fun joinChat(chatId: String, userId: String, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        chatsRef.child(chatId).child("members").child(userId).setValue(true)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun joinChatByInviteKey(inviteKey: String, userId: String, onComplete: (TelegramChat?) -> Unit) {
        if (!isInitialized || inviteKey.isBlank()) {
            onComplete(null)
            return
        }
        val cleanKey = inviteKey.trim()
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundChat: TelegramChat? = null
                for (child in snapshot.children) {
                    val chat = child.getValue(TelegramChat::class.java)
                    if (chat != null && chat.inviteKey.trim() == cleanKey) {
                        foundChat = chat
                        break
                    }
                }
                if (foundChat != null) {
                    joinChat(foundChat.id, userId) { success ->
                        if (success) {
                            onComplete(foundChat)
                        } else {
                            onComplete(null)
                        }
                    }
                } else {
                    onComplete(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(null)
            }
        })
    }

    fun observeChats(onUpdate: (List<TelegramChat>) -> Unit): ValueEventListener? {
        if (!isInitialized) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TelegramChat>()
                val myId = currentUid ?: return
                for (child in snapshot.children) {
                    val chat = child.getValue(TelegramChat::class.java)
                    if (chat != null) {
                        // ONLY show chats where I am a member or creator!
                        val isMember = chat.members.containsKey(myId)
                        if (isMember || chat.creatorId == myId) {
                            list.add(chat)
                        }
                    }
                }
                // Sort by last message time
                list.sortByDescending { it.lastMessageTime }
                onUpdate(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Observe chats cancelled: ${error.message}")
            }
        }
        chatsRef.addValueEventListener(listener)
        return listener
    }

    fun observeMessages(chatId: String, onUpdate: (List<TelegramMessage>) -> Unit): ValueEventListener? {
        if (!isInitialized) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TelegramMessage>()
                for (child in snapshot.children) {
                    val msg = child.getValue(TelegramMessage::class.java)
                    if (msg != null) {
                        list.add(msg)
                    }
                }
                list.sortBy { it.timestamp }
                onUpdate(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Observe messages cancelled: ${error.message}")
            }
        }
        messagesRef.child(chatId).addValueEventListener(listener)
        return listener
    }

    fun sendMessage(
        chatId: String,
        text: String,
        senderName: String,
        replyToId: String = "",
        replyToText: String = "",
        replyToSenderName: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isInitialized) return
        val msgId = messagesRef.child(chatId).push().key ?: return
        val myId = currentUid ?: return

        val message = TelegramMessage(
            id = msgId,
            chatId = chatId,
            senderId = myId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName
        )

        messagesRef.child(chatId).child(msgId).setValue(message)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Update chat last message
                    val updates = mapOf<String, Any>(
                        "lastMessage" to text,
                        "lastMessageTime" to System.currentTimeMillis()
                    )
                    chatsRef.child(chatId).updateChildren(updates)
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
    }

    fun deleteChat(chatId: String, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        chatsRef.child(chatId).removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    messagesRef.child(chatId).removeValue()
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
    }

    fun leaveChat(chatId: String, userId: String, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        chatsRef.child(chatId).child("members").child(userId).removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    chatsRef.child(chatId).child("admins").child(userId).removeValue()
                    chatsRef.child(chatId).child("adminPermissions").child(userId).removeValue()
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
    }

    fun setAdminStatus(chatId: String, userId: String, isAdmin: Boolean, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        if (isAdmin) {
            chatsRef.child(chatId).child("admins").child(userId).setValue(true)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        chatsRef.child(chatId).child("adminPermissions").child(userId).setValue("canWrite,canPin")
                            .addOnCompleteListener { onComplete(it.isSuccessful) }
                    } else {
                        onComplete(false)
                    }
                }
        } else {
            chatsRef.child(chatId).child("admins").child(userId).removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        chatsRef.child(chatId).child("adminPermissions").child(userId).removeValue()
                            .addOnCompleteListener { onComplete(it.isSuccessful) }
                    } else {
                        onComplete(false)
                    }
                }
        }
    }

    fun updateAdminPermissions(chatId: String, userId: String, permissions: String, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        chatsRef.child(chatId).child("adminPermissions").child(userId).setValue(permissions)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun pinMessage(chatId: String, messageId: String, onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            onComplete(false)
            return
        }
        chatsRef.child(chatId).child("pinnedMessageId").setValue(messageId)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun markMessagesAsRead(chatId: String) {
        if (!isInitialized) return
        val myId = currentUid ?: return
        messagesRef.child(chatId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val msg = child.getValue(TelegramMessage::class.java)
                    if (msg != null && msg.senderId != myId) {
                        val isReadDb = child.child("isRead").getValue(Boolean::class.java)
                            ?: child.child("read").getValue(Boolean::class.java)
                            ?: false
                        if (!isReadDb) {
                            child.ref.child("isRead").setValue(true)
                            child.ref.child("read").setValue(true)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun setTypingStatus(chatId: String, isTyping: Boolean) {
        if (!isInitialized) return
        val myId = currentUid ?: return
        val typingRef = chatsRef.child(chatId).child("typing").child(myId)
        if (isTyping) {
            typingRef.setValue(true)
        } else {
            typingRef.removeValue()
        }
    }

    fun observeTypingStatus(chatId: String, onUpdate: (Map<String, Boolean>) -> Unit): ValueEventListener? {
        if (!isInitialized) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingMap = mutableMapOf<String, Boolean>()
                for (child in snapshot.children) {
                    val userId = child.key
                    val isTyping = child.getValue(Boolean::class.java) ?: false
                    if (userId != null && userId != currentUid && isTyping) {
                        typingMap[userId] = true
                    }
                }
                onUpdate(typingMap)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        chatsRef.child(chatId).child("typing").addValueEventListener(listener)
        return listener
    }
}
