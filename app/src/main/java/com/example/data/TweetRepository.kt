package com.example.data

import kotlinx.coroutines.flow.Flow
import java.lang.Exception

class TweetRepository(
    private val dao: TweetDownloadDao,
    private val service: FxTwitterService = FxTwitterService.create()
) {
    val allDownloads: Flow<List<TweetDownloadEntity>> = dao.getAllDownloads()
    val authorRankings: Flow<List<AuthorRanking>> = dao.getAuthorRankings()

    suspend fun getDownloadById(tweetId: String): TweetDownloadEntity? {
        return dao.getDownloadById(tweetId)
    }

    suspend fun insertDownload(entity: TweetDownloadEntity) {
        dao.insertDownload(entity)
    }

    suspend fun updateDownload(entity: TweetDownloadEntity) {
        dao.updateDownload(entity)
    }

    suspend fun deleteDownloadById(tweetId: String) {
        dao.deleteDownloadById(tweetId)
    }

    // Parse X/Twitter URL using FxTwitter API
    suspend fun parseTweetUrl(url: String): TweetDownloadEntity {
        val tweetId = extractTweetId(url) ?: throw Exception("无法从链接中提取推文ID，请确保是正确的 Twitter/X 分享链接。")
        try {
            val response = service.getTweetStatus(tweetId)
            return convertResponseToEntity(response, url)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                throw Exception("无法访问该推文 (401)。这通常是因为推文是私密的（锁定账号），或者该区域无法访问。")
            }
            throw Exception("网络请求失败 (${e.code()})，请稍后再试。")
        } catch (e: Exception) {
            throw e
        }
    }

    private fun extractTweetId(url: String): String? {
        val cleanUrl = url.trim()
        val match = "status/(\\d+)".toRegex().find(cleanUrl)
        return match?.groupValues?.get(1)
    }

    private fun convertResponseToEntity(response: FxTwitterResponse, originalUrl: String): TweetDownloadEntity {
        // If Response code is null or not success of some kind, still double check tweet payload robustness
        val tweet = response.tweet ?: throw Exception(response.message ?: "该推文数据未公开或链接有误")
        
        val id = tweet.id ?: throw Exception("无推文ID，解析失败")
        val text = tweet.text ?: "无标题"
        val author = tweet.author ?: FxAuthor("unknown", "未知作者", "unknown", "")
        
        val videos = mutableListOf<VideoQuality>()
        
        fun extractHeightFromUrl(url: String): Int? {
            val regex = "/(\\d+)x(\\d+)/".toRegex()
            val m = regex.find(url)
            return m?.groupValues?.get(2)?.toInt()
        }

        var videoIdx = 0
        
        // Formats can live under media.videos[i].formats
        tweet.media?.videos?.forEach { fxVideo ->
            val formats = fxVideo.formats
            if (!formats.isNullOrEmpty()) {
                formats.forEach { fmt ->
                    if (fmt.url != null) {
                        val urlStr = fmt.url
                        val w = fmt.width ?: 1280
                        val h = fmt.height ?: (extractHeightFromUrl(urlStr) ?: 720)
                        videos.add(VideoQuality(
                            videoIndex = videoIdx,
                            url = urlStr,
                            quality = "${h}p",
                            width = w,
                            height = h,
                            bitrate = fmt.bitrate ?: 0
                        ))
                    }
                }
            } else if (fxVideo.url != null) {
                val urlStr = fxVideo.url
                val h = extractHeightFromUrl(urlStr) ?: 720
                videos.add(VideoQuality(
                    videoIndex = videoIdx,
                    url = urlStr,
                    quality = "${h}p",
                    width = 1280,
                    height = h
                ))
            }
            videoIdx++
        }
        
        // Also look into media.all formats
        if (videos.isEmpty() && !tweet.media?.all.isNullOrEmpty()) {
            tweet.media?.all?.forEach { item ->
                if (item.type == "video") {
                    val formats = item.formats
                    if (!formats.isNullOrEmpty()) {
                        formats.forEach { fmt ->
                            if (fmt.url != null) {
                                val urlStr = fmt.url
                                val w = fmt.width ?: 1280
                                val h = fmt.height ?: (extractHeightFromUrl(urlStr) ?: 720)
                                videos.add(VideoQuality(
                                    videoIndex = videoIdx,
                                    url = urlStr,
                                    quality = "${h}p",
                                    width = w,
                                    height = h,
                                    bitrate = fmt.bitrate ?: 0
                                ))
                            }
                        }
                    } else if (item.url != null) {
                        val urlStr = item.url
                        val h = extractHeightFromUrl(urlStr) ?: 720
                        videos.add(VideoQuality(
                            videoIndex = videoIdx,
                            url = urlStr,
                            quality = "${h}p",
                            width = 1280,
                            height = h
                        ))
                    }
                    videoIdx++
                }
            }
        }
        
        if (videos.isEmpty()) {
            throw Exception("此推文未包含任何可解析的视频。")
        }
        
        // Sort: Group by videoIndex first, then sort each group by height descending, then combine.
        // That way, we can support multi-video selection where each video has its highest resolution selected.
        val grouped = videos.groupBy { it.videoIndex }
        val sortedVideos = mutableListOf<VideoQuality>()
        grouped.forEach { (_, qualities) ->
            val sortedList = qualities.sortedWith(compareByDescending<VideoQuality> { it.height }.thenByDescending { it.bitrate })
            sortedVideos.addAll(sortedList)
        }

        val authorName = author.name ?: author.screen_name ?: "Twitter用户"
        val authorHandle = author.screen_name ?: author.name ?: "unknown"
        val authorAvatarUrl = author.avatar_url ?: ""
        val thumbnailUrl = tweet.media?.thumbnail ?: tweet.media?.videos?.firstOrNull()?.thumbnail_url ?: ""
        val duration = tweet.duration ?: tweet.media?.duration ?: tweet.media?.videos?.firstOrNull()?.duration ?: 0.0
        
        return TweetDownloadEntity(
            tweetId = id,
            url = originalUrl,
            title = text,
            authorName = authorName,
            authorHandle = authorHandle,
            authorAvatarUrl = authorAvatarUrl,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = duration.toLong(),
            videosJson = TweetDownloadEntity.createVideosJson(sortedVideos),
            localFilePathsJson = "{}"
        )
    }
}
