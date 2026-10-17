package com.example.ui.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.util.Calendar

data class DeviceAudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)

data class SystemRingtoneItem(
    val title: String,
    val uri: Uri
)

object AlarmScheduler {

    fun scheduleAlarm(
        context: Context,
        triggerTimeMillis: Long,
        title: String,
        noteId: Long = 0L,
        ringtoneUri: Uri? = null
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_NOTE_ID, noteId)
            ringtoneUri?.let { putExtra(AlarmReceiver.EXTRA_RINGTONE_URI, it.toString()) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (triggerTimeMillis % 100000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: SecurityException) {
            // Fallback for Android 12+ if exact alarm permission isn't granted yet
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun cancelAlarm(context: Context, triggerTimeMillis: Long, noteId: Long = 0L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (triggerTimeMillis % 100000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        try {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (ignored: Exception) {}
    }

    fun getSystemRingtones(context: Context): List<SystemRingtoneItem> {
        val list = mutableListOf<SystemRingtoneItem>()
        try {
            val ringtoneMgr = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            val cursor: Cursor? = ringtoneMgr.cursor
            cursor?.let {
                while (it.moveToNext()) {
                    val title = it.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "Ringtone"
                    val pos = it.position
                    val uri = ringtoneMgr.getRingtoneUri(pos)
                    if (uri != null) {
                        list.add(SystemRingtoneItem(title = title, uri = uri))
                    }
                }
            }
        } catch (ignored: Exception) {}

        if (list.isEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let {
                list.add(SystemRingtoneItem("Default Alarm Sound", it))
            }
        }
        return list
    }

    fun getDeviceMusicFiles(context: Context): List<DeviceAudioFile> {
        val audioList = mutableListOf<DeviceAudioFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown Audio"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    audioList.add(DeviceAudioFile(id, title, artist, contentUri))
                }
            }
        } catch (ignored: Exception) {}

        return audioList
    }
}
