package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TweetDownloadDao {
    @Query("SELECT * FROM tweet_downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<TweetDownloadEntity>>

    @Query("SELECT * FROM tweet_downloads WHERE downloadStatus = 'Success' ORDER BY timestamp DESC")
    fun getDownloadedItemsSync(): List<TweetDownloadEntity>

    @Query("SELECT * FROM tweet_downloads WHERE tweetId = :tweetId")
    suspend fun getDownloadById(tweetId: String): TweetDownloadEntity?

    @Query("SELECT * FROM tweet_downloads WHERE tweetId = :tweetId")
    fun getDownloadByIdSync(tweetId: String): TweetDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: TweetDownloadEntity)

    @Update
    suspend fun updateDownload(entity: TweetDownloadEntity)

    @Query("DELETE FROM tweet_downloads WHERE tweetId = :tweetId")
    suspend fun deleteDownloadById(tweetId: String)

    @Query("""
        SELECT authorName, authorHandle, authorAvatarUrl, COUNT(*) as downloadCount 
        FROM tweet_downloads 
        WHERE downloadStatus = 'Success' 
        GROUP BY authorHandle 
        ORDER BY downloadCount DESC
    """)
    fun getAuthorRankings(): Flow<List<AuthorRanking>>
}
