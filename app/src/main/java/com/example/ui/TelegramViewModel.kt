package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseService
import com.example.data.TelegramChat
import com.example.data.TelegramMessage
import com.example.data.TelegramUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: TelegramUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class TelegramViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TelegramViewModel"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<TelegramMessage>>(emptyList())
    val messages: StateFlow<List<TelegramMessage>> = _messages.asStateFlow()

    private val _searchedUsers = MutableStateFlow<List<TelegramUser>>(emptyList())
    val searchedUsers: StateFlow<List<TelegramUser>> = _searchedUsers.asStateFlow()

    private val _searchedChats = MutableStateFlow<List<TelegramChat>>(emptyList())
    val searchedChats: StateFlow<List<TelegramChat>> = _searchedChats.asStateFlow()

    private val _activeChat = MutableStateFlow<TelegramChat?>(null)
    val activeChat: StateFlow<TelegramChat?> = _activeChat.asStateFlow()

    private val _activeChatTypingUsers = MutableStateFlow<List<String>>(emptyList())
    val activeChatTypingUsers: StateFlow<List<String>> = _activeChatTypingUsers.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    private val _customWebApps = MutableStateFlow<List<com.example.data.CustomWebApp>>(emptyList())
    val customWebApps: StateFlow<List<com.example.data.CustomWebApp>> = _customWebApps.asStateFlow()

    private val chatsMessagesListeners = mutableMapOf<String, ValueEventListener>()

    private var chatsListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null
    private var currentUserListener: ValueEventListener? = null
    private var customWebAppsListener: ValueEventListener? = null

    init {
        FirebaseService.init(application)
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val uid = FirebaseService.currentUid
        if (uid != null) {
            _authState.value = AuthState.Loading
            observeCurrentUser(uid)
            startObservingChats()
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun loginWithEmail(email: String, pss: String) {
        if (email.isBlank() || pss.isBlank()) {
            _authState.value = AuthState.Error("Email and Password cannot be empty")
            return
        }
        _authState.value = AuthState.Loading
        FirebaseService.auth.signInWithEmailAndPassword(email, pss)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: return@addOnCompleteListener
                    observeCurrentUser(uid)
                    startObservingChats()
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Login failed")
                }
            }
    }

    fun registerWithEmail(email: String, pss: String, displayName: String) {
        if (email.isBlank() || pss.isBlank() || displayName.isBlank()) {
            _authState.value = AuthState.Error("Please fill out all fields")
            return
        }
        _authState.value = AuthState.Loading
        FirebaseService.auth.createUserWithEmailAndPassword(email, pss)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: return@addOnCompleteListener
                    val newUser = TelegramUser(
                        uid = uid,
                        displayName = displayName,
                        email = email,
                        photoUrl = "https://ui-avatars.com/api/?name=${displayName.replace(" ", "+")}&background=0D8ABC&color=fff",
                        status = "online",
                        lastSeen = System.currentTimeMillis()
                    )
                    FirebaseService.saveUser(newUser) { success ->
                        if (success) {
                            _authState.value = AuthState.Authenticated(newUser)
                            startObservingChats()
                        } else {
                            _authState.value = AuthState.Error("Failed to save user record")
                        }
                    }
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Registration failed")
                }
            }
    }

    // Direct Google authenticated credential binding
    fun loginWithGoogleCredential(idToken: String, forcedDisplayName: String? = null) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseService.auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val fbUser = task.result?.user ?: return@addOnCompleteListener
                    val displayName = forcedDisplayName ?: fbUser.displayName ?: "Google User"
                    val newUser = TelegramUser(
                        uid = fbUser.uid,
                        displayName = displayName,
                        email = fbUser.email ?: "",
                        photoUrl = fbUser.photoUrl?.toString() ?: "https://ui-avatars.com/api/?name=${displayName.replace(" ", "+")}&background=50a2e9&color=fff",
                        status = "online",
                        lastSeen = System.currentTimeMillis()
                    )
                    FirebaseService.saveUser(newUser) { success ->
                        if (success) {
                            _authState.value = AuthState.Authenticated(newUser)
                            startObservingChats()
                        } else {
                            // If user already existed, load existing user options
                            observeCurrentUser(fbUser.uid)
                            startObservingChats()
                        }
                    }
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Google Authentication failed")
                }
            }
    }

    // Elegant override mock auth system for demo/sandbox environments when Google Play Services are absent!
    fun loginMockOverride(mockEmail: String, forcedName: String) {
        _authState.value = AuthState.Loading
        // Attempt login or registration directly
        val cleanEmail = mockEmail.trim()
        val dummyPass = "MockPassword123!"
        
        FirebaseService.auth.signInWithEmailAndPassword(cleanEmail, dummyPass)
            .addOnCompleteListener { loginTask ->
                if (loginTask.isSuccessful) {
                    val uid = loginTask.result?.user?.uid ?: return@addOnCompleteListener
                    observeCurrentUser(uid)
                    startObservingChats()
                } else {
                    // Try to register directly
                    FirebaseService.auth.createUserWithEmailAndPassword(cleanEmail, dummyPass)
                        .addOnCompleteListener { regTask ->
                            if (regTask.isSuccessful) {
                                val uid = regTask.result?.user?.uid ?: return@addOnCompleteListener
                                val newUser = TelegramUser(
                                    uid = uid,
                                    displayName = forcedName,
                                    email = cleanEmail,
                                    photoUrl = "https://ui-avatars.com/api/?name=${forcedName.replace(" ", "+")}&background=50a2e9&color=fff",
                                    status = "online",
                                    lastSeen = System.currentTimeMillis()
                                )
                                FirebaseService.saveUser(newUser) {
                                    _authState.value = AuthState.Authenticated(newUser)
                                    startObservingChats()
                                }
                            } else {
                                _authState.value = AuthState.Error("A Sandbox Override Account could not be initialized: ${regTask.exception?.message}")
                            }
                        }
                }
            }
    }

    private fun observeCurrentUser(uid: String) {
        currentUserListener?.let { FirebaseService.usersRef.child(uid).removeEventListener(it) }
        currentUserListener = FirebaseService.observeCurrentUser(uid) { user ->
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
            } else {
                // Save user record if auth exists but RTDB user is missing
                val fbUser = FirebaseService.auth.currentUser
                if (fbUser != null) {
                    val placeholderName = fbUser.displayName ?: fbUser.email?.substringBefore("@") ?: "User"
                    val newUser = TelegramUser(
                        uid = fbUser.uid,
                        displayName = placeholderName,
                        email = fbUser.email ?: "",
                        photoUrl = "https://ui-avatars.com/api/?name=${placeholderName.replace(" ", "+")}&background=50a2e9&color=fff",
                        status = "online",
                        lastSeen = System.currentTimeMillis()
                    )
                    FirebaseService.saveUser(newUser) {
                        _authState.value = AuthState.Authenticated(newUser)
                    }
                }
            }
        }
    }

    private fun startObservingChats() {
        startObservingCustomWebApps()
        chatsListener?.let { FirebaseService.chatsRef.removeEventListener(it) }
        chatsListener = FirebaseService.observeChats { chatList ->
            _chats.value = chatList

            val currentUids = chatList.map { it.id }.toSet()
            val removedChatIds = chatsMessagesListeners.keys.filter { it !in currentUids }
            removedChatIds.forEach { cid ->
                chatsMessagesListeners[cid]?.let { listener ->
                    FirebaseService.messagesRef.child(cid).removeEventListener(listener)
                }
                chatsMessagesListeners.remove(cid)
            }
            // Filter unread counts to keep only active chat IDs
            _unreadCounts.value = _unreadCounts.value.filterKeys { it in currentUids }

            chatList.forEach { chat ->
                if (!chatsMessagesListeners.containsKey(chat.id)) {
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val myId = FirebaseService.currentUid ?: return
                            val activeChatId = _activeChat.value?.id
                            if (chat.id == activeChatId) {
                                val updatedMap = _unreadCounts.value.toMutableMap()
                                updatedMap[chat.id] = 0
                                _unreadCounts.value = updatedMap
                                FirebaseService.markMessagesAsRead(chat.id)
                                return
                            }
                            var unreadCount = 0
                            for (child in snapshot.children) {
                                val msg = child.getValue(TelegramMessage::class.java)
                                if (msg != null && msg.senderId != myId) {
                                    val isReadDb = child.child("isRead").getValue(Boolean::class.java)
                                        ?: child.child("read").getValue(Boolean::class.java)
                                        ?: false
                                    if (!isReadDb) {
                                        unreadCount++
                                    }
                                }
                            }
                            val updatedMap = _unreadCounts.value.toMutableMap()
                            updatedMap[chat.id] = unreadCount
                            _unreadCounts.value = updatedMap
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    }
                    FirebaseService.messagesRef.child(chat.id).addValueEventListener(listener)
                    chatsMessagesListeners[chat.id] = listener
                }
            }
        }
    }

    fun selectChat(chat: TelegramChat?) {
        messagesListener?.let { listener ->
            val prevChatId = _activeChat.value?.id
            if (prevChatId != null) {
                FirebaseService.messagesRef.child(prevChatId).removeEventListener(listener)
            }
        }
        typingListener?.let { listener ->
            val prevChatId = _activeChat.value?.id
            if (prevChatId != null) {
                FirebaseService.chatsRef.child(prevChatId).child("typing").removeEventListener(listener)
            }
        }
        _activeChat.value = chat
        _messages.value = emptyList()
        _activeChatTypingUsers.value = emptyList()

        if (chat != null) {
            val updatedMap = _unreadCounts.value.toMutableMap()
            updatedMap[chat.id] = 0
            _unreadCounts.value = updatedMap
            FirebaseService.markMessagesAsRead(chat.id)

            messagesListener = FirebaseService.observeMessages(chat.id) { messageList ->
                _messages.value = messageList
                FirebaseService.markMessagesAsRead(chat.id)
            }
            typingListener = FirebaseService.observeTypingStatus(chat.id) { typingMap ->
                val uids = typingMap.keys.toList()
                if (uids.isEmpty()) {
                    _activeChatTypingUsers.value = emptyList()
                } else {
                    val names = mutableListOf<String>()
                    var count = 0
                    uids.forEach { uid ->
                        fetchUserProfile(uid) { user ->
                            if (user != null) {
                                names.add(user.displayName)
                            }
                            count++
                            if (count == uids.size) {
                                _activeChatTypingUsers.value = names
                            }
                        }
                    }
                }
            }
        }
    }

    fun openSavedMessages() {
        val myId = FirebaseService.currentUid ?: return
        val savedChat = _chats.value.find { it.type == "SAVED" }
        if (savedChat != null) {
            selectChat(savedChat)
        } else {
            FirebaseService.createChat(
                name = "Избранное",
                type = "SAVED",
                members = emptyList(),
                isPrivate = true
            ) { chatId ->
                if (chatId != null) {
                    val newChat = TelegramChat(
                        id = chatId,
                        name = "Избранное",
                        type = "SAVED",
                        creatorId = myId,
                        members = mapOf(myId to true)
                    )
                    selectChat(newChat)
                }
            }
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        val chat = _activeChat.value ?: return
        FirebaseService.setTypingStatus(chat.id, isTyping)
    }

    fun archiveChat(chatId: String, isArchived: Boolean) {
        FirebaseService.archiveChat(chatId, isArchived)
    }

    fun editMessage(messageId: String, newText: String) {
        val chat = _activeChat.value ?: return
        FirebaseService.editMessage(chat.id, messageId, newText)
    }

    fun deleteMessage(messageId: String) {
        val chat = _activeChat.value ?: return
        FirebaseService.deleteMessage(chat.id, messageId)
    }

    fun sendMessage(
        text: String,
        replyToId: String = "",
        replyToText: String = "",
        replyToSenderName: String = "",
        webAppUrl: String = "",
        webAppName: String = ""
    ) {
        val chat = _activeChat.value ?: return
        if (text.isBlank() && webAppUrl.isBlank()) return
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return

        FirebaseService.sendMessage(
            chatId = chat.id,
            text = text.trim(),
            senderName = myUser.displayName,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName,
            webAppUrl = webAppUrl,
            webAppName = webAppName
        )
    }

    fun forwardMessage(
        targetChatId: String,
        text: String
    ) {
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return
        FirebaseService.sendMessage(
            chatId = targetChatId,
            text = text.trim(),
            senderName = myUser.displayName,
            replyToId = "",
            replyToText = "",
            replyToSenderName = ""
        )
    }

    fun deleteChat(chatId: String, onResult: (Boolean) -> Unit = {}) {
        FirebaseService.deleteChat(chatId) { success ->
            if (success && _activeChat.value?.id == chatId) {
                selectChat(null)
            }
            onResult(success)
        }
    }

    fun leaveChat(chatId: String, onResult: (Boolean) -> Unit = {}) {
        val myUid = FirebaseService.currentUid ?: return
        FirebaseService.leaveChat(chatId, myUid) { success ->
            if (success && _activeChat.value?.id == chatId) {
                selectChat(null)
            }
            onResult(success)
        }
    }

    fun kickUser(chatId: String, userId: String, onResult: (Boolean) -> Unit = {}) {
        FirebaseService.leaveChat(chatId, userId) { success ->
            onResult(success)
        }
    }

    fun setAdminStatus(chatId: String, userId: String, isAdmin: Boolean, onResult: (Boolean) -> Unit = {}) {
        FirebaseService.setAdminStatus(chatId, userId, isAdmin) { success ->
            onResult(success)
        }
    }

    fun updateAdminPermissions(chatId: String, userId: String, permissions: String, onResult: (Boolean) -> Unit = {}) {
        FirebaseService.updateAdminPermissions(chatId, userId, permissions) { success ->
            onResult(success)
        }
    }

    fun pinMessage(chatId: String, messageId: String, onResult: (Boolean) -> Unit = {}) {
        FirebaseService.pinMessage(chatId, messageId) { success ->
            onResult(success)
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchedUsers.value = emptyList()
            _searchedChats.value = emptyList()
            return
        }
        FirebaseService.searchUsers(query) { list ->
            _searchedUsers.value = list
        }
        FirebaseService.searchChats(query) { list ->
            _searchedChats.value = list
        }
    }

    fun createChatRoom(
        name: String,
        type: String,
        username: String = "",
        isPrivate: Boolean = false,
        partnerUid: String? = null,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val members = partnerUid?.let { listOf(it) } ?: emptyList()
            val cleanUsername = username.removePrefix("@").trim().lowercase()
            val inviteKey = if (isPrivate) java.util.UUID.randomUUID().toString().take(8).uppercase() else ""
            
            if (type != "DIRECT" && !isPrivate && cleanUsername.isNotEmpty()) {
                FirebaseService.checkGroupUsernameExists(cleanUsername) { exists ->
                    if (exists) {
                        onComplete("Этот юзернейм группы уже занят")
                    } else {
                        FirebaseService.createChat(name, type, members, cleanUsername, isPrivate, inviteKey) { generatedId ->
                            if (generatedId != null) {
                                onComplete(null) // Success
                            } else {
                                onComplete("Не удалось создать чат")
                            }
                        }
                    }
                }
            } else {
                FirebaseService.createChat(name, type, members, cleanUsername, isPrivate, inviteKey) { generatedId ->
                    if (generatedId != null) {
                        onComplete(null) // Success
                    } else {
                        onComplete("Не удалось создать чат")
                    }
                }
            }
        }
    }

    fun updateProfile(displayName: String, onComplete: (Boolean) -> Unit) {
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return
        val updated = myUser.copy(displayName = displayName)
        FirebaseService.saveUser(updated) { success ->
            onComplete(success)
        }
    }

    fun updateProfileAndUsername(displayName: String, username: String, onComplete: (String?) -> Unit) {
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return
        val cleanUsername = username.removePrefix("@").trim().lowercase()
        if (displayName.isBlank()) {
            onComplete("Имя не может быть пустым")
            return
        }
        if (cleanUsername.length < 3) {
            onComplete("Имя пользователя слишком короткое (мин. 3 символа)")
            return
        }
        val regex = "^[a-zA-Z0-9_]+$".toRegex()
        if (!regex.matches(cleanUsername)) {
            onComplete("Допустимы только латинские буквы, цифры и подчеркивания")
            return
        }

        fun saveWithUsername(u: String) {
            val updated = myUser.copy(displayName = displayName, username = u)
            FirebaseService.saveUser(updated) { success ->
                if (success) {
                    onComplete(null)
                } else {
                    onComplete("Ошибка сохранения профиля")
                }
            }
        }

        if (cleanUsername == myUser.username.lowercase()) {
            saveWithUsername(myUser.username)
        } else {
            FirebaseService.checkUsernameExists(cleanUsername) { exists ->
                if (exists) {
                    onComplete("Этот юзернейм уже занят, введите другой")
                } else {
                    saveWithUsername(cleanUsername)
                }
            }
        }
    }

    fun saveProfileUsername(username: String, onComplete: (String?) -> Unit) {
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return
        val cleanUsername = username.removePrefix("@").trim().lowercase()
        if (cleanUsername.length < 3) {
            onComplete("Имя пользователя слишком короткое (мин. 3 символа)")
            return
        }
        val regex = "^[a-zA-Z0-9_]+$".toRegex()
        if (!regex.matches(cleanUsername)) {
            onComplete("Допустимы только латинские буквы, цифры и подчеркивания")
            return
        }
        FirebaseService.checkUsernameExists(cleanUsername) { exists ->
            if (exists) {
                onComplete("Этот юзернейм уже занят, введите другой")
            } else {
                val updated = myUser.copy(username = cleanUsername)
                FirebaseService.saveUser(updated) { success ->
                    if (success) {
                        _authState.value = AuthState.Authenticated(updated)
                        onComplete(null) // success, no error message
                    } else {
                        onComplete("Ошибка сохранения профиля")
                    }
                }
            }
        }
    }

    fun joinPublicChat(chat: TelegramChat) {
        val myId = FirebaseService.currentUid ?: return
        FirebaseService.joinChat(chat.id, myId) { success ->
            if (success) {
                selectChat(chat.copy(members = chat.members + (myId to true)))
            }
        }
    }

    fun joinPrivateChatByInviteKey(key: String, onResult: (String?) -> Unit) {
        val myId = FirebaseService.currentUid ?: return
        FirebaseService.joinChatByInviteKey(key, myId) { chat ->
            if (chat != null) {
                selectChat(chat)
                onResult(null) // Success
            } else {
                onResult("Неверный ключ приглашения или группа не найдена")
            }
        }
    }

    fun fetchUserProfile(uid: String, onResult: (TelegramUser?) -> Unit) {
        FirebaseService.getUserProfile(uid, onResult)
    }

    fun logOut() {
        val myId = FirebaseService.currentUid
        if (myId != null) {
            // Update online status to offline
            FirebaseService.usersRef.child(myId).child("status").setValue("offline")
        }

        // Clean observers
        currentUserListener?.let {
            val uid = FirebaseService.currentUid
            if (uid != null) FirebaseService.usersRef.child(uid).removeEventListener(it)
        }
        chatsListener?.let { FirebaseService.chatsRef.removeEventListener(it) }
        customWebAppsListener?.let { FirebaseService.customAppsRef.removeEventListener(it) }
        messagesListener?.let { listener ->
            val chatId = _activeChat.value?.id
            if (chatId != null) FirebaseService.messagesRef.child(chatId).removeEventListener(listener)
        }
        typingListener?.let { listener ->
            val chatId = _activeChat.value?.id
            if (chatId != null) FirebaseService.chatsRef.child(chatId).child("typing").removeEventListener(listener)
        }

        chatsMessagesListeners.forEach { (cid, listener) ->
            FirebaseService.messagesRef.child(cid).removeEventListener(listener)
        }
        chatsMessagesListeners.clear()
        _unreadCounts.value = emptyMap()

        FirebaseService.auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _chats.value = emptyList()
        _messages.value = emptyList()
        _activeChat.value = null
    }

    override fun onCleared() {
        super.onCleared()
        currentUserListener?.let {
            val uid = FirebaseService.currentUid
            if (uid != null) FirebaseService.usersRef.child(uid).removeEventListener(it)
        }
        chatsListener?.let { FirebaseService.chatsRef.removeEventListener(it) }
        customWebAppsListener?.let { FirebaseService.customAppsRef.removeEventListener(it) }
        messagesListener?.let { listener ->
            val chatId = _activeChat.value?.id
            if (chatId != null) FirebaseService.messagesRef.child(chatId).removeEventListener(listener)
        }
        typingListener?.let { listener ->
            val chatId = _activeChat.value?.id
            if (chatId != null) FirebaseService.chatsRef.child(chatId).child("typing").removeEventListener(listener)
        }

        chatsMessagesListeners.forEach { (cid, listener) ->
            FirebaseService.messagesRef.child(cid).removeEventListener(listener)
        }
        chatsMessagesListeners.clear()
    }

    private fun startObservingCustomWebApps() {
        customWebAppsListener?.let { FirebaseService.customAppsRef.removeEventListener(it) }
        customWebAppsListener = FirebaseService.observeCustomWebApps { appList ->
            _customWebApps.value = appList
        }
    }

    fun createCustomWebApp(name: String, url: String, onComplete: (Boolean) -> Unit) {
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return
        FirebaseService.createCustomWebApp(name, url, myUser.displayName, onComplete)
    }

    fun deleteCustomWebApp(id: String, onComplete: (Boolean) -> Unit) {
        FirebaseService.deleteCustomWebApp(id, onComplete)
    }
}
