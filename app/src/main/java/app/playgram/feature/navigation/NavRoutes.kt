package app.playgram.feature.navigation

object NavRoutes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val CHAT_DETAIL = "chat/{chatId}"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val DEVICES = "devices"
    const val SETTINGS = "settings"
    const val BROWSER = "browser?url={url}"
    const val TOOLS = "tools?link={link}"
    const val PROXY = "proxy"
    const val SYNC = "sync"
    const val AUTOMATION = "automation"
    const val BULK = "bulk?chatId={chatId}"
    const val PRO = "pro"
    const val PLAYER = "player?url={url}&title={title}"

    fun chatDetail(chatId: Long): String = "chat/$chatId"
    fun browser(url: String): String = "browser?url=${java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")}"
    fun tools(link: String = ""): String = if (link.isNotEmpty()) "tools?link=${java.net.URLEncoder.encode(link, "UTF-8").replace("+", "%20")}" else "tools?link="
    fun bulk(chatId: Long): String = "bulk?chatId=$chatId"
    fun player(url: String, title: String): String = "player?url=${java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")}&title=${java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")}"
}
