package github.xzynine.two_fas.lib.webdav

import cn.hutool.core.net.URLDecoder
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class WebDav(
    val path: String,
    val authorization: Authorization
) {
    companion object {

        @Suppress("DateTimeFormatter")
        private val dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

        // 指定返回哪些属性
        @Language("xml")
        private const val DIR = """
            <?xml version="1.0"?>
            <a:propfind xmlns:a="DAV:">
                <a:prop>
                    <a:displayname/>
                    <a:resourcetype/>
                    <a:getcontentlength/>
                    <a:creationdate/>
                    <a:getlastmodified/>
                    %s
                </a:prop>
            </a:propfind>"""

        @Language("xml")
        private const val EXISTS = """
            <?xml version="1.0"?>
            <propfind xmlns="DAV:">
               <prop>
                  <resourcetype />
               </prop>
            </propfind>"""

        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
    }

    private val url: URL = URL(path)
    private val httpUrl: String? by lazy {
        val raw = url.toString()
            .replace("davs://", "https://")
            .replace("dav://", "http://")
        return@lazy kotlin.runCatching {
            raw.toHttpUrl().toString()
        }.getOrNull()
    }
    private val webDavClient by lazy {
        val authInterceptor = Interceptor { chain ->
            var request = chain.request()
            if (request.url.host.equals(host, true)) {
                request = request
                    .newBuilder()
                    .header(authorization.name, authorization.data)
                    .build()
            }
            chain.proceed(request)
        }
        OkHttpClient.Builder().run {
            callTimeout(0, TimeUnit.SECONDS)
            addInterceptor(authInterceptor)
            addNetworkInterceptor(authInterceptor)
            build()
        }
    }
    private val host: String?
        get() = url.host?.let {
            if (it.startsWith("[")) {
                it.substring(1, it.lastIndex)
            } else {
                it
            }
        }

    /**
     * 获取当前url文件信息
     */
    @Throws(WebDavException::class)
    suspend fun getWebDavFile(): WebDavFile? {
        return propFindResponse(depth = 0)?.let {
            parseBody(it).firstOrNull()
        }
    }

    /**
     * 列出当前路径下的文件
     * @return 文件列表
     */
    @Throws(WebDavException::class)
    suspend fun listFiles(): List<WebDavFile> {
        propFindResponse()?.let {
            return parseBody(it).filter {
                it.path != path
            }
        }
        return emptyList()
    }

    /**
     * @param propsList 指定列出文件的哪些属性
     */
    @Throws(WebDavException::class)
    private suspend fun propFindResponse(
        propsList: List<String> = emptyList(),
        depth: Int = 1
    ): String? {
        val requestProps = StringBuilder()
        for (p in propsList) {
            requestProps.append("<a:${p}/>\n")
        }
        val requestPropsStr: String = if (requestProps.toString().isEmpty()) {
            DIR.replace("%s", "")
        } else {
            String.format(DIR, requestProps.toString() + "\n")
        }
        val url = httpUrl ?: return null
        return webDavClient.newCall(
            Request.Builder()
                .url(url)
                .addHeader("Depth", depth.toString())
                .method("PROPFIND", requestPropsStr.toRequestBody("text/plain".toMediaType()))
                .build()
        ).execute().use {
            checkResult(it)
            it.body?.string()
        }
    }

    /**
     * 解析webDav返回的xml
     */
    private fun parseBody(s: String): List<WebDavFile> {
        val list = ArrayList<WebDavFile>()
        val document = kotlin.runCatching {
            Jsoup.parse(s, Parser.xmlParser())
        }.getOrElse {
            Jsoup.parse(s)
        }
        
        val elements = document.select("response")
        val urlStr = httpUrl ?: return list
        
        // 简化的基础URL处理
        val baseUrl = urlStr.substringBeforeLast("/") + "/"
        
        for (element in elements) {
            val href = element.selectFirst("href")?.text() ?: continue
            val hrefDecode = URLDecoder.decodeForPath(href, Charsets.UTF_8)
            val fileName = hrefDecode.removeSuffix("/").substringAfterLast("/")
            
            try {
                val urlName = hrefDecode.ifEmpty {
                    url.file.replace("/", "")
                }
                val displayName = element.selectFirst("displayname")?.text()?.takeIf { it.isNotEmpty() }
                    ?.let { URLDecoder.decodeForPath(it, Charsets.UTF_8) } ?: fileName
                val contentType = element.selectFirst("getcontenttype")?.text().orEmpty()
                val resourceType = element.selectFirst("resourcetype")?.html()?.trim().orEmpty()
                val size = kotlin.runCatching {
                    element.selectFirst("getcontentlength")?.text()?.toLong() ?: 0
                }.getOrDefault(0)
                val lastModify: Long = kotlin.runCatching {
                    element.selectFirst("getlastmodified")?.text()?.let { 
                        LocalDateTime.parse(it, dateTimeFormatter)
                            .toInstant(ZoneOffset.of("+8")).toEpochMilli()
                    }
                }.getOrNull() ?: 0
                
                var fullURL = if (hrefDecode.startsWith("http")) {
                    hrefDecode
                } else {
                    baseUrl + hrefDecode.removePrefix("/")
                }
                if (WebDavFile.isDir(contentType, resourceType) && !fullURL.endsWith("/")) {
                    fullURL += "/"
                }
                
                val webDavFile = WebDavFile(
                    fullURL,
                    authorization,
                    displayName = displayName,
                    urlName = urlName,
                    size = size,
                    contentType = contentType,
                    resourceType = resourceType,
                    lastModify = lastModify
                )
                list.add(webDavFile)
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
        return list
    }

    /**
     * 文件是否存在
     */
    suspend fun exists(): Boolean {
        val url = httpUrl ?: return false
        return kotlin.runCatching {
            webDavClient.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Depth", "0")
                    .method("PROPFIND", EXISTS.toRequestBody("application/xml".toMediaType()))
                    .build()
            ).execute().use { it.isSuccessful }
        }.onFailure { 
            coroutineContext.ensureActive()
        }.getOrDefault(false)
    }

    /**
     * 检查用户名密码是否有效
     */
    suspend fun check(): Boolean {
        return kotlin.runCatching {
            webDavClient.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Depth", "0")
                    .method("PROPFIND", EXISTS.toRequestBody("application/xml".toMediaType()))
                    .build()
            ).execute().use { it.code != 401 }
        }.onFailure { 
            coroutineContext.ensureActive()
        }.getOrDefault(true)
    }

    /**
     * 根据自己的URL，在远程处创建对应的文件夹
     * @return 是否创建成功
     */
    suspend fun makeAsDir(): Boolean {
        val url = httpUrl ?: return false
        return kotlin.runCatching {
            if (!exists()) {
                webDavClient.newCall(
                    Request.Builder()
                        .url(url)
                        .method("MKCOL", null)
                        .build()
                ).execute().use { 
                    checkResult(it)
                }
            }
        }.onFailure { 
            coroutineContext.ensureActive()
            it.printStackTrace()
        }.isSuccess
    }

    /**
     * 下载到本地
     * @param savedPath       本地的完整路径，包括最后的文件名
     * @param replaceExisting 是否替换本地的同名文件
     */
    @Throws(WebDavException::class)
    suspend fun downloadTo(savedPath: String, replaceExisting: Boolean) {
        val file = File(savedPath)
        if (file.exists() && !replaceExisting) {
            return
        }
        downloadInputStream().use { byteStream ->
            FileOutputStream(file).use { 
                byteStream.copyTo(it)
            }
        }
    }

    /**
     * 下载文件,返回ByteArray
     */
    @Throws(WebDavException::class)
    suspend fun download(): ByteArray {
        return downloadInputStream().use { it.readBytes() }
    }

    /**
     * 上传文件
     */
    @Throws(WebDavException::class)
    suspend fun upload(localPath: String, contentType: String = DEFAULT_CONTENT_TYPE) {
        upload(File(localPath), contentType)
    }

    @Throws(WebDavException::class)
    suspend fun upload(file: File, contentType: String = DEFAULT_CONTENT_TYPE) {
        withContext(IO) {
            if (!file.exists()) throw WebDavException("文件不存在")
            val fileBody = file.asRequestBody(contentType.toMediaType())
            val url = httpUrl ?: throw WebDavException("url不能为空")
            webDavClient.newCall(
                Request.Builder()
                    .url(url)
                    .put(fileBody)
                    .build()
            ).execute().use { 
                checkResult(it)
            }
        }
    }

    @Throws(WebDavException::class)
    suspend fun upload(byteArray: ByteArray, contentType: String = DEFAULT_CONTENT_TYPE) {
        withContext(IO) {
            val fileBody = byteArray.toRequestBody(contentType.toMediaType())
            val url = httpUrl ?: throw WebDavException("url不能为空")
            webDavClient.newCall(
                Request.Builder()
                    .url(url)
                    .put(fileBody)
                    .build()
            ).execute().use { 
                checkResult(it)
            }
        }
    }

    @Throws(WebDavException::class)
    suspend fun downloadInputStream(): InputStream {
        val url = httpUrl ?: throw WebDavException("WebDav下载出错\nurl为空")
        val response = webDavClient.newCall(
            Request.Builder()
                .url(url)
                .build()
        ).execute().use { 
            checkResult(it)
            it
        }
        return response.body?.byteStream() ?: throw WebDavException("WebDav下载出错\nNull Exception")
    }

    /**
     * 移除文件/文件夹
     */
    suspend fun delete(): Boolean {
        val url = httpUrl ?: return false
        return kotlin.runCatching {
            webDavClient.newCall(
                Request.Builder()
                    .url(url)
                    .method("DELETE", null)
                    .build()
            ).execute().use { 
                checkResult(it)
            }
        }.onFailure { 
            coroutineContext.ensureActive()
            it.printStackTrace()
        }.isSuccess
    }

    /**
     * 检测返回结果是否正确
     */
    private fun checkResult(response: Response) {
        if (!response.isSuccessful) {
            val body = response.body?.string()
            if (response.code == 401) {
                val headers = response.headers("WWW-Authenticate")
                val supportBasicAuth = headers.any { 
                    it.startsWith("Basic", ignoreCase = true)
                }
                if (headers.isNotEmpty() && !supportBasicAuth) {
                    println("服务器不支持BasicAuth认证")
                }
            }
            
            if (response.message.isNotBlank() || body.isNullOrBlank()) {
                throw WebDavException("${url}\n${response.code}:${response.message}")
            }
            
            val document = Jsoup.parse(body)
            val exception = document.selectFirst("s:exception")?.text()
            val message = document.selectFirst("s:message")?.text()
            if (exception == "ObjectNotFound") {
                throw ObjectNotFoundException(
                    message ?: "$path doesn't exist. code:${response.code}"
                )
            }
            throw WebDavException(message ?: "未知错误 code:${response.code}")
        }
    }

}
