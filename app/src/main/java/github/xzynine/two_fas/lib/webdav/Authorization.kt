package github.xzynine.two_fas.lib.webdav

import github.xzynine.two_fas.entities.Server
import okhttp3.Credentials
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class Authorization(
    val username: String,
    val password: String,
    val charset: Charset = StandardCharsets.ISO_8859_1
) {

    var name = "Authorization"
        private set

    var data: String = Credentials.basic(username, password, charset)
        private set

    override fun toString(): String {
        return "$username:$password"
    }

    constructor(serverID: Long) : this(
        Server.WebDavConfig("", "", "") // 简化实现，暂时不依赖appDb
    )

    constructor(webDavConfig: Server.WebDavConfig) : this(webDavConfig.username, webDavConfig.password)

}
