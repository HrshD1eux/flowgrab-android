package hrshd1eux.flowgrab

import hrshd1eux.flowgrab.database.objects.CommandTemplate
import hrshd1eux.flowgrab.download.Task
import hrshd1eux.flowgrab.download.TaskFactory
import hrshd1eux.flowgrab.util.DownloadUtil.DownloadPreferences
import hrshd1eux.flowgrab.util.DownloadUtil.toAudioFormatSorter
import hrshd1eux.flowgrab.util.DownloadUtil.toFormatSorter
import hrshd1eux.flowgrab.util.DownloadUtil.toVideoFormatSorter
import hrshd1eux.flowgrab.util.FORMAT_COMPATIBILITY
import hrshd1eux.flowgrab.util.FORMAT_QUALITY
import hrshd1eux.flowgrab.util.HIGH
import hrshd1eux.flowgrab.util.LOW
import hrshd1eux.flowgrab.util.M4A
import hrshd1eux.flowgrab.util.MEDIUM
import hrshd1eux.flowgrab.util.OPUS
import hrshd1eux.flowgrab.util.connectWithDelimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testTextJoin() {
        assertEquals(
            connectWithDelimiter("123", "456", "789", delimiter = ","),
            listOf(123, 456, 789).joinToString(separator = ",") { it.toString() },
        )
        assertEquals(connectWithDelimiter(delimiter = ","), "")
        assertEquals(emptyList<String>().joinToString(separator = ",") { it }, "")
    }

    @Test
    fun testVideoFormatSorterCompatibility() {
        val prefs =
            DownloadPreferences.EMPTY.copy(
                videoFormat = FORMAT_COMPATIBILITY,
                videoResolution = 3, // 1080p
            )
        val sorter = prefs.toVideoFormatSorter()
        assertEquals("proto,vcodec:h264,ext,res:1080", sorter)
    }

    @Test
    fun testVideoFormatSorterQualityWithAv1() {
        val prefs =
            DownloadPreferences.EMPTY.copy(
                videoFormat = FORMAT_QUALITY,
                videoResolution = 1, // 2160p (4K)
                supportAv1HardwareDecoding = true,
            )
        val sorter = prefs.toVideoFormatSorter()
        assertEquals("res:2160,vcodec:av01", sorter)
    }

    @Test
    fun testVideoFormatSorterQualityWithVp9() {
        val prefs =
            DownloadPreferences.EMPTY.copy(
                videoFormat = FORMAT_QUALITY,
                videoResolution = 4, // 720p
                supportAv1HardwareDecoding = false,
            )
        val sorter = prefs.toVideoFormatSorter()
        assertEquals("res:720,vcodec:vp9.2", sorter)
    }

    @Test
    fun testAudioFormatSorterPresets() {
        val m4aHigh =
            DownloadPreferences.EMPTY.copy(
                useCustomAudioPreset = true,
                audioFormat = M4A,
                audioQuality = HIGH,
            )
        assertEquals("acodec:aac,abr~192", m4aHigh.toAudioFormatSorter())

        val opusMed =
            DownloadPreferences.EMPTY.copy(
                useCustomAudioPreset = true,
                audioFormat = OPUS,
                audioQuality = MEDIUM,
            )
        assertEquals("acodec:opus,abr~128", opusMed.toAudioFormatSorter())

        val opusLow =
            DownloadPreferences.EMPTY.copy(
                useCustomAudioPreset = true,
                audioFormat = OPUS,
                audioQuality = LOW,
            )
        assertEquals("acodec:opus,abr~64", opusLow.toAudioFormatSorter())

        val disabledPreset =
            DownloadPreferences.EMPTY.copy(
                useCustomAudioPreset = false,
                audioFormat = OPUS,
                audioQuality = HIGH,
            )
        assertEquals("", disabledPreset.toAudioFormatSorter())
    }

    @Test
    fun testCombinedFormatSorter() {
        val prefs =
            DownloadPreferences.EMPTY.copy(
                videoFormat = FORMAT_COMPATIBILITY,
                videoResolution = 3,
                useCustomAudioPreset = true,
                audioFormat = M4A,
                audioQuality = HIGH,
            )
        assertEquals("proto,vcodec:h264,ext,res:1080,acodec:aac,abr~192", prefs.toFormatSorter())
    }

    @Test
    fun testTaskFactoryCreateWithCustomCommand() {
        val template = CommandTemplate(id = 1, name = "Extract Audio", template = "-x --audio-format mp3")
        val url = "https://example.com/video"
        val taskWithState =
            TaskFactory.createWithCustomCommand(
                url = url,
                template = template,
                preferences = DownloadPreferences.EMPTY,
            )

        assertEquals(url, taskWithState.task.url)
        assertTrue(taskWithState.task.type is Task.TypeInfo.CustomCommand)
        assertEquals(template, (taskWithState.task.type as Task.TypeInfo.CustomCommand).template)
        assertEquals(Task.DownloadState.Idle, taskWithState.state.downloadState)
        assertEquals("Extract Audio", taskWithState.state.viewState.title)
    }

    @Test
    fun testCustomCommandUrlSplitting() {
        val rawInput = "https://example.com/1 \n https://example.com/2   \nhttps://example.com/3"
        val urls = rawInput.split(Regex("[\n ]")).filter { it.isNotBlank() }
        assertEquals(3, urls.size)
        assertEquals("https://example.com/1", urls[0])
        assertEquals("https://example.com/2", urls[1])
        assertEquals("https://example.com/3", urls[2])
    }
}
