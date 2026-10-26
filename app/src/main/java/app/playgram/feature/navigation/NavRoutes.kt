package app.playgram.feature.navigation

object NavRoutes {
    const val HOME = "home"
    const val CHAT_DETAIL = "chat/{chatId}"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player?url={url}&title={title}"

    fun chatDetail(chatId: Long): String = "chat/$chatId"
    fun player(url: String, title: String): String = "player?url=${java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")}&title=${java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")}"
}
