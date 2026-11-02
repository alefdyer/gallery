package com.asinosoft.photogallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailCacheTest {
    @Test
    fun identityUsesBasenameAndPixelSize() {
        val media = Media(
            id = 11,
            filename = "IMG_0001.jpg",
            path = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
            image = Image(width = 4032, height = 3024),
        )
        val copy = media.copy(
            id = 99,
            path = "/Dropbox/Photos/IMG_0001.jpg",
            storageId = 5,
        )
        assertEquals("thumb_7bcd06807ecb617c03ebcb2a334ce5f0_4032x3024.jpg", identityFileName(media))
        assertEquals(identityFileName(media), identityFileName(copy))
        assertEquals(identityStem(media), identityStem(copy))
    }

    @Test
    fun identityStripsPathAndUnsafeCharacters() {
        assertEquals(
            "thumb_78010368acc6a958c7b43a0e7324d03f_800x600.jpg",
            identityFileName("vacation pic.jpg", "/foo/bar", 800, 600)
        )
    }

    @Test
    fun identityRequiresPixelSize() {
        assertNull(identityFileName("a.jpg", "", 0, 600))
        assertNull(identityFileName("a.jpg", "", 100, 0))
        assertEquals("id-7", identityStem(Media(id = 7, filename = "a.jpg")))
    }

    @Test
    fun matchByFilenameSharesWhenSizeUnknown() {
        val names = listOf("thumb_a03f7277060ad097e9ef9075ebae3670_1920x1080.jpg", "thumb_78010368acc6a958c7b43a0e7324d03f_10x10.jpg")
        val remote = Media(id = 2, filename = "shot.jpg")
        assertEquals("thumb_a03f7277060ad097e9ef9075ebae3670_1920x1080.jpg", matchByFilename(names, remote))
    }

    @Test
    fun matchByFilenameDoesNotGuessAmongSeveralSizes() {
        val names = listOf("thumb_a03f7277060ad097e9ef9075ebae3670_1920x1080.jpg", "thumb_a03f7277060ad097e9ef9075ebae3670_800x600.jpg")
        val remote = Media(id = 2, filename = "shot.jpg")
        assertNull(matchByFilename(names, remote))
        assertEquals(
            "thumb_a03f7277060ad097e9ef9075ebae3670_800x600.jpg",
            matchByFilename(names, remote.copy(image = Image(800, 600)))
        )
    }
}
