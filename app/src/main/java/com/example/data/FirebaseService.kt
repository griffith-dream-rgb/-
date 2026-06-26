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
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
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

    fun searchUsers(query: String, onResult: (List<TelegramUser>) -> Unit) {
        if (!isInitialized || query.isBlank()) {
            onResult(emptyList())
            return
        }
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val results = mutableListOf<TelegramUser>()
                for (child in snapshot.children) {
                    val user = child.getValue(TelegramUser::class.java)
                    if (user != null && user.uid != currentUid) {
                        val matchesName = user.displayName.contains(query, ignoreCase = true)
                        val matchesEmail = user.email.contains(query, ignoreCase = true)
                        if (matchesName || matchesEmail) {
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

    fun createChat(name: String, type: String, members: List<String>, onComplete: (String?) -> Unit) {
        if (!isInitialized) return
        val chatId = chatsRef.push().key ?: return
        val currentId = currentUid ?: return

        // Standardise direct chats name based on other member's name
        val chatMembers = (members + currentId).associateWith { true }
        val newChat = TelegramChat(
            id = chatId,
            name = name,
            type = type,
            creatorId = currentId,
            members = chatMembers,
            lastMessageTime = System.currentTimeMillis(),
            lastMessage = "Direct Room Created"
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

    fun observeChats(onUpdate: (List<TelegramChat>) -> Unit): ValueEventListener? {
        if (!isInitialized) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TelegramChat>()
                val myId = currentUid ?: return
                for (child in snapshot.children) {
                    val chat = child.getValue(TelegramChat::class.java)
                    if (chat != null) {
                        // Show all channels, or direct chats/groups where I am a creator or member
                        val isMember = chat.members.containsKey(myId)
                        val isPublicChannel = chat.type == "CHANNEL"
                        if (isMember || isPublicChannel || chat.creatorId == myId) {
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

    fun sendMessage(chatId: String, text: String, senderName: String, onComplete: (Boolean) -> Unit = {}) {
        if (!isInitialized) return
        val msgId = messagesRef.child(chatId).push().key ?: return
        val myId = currentUid ?: return

        val message = TelegramMessage(
            id = msgId,
            chatId = chatId,
            senderId = myId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis()
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
}
