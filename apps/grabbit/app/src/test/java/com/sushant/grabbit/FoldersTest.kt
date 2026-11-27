package com.sushant.grabbit

import com.sushant.grabbit.data.Folders
import com.sushant.grabbit.download.DlOpts
import org.junit.Assert.assertEquals
import org.junit.Test

class FoldersTest {
    @Test fun readablePaths() {
        assertEquals("Internal storage › Download › Lectures", Folders.readablePath("primary:Download/Lectures"))
        assertEquals("Internal storage › Movies", Folders.readablePath("primary:Movies"))
        assertEquals("SD card › Videos", Folders.readablePath("1A2B-3C4D:Videos"))
    }

    @Test fun folderSurvivesOptionRoundTrip() {
        val o = DlOpts(folder = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FLectures")
        assertEquals(o, DlOpts.decode(o.encode()))
    }
}
