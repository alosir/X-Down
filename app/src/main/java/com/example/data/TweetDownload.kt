package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "tweet_downloads")
data class TweetDownloadEntity(
    @PrimaryKey
    val tweetId: String,
    val url: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadStatus: String = "Idle", // Idle, Downloading, Paused, Success, Failed
    val videosJson: String, // JSON array of available qualities/resolutions (VideoQuality)
    val localFilePathsJson: String = "{}" // JSON map of videoIndex -> localFilePath (e.g. {"0": "/storage/emulated/0/Movies/XDownloader/username/video_1080p.mp4"})
) {
    // Parse helper
    fun getVideos(): List<VideoQuality> {
        val list = mutableListOf<VideoQuality>()
        try {
            if (videosJson.isNotEmpty()) {
                val array = JSONArray(videosJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        VideoQuality(
                            videoIndex = obj.optInt("videoIndex", 0),
                            url = obj.optString("url", ""),
                            quality = obj.optString("quality", "unknown"),
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0),
                            bitrate = obj.optInt("bitrate", 0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getLocalFilePaths(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            if (localFilePathsJson.isNotEmpty()) {
                val obj = JSONObject(localFilePathsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key.toInt()] = obj.getString(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // Builder helpers
    companion object {
        fun createVideosJson(videos: List<VideoQuality>): String {
            val array = JSONArray()
            videos.forEach { video ->
                val obj = JSONObject().apply {
                    put("videoIndex", video.videoIndex)
                    put("url", video.url)
                    put("quality", video.quality)
                    put("width", video.width)
                    put("height", video.height)
                    put("bitrate", video.bitrate)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun createLocalFilePathsJson(map: Map<Int, String>): String {
            val obj = JSONObject()
            map.forEach { (index, path) ->
                obj.put(index.toString(), path)
            }
            return obj.toString()
        }
    }
}

data class VideoQuality(
    val videoIndex: Int = 0,
    val url: String,
    val quality: String, // e.g. "1080p", "720p", "480p"
    val width: Int,
    val height: Int,
    val bitrate: Int = 0
)

data class AuthorRanking(
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val downloadCount: Int
)
