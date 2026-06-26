package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.data.*
import com.example.ui.AuthState
import com.example.ui.TelegramViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseService.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            val customColorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF5288C1),
                    onPrimary = Color.White,
                    secondary = Color(0xFF2B3A4A),
                    onSecondary = Color.White,
                    background = Color(0xFF17212B),
                    onBackground = Color.White,
                    surface = Color(0xFF1E2C3A),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF243444),
                    onSurfaceVariant = Color(0xFF8B9BAA)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF517DA2),
                    onPrimary = Color.White,
                    secondary = Color(0xFFE4ECF4),
                    onSecondary = Color(0xFF2D3E4E),
                    background = Color.White,
                    onBackground = Color(0xFF1C1B1F),
                    surface = Color(0xFFF1F5F9),
                    onSurface = Color(0xFF1C1B1F),
                    surfaceVariant = Color(0xFFE2E8F0),
                    onSurfaceVariant = Color(0xFF64748B)
                )
            }

            MaterialTheme(
                colorScheme = customColorScheme,
                content = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TelegramApp(
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { isDarkTheme = !isDarkTheme }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun TelegramApp(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: TelegramViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val activeChat by viewModel.activeChat.collectAsState()

    AnimatedContent(
        targetState = authState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "AuthTransition"
    ) { state ->
        when (state) {
            is AuthState.Authenticated -> {
                if (state.user.username.isEmpty()) {
                    UsernameSetupScreen(
                        currentUser = state.user,
                        viewModel = viewModel
                    )
                } else if (activeChat == null) {
                    MainDashboardScreen(
                        currentUser = state.user,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        viewModel = viewModel
                    )
                } else {
                    ChatConversationScreen(
                        chat = activeChat!!,
                        currentUser = state.user,
                        onBack = { viewModel.selectChat(null) },
                        viewModel = viewModel
                    )
                }
            }
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is AuthState.Error -> {
                AuthLandingScreen(
                    errorMessage = state.message,
                    viewModel = viewModel
                )
            }
            else -> {
                AuthLandingScreen(
                    errorMessage = null,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ============================================
// UNIQUE USERNAME SETUP SCREEN
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSetupScreen(
    currentUser: TelegramUser,
    viewModel: TelegramViewModel
) {
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Имя пользователя",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Создайте уникальное имя пользователя. Другие люди смогут искать вас по нему, не видя вашей почты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Имя пользователя") },
                placeholder = { Text("username") },
                prefix = { Text("@", color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                isError = errorMessage != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_setup_input")
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 6.dp, start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        if (username.isBlank()) {
                            errorMessage = "Пожалуйста, введите имя пользователя"
                        } else {
                            isLoading = true
                            viewModel.saveProfileUsername(username) { error ->
                                isLoading = false
                                if (error != null) {
                                    errorMessage = error
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("username_setup_submit")
                ) {
                    Text("Продолжить")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { viewModel.logOut() }
                ) {
                    Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ============================================
// AUTHORIZATION SCREEN
// ============================================
@Composable
fun AuthLandingScreen(
    errorMessage: String?,
    viewModel: TelegramViewModel
) {
    val context = LocalContext.current
    var isRegisterState by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    // Playground Sandbox bypass for Emulators
    var showSandboxOverlay by remember { mutableStateOf(false) }
    var sandboxEmail by remember { mutableStateOf("demo_user@telegram.example") }
    var sandboxName by remember { mutableStateOf("Telegram Demo") }

    // Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("791368904590-6c94q6mfmmghce03dqcr6iq0j2hdb151.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val token = account.idToken
                if (!token.isNullOrEmpty()) {
                    viewModel.loginWithGoogleCredential(token, account.displayName)
                } else {
                    Toast.makeText(context, "Google Token was empty.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Google Login ApiException", e)
                Toast.makeText(context, "Вход через Google не удался: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Вход отменен.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Paper plane visual icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF50A2E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Telegram Logo",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(end = 4.dp, bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isRegisterState) "Создание аккаунта" else "Вход в Telegram",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isRegisterState) "Присоединяйтесь к быстрому мессенджеру" else "Войдите, чтобы начать безопасную переписку",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isRegisterState) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Имя") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Эл. почта") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isRegisterState) {
                        viewModel.registerWithEmail(email, password, displayName)
                    } else {
                        viewModel.loginWithEmail(email, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_submit_button")
            ) {
                Text(text = if (isRegisterState) "Зарегистрироваться" else "Войти")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Google sign in button
            OutlinedButton(
                onClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_auth_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "Google Icon",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Войти через Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { isRegisterState = !isRegisterState }
            ) {
                Text(
                    text = if (isRegisterState) "Уже есть аккаунт? Войти" else "Нет аккаунта? Создать аккаунт",
                    color = MaterialTheme.colorScheme.primary
                )
            }


        }
    }


}

// ============================================
// PRIMARY CHATS LIST DASHBOARD
// ============================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainDashboardScreen(
    currentUser: TelegramUser,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: TelegramViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilterTab by remember { mutableStateOf(0) } // 0: All, 1: Private, 2: Groups, 3: Channels
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val chats by viewModel.chats.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val searchedUsers by viewModel.searchedUsers.collectAsState()
    val searchedChats by viewModel.searchedChats.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInviteKeyDialog by remember { mutableStateOf(false) }
    var chatToJoin by remember { mutableStateOf<TelegramChat?>(null) }
    var selectedUserProfileUser by remember { mutableStateOf<TelegramUser?>(null) }
    var chatToDeleteOrLeave by remember { mutableStateOf<TelegramChat?>(null) }

    val filteredChats = remember(chats, activeFilterTab, searchQuery, currentUser.archivedChats) {
        val archivedIds = currentUser.archivedChats.keys
        val matchesQuery = chats.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
        when (activeFilterTab) {
            1 -> matchesQuery.filter { it.type == "DIRECT" && !archivedIds.contains(it.id) }
            2 -> matchesQuery.filter { it.type == "GROUP" && !archivedIds.contains(it.id) }
            3 -> matchesQuery.filter { it.type == "CHANNEL" && !archivedIds.contains(it.id) }
            4 -> matchesQuery.filter { archivedIds.contains(it.id) }
            else -> matchesQuery.filter { !archivedIds.contains(it.id) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(290.dp)
            ) {
                // Drawer Profile Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF50A2E9),
                                    Color(0xFF2C86CE)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = currentUser.photoUrl.ifEmpty {
                                        "https://ui-avatars.com/api/?name=${currentUser.displayName.replace(" ", "+")}&background=fff&color=50a2e9"
                                    }
                                ),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentUser.displayName,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "@${currentUser.username}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Menu items
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    label = { Text("Избранное") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.openSavedMessages()
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                    label = { Text("Создать группу") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showCreateDialog = true
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                    label = { Text("Создать канал") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showCreateDialog = true
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    label = { Text("Войти по ключу") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showInviteKeyDialog = true
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Настройки профиля") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showSettingsDialog = true
                    }
                )

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null
                        )
                    },
                    label = { Text(if (isDarkTheme) "Дневной режим" else "Ночной режим") },
                    selected = false,
                    onClick = {
                        onThemeToggle()
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    label = { Text("Выйти", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.logOut()
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    ) {
        val scaffoldWindowInsets = WindowInsets.safeDrawing
        Scaffold(
            contentWindowInsets = scaffoldWindowInsets,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    // Header Row
                    TopAppBar(
                        title = {
                            Text(
                                "Telegram",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 21.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, tint = Color.White, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                Toast.makeText(viewModel.getApplication(), "Сетевое соединение активно", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.CloudDone, tint = Color.White, contentDescription = "Cloud Done")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    // Unified Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchUsers(it)
                        },
                        placeholder = {
                            Text(
                                "Поиск контактов и каналов...",
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, tint = Color.White.copy(alpha = 0.8f), contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.searchUsers("")
                                }) {
                                    Icon(Icons.Filled.Close, tint = Color.White, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    // Material Tabs
                    val tabTitles = listOf("Все", "Личные", "Группы", "Каналы", "Архив")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = activeFilterTab == index
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { activeFilterTab = index }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(2.dp)
                                            .width(20.dp)
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Create Group/Channel")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                if (searchQuery.isNotEmpty() && (searchedUsers.isNotEmpty() || searchedChats.isNotEmpty())) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Text(
                            text = "Результаты поиска",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (searchedUsers.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Пользователи",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(searchedUsers) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.createChatRoom(
                                                    name = user.displayName,
                                                    type = "DIRECT",
                                                    partnerUid = user.uid
                                                )
                                                searchQuery = ""
                                            }
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.displayName.take(2).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "@${user.username}",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedUserProfileUser = user
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = "Профиль",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                }
                            }

                            if (searchedChats.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Группы и каналы",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(searchedChats) { chat ->
                                    val isMember = chat.members.containsKey(currentUser.uid)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isMember) {
                                                    viewModel.selectChat(chat)
                                                    searchQuery = ""
                                                } else {
                                                    chatToJoin = chat
                                                }
                                            }
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (chat.type == "GROUP") Icons.Filled.Group else Icons.Filled.Campaign,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                contentDescription = null
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chat.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "@${chat.username}",
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                        if (isMember) {
                                            Text(
                                                text = "Вступил",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "Войти",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else if (filteredChats.isEmpty()) {
                    // Empty Chats placeholder
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Чаты отсутствуют",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Используйте поиск вверху или нажмите на синий карандаш запустить общение",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // Normal Chat List Render
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredChats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { viewModel.selectChat(chat) },
                                        onLongClick = { chatToDeleteOrLeave = chat }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Chat Room Initials Beautiful Avatar (Professional Polish Theme)
                                val initials = remember(chat.name) {
                                    val parts = chat.name.split(" ").filter { it.isNotEmpty() }
                                    if (parts.size >= 2) {
                                        (parts[0].take(1) + parts[1].take(1)).uppercase()
                                    } else if (chat.name.isNotEmpty()) {
                                        chat.name.take(2).uppercase()
                                    } else {
                                        "?"
                                    }
                                }
                                val avatarBgColor = remember(chat.name) {
                                    val colors = listOf(
                                        Color(0xFFE57373), // Red
                                        Color(0xFF40A7E3), // Blue
                                        Color(0xFF81C784), // Green
                                        Color(0xFF9575CD), // Purple
                                        Color(0xFFFFB74D)  // Orange
                                    )
                                    val index = Math.abs(chat.name.hashCode()) % colors.size
                                    colors[index]
                                }

                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(avatarBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Text metadata
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chat.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Formatted Date Time
                                        val timeString = remember(chat.lastMessageTime) {
                                            if (chat.lastMessageTime == 0L) "" else {
                                                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                sdf.format(Date(chat.lastMessageTime))
                                            }
                                        }
                                        Text(
                                            text = timeString,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chat.lastMessage.ifEmpty { "Нет сообщений" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Status Tick Marks / Broadcaster Pill
                                        if (chat.type == "CHANNEL") {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "Канал",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            val count = unreadCounts[chat.id] ?: 0
                                            if (count > 0 && chat.lastMessage.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF4CC459)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = count.toString(),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            } else if (chat.lastMessage.isNotEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Filled.DoneAll,
                                                    contentDescription = "Read Status",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(
                                modifier = Modifier.padding(start = 80.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Connection Status Row
            val statusText = remember(currentUser) {
                val rawName = currentUser.displayName.ifEmpty { currentUser.email.substringBefore("@") }
                val suffix = if (rawName.isNotEmpty()) rawName.lowercase().replace(" ", "-").replace("@", "") else "menejer-ca10d"
                "CONNECTED: $suffix (FIREBASE)"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF1E2C3A) else Color(0xFFF1F5F9))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CC459))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    color = if (isDarkTheme) Color(0xFF8B9BAA) else Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("") }
        var isChannelType by remember { mutableStateOf(false) }
        var isPrivateChat by remember { mutableStateOf(false) }
        var groupUsername by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }
        var isCreating by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isCreating) showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (isChannelType) "Новый Канал" else "Новая Группа",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = roomName,
                        onValueChange = { 
                            roomName = it
                            dialogError = null
                        },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChannelType,
                            onCheckedChange = { isChannelType = it }
                        )
                        Text(
                            "Создать как вещательный канал",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isPrivateChat,
                            onCheckedChange = { isPrivateChat = it }
                        )
                        Text(
                            "Сделать закрытым (приватным)",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isPrivateChat) {
                        OutlinedTextField(
                            value = groupUsername,
                            onValueChange = { 
                                groupUsername = it
                                dialogError = null
                            },
                            label = { Text("Короткое имя (юзернейм)") },
                            placeholder = { Text("my_group") },
                            prefix = { Text("@", color = MaterialTheme.colorScheme.secondary) },
                            singleLine = true,
                            isError = dialogError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Вход в закрытый чат возможен только по автоматически генерируемому ключу приглашения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isCreating) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Отмена")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (roomName.isBlank()) {
                                        dialogError = "Введите название"
                                        return@Button
                                    }
                                    if (!isPrivateChat && groupUsername.isBlank()) {
                                        dialogError = "Введите юзернейм для открытого чата"
                                        return@Button
                                    }
                                    isCreating = true
                                    viewModel.createChatRoom(
                                        name = roomName,
                                        type = if (isChannelType) "CHANNEL" else "GROUP",
                                        username = if (isPrivateChat) "" else groupUsername,
                                        isPrivate = isPrivateChat
                                    ) { error ->
                                        isCreating = false
                                        if (error != null) {
                                            dialogError = error
                                        } else {
                                            showCreateDialog = false
                                            Toast.makeText(viewModel.getApplication(), "Чат успешно создан!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Создать")
                            }
                        }
                    }
                }
            }
        }
    }

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    if (showSettingsDialog) {
        var editName by remember { mutableStateOf(currentUser.displayName) }
        var editUsername by remember { mutableStateOf(currentUser.username) }
        var isSavingSettings by remember { mutableStateOf(false) }
        var settingsError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { if (!isSavingSettings) showSettingsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Настройки профиля",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = editName,
                        onValueChange = { 
                            editName = it
                            settingsError = null
                        },
                        label = { Text("Ваше имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { 
                            editUsername = it
                            settingsError = null
                        },
                        label = { Text("Имя пользователя") },
                        prefix = { Text("@") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val annotatedString = androidx.compose.ui.text.buildAnnotatedString { 
                                        append("@${currentUser.username}") 
                                    }
                                    clipboardManager.setText(annotatedString)
                                    Toast.makeText(context, "Имя пользователя скопировано!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать юзернейм"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (settingsError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = settingsError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isSavingSettings) {
                        CircularProgressIndicator()
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showSettingsDialog = false }) {
                                Text("Закрыть")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (editName.isBlank()) {
                                        settingsError = "Имя не может быть пустым"
                                        return@Button
                                    }
                                    if (editUsername.isBlank()) {
                                        settingsError = "Имя пользователя не может быть пустым"
                                        return@Button
                                    }
                                    isSavingSettings = true
                                    viewModel.updateProfileAndUsername(editName, editUsername) { error ->
                                        isSavingSettings = false
                                        if (error != null) {
                                            settingsError = error
                                        } else {
                                            showSettingsDialog = false
                                            Toast.makeText(context, "Профиль успешно обновлен!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Сохранить")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteKeyDialog) {
        var inviteKeyInput by remember { mutableStateOf("") }
        var isJoiningKey by remember { mutableStateOf(false) }
        var keyErrorMessage by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { if (!isJoiningKey) showInviteKeyDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Войти в приватный чат",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Введите 8-значный код приглашения закрытой группы или канала.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = inviteKeyInput,
                        onValueChange = { 
                            inviteKeyInput = it
                            keyErrorMessage = null
                        },
                        label = { Text("Код приглашения") },
                        placeholder = { Text("ABC123XY") },
                        singleLine = true,
                        isError = keyErrorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (keyErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = keyErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isJoiningKey) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showInviteKeyDialog = false }) {
                                Text("Отмена")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (inviteKeyInput.isBlank()) {
                                        keyErrorMessage = "Пожалуйста, введите код"
                                        return@Button
                                    }
                                    isJoiningKey = true
                                    viewModel.joinPrivateChatByInviteKey(inviteKeyInput) { error ->
                                        isJoiningKey = false
                                        if (error != null) {
                                            keyErrorMessage = error
                                        } else {
                                            showInviteKeyDialog = false
                                            Toast.makeText(viewModel.getApplication(), "Вы успешно вступили в чат!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Войти")
                            }
                        }
                    }
                }
            }
        }
    }

    if (chatToJoin != null) {
        val chat = chatToJoin!!
        Dialog(onDismissRequest = { chatToJoin = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (chat.type == "GROUP") Icons.Filled.Group else Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Присоединиться?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Вы хотите вступить в ${chat.name} (@${chat.username})?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { chatToJoin = null }) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                viewModel.joinPublicChat(chat)
                                chatToJoin = null
                                searchQuery = ""
                            }
                        ) {
                            Text("Присоединиться")
                        }
                    }
                }
            }
        }
    }

    if (selectedUserProfileUser != null) {
        val user = selectedUserProfileUser!!
        Dialog(onDismissRequest = { selectedUserProfileUser = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(2).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (user.username.isNotEmpty()) "@${user.username}" else "@username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (user.status == "online") Color(0xFF4CAF50) else Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (user.status == "online") "в сети" else "офлайн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (user.username.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.buildAnnotatedString { append("@${user.username}") }
                                        )
                                        Toast.makeText(context, "Юзернейм скопирован!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Имя пользователя",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (user.email.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.buildAnnotatedString { append(user.email) }
                                        )
                                        Toast.makeText(context, "E-mail скопирован!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Электронная почта",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { selectedUserProfileUser = null }
                        ) {
                            Text("Закрыть")
                        }

                        if (user.uid != currentUser.uid) {
                            Button(
                                onClick = {
                                    viewModel.createChatRoom(
                                        name = user.displayName,
                                        type = "DIRECT",
                                        partnerUid = user.uid
                                    )
                                    selectedUserProfileUser = null
                                    searchQuery = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Написать")
                            }
                        }
                    }
                }
            }
        }
    }

    if (chatToDeleteOrLeave != null) {
        val chat = chatToDeleteOrLeave!!
        val isDirect = chat.type == "DIRECT"
        AlertDialog(
            onDismissRequest = { chatToDeleteOrLeave = null },
            title = {
                Text(
                    text = when (chat.type) {
                        "DIRECT" -> "Удалить чат?"
                        "GROUP" -> "Выйти из группы?"
                        else -> "Выйти из канала?"
                    }
                )
            },
            text = {
                Text(
                    text = when (chat.type) {
                        "DIRECT" -> "Вы действительно хотите удалить чат с пользователем ${chat.name}? Это действие удалит всю историю сообщений для всех участников."
                        "GROUP" -> "Вы действительно хотите выйти из группы \"${chat.name}\"?"
                        else -> "Вы действительно хотите выйти из канала \"${chat.name}\"?"
                    }
                )
            },
            confirmButton = {
                Column {
                    val isArchived = currentUser.archivedChats.containsKey(chat.id)
                    Button(
                        onClick = {
                            viewModel.archiveChat(chat.id, !isArchived)
                            chatToDeleteOrLeave = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(if (isArchived) "Разархивировать" else "В архив")
                    }

                    Button(
                        onClick = {
                            if (isDirect) {
                                viewModel.deleteChat(chat.id) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Чат удален", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка при удалении чата", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                viewModel.leaveChat(chat.id) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Вы вышли из ${if (chat.type == "GROUP") "группы" else "канала"}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка при выходе", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            chatToDeleteOrLeave = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Да, " + if (isDirect) "удалить" else "выйти", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDeleteOrLeave = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ============================================
// CHAT CONVERSATIONTIMELINE SCREEN
// ============================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatConversationScreen(
    chat: TelegramChat,
    currentUser: TelegramUser,
    onBack: () -> Unit,
    viewModel: TelegramViewModel
) {
    var userMessageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isOwner = remember(chat, currentUser) { chat.creatorId == currentUser.uid }
    val isAdmin = remember(chat, currentUser) { isOwner || (chat.admins[currentUser.uid] == true) }
    val currentPermissions = remember(chat, currentUser) { 
        if (isOwner) "canWrite,canPin,canAddAdmins"
        else chat.adminPermissions[currentUser.uid] ?: "" 
    }
    val canWrite = remember(chat, currentUser, isOwner, isAdmin, currentPermissions) {
        if (chat.type == "GROUP" || chat.type == "DIRECT") true
        else isOwner || (isAdmin && currentPermissions.contains("canWrite"))
    }
    val canPin = remember(chat, currentUser, isOwner, isAdmin, currentPermissions) {
        isOwner || (isAdmin && currentPermissions.contains("canPin"))
    }
    val canAddAdmins = remember(chat, currentUser, isOwner, isAdmin, currentPermissions) {
        isOwner || (isAdmin && currentPermissions.contains("canAddAdmins"))
    }

    var selectedUserProfileUser by remember { mutableStateOf<TelegramUser?>(null) }
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var showChatSettingsDialog by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<TelegramMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<TelegramMessage?>(null) }
    var editingMessage by remember { mutableStateOf<TelegramMessage?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var messageToForward by remember { mutableStateOf<TelegramMessage?>(null) }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var activeWebAppUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessages by remember { mutableStateOf<List<TelegramMessage>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val typingUsers by viewModel.activeChatTypingUsers.collectAsState()

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) {
            messages
        } else {
            messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(userMessageText) {
        if (userMessageText.isNotEmpty()) {
            viewModel.setTypingStatus(true)
            kotlinx.coroutines.delay(3000)
            viewModel.setTypingStatus(false)
        } else {
            viewModel.setTypingStatus(false)
        }
    }

    DisposableEffect(chat.id) {
        onDispose {
            viewModel.setTypingStatus(false)
        }
    }

    // Scroll to bottom when messages list count gains length
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            scrollState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (selectedMessages.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedMessages.size}", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { selectedMessages = emptyList() }) {
                            Icon(Icons.Filled.Close, tint = Color.White, contentDescription = "Отмена")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val text = selectedMessages.sortedBy { it.timestamp }.joinToString("\n") { it.text }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            selectedMessages = emptyList()
                        }) {
                            Icon(Icons.Filled.ContentCopy, tint = Color.White, contentDescription = "Копировать")
                        }
                        IconButton(onClick = {
                            showForwardDialog = true
                        }) {
                            Icon(Icons.Filled.Send, tint = Color.White, contentDescription = "Переслать")
                        }
                        if (selectedMessages.all { it.senderId == currentUser.uid }) {
                            IconButton(onClick = {
                                selectedMessages.forEach { msg ->
                                    viewModel.deleteMessage(msg.id)
                                }
                                selectedMessages = emptyList()
                            }) {
                                Icon(Icons.Filled.Delete, tint = Color.White, contentDescription = "Удалить")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск сообщений...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false 
                            searchQuery = ""
                        }) {
                            Icon(Icons.Filled.Close, tint = Color.White, contentDescription = "Закрыть поиск")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        val partnerUid = remember(chat) { chat.members.keys.firstOrNull { it != currentUser.uid } }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = chat.type == "DIRECT" && partnerUid != null) {
                                    if (partnerUid != null) {
                                        viewModel.fetchUserProfile(partnerUid) { user ->
                                            selectedUserProfileUser = user
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.name.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = chat.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val statusText = if (typingUsers.isNotEmpty()) {
                                    if (typingUsers.size == 1) {
                                        "${typingUsers.first()} печатает..."
                                    } else {
                                        "${typingUsers.joinToString(", ")} печатают..."
                                    }
                                } else {
                                    when {
                                        chat.type == "DIRECT" -> "в сети"
                                        chat.isPrivate -> "приватный (ключ: ${chat.inviteKey})"
                                        chat.type == "CHANNEL" -> "публичный канал (@${chat.username})"
                                        else -> "публичная группа (@${chat.username})"
                                    }
                                }
                                Text(
                                    text = statusText,
                                    color = if (typingUsers.isNotEmpty()) Color(0xFF8CD8A2) else Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, tint = Color.White, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, tint = Color.White, contentDescription = "Поиск")
                        }
                        IconButton(onClick = {
                            Toast.makeText(viewModel.getApplication(), "Секретный чат зашифрован", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Lock, tint = Color.White, contentDescription = "Security Options")
                        }
                        if (chat.type != "DIRECT") {
                            IconButton(onClick = { showChatSettingsDialog = true }) {
                                Icon(Icons.Filled.Settings, tint = Color.White, contentDescription = "Настройки")
                            }
                        }
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, tint = Color.White, contentDescription = "Еще")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в .txt") },
                                onClick = {
                                    showMoreMenu = false
                                    exportChatToTxt(context, chat, messages)
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.SaveAlt, contentDescription = null)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Authentic Chat Box Wallpaper with subtle vertical gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E2A3A).copy(alpha = 0.05f),
                                Color(0xFF17212B).copy(alpha = 0.15f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Pinned Message Horizontal Bar
                val pinnedMessage = remember(messages, chat.pinnedMessageId) { 
                    messages.find { it.id == chat.pinnedMessageId } 
                }
                if (pinnedMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable {
                                // Scroll to the pinned message
                                val idx = messages.indexOfFirst { it.id == pinnedMessage.id }
                                if (idx != -1) {
                                    coroutineScope.launch {
                                        scrollState.animateScrollToItem(idx)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Закрепленное сообщение",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = pinnedMessage.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (canPin) {
                            IconButton(
                                onClick = {
                                    viewModel.pinMessage(chat.id, "") { success ->
                                        if (success) {
                                            Toast.makeText(context, "Закрепление снято", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Unpin",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                }

                // Scrollable messages stream
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMessages) { msg ->
                        val isMyMessage = msg.senderId == currentUser.uid
                        val rowArrangement = if (isMyMessage) Alignment.End else Alignment.Start
                        val isSelected = selectedMessages.contains(msg)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedMessages.isNotEmpty()) {
                                            selectedMessages = if (isSelected) selectedMessages - msg else selectedMessages + msg
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedMessages.isEmpty()) {
                                            selectedMessageForMenu = msg
                                        }
                                    }
                                )
                                .padding(vertical = 2.dp),
                            horizontalAlignment = rowArrangement
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .shadow(2.dp, RoundedCornerShape(12.dp))
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isMyMessage) 12.dp else 2.dp,
                                            bottomEnd = if (isMyMessage) 2.dp else 12.dp
                                        )
                                    )
                                    .background(
                                        if (isMyMessage) {
                                            Color(0xFF2B5F8C) // Telegram dark greenish blue bubble
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    // Reply preview inside message bubble
                                    if (msg.replyToId.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp)
                                                .background(
                                                    if (isMyMessage) Color.White.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(24.dp)
                                                    .background(
                                                        if (isMyMessage) Color.White else MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(1.dp)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = msg.replyToSenderName,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = msg.replyToText,
                                                    color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Display name for general other members in groups
                                    if (!isMyMessage && chat.type != "DIRECT") {
                                        Text(
                                            text = msg.senderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(bottom = 2.dp)
                                                .clickable {
                                                    viewModel.fetchUserProfile(msg.senderId) { user ->
                                                        selectedUserProfileUser = user
                                                    }
                                                }
                                        )
                                    }

                                    MarkdownText(
                                        text = msg.text,
                                        color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp
                                    )
                                    
                                    if (msg.webAppUrl.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { activeWebAppUrl = msg.webAppUrl },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isMyMessage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Gamepad, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(if (msg.webAppName.isNotEmpty()) "Открыть ${msg.webAppName}" else "Открыть приложение")
                                            }
                                        }
                                    }

                                    // Time + Read Tick Row
                                    Row(
                                        modifier = Modifier.align(Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (msg.isEdited) {
                                            Text(
                                                text = "изм. ",
                                                fontSize = 9.sp,
                                                color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 2.dp, top = 2.dp)
                                            )
                                        }

                                        val timeFormatted = remember(msg.timestamp) {
                                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            sdf.format(Date(msg.timestamp))
                                        }
                                        Text(
                                            text = timeFormatted,
                                            fontSize = 9.sp,
                                            color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                                        )

                                        if (isMyMessage) {
                                            Icon(
                                                imageVector = if (msg.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                                contentDescription = if (msg.isRead) "Прочитано" else "Отправлено",
                                                tint = if (msg.isRead) Color(0xFF8CD8A2) else Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Reply Preview
                if (replyingToMessage != null) {
                    val reply = replyingToMessage!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ответ для ${reply.senderName}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = reply.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { replyingToMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Chat Input field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        showAttachmentMenu = true
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attach",
                            tint = if (canWrite) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    IconButton(onClick = {
                        Toast.makeText(viewModel.getApplication(), "Голосовые сообщения доступны по подписке Premium", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice Message",
                            tint = if (canWrite) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    TextField(
                        value = userMessageText,
                        onValueChange = { userMessageText = it },
                        enabled = canWrite,
                        placeholder = { Text(if (canWrite) "Написать сообщение..." else "Писать могут только админы") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .testTag("chat_message_input")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (userMessageText.isNotEmpty() && canWrite) {
                                if (editingMessage != null) {
                                    viewModel.editMessage(editingMessage!!.id, userMessageText)
                                    editingMessage = null
                                } else {
                                    viewModel.sendMessage(
                                        text = userMessageText,
                                        replyToId = replyingToMessage?.id ?: "",
                                        replyToText = replyingToMessage?.text ?: "",
                                        replyToSenderName = replyingToMessage?.senderName ?: ""
                                    )
                                    replyingToMessage = null
                                }
                                userMessageText = ""
                            }
                        },
                        enabled = canWrite && userMessageText.isNotEmpty(),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canWrite && userMessageText.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f))
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canWrite && userMessageText.isNotEmpty()) Color.White else Color.Gray,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 2.dp, bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }

    if (selectedUserProfileUser != null) {
        val user = selectedUserProfileUser!!
        Dialog(onDismissRequest = { selectedUserProfileUser = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(2).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (user.username.isNotEmpty()) "@${user.username}" else "@username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (user.status == "online") Color(0xFF4CAF50) else Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (user.status == "online") "в сети" else "офлайн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (user.username.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.buildAnnotatedString { append("@${user.username}") }
                                        )
                                        Toast.makeText(context, "Юзернейм скопирован!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Имя пользователя",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (user.email.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.buildAnnotatedString { append(user.email) }
                                        )
                                        Toast.makeText(context, "E-mail скопирован!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Электронная почта",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { selectedUserProfileUser = null }
                        ) {
                            Text("Закрыть")
                        }

                        if (user.uid != currentUser.uid) {
                            Button(
                                onClick = {
                                    viewModel.createChatRoom(
                                        name = user.displayName,
                                        type = "DIRECT",
                                        partnerUid = user.uid
                                    )
                                    selectedUserProfileUser = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Написать")
                            }
                        }
                    }
                }
            }
        }
    }

    var memberToEditPermissions by remember { mutableStateOf<TelegramUser?>(null) }
    var chatMembersList by remember { mutableStateOf<List<TelegramUser>>(emptyList()) }
    LaunchedEffect(chat.members, showChatSettingsDialog) {
        if (showChatSettingsDialog) {
            val loaded = mutableListOf<TelegramUser>()
            var count = 0
            val memberUids = chat.members.keys.toList()
            if (memberUids.isEmpty()) {
                chatMembersList = emptyList()
            } else {
                memberUids.forEach { uid ->
                    viewModel.fetchUserProfile(uid) { user ->
                        if (user != null) {
                            loaded.add(user)
                        }
                        count++
                        if (count == memberUids.size) {
                            chatMembersList = loaded.sortedBy { it.displayName }
                        }
                    }
                }
            }
        }
    }

    if (showChatSettingsDialog) {
        Dialog(onDismissRequest = { showChatSettingsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (chat.type == "CHANNEL") "Настройки канала" else "Настройки группы",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = chat.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (chat.type == "CHANNEL") "Канал" else "Группа",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (chat.inviteKey.isNotEmpty()) {
                        Text(
                            text = "Ключ приглашения: ${chat.inviteKey}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (chat.username.isNotEmpty()) {
                        Text(
                            text = "Имя пользователя: @${chat.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Участники (${chatMembersList.size}):",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatMembersList) { member ->
                            val isMemberOwner = member.uid == chat.creatorId
                            val isMemberAdmin = chat.admins[member.uid] == true
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.displayName.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = member.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isMemberOwner) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Владелец", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (isMemberAdmin) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Админ", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = if (member.status == "online") "в сети" else "офлайн",
                                        color = if (member.status == "online") Color(0xFF4CAF50) else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                
                                // Show actions on users if current user is owner or admin with canAddAdmins
                                if (member.uid != chat.creatorId && member.uid != currentUser.uid) {
                                    Row {
                                        // Toggle admin status button (only if owner or has canAddAdmins)
                                        if (isOwner || canAddAdmins) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.setAdminStatus(chat.id, member.uid, !isMemberAdmin) { success ->
                                                        if (success) {
                                                            Toast.makeText(context, "Статус изменен", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isMemberAdmin) Icons.Filled.SupervisorAccount else Icons.Outlined.SupervisorAccount,
                                                    contentDescription = "Админ",
                                                    tint = if (isMemberAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            
                                            // Owner only can edit admin permissions
                                            if (isOwner && isMemberAdmin) {
                                                IconButton(
                                                    onClick = {
                                                        memberToEditPermissions = member
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Права",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Kick user (owner or admins can kick)
                                        IconButton(
                                            onClick = {
                                                viewModel.kickUser(chat.id, member.uid) { success ->
                                                    if (success) {
                                                        Toast.makeText(context, "Пользователь удален", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showChatSettingsDialog = false }) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        }
    }

    if (memberToEditPermissions != null) {
        val targetUser = memberToEditPermissions!!
        val currentPerms = chat.adminPermissions[targetUser.uid] ?: "canWrite,canPin"
        var writeVal by remember(currentPerms) { mutableStateOf(currentPerms.contains("canWrite")) }
        var pinVal by remember(currentPerms) { mutableStateOf(currentPerms.contains("canPin")) }
        var addAdminsVal by remember(currentPerms) { mutableStateOf(currentPerms.contains("canAddAdmins")) }
        
        AlertDialog(
            onDismissRequest = { memberToEditPermissions = null },
            title = {
                Text(text = "Разрешения для @${targetUser.username}")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (chat.type == "CHANNEL") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = writeVal, onCheckedChange = { writeVal = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Публиковать сообщения")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pinVal, onCheckedChange = { pinVal = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Закреплять сообщения")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addAdminsVal, onCheckedChange = { addAdminsVal = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Назначать новых администраторов")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val list = mutableListOf<String>()
                        if (writeVal || chat.type == "GROUP") list.add("canWrite")
                        if (pinVal) list.add("canPin")
                        if (addAdminsVal) list.add("canAddAdmins")
                        val finalPerms = list.joinToString(",")
                        viewModel.updateAdminPermissions(chat.id, targetUser.uid, finalPerms) { success ->
                            if (success) {
                                Toast.makeText(context, "Разрешения обновлены", Toast.LENGTH_SHORT).show()
                            }
                        }
                        memberToEditPermissions = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToEditPermissions = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (selectedMessageForMenu != null) {
        val msg = selectedMessageForMenu!!
        val isPinned = msg.id == chat.pinnedMessageId
        AlertDialog(
            onDismissRequest = { selectedMessageForMenu = null },
            title = { Text("Сообщение от ${msg.senderName}") },
            text = {
                Text(
                    text = msg.text,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Выбрать (Multi-select)
                    Button(
                        onClick = {
                            selectedMessages = listOf(msg)
                            selectedMessageForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать")
                        }
                    }

                    // Копировать
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(msg.text) })
                            Toast.makeText(context, "Сообщение скопировано", Toast.LENGTH_SHORT).show()
                            selectedMessageForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Копировать")
                        }
                    }
                    
                    // Ответить
                    if (chat.type != "CHANNEL") {
                        Button(
                            onClick = {
                                replyingToMessage = msg
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ответить")
                            }
                        }
                    }
                    
                    // Переслать (Forward)
                    Button(
                        onClick = {
                            messageToForward = msg
                            showForwardDialog = true
                            selectedMessageForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Переслать")
                        }
                    }
                    
                    // Pin / Unpin
                    if (canPin) {
                        Button(
                            onClick = {
                                viewModel.pinMessage(chat.id, if (isPinned) "" else msg.id) { success ->
                                    if (success) {
                                        Toast.makeText(context, if (isPinned) "Сообщение откреплено" else "Сообщение закреплено", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isPinned) "Открепить" else "Закрепить")
                            }
                        }
                    }

                    if (msg.senderId == currentUser.uid) {
                        Button(
                            onClick = {
                                userMessageText = msg.text // populate input field
                                replyingToMessage = null // cancel reply if any
                                editingMessage = msg
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Редактировать")
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.deleteMessage(msg.id)
                                Toast.makeText(context, "Сообщение удалено", Toast.LENGTH_SHORT).show()
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Удалить")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedMessageForMenu = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }

    if (showForwardDialog) {
        val messagesToForward = remember(selectedMessages, messageToForward) {
            if (selectedMessages.isNotEmpty()) selectedMessages.sortedBy { it.timestamp }
            else listOfNotNull(messageToForward)
        }
        
        if (messagesToForward.isNotEmpty()) {
            val allChats by viewModel.chats.collectAsState()
            val otherChats = remember(allChats, chat) { allChats.filter { it.id != chat.id } }
            
            AlertDialog(
                onDismissRequest = { 
                    showForwardDialog = false
                    messageToForward = null
                },
                title = { Text(if (messagesToForward.size == 1) "Переслать сообщение" else "Переслать сообщения (${messagesToForward.size})") },
                text = {
                    Column {
                        Text(
                            text = if (messagesToForward.size == 1) "Выберите чат для пересылки сообщения от ${messagesToForward.first().senderName}:" else "Выберите чат для пересылки сообщений:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (otherChats.isEmpty()) {
                            Text(
                                text = "Нет других доступных чатов",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxHeight(0.4f)
                            ) {
                                items(otherChats) { c ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .clickable {
                                                messagesToForward.forEach { msg ->
                                                    viewModel.forwardMessage(
                                                        targetChatId = c.id,
                                                        text = "[Переслано от ${msg.senderName}]: ${msg.text}"
                                                    )
                                                }
                                                Toast.makeText(context, if (messagesToForward.size == 1) "Сообщение переслано в ${c.name}" else "Сообщения пересланы", Toast.LENGTH_SHORT).show()
                                                showForwardDialog = false
                                                messageToForward = null
                                                selectedMessages = emptyList()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = c.name.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = c.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showForwardDialog = false
                        messageToForward = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
        }
    }

    // Attachment Menu Bottom Sheet
    if (showAttachmentMenu) {
        val customWebApps by viewModel.customWebApps.collectAsState()
        var miniAppsTab by remember { mutableIntStateOf(0) }
        var showCreateAppDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showAttachmentMenu = false }
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
                Text("Мини-приложения (Web Apps)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                TabRow(selectedTabIndex = miniAppsTab) {
                    Tab(
                        selected = miniAppsTab == 0,
                        onClick = { miniAppsTab = 0 },
                        text = { Text("Официально") }
                    )
                    Tab(
                        selected = miniAppsTab == 1,
                        onClick = { miniAppsTab = 1 },
                        text = { Text("Моё") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (miniAppsTab == 0) {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("Крестики-нолики") },
                                supportingContent = { Text("Сыграть с собеседником") },
                                leadingContent = { 
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    showAttachmentMenu = false
                                    viewModel.sendMessage(
                                        text = "Присоединяйся к моей игре!",
                                        webAppUrl = "file:///android_asset/game.html",
                                        webAppName = "Крестики-нолики"
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (customWebApps.isEmpty()) {
                            Text(
                                text = "Нет своих мини-приложений. Создайте новое!",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn {
                                items(customWebApps) { app ->
                                    ListItem(
                                        headlineContent = { Text(app.name) },
                                        supportingContent = { Text(app.url) },
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                            }
                                        },
                                        trailingContent = {
                                            if (app.creatorId == currentUser.uid) {
                                                IconButton(onClick = { viewModel.deleteCustomWebApp(app.id) {} }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            showAttachmentMenu = false
                                            viewModel.sendMessage(
                                                text = "Присоединяйся к моему приложению: ${app.name}!",
                                                webAppUrl = app.url,
                                                webAppName = app.name
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        FloatingActionButton(
                            onClick = { showCreateAppDialog = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Filled.Add, "Создать")
                        }
                    }
                }
            }
        }

        if (showCreateAppDialog) {
            var newAppName by remember { mutableStateOf("") }
            var newAppUrl by remember { mutableStateOf("") }
            var isCreating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showCreateAppDialog = false },
                title = { Text("Новое мини-приложение") },
                text = {
                    Column {
                        Text("Укажите название и URL вашего веб-приложения.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newAppName,
                            onValueChange = { newAppName = it },
                            label = { Text("Название (например, Шахматы)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newAppUrl,
                            onValueChange = { newAppUrl = it },
                            label = { Text("URL (https://...)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAppName.isNotBlank() && newAppUrl.isNotBlank()) {
                                isCreating = true
                                viewModel.createCustomWebApp(newAppName, newAppUrl) { success ->
                                    isCreating = false
                                    if (success) {
                                        showCreateAppDialog = false
                                    } else {
                                        Toast.makeText(context, "Ошибка создания", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isCreating
                    ) {
                        Text(if (isCreating) "Создание..." else "Создать")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateAppDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }

    // Fullscreen WebApp Overlay
    if (activeWebAppUrl != null) {
        Dialog(
            onDismissRequest = { activeWebAppUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Toolbar for WebApp
                    TopAppBar(
                        title = { Text("Мини-приложение", fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = { activeWebAppUrl = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close WebApp")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    // WebView Container
                    val contextLocal = LocalContext.current
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = {
                            android.webkit.WebView(contextLocal).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                webChromeClient = android.webkit.WebChromeClient()
                                webViewClient = android.webkit.WebViewClient()
                            }
                        },
                        update = { webView ->
                            // Add params like room_id, user_id
                            val params = "?room_id=${chat.id}&user_id=${currentUser.uid}&name=${currentUser.username.ifEmpty { currentUser.displayName }}"
                            webView.loadUrl(activeWebAppUrl!! + params)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

fun exportChatToTxt(context: android.content.Context, chat: TelegramChat, messages: List<TelegramMessage>) {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    val sb = java.lang.StringBuilder()
    sb.append("=== Экспорт чата: ${chat.name} ===\n")
    sb.append("Тип чата: ${chat.type}\n")
    sb.append("Дата экспорта: ${sdf.format(Date())}\n")
    sb.append("Количество сообщений: ${messages.size}\n")
    sb.append("========================================\n\n")

    messages.forEach { msg ->
        val dateStr = sdf.format(Date(msg.timestamp))
        sb.append("[$dateStr] ${msg.senderName}: ${msg.text}\n")
    }

    val textToExport = sb.toString()
    
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "История чата - ${chat.name}")
        putExtra(android.content.Intent.EXTRA_TEXT, textToExport)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Экспорт чата"))
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    val annotatedString = remember(text, color) {
        androidx.compose.ui.text.buildAnnotatedString {
            var currentIndex = 0
            val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
            val italicRegex = Regex("_(.*?)_")
            val codeRegex = Regex("`(.*?)`")
            
            // In a real app we'd use a proper parser to avoid overlap, 
            // but for simplicity we'll just parse one by one if not overlapping.
            // Better to just do bold parsing for now.
            var rawText = text
            // Wait, building annotated string by just appending is easier.
            // Actually, let's keep it very simple.
            append(text)
            
            boldRegex.findAll(text).forEach { matchResult ->
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
            italicRegex.findAll(text).forEach { matchResult ->
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
            codeRegex.findAll(text).forEach { matchResult ->
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.3f)
                    ),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
        }
    }
    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}
