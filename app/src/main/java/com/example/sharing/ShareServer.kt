package com.example.sharing

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.util.Collections

class ShareServer(private val context: Context) {
    private var server: HttpServer? = null
    private val db = AppDatabase.getDatabase(context)

    fun start(port: Int = 8282): String? {
        if (server != null) {
            val localIp = getLocalIpAddress() ?: "127.0.0.1"
            return "http://$localIp:$port"
        }
        try {
            val localIp = getLocalIpAddress() ?: return null
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/", DashboardHandler())
                createContext("/stream", StreamHandler())
                executor = null // default executor
                start()
            }
            Log.d("ShareServer", "Started sharing server at http://$localIp:$port")
            return "http://$localIp:$port"
        } catch (e: Exception) {
            Log.e("ShareServer", "Failed to start server", e)
            return null
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    fun isRunning(): Boolean = server != null

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.name.contains("p2p") || intf.name.contains("wlan1")) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4 && (sAddr.startsWith("192.168.") || sAddr.startsWith("10.") || sAddr.startsWith("172."))) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    private inner class DashboardHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod.uppercase() != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    return
                }

                // Query DB synchronously (safe inside HTTP thread)
                val completed = db.tweetDownloadDao().getDownloadedItemsSync()

                // Generate HTML list
                val listHtml = StringBuilder()
                if (completed.isEmpty()) {
                    listHtml.append("""
                        <div class="empty-state">
                            <p>📁 暂无已下载完成的视频文件可用</p>
                            <span>先在手机APP解析并下载一些 Twitter / X 视频吧！</span>
                        </div>
                    """.trimIndent())
                } else {
                    completed.forEach { item ->
                        val paths = item.getLocalFilePaths()
                        paths.forEach { (index, filePath) ->
                            val f = File(filePath)
                            if (f.exists()) {
                                val sizeMb = String.format("%.2f", f.length() / (1024.0 * 1024.0))
                                val videoQuality = item.getVideos().find { it.videoIndex == index }?.quality ?: "High"
                                listHtml.append("""
                                    <div class="card">
                                        <div class="card-info">
                                            <h3>${escapeHtml(item.title)}</h3>
                                            <p class="author">👤 @${escapeHtml(item.authorHandle)} (${escapeHtml(item.authorName)})</p>
                                            <p class="meta">🎬 清晰度: <strong>$videoQuality</strong> • 📦 大小: $sizeMb MB</p>
                                        </div>
                                        <div class="card-action">
                                            <button onclick="playUrl('/stream?tweetId=${item.tweetId}&index=$index')">📺 投屏播放</button>
                                            <a href="/stream?tweetId=${item.tweetId}&index=$index" download="${item.authorHandle}_$videoQuality.mp4">📥 下载到本机</a>
                                        </div>
                                    </div>
                                """.trimIndent())
                            }
                        }
                    }
                }

                val html = """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>X视频局域网共享中心</title>
                        <style>
                            :root {
                                --bg: #0f111a;
                                --card-bg: #1a1c29;
                                --item-bg: #24273a;
                                --text: #eceff4;
                                --text-mute: #81a1c1;
                                --accent: #1da1f2;
                                --accent-hover: #1a8cd8;
                            }
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                background-color: var(--bg);
                                color: var(--text);
                                margin: 0;
                                padding: 16px;
                            }
                            header {
                                max-width: 800px;
                                margin: 0 auto 24px;
                                border-bottom: 1px solid rgba(255,255,255,0.1);
                                padding-bottom: 16px;
                            }
                            h1 {
                                font-size: 24px;
                                color: var(--accent);
                                margin: 0;
                            }
                            header p {
                                color: var(--text-mute);
                                font-size: 14px;
                                margin: 4px 0 0;
                            }
                            .container {
                                max-width: 800px;
                                margin: 0 auto;
                                display: flex;
                                flex-direction: column;
                                gap: 16px;
                            }
                            .card {
                                background-color: var(--card-bg);
                                border-radius: 12px;
                                padding: 16px;
                                display: flex;
                                flex-direction: column;
                                justify-content: space-between;
                                border: 1px solid rgba(29, 161, 242, 0.15);
                                gap: 12px;
                            }
                            @media(min-width: 600px) {
                                .card {
                                    flex-direction: row;
                                    align-items: center;
                                }
                            }
                            .card-info {
                                flex: 1;
                            }
                            .card-info h3 {
                                margin: 0 0 8px;
                                font-size: 16px;
                                font-weight: 600;
                                line-height: 1.4;
                            }
                            .author {
                                font-size: 13px;
                                color: var(--accent);
                                margin: 0 0 6px;
                                font-weight: 500;
                            }
                            .meta {
                                font-size: 12px;
                                color: var(--text-mute);
                                margin: 0;
                            }
                            .card-action {
                                display: flex;
                                gap: 10px;
                            }
                            button, a {
                                text-decoration: none;
                                background-color: var(--accent);
                                color: white;
                                border: none;
                                padding: 10px 16px;
                                border-radius: 8px;
                                font-size: 13px;
                                font-weight: 600;
                                cursor: pointer;
                                text-align: center;
                                transition: background 0.2s;
                            }
                            button:hover, a:hover {
                                background-color: var(--accent-hover);
                            }
                            a {
                                background-color: #4c566a;
                            }
                            a:hover {
                                background-color: #5e81ac;
                            }
                            .player-box {
                                max-width: 800px;
                                margin: 0 auto 20px;
                                display: none;
                                background: black;
                                border-radius: 12px;
                                overflow: hidden;
                            }
                            video {
                                width: 100%;
                                display: block;
                                aspect-ratio: 16/9;
                            }
                            .empty-state {
                                text-align: center;
                                padding: 48px;
                                background-color: var(--card-bg);
                                border-radius: 12px;
                                border: 1px dashed rgba(255,255,255,0.1);
                            }
                            .empty-state p {
                                font-size: 16px;
                                margin: 0 0 6px;
                            }
                            .empty-state span {
                                font-size: 13px;
                                color: var(--text-mute);
                            }
                        </style>
                    </head>
                    <body>
                        <header>
                            <h1>X视频局域网投屏中心</h1>
                            <p>局域网下的智能电视、电脑、手机或平板浏览器中都能直接点播播放视频</p>
                        </header>
                        <main class="container">
                            <div id="player-box" class="player-box">
                                <video id="web-player" controls autoplay></video>
                            </div>
                            $listHtml
                        </main>
                        <script>
                            function playUrl(url) {
                                const playerBox = document.getElementById('player-box');
                                const player = document.getElementById('web-player');
                                player.src = url;
                                playerBox.style.display = 'block';
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                            }
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                val bytes = html.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                val os: OutputStream = exchange.responseBody
                os.write(bytes)
                os.close()
            } catch (e: Exception) {
                Log.e("ShareServer", "Dashboard error", e)
                try { exchange.sendResponseHeaders(500, -1) } catch(any: Exception){}
            }
        }
    }

    private inner class StreamHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod.uppercase() != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    return
                }

                // Parse query
                val query = exchange.requestURI.query ?: ""
                val params = parseQueryParams(query)
                val tweetId = params["tweetId"]
                val index = params["index"]?.toIntOrNull() ?: 0

                if (tweetId == null) {
                    exchange.sendResponseHeaders(400, -1)
                    return
                }

                val item = db.tweetDownloadDao().getDownloadByIdSync(tweetId)
                if (item == null) {
                    exchange.sendResponseHeaders(444, -1)
                    return
                }

                val paths = item.getLocalFilePaths()
                val path = paths[index]
                if (path == null) {
                    exchange.sendResponseHeaders(404, -1)
                    return
                }

                val file = File(path)
                if (!file.exists()) {
                    exchange.sendResponseHeaders(404, -1)
                    return
                }

                exchange.responseHeaders.set("Content-Type", "video/mp4")
                exchange.responseHeaders.set("Accept-Ranges", "bytes")
                
                // Simple support for full file stream or standard streaming
                exchange.sendResponseHeaders(200, file.length())
                
                val buffer = ByteArray(16 * 1024)
                val fis = FileInputStream(file)
                val bis = BufferedInputStream(fis)
                val os = exchange.responseBody
                
                var bytesRead: Int
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
                
                os.close()
                bis.close()
                fis.close()
            } catch (e: Exception) {
                Log.e("ShareServer", "Stream error", e)
                try { exchange.sendResponseHeaders(500, -1) } catch(any: Exception){}
            }
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        query.split("&").forEach { param ->
            val parts = param.split("=")
            if (parts.size >= 2) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                params[key] = value
            }
        }
        return params
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }
}
