package app.playgram.core.telegram

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import app.playgram.BuildConfig
import app.playgram.PlaygramApp
import app.playgram.core.model.MediaItem
import app.playgram.core.model.TelegramChat
import app.playgram.core.model.TelegramMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class AuthStep {
    UNAUTHENTICATED,
    WAIT_PHONE,
    WAIT_CODE,
    WAIT_PASSWORD,
    AUTHENTICATED
}

data class TelegramUserProfile(
    val phoneNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val avatarUrl: String? = null
)

/**
 * Talks to Telegram through TDLib. Until the user logs in with their own account it serves a small
 * set of sample chats ("sample data"); after login every list here comes from TDLib.
 *
 * The app needs the user's own api_id / api_hash (https://my.telegram.org -> API development tools).
 * They are entered inside the app and kept in SharedPreferences.
 */
/**
 * One player's reads. Closed when that player goes away, so its last reads cannot restart the
 * download of a file that is about to be deleted; other players have their own session.
 */
class StreamSession {
    @Volatile var closed = false

    companion object {
        /** For callers that never close, such as the stream server. */
        val OPEN = StreamSession()
    }
}

class TelegramManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playgram.telegram", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _authStep = MutableStateFlow(AuthStep.UNAUTHENTICATED)
    val authStep: StateFlow<AuthStep> = _authStep.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    /** True once a Telegram session exists (also on restart, before TDLib reports Ready). */
    private val _signedIn = MutableStateFlow(prefs.getBoolean("telegram.loggedIn", false))
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** The channel whose videos Home shows on every launch; 0 = none chosen yet. */
    private val _defaultChatId = MutableStateFlow(prefs.getLong("default.chat", 0L))
    val defaultChatId: StateFlow<Long> = _defaultChatId.asStateFlow()

    fun setDefaultChat(chatId: Long) {
        _defaultChatId.value = chatId
        prefs.edit().putLong("default.chat", chatId).apply()
    }

    private val _userProfile = MutableStateFlow<TelegramUserProfile?>(null)
    val userProfile: StateFlow<TelegramUserProfile?> = _userProfile.asStateFlow()

    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _credentialsSet = MutableStateFlow(false)
    val credentialsSet: StateFlow<Boolean> = _credentialsSet.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    private val _fileUpdates = MutableSharedFlow<TdApi.File>(extraBufferCapacity = 256)
    /** Every TDLib UpdateFile, used by the download manager and the streaming data source. */
    val fileUpdates: SharedFlow<TdApi.File> = _fileUpdates.asSharedFlow()

    var pendingPhoneNumber: String = ""
        private set

    private var tdClient: Client? = null
    @Volatile private var loggedIn = false

    private val tdChats = ConcurrentHashMap<Long, TdApi.Chat>()
    private val chatOrder = ConcurrentHashMap<Long, Long>()
    private val messageStores = ConcurrentHashMap<Long, MutableStateFlow<List<TelegramMessage>>>()
    private val rawMessages = ConcurrentHashMap<Long, MutableList<TdApi.Message>>()

    companion object {
        private const val TAG = "PlaygramTG"

        @Volatile
        private var INSTANCE: TelegramManager? = null

        fun getInstance(context: Context): TelegramManager {
            return INSTANCE ?: synchronized(this) {
                val inst = TelegramManager(context.applicationContext)
                INSTANCE = inst
                inst
            }
        }
    }

    // Keys baked in at build time (local.properties: TG_API_ID / TG_API_HASH) win; the in-app form is only a fallback.
    private val apiId: Int get() = if (BuildConfig.TG_API_ID > 0) BuildConfig.TG_API_ID else prefs.getInt("api.id", 0)
    private val apiHash: String get() = if (BuildConfig.TG_API_ID > 0) BuildConfig.TG_API_HASH else prefs.getString("api.hash", "") ?: ""

    init {
        _credentialsSet.value = apiId > 0 && apiHash.isNotBlank()
        if (!prefs.getBoolean("telegram.loggedIn", false)) {
            // Earlier builds stored a made-up "logged in" profile; that was never a real session.
            prefs.edit().remove("user.phone").remove("user.name").remove("user.username").apply()
        }
        publishChats()
        // Not signed in yet: start the engine right away so the login screen can ask for the phone number.
        if (_credentialsSet.value && !prefs.getBoolean("telegram.loggedIn", false)) startLogin()
        // A saved session resumes by itself once TDLib starts with the same credentials.
        if (_credentialsSet.value && prefs.getBoolean("telegram.loggedIn", false)) initTdlibEngine()
    }

    // ------------------------------------------------------------------ credentials

    /** Returns an error message, or null when saved. */
    fun saveCredentials(id: String, hash: String): String? {
        val parsed = id.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) return "api_id must be a number"
        if (!Regex("^[0-9a-fA-F]{32}$").matches(hash.trim())) return "api_hash must be 32 hex characters"
        prefs.edit().putInt("api.id", parsed).putString("api.hash", hash.trim()).apply()
        _credentialsSet.value = true
        _authError.value = null
        // A client created with other credentials has to be restarted.
        tdClient?.send(TdApi.Close()) {}
        tdClient = null
        initTdlibEngine()
        return null
    }

    fun clearCredentials() {
        prefs.edit().remove("api.id").remove("api.hash").apply()
        _credentialsSet.value = false
    }

    // ------------------------------------------------------------------ TDLib engine

    private fun initTdlibEngine() {
        if (tdClient != null || !_credentialsSet.value) return
        try {
            // TDLib's default logs every request and reply in full; while a video streams that is
            // hundreds of lines a second, which slows TDLib itself down. Errors only.
            try { Client.execute(TdApi.SetLogVerbosityLevel(1)) } catch (t: Throwable) { Log.w(TAG, "Couldn't lower TDLib logging", t) }
            tdClient = Client.create({ update -> handleTdUpdate(update) }, { err ->
                Log.e(TAG, "TDLib update error", err)
            }, { defaultErr ->
                Log.e(TAG, "TDLib default error", defaultErr)
            })
            _engineError.value = null
            Log.i(TAG, "TDLib client created")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize TDLib native library", t)
            _engineError.value = "Telegram engine failed to start: ${t.message}"
        }
    }

    private fun handleTdUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
            is TdApi.UpdateNewChat -> {
                tdChats[update.chat.id] = update.chat
                chatOrder[update.chat.id] = mainOrder(update.chat.positions)
                publishChats()
            }
            is TdApi.UpdateChatPhoto -> tdChats[update.chatId]?.let { it.photo = update.photo; publishChats() }
            is TdApi.UpdateChatTitle -> tdChats[update.chatId]?.let { it.title = update.title; publishChats() }
            is TdApi.UpdateChatLastMessage -> tdChats[update.chatId]?.let {
                it.lastMessage = update.lastMessage
                it.positions = update.positions
                chatOrder[update.chatId] = mainOrder(update.positions)
                publishChats()
            }
            is TdApi.UpdateChatPosition -> tdChats[update.chatId]?.let { chat ->
                if (update.position.list is TdApi.ChatListMain) {
                    chatOrder[update.chatId] = update.position.order
                    publishChats()
                }
            }
            is TdApi.UpdateChatReadInbox -> tdChats[update.chatId]?.let {
                it.unreadCount = update.unreadCount
                it.lastReadInboxMessageId = update.lastReadInboxMessageId
                publishChats()
            }
            is TdApi.UpdateChatNotificationSettings -> tdChats[update.chatId]?.let {
                it.notificationSettings = update.notificationSettings
                publishChats()
            }
            is TdApi.UpdateNewMessage -> onNewMessage(update.message)
            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update.file)
            else -> Unit
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val dbDir = File(context.filesDir, "tdlib").apply { mkdirs() }
                val params = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = dbDir.absolutePath
                    filesDirectory = File(context.filesDir, "tdlib_files").apply { mkdirs() }.absolutePath
                    useMessageDatabase = true
                    useChatInfoDatabase = true
                    useFileDatabase = true
                    useSecretChats = false
                    apiId = this@TelegramManager.apiId
                    apiHash = this@TelegramManager.apiHash
                    systemLanguageCode = "en"
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    systemVersion = "Android ${Build.VERSION.RELEASE}"
                    applicationVersion = "1.0.0"
                    useTestDc = false
                }
                tdClient?.send(params) { res ->
                    if (res is TdApi.Error) {
                        Log.e(TAG, "SetTdlibParameters failed: ${res.message}")
                        _authError.value = res.message
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authStep.value = AuthStep.WAIT_PHONE
                _isLoading.value = false
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authStep.value = AuthStep.WAIT_CODE
                _isLoading.value = false
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authStep.value = AuthStep.WAIT_PASSWORD
                _isLoading.value = false
            }
            is TdApi.AuthorizationStateWaitRegistration -> {
                _authError.value = "This phone number has no Telegram account yet. Create it in the Telegram app first."
                _isLoading.value = false
            }
            is TdApi.AuthorizationStateReady -> {
                loggedIn = true
                _authStep.value = AuthStep.AUTHENTICATED
                _isLoading.value = false
                _authError.value = null
                prefs.edit().putBoolean("telegram.loggedIn", true).apply()
                _signedIn.value = true
                syncProfileAndChats()
                cleanUpExpiredStreams()
                enforceCacheLimit()
            }
            is TdApi.AuthorizationStateLoggingOut -> _isLoading.value = true
            is TdApi.AuthorizationStateClosed -> {
                tdClient = null
                loggedIn = false
                tdChats.clear(); chatOrder.clear(); messageStores.clear(); rawMessages.clear(); resolvedFiles.clear()
                if (_credentialsSet.value && !prefs.getBoolean("telegram.loggedOut", false)) {
                    // Come back as a fresh client so the next login can start straight away.
                    initTdlibEngine()
                }
            }
            else -> Unit
        }
    }

    private fun syncProfileAndChats() {
        tdClient?.send(TdApi.GetMe()) { me ->
            if (me is TdApi.User) {
                val profile = TelegramUserProfile(
                    phoneNumber = me.phoneNumber?.let { if (it.startsWith("+")) it else "+$it" } ?: pendingPhoneNumber,
                    firstName = me.firstName ?: "",
                    lastName = me.lastName ?: "",
                    username = me.usernames?.activeUsernames?.firstOrNull() ?: "",
                    avatarUrl = me.profilePhoto?.small?.let { "tg://file/${it.id}" }
                )
                _userProfile.value = profile
                prefs.edit()
                    .putString("user.phone", profile.phoneNumber)
                    .putString("user.name", "${profile.firstName} ${profile.lastName}".trim())
                    .apply()
            }
        }
        // Loads the chat list; each chat arrives as an UpdateNewChat.
        tdClient?.send(TdApi.LoadChats(TdApi.ChatListMain(), 200)) { }
    }

    // ------------------------------------------------------------------ chat list

    private fun mainOrder(positions: Array<TdApi.ChatPosition>?): Long =
        positions?.firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L

    private fun publishChats() {
        if (!loggedIn) {
            _chats.value = emptyList()
            return
        }
        _chats.value = tdChats.values
            .filter { (chatOrder[it.id] ?: 0L) != 0L }
            .sortedByDescending { chatOrder[it.id] ?: 0L }
            .map { toModel(it) }
    }

    private fun toModel(chat: TdApi.Chat): TelegramChat {
        val type = chat.type
        val isChannel = type is TdApi.ChatTypeSupergroup && type.isChannel
        val isGroup = type is TdApi.ChatTypeBasicGroup || (type is TdApi.ChatTypeSupergroup && !type.isChannel)
        val last = chat.lastMessage
        val pinned = chat.positions?.firstOrNull { it.list is TdApi.ChatListMain }?.isPinned ?: false
        return TelegramChat(
            id = chat.id,
            title = chat.title,
            username = null,
            avatarUrl = chat.photo?.small?.let { "tg://file/${it.id}" },
            unreadCount = chat.unreadCount,
            isChannel = isChannel,
            isGroup = isGroup,
            lastMessagePreview = last?.let { describe(it.content) } ?: "",
            lastMessageTime = last?.let { formatTime(it.date) } ?: "",
            mediaCount = if (last != null && mediaOf(chat.id, last) != null) 1 else 0,
            isMuted = chat.notificationSettings?.muteFor?.let { it > 0 } ?: false,
            isPinned = pinned
        )
    }

    private fun describe(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessageVideo -> "Video · " + (content.caption.text.ifBlank { content.video.fileName })
        is TdApi.MessageDocument -> "File · " + (content.caption.text.ifBlank { content.document.fileName })
        is TdApi.MessageAudio -> "Audio · " + (content.caption.text.ifBlank { content.audio.fileName })
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessagePhoto -> "Photo" + content.caption.text.let { if (it.isBlank()) "" else " · $it" }
        is TdApi.MessageVoiceNote -> "Voice message"
        is TdApi.MessageSticker -> "Sticker"
        else -> "Message"
    }

    private fun formatTime(epochSeconds: Int): String {
        val then = Calendar.getInstance().apply { timeInMillis = epochSeconds * 1000L }
        val now = Calendar.getInstance()
        return when {
            then.get(Calendar.YEAR) == now.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(then.time)
            now.timeInMillis - then.timeInMillis < 2 * 24 * 3600 * 1000L &&
                    then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
            then.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("MMM d", Locale.getDefault()).format(then.time)
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(then.time)
        }
    }

    // ------------------------------------------------------------------ messages

    private fun storeFor(chatId: Long) = messageStores.getOrPut(chatId) { MutableStateFlow(emptyList()) }

    /** Live messages of a chat, oldest first. Sample chats keep serving sample messages. */
    fun messages(chatId: Long): StateFlow<List<TelegramMessage>> {
        val store = storeFor(chatId)
        if (loggedIn && store.value.isEmpty()) {
            openChat(chatId)
        }
        return store.asStateFlow()
    }

    fun openChat(chatId: Long) {
        if (!loggedIn) return
        tdClient?.send(TdApi.OpenChat(chatId)) { }
        loadHistory(chatId, 0L, 60)
    }

    fun closeChat(chatId: Long) {
        if (loggedIn) tdClient?.send(TdApi.CloseChat(chatId)) { }
    }

    /** Older messages than the ones already shown. */
    fun loadOlder(chatId: Long) {
        val oldest = rawMessages[chatId]?.minByOrNull { it.id }?.id ?: 0L
        loadHistory(chatId, oldest, 40)
    }

    private fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int) {
        tdClient?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) { res ->
            if (res is TdApi.Messages) {
                val list = rawMessages.getOrPut(chatId) { mutableListOf() }
                synchronized(list) {
                    res.messages.forEach { m -> if (list.none { it.id == m.id }) list.add(m) }
                }
                republishMessages(chatId)
                // The first page can be shorter than asked while TDLib is still fetching; ask once more.
                if (fromMessageId == 0L && res.messages.size < limit / 2 && res.messages.isNotEmpty()) {
                    val oldest = res.messages.minOf { it.id }
                    tdClient?.send(TdApi.GetChatHistory(chatId, oldest, 0, limit, false)) { more ->
                        if (more is TdApi.Messages) {
                            synchronized(list) { more.messages.forEach { m -> if (list.none { it.id == m.id }) list.add(m) } }
                            republishMessages(chatId)
                        }
                    }
                }
            }
        }
    }

    private fun onNewMessage(message: TdApi.Message) {
        val list = rawMessages[message.chatId] ?: return
        synchronized(list) { if (list.none { it.id == message.id }) list.add(message) }
        republishMessages(message.chatId)
    }

    private fun republishMessages(chatId: Long) {
        val list = rawMessages[chatId] ?: return
        val snapshot = synchronized(list) { list.sortedBy { it.id } }
        storeFor(chatId).value = snapshot.map { toMessage(chatId, it) }
    }

    private fun toMessage(chatId: Long, m: TdApi.Message) = TelegramMessage(
        id = m.id,
        chatId = chatId,
        text = textOf(m.content),
        dateMs = m.date * 1000L,
        media = mediaOf(chatId, m),
        photoFileId = (m.content as? TdApi.MessagePhoto)?.photo?.sizes?.let { sz ->
            (sz.firstOrNull { it.width >= 600 } ?: sz.lastOrNull())?.photo?.id
        }
    )

    /** Searches the whole history of one chat on Telegram's side (captions and file names); newest first. */
    fun searchChat(chatId: Long, query: String, onResult: (List<TelegramMessage>) -> Unit) {
        if (!loggedIn) { onResult(emptyList()); return }
        val c = tdClient
        if (c == null) { onResult(emptyList()); return }
        c.send(TdApi.SearchChatMessages(chatId, null, query, null, 0, 0, 100, null)) { res ->
            val found = (res as? TdApi.FoundChatMessages)?.messages.orEmpty()
            onResult(found.map { toMessage(chatId, it) })
        }
    }

    /**
     * Finds videos of one chat by name across its whole history, not only the part loaded on screen.
     * Runs Telegram's search for captions, video files and document files (file names), also with
     * spaces written as underscores, and follows the pages so old titles show up. Newest first.
     */
    fun searchVideos(chatId: Long, query: String, onResult: (List<TelegramMessage>) -> Unit) {
        val c = tdClient
        if (!loggedIn || c == null || query.isBlank()) { onResult(emptyList()); return }
        val found = ConcurrentHashMap<Long, TdApi.Message>()
        val variants = listOf(query, query.replace(' ', '_')).distinct()
        val filters = listOf<() -> TdApi.SearchMessagesFilter?>(
            { null }, { TdApi.SearchMessagesFilterVideo() }, { TdApi.SearchMessagesFilterDocument() }
        )
        val pending = java.util.concurrent.atomic.AtomicInteger(variants.size * filters.size)
        fun oneDone() { if (pending.decrementAndGet() == 0) onResult(found.values.sortedByDescending { it.id }.map { toMessage(chatId, it) }.filter { it.media != null }) }
        fun page(q: String, filter: () -> TdApi.SearchMessagesFilter?, from: Long, pagesLeft: Int) {
            c.send(TdApi.SearchChatMessages(chatId, null, q, null, from, 0, 100, filter())) { res ->
                val r = res as? TdApi.FoundChatMessages
                r?.messages?.forEach { found[it.id] = it }
                if (r != null && r.messages.isNotEmpty() && r.nextFromMessageId != 0L && pagesLeft > 1) page(q, filter, r.nextFromMessageId, pagesLeft - 1)
                else oneDone()
            }
        }
        variants.forEach { q -> filters.forEach { f -> page(q, f, 0L, 5) } }
    }

    private fun isVideoMessage(m: TdApi.Message): Boolean = when (val c = m.content) {
        is TdApi.MessageVideo -> true
        is TdApi.MessageDocument -> mediaOf(m.chatId, m) != null
        else -> false
    }

    /** The next video posted after [msgId] in the same chat (the next episode), or null when there is none. */
    fun nextVideoAfter(chatId: Long, msgId: Long, onResult: (MediaItem?) -> Unit) {
        val c = tdClient
        if (!loggedIn || c == null) { onResult(null); return }
        // A negative offset reads towards newer messages; the page comes back newest first.
        c.send(TdApi.GetChatHistory(chatId, msgId, -49, 50, false)) { res ->
            val next = (res as? TdApi.Messages)?.messages.orEmpty()
                .filter { it.id > msgId && isVideoMessage(it) }
                .minByOrNull { it.id }
            onResult(next?.let { mediaOf(chatId, it) })
        }
    }

    /** A subtitle file posted in a chat. */
    data class SubtitleFile(val name: String, val fileId: Int)

    /** Subtitle documents (.srt/.vtt/.ass) posted around a video, and ones named like it; name matches first. */
    fun findSubtitles(chatId: Long, msgId: Long, videoTitle: String, onResult: (List<SubtitleFile>) -> Unit) {
        val c = tdClient
        if (!loggedIn || c == null) { onResult(emptyList()); return }
        val ext = Regex(".*\\.(srt|vtt|ass|ssa)$", RegexOption.IGNORE_CASE)
        val base = videoTitle.substringBeforeLast('.').lowercase()
        val found = ConcurrentHashMap<Long, TdApi.Message>()
        val pending = java.util.concurrent.atomic.AtomicInteger(2)
        fun done() {
            if (pending.decrementAndGet() != 0) return
            val subs = found.values.mapNotNull { m ->
                val d = (m.content as? TdApi.MessageDocument)?.document ?: return@mapNotNull null
                if (ext.matches(d.fileName.orEmpty())) SubtitleFile(d.fileName, d.document.id) to m.id else null
            }
            val sorted = subs.sortedWith(compareBy({ !it.first.name.lowercase().startsWith(base.take(20)) }, { Math.abs(it.second - msgId) }))
            onResult(sorted.map { it.first }.distinctBy { it.name }.take(12))
        }
        c.send(TdApi.GetChatHistory(chatId, msgId, -15, 30, false)) { res ->
            (res as? TdApi.Messages)?.messages?.forEach { found[it.id] = it }
            done()
        }
        val query = base.replace('_', ' ').replace('.', ' ').trim().take(40)
        if (query.isBlank()) done()
        else c.send(TdApi.SearchChatMessages(chatId, null, query, null, 0, 0, 30, TdApi.SearchMessagesFilterDocument())) { res ->
            (res as? TdApi.FoundChatMessages)?.messages?.forEach { found[it.id] = it }
            done()
        }
    }

    private fun textOf(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessageVideo -> content.caption.text
        is TdApi.MessageDocument -> content.caption.text
        is TdApi.MessageAudio -> content.caption.text
        is TdApi.MessageAnimation -> content.caption.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Photo" }
        else -> describe(content)
    }

    private fun qualityLabel(height: Int): String = when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        height >= 450 -> "480p"
        height > 0 -> "${height}p"
        else -> ""
    }

    private fun mediaOf(chatId: Long, m: TdApi.Message): MediaItem? {
        val chatTitle = tdChats[chatId]?.title
        fun item(
            file: TdApi.File, name: String?, caption: String, duration: Int, w: Int, h: Int,
            mime: String?, kind: String, thumb: TdApi.Thumbnail? = null
        ): MediaItem {
            val size = if (file.size > 0) file.size else file.expectedSize
            val title = name?.takeIf { it.isNotBlank() } ?: caption.lineSequence().firstOrNull().orEmpty().ifBlank { "$kind ${m.id}" }
            return MediaItem(
                id = "tg_${chatId}_${m.id}",
                title = title,
                durationSeconds = duration.toLong(),
                sizeBytes = size,
                streamUrl = "tg://file/${file.id}?chat=$chatId&msg=${m.id}",
                thumbnailUrl = thumb?.file?.let { "tg://file/${it.id}" },
                mimeType = mime ?: "video/mp4",
                quality = qualityLabel(h),
                channelTitle = chatTitle,
                resolution = if (w > 0 && h > 0) "${w}x$h" else "",
                audioTracks = emptyList(),
                subtitleTracks = emptyList()
            )
        }
        return when (val c = m.content) {
            is TdApi.MessageVideo -> item(c.video.video, c.video.fileName, c.caption.text, c.video.duration, c.video.width, c.video.height, c.video.mimeType, "Video", c.video.thumbnail)
            is TdApi.MessageDocument -> {
                val mime = c.document.mimeType ?: ""
                val name = c.document.fileName ?: ""
                val isVideo = mime.startsWith("video/") || Regex(".*\\.(mkv|mp4|avi|mov|webm|m4v|ts)$", RegexOption.IGNORE_CASE).matches(name)
                if (isVideo) item(c.document.document, name, c.caption.text, 0, 0, 0, mime.ifBlank { "video/x-matroska" }, "File", c.document.thumbnail) else null
            }
            is TdApi.MessageAudio -> item(c.audio.audio, c.audio.fileName, c.caption.text, c.audio.duration, 0, 0, c.audio.mimeType ?: "audio/mpeg", "Audio")
            is TdApi.MessageAnimation -> item(c.animation.animation, c.animation.fileName, c.caption.text, c.animation.duration, c.animation.width, c.animation.height, c.animation.mimeType, "GIF", c.animation.thumbnail)
            else -> null
        }
    }

    // ------------------------------------------------------------------ files

    private fun fileOf(content: TdApi.MessageContent): TdApi.File? = when (content) {
        is TdApi.MessageVideo -> content.video.video
        is TdApi.MessageDocument -> content.document.document
        is TdApi.MessageAudio -> content.audio.audio
        is TdApi.MessageAnimation -> content.animation.animation
        else -> null
    }

    private fun fileNameOf(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessageVideo -> content.video.fileName
        is TdApi.MessageDocument -> content.document.fileName
        is TdApi.MessageAudio -> content.audio.fileName
        is TdApi.MessageAnimation -> content.animation.fileName
        else -> null
    }

    /**
     * The TDLib file behind a `tg://file/<id>?chat=&msg=` link. File ids are only valid for one TDLib
     * session and get handed to other files after a restart, so the file is found through its message,
     * or for older links (no message ids) by its file name; the bare id is the last resort.
     */
    fun resolveFile(link: android.net.Uri, onResult: (TdApi.File?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        // A player opens the same link several times (header, index, seek); look it up once.
        resolvedFiles[link.toString()]?.let { id ->
            c.send(TdApi.GetFile(id)) { r ->
                val f = (r as? TdApi.File)?.takeIf { it.size > 0 || it.expectedSize > 0 }
                if (f != null) onResult(f) else { resolvedFiles.remove(link.toString()); resolveFile(link, onResult) }
            }
            return
        }
        if (hasMessageHint(link)) {
            resolveMessage(link) { m ->
                val f = m?.let { fileOf(it.content) }
                if (f != null) resolvedFiles[link.toString()] = f.id
                onResult(f)
            }
        } else {
            val id = link.lastPathSegment?.toIntOrNull() ?: 0
            c.send(TdApi.GetFile(id)) { r -> onResult((r as? TdApi.File)?.takeIf { it.size > 0 || it.expectedSize > 0 }) }
        }
    }

    /** Link -> TDLib file id found this session. File ids are only valid until TDLib restarts. */
    private val resolvedFiles = ConcurrentHashMap<String, Int>()

    private fun hasMessageHint(link: android.net.Uri) =
        (link.getQueryParameter("chat") != null && link.getQueryParameter("msg") != null) || !link.getQueryParameter("name").isNullOrBlank()

    /** The message a link points at: by chat/message ids, or for older links by file name. */
    private fun resolveMessage(link: android.net.Uri, onResult: (TdApi.Message?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        val chat = link.getQueryParameter("chat")?.toLongOrNull()
        val msg = link.getQueryParameter("msg")?.toLongOrNull()
        val name = link.getQueryParameter("name")
        when {
            chat != null && msg != null -> c.send(TdApi.GetMessage(chat, msg)) { m -> onResult(m as? TdApi.Message) }
            !name.isNullOrBlank() -> c.send(TdApi.SearchMessages(TdApi.ChatListMain(), name, "", 20, null, null, 0, 0)) { res ->
                onResult((res as? TdApi.FoundMessages)?.messages?.firstOrNull { fileNameOf(it.content) == name })
            }
            else -> onResult(null)
        }
    }

    /** Older history links carry no message ids; the file name lets [resolveFile] find them again. */
    fun withNameHint(link: String, title: String): String =
        if (link.startsWith("tg://") && !link.contains("msg=") && !link.contains("name=")) {
            link + (if ('?' in link) "&" else "?") + "name=" + android.net.Uri.encode(title)
        } else link

    /** TDLib file id of the thumbnail behind a link, for [app.playgram.feature.chats.TgImage]. */
    fun thumbnailFor(link: String, onResult: (Int?) -> Unit) {
        val uri = android.net.Uri.parse(link)
        if (!link.startsWith("tg://") || !hasMessageHint(uri)) { onResult(null); return }
        resolveMessage(uri) { m ->
            val thumb = when (val c = m?.content) {
                is TdApi.MessageVideo -> c.video.thumbnail
                is TdApi.MessageDocument -> c.document.thumbnail
                is TdApi.MessageAnimation -> c.animation.thumbnail
                else -> null
            }
            onResult(thumb?.file?.id)
        }
    }

    /** Starts (or continues) a TDLib download of the whole file. */
    fun startFileDownload(fileId: Int, priority: Int = 16) {
        tdClient?.send(TdApi.DownloadFile(fileId, priority, 0, 0, false)) { }
    }

    /** Downloads a small file (thumbnail / photo) completely and returns its local path, or null. */
    fun fetchFilePath(fileId: Int, onResult: (String?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        c.send(TdApi.DownloadFile(fileId, 8, 0, 0, true)) { res ->
            val f = res as? TdApi.File
            onResult(f?.local?.path?.takeIf { it.isNotEmpty() && f.local.isDownloadingCompleted })
        }
    }

    fun cancelFileDownload(fileId: Int) {
        tdClient?.send(TdApi.CancelDownloadFile(fileId, false)) { }
    }

    /** Asks TDLib to fetch from [offset] on; used by the streaming data source. */
    fun requestFileRange(fileId: Int, offset: Long, limit: Long = 0L, session: StreamSession = StreamSession.OPEN) {
        if (session.closed) return
        streamingFiles.add(fileId)
        tdClient?.send(TdApi.DownloadFile(fileId, 32, offset, limit, false)) { }
    }

    // ------------------------------------------------------------------ stream cache

    /** Files a stream asked TDLib for; TDLib keeps downloading them to the end unless told to stop. */
    private val streamingFiles = ConcurrentHashMap.newKeySet<Int>()
    private val cachePrefs: SharedPreferences = context.getSharedPreferences("playgram.streamcache", Context.MODE_PRIVATE)
    private val sevenDaysMs = 7L * 24 * 3600 * 1000

    /**
     * Called when the player closes. Stops the background download the stream started (files queued in
     * Downloads keep going) and applies "Keep this file": 0 discard now, 1 delete after 7 days, 2/3 keep.
     */
    fun finishStream(link: String, keepMode: Int, busyFileIds: Set<Int>) {
        val files = streamingFiles.filter { it !in busyFileIds }
        streamingFiles.clear()
        files.forEach { cancelFileDownload(it) }
        if (!link.startsWith("tg://")) return
        when (keepMode) {
            0 -> {
                cachePrefs.edit().remove(link).apply()
                // The released player can still be finishing a read; delete once it has let go.
                scope.launch {
                    kotlinx.coroutines.delay(1_500)
                    resolveFile(android.net.Uri.parse(link)) { f ->
                        if (f != null && f.id !in PlaygramApp.instance.downloadManager.busyTelegramFileIds()) {
                            val c = tdClient ?: return@resolveFile
                            c.send(TdApi.CancelDownloadFile(f.id, false)) { c.send(TdApi.DeleteFile(f.id)) { } }
                        }
                    }
                }
            }
            1 -> cachePrefs.edit().putLong(link, System.currentTimeMillis()).apply()
            else -> cachePrefs.edit().remove(link).apply()
        }
        enforceCacheLimit()
    }

    /** Most the streaming cache may hold; the least recently watched files go first. */
    val cacheLimitBytes = 5L shl 30

    private val mediaFileTypes
        get() = arrayOf<TdApi.FileType>(
            TdApi.FileTypeVideo(), TdApi.FileTypeDocument(), TdApi.FileTypeAudio(),
            TdApi.FileTypeAnimation(), TdApi.FileTypeVideoNote()
        )

    /**
     * Trims TDLib's media files to [cacheLimitBytes], oldest access first. Files touched in the last
     * minute (the video playing, a download running) are left alone.
     */
    fun enforceCacheLimit() {
        val c = tdClient ?: return
        // A running download is a file the user asked to keep; never trim the cache under it.
        if (PlaygramApp.instance.downloadManager.busyTelegramFileIds().isNotEmpty()) return
        c.send(TdApi.OptimizeStorage(cacheLimitBytes, 365 * 24 * 3600, 1_000_000, 60, mediaFileTypes, longArrayOf(), longArrayOf(), false, 0)) { }
    }

    /** Deletes streamed files whose 7 days have passed. Runs once per login. */
    private fun cleanUpExpiredStreams() {
        val now = System.currentTimeMillis()
        cachePrefs.all.forEach { (link, at) ->
            if (at is Long && now - at > sevenDaysMs) {
                resolveFile(android.net.Uri.parse(link)) { f -> if (f != null) tdClient?.send(TdApi.DeleteFile(f.id)) { } }
                cachePrefs.edit().remove(link).apply()
            }
        }
    }

    fun deleteCachedFile(fileId: Int) {
        tdClient?.send(TdApi.DeleteFile(fileId)) { }
    }

    /** Bytes TDLib keeps on disk for downloaded and streamed files. */
    fun cacheSize(onResult: (Long?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        c.send(TdApi.GetStorageStatisticsFast()) { r -> onResult((r as? TdApi.StorageStatisticsFast)?.filesSize) }
    }

    /**
     * Deletes the videos and files TDLib keeps from streaming. Files saved to Downloads are copies and stay;
     * files touched in the last minute (a download in progress) are skipped.
     */
    fun clearMediaCache(onDone: () -> Unit) {
        val c = tdClient
        if (c == null) { onDone(); return }
        c.send(TdApi.OptimizeStorage(0, 0, 0, 60, mediaFileTypes, longArrayOf(), longArrayOf(), false, 0)) {
            cachePrefs.edit().clear().apply()
            onDone()
        }
    }

    /** Bytes already on disk starting at [offset], wherever TDLib is downloading right now. */
    fun downloadedPrefixSize(fileId: Int, offset: Long, onResult: (Long?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        c.send(TdApi.GetFileDownloadedPrefixSize(fileId, offset)) { r -> onResult((r as? TdApi.FileDownloadedPrefixSize)?.size ?: 0L) }
    }

    fun getFile(fileId: Int, onResult: (TdApi.File?) -> Unit) {
        val c = tdClient
        if (c == null) { onResult(null); return }
        c.send(TdApi.GetFile(fileId)) { res -> onResult(res as? TdApi.File) }
    }

    // ------------------------------------------------------------------ login

    fun toggleDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
        prefs.edit().putBoolean("telegram.demo", enabled).putBoolean("telegram.demo.chosen", true).apply()
        messageStores.clear()
        publishChats()
    }

    fun startLogin() {
        _authError.value = null
        prefs.edit().putBoolean("telegram.loggedOut", false).apply()
        initTdlibEngine()
    }

    fun sendPhoneNumber(phone: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 8 || !cleanPhone.startsWith("+")) {
            val err = "Enter your number with the country code, like +91 98765 43210"
            _authError.value = err
            onError(err)
            return
        }
        val client = tdClient
        if (client == null) {
            val err = _engineError.value ?: "Telegram engine is not running. Save your api_id and api_hash first."
            _authError.value = err
            onError(err)
            return
        }
        pendingPhoneNumber = cleanPhone
        _isLoading.value = true
        _authError.value = null
        val setPhone = TdApi.SetAuthenticationPhoneNumber().apply {
            phoneNumber = cleanPhone
            settings = TdApi.PhoneNumberAuthenticationSettings().apply {
                allowSmsRetrieverApi = false
                authenticationTokens = emptyArray()
            }
        }
        client.send(setPhone) { result ->
            if (result is TdApi.Error) {
                _isLoading.value = false
                _authError.value = result.message
                onError(result.message)
            } else {
                // The next screen is chosen by UpdateAuthorizationState.
                onSuccess()
            }
        }
    }

    fun sendAuthCode(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanCode = code.trim()
        if (cleanCode.length < 4) {
            val err = "Enter the code Telegram sent you"
            _authError.value = err
            onError(err)
            return
        }
        val client = tdClient ?: run { onError("Telegram engine is not running"); return }
        _isLoading.value = true
        _authError.value = null
        client.send(TdApi.CheckAuthenticationCode(cleanCode)) { result ->
            if (result is TdApi.Error) {
                _isLoading.value = false
                _authError.value = result.message
                onError(result.message)
            } else {
                onSuccess()
            }
        }
    }

    fun sendPassword(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (password.isEmpty()) {
            val err = "Password cannot be empty"
            _authError.value = err
            onError(err)
            return
        }
        val client = tdClient ?: run { onError("Telegram engine is not running"); return }
        _isLoading.value = true
        _authError.value = null
        client.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                _isLoading.value = false
                _authError.value = result.message
                onError(result.message)
            } else {
                onSuccess()
            }
        }
    }

    /** Back to the phone step (wrong number): TDLib has no "back", so restart the login. */
    fun restartLogin() {
        _authError.value = null
        _isLoading.value = false
        tdClient?.send(TdApi.LogOut()) { }
        _authStep.value = AuthStep.UNAUTHENTICATED
    }

    fun logOut() {
        prefs.edit()
            .remove("user.phone")
            .remove("user.name")
            .remove("telegram.loggedIn")
            .remove("default.chat")
            .apply()
        _defaultChatId.value = 0L
        tdClient?.send(TdApi.LogOut()) { }
        loggedIn = false
        _userProfile.value = null
        _authStep.value = AuthStep.UNAUTHENTICATED
        _signedIn.value = false
        tdChats.clear(); chatOrder.clear(); messageStores.clear(); rawMessages.clear()
        publishChats()
    }

    // ------------------------------------------------------------------ sample data

    private fun sampleChats(): List<TelegramChat> {
        val demoChats = listOf(
            TelegramChat(
                id = -1001234567890L,
                title = "Playgram 4K Cinema Hub",
                username = "playgram_cinema",
                avatarUrl = null,
                unreadCount = 12,
                isChannel = true,
                isGroup = false,
                lastMessagePreview = "Tears of Steel (4K Remaster) [Dual Audio] uploaded",
                lastMessageTime = "14:20",
                mediaCount = 184
            ),
            TelegramChat(
                id = -1001987654321L,
                title = "Anime & Web Series Direct",
                username = "animedrop_hq",
                avatarUrl = null,
                unreadCount = 4,
                isChannel = true,
                isGroup = false,
                lastMessagePreview = "Big Buck Bunny HLS Multi-bitrate Stream",
                lastMessageTime = "Yesterday",
                mediaCount = 92
            ),
            TelegramChat(
                id = -1001122334455L,
                title = "Open Source Documentaries",
                username = "open_documentaries",
                avatarUrl = null,
                unreadCount = 0,
                isChannel = true,
                isGroup = false,
                lastMessagePreview = "Sintel 4K Cinema Audio available",
                lastMessageTime = "Oct 21",
                mediaCount = 45
            ),
            TelegramChat(
                id = -1009988776655L,
                title = "Playgram Test & Feedback",
                username = "playgram_community",
                avatarUrl = null,
                unreadCount = 31,
                isChannel = false,
                isGroup = true,
                lastMessagePreview = "Welcome to Playgram",
                lastMessageTime = "Just now",
                mediaCount = 12
            )
        )
        return demoChats
    }

    fun getChatMessages(chatId: Long): List<TelegramMessage> {
        val sampleMedia = getSampleMediaForChat(chatId)
        return sampleMedia.mapIndexed { index, media ->
            TelegramMessage(
                id = 1000L + index,
                chatId = chatId,
                text = "🔥 ${media.title}\n\nHigh efficiency encode with multi-audio tracks and subtitles.",
                dateMs = System.currentTimeMillis() - (index * 3600000L),
                media = media
            )
        }
    }

    fun getSampleMediaForChat(chatId: Long): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "media_1",
                title = "Big Buck Bunny (1080p sample)",
                durationSeconds = 10,
                sizeBytes = 5238714L,
                streamUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_5MB.mp4",
                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                quality = "1080p",
                channelTitle = "Playgram 4K Cinema Hub",
                resolution = "1920x1080",
                audioTracks = listOf("AAC Stereo (English)", "AC3 5.1 Surround"),
                subtitleTracks = listOf("English", "Spanish")
            ),
            MediaItem(
                id = "media_2",
                title = "Tears of Steel (720p)",
                durationSeconds = 734,
                sizeBytes = 372178639L,
                streamUrl = "https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov",
                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                quality = "720p",
                channelTitle = "Playgram 4K Cinema Hub",
                resolution = "1280x720",
                audioTracks = listOf("DTS-HD (English)", "Stereo Blend"),
                subtitleTracks = listOf("English", "French", "German")
            ),
            MediaItem(
                id = "media_3",
                title = "Sintel (trailer, 720p)",
                durationSeconds = 52,
                sizeBytes = 7608204L,
                streamUrl = "https://download.blender.org/durian/trailer/sintel_trailer-720p.mp4",
                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                quality = "720p",
                channelTitle = "Open Source Documentaries",
                resolution = "1280x720",
                audioTracks = listOf("Surround 5.1"),
                subtitleTracks = listOf("English", "Hindi", "Japanese")
            ),
            MediaItem(
                id = "media_4",
                title = "Elephants Dream (576p)",
                durationSeconds = 653,
                sizeBytes = 47065346L,
                streamUrl = "https://archive.org/download/ElephantsDream/ed_1024_512kb.mp4",
                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                quality = "576p",
                channelTitle = "Playgram 4K Cinema Hub",
                resolution = "1024x576",
                audioTracks = listOf("English Stereo"),
                subtitleTracks = listOf("English")
            )
        )
    }
}
