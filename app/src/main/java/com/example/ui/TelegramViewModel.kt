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

    private val _activeChat = MutableStateFlow<TelegramChat?>(null)
    val activeChat: StateFlow<TelegramChat?> = _activeChat.asStateFlow()

    private var chatsListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null
    private var currentUserListener: ValueEventListener? = null

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
        chatsListener?.let { FirebaseService.chatsRef.removeEventListener(it) }
        chatsListener = FirebaseService.observeChats { chatList ->
            _chats.value = chatList
        }
    }

    fun selectChat(chat: TelegramChat?) {
        _activeChat.value = chat
        messagesListener?.let { listener ->
            val prevChatId = _activeChat.value?.id
            if (prevChatId != null) {
                FirebaseService.messagesRef.child(prevChatId).removeEventListener(listener)
            }
        }
        _messages.value = emptyList()

        if (chat != null) {
            messagesListener = FirebaseService.observeMessages(chat.id) { messageList ->
                _messages.value = messageList
            }
        }
    }

    fun sendMessage(text: String) {
        val chat = _activeChat.value ?: return
        if (text.isBlank()) return
        val myUser = (authState.value as? AuthState.Authenticated)?.user ?: return

        FirebaseService.sendMessage(chat.id, text.trim(), myUser.displayName)
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchedUsers.value = emptyList()
            return
        }
        FirebaseService.searchUsers(query) { list ->
            _searchedUsers.value = list
        }
    }

    fun createChatRoom(name: String, type: String, partnerUid: String? = null) {
        viewModelScope.launch {
            val members = partnerUid?.let { listOf(it) } ?: emptyList()
            FirebaseService.createChat(name, type, members) { generatedId ->
                if (generatedId != null) {
                    // Create chat succeeded
                    Log.d(TAG, "Chat room created successfully: $generatedId")
                }
            }
        }
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
        messagesListener?.let { listener ->
            val chatId = _activeChat.value?.id
            if (chatId != null) FirebaseService.messagesRef.child(chatId).removeEventListener(listener)
        }

        FirebaseService.auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _chats.value = emptyList()
        _messages.value = emptyList()
        _activeChat.value = null
    }

    override fun onCleared() {
        super.onCleared()
        logOut()
    }
}
