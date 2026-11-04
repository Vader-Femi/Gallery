package com.femi.gallery.data.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MotionPhotoParserTest {

    @Test
    fun parseXmp_googleMicroVideoOffset() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
             <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
               GCamera:MotionPhoto="1"
               GCamera:MicroVideo="1"
               GCamera:MicroVideoOffset="2048000"
               GCamera:MicroVideoPresentationTimestampUs="1500000"/>
             </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val fileLength = 5_000_000L
        val result = MotionPhotoParser.parseXmp(xmp, fileLength)

        assertNotNull(result)
        assertEquals(5_000_000L - 2_048_000L, result!!.offset)
        assertEquals(2_048_000L, result.length)
        assertEquals(1_500_000L, result.presentationTimestampUs)
    }

    @Test
    fun parseXmp_containerDirectoryItemLength() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
             <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description xmlns:Container="http://ns.google.com/photos/1.0/container/"
               xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
               <Container:Directory>
                <rdf:Seq>
                 <rdf:li rdf:parseType="Resource">
                  <Item:Mime>image/jpeg</Item:Mime>
                  <Item:Semantic>Primary</Item:Semantic>
                  <Item:Length>0</Item:Length>
                 </rdf:li>
                 <rdf:li rdf:parseType="Resource" Item:Semantic="MotionPhoto" Item:Mime="video/mp4" Item:Length="3145728"/>
                </rdf:Seq>
               </Container:Directory>
              </rdf:Description>
             </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val fileLength = 6_000_000L
        val result = MotionPhotoParser.parseXmp(xmp, fileLength)

        assertNotNull(result)
        assertEquals(6_000_000L - 3_145_728L, result!!.offset)
        assertEquals(3_145_728L, result.length)
    }

    @Test
    fun parseXmp_nonMotionPhotoReturnsNull() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
             <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description xmlns:tiff="http://ns.adobe.com/tiff/1.0/"
               tiff:Make="Google"
               tiff:Model="Pixel 9"/>
             </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val result = MotionPhotoParser.parseXmp(xmp, 5_000_000L)
        assertNull(result)
    }

    @Test
    fun findFtypInBytes_detectsValidBrand() {
        // Construct byte buffer with junk preceding an MP4 header: [size: 4 bytes, 'ftyp', 'mp42', minor_version...]
        val junk = ByteArray(20) { 0x55.toByte() }
        val mp4Box = byteArrayOf(
            0x00, 0x00, 0x00, 0x20, // 32 bytes box size
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
        )
        val combined = junk + mp4Box

        val matchOffset = MotionPhotoParser.findFtypInBytes(
            searchBytes = combined,
            searchLen = combined.size,
            baseOffset = 1000L,
        )

        assertNotNull(matchOffset)
        // junk is 20 bytes, so box start is at baseOffset + 20
        assertEquals(1020L, matchOffset)
    }

    @Test
    fun parseSamsungSefDirectory_extractsMotionPhotoEntry() {
        // Construct a SEFH buffer
        val buf = ByteBuffer.allocate(64)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x48464553) // 'SEFH' in little-endian
        buf.putInt(100)        // version
        buf.putInt(1)          // entry count = 1

        // Entry: tag type (0x0A30), noff (4000), payloadLen (3500)
        buf.putShort(0)        // reserved
        buf.putShort(0x0A30.toShort()) // tagType = MotionPhoto_Data
        buf.putInt(4000)       // noff
        buf.putInt(3500)       // payloadLen
        buf.flip()

        val sefhPos = 10_000L
        val result = MotionPhotoParser.parseSamsungSefDirectory(
            sefhBuf = buf,
            sefhPos = sefhPos,
            checkPayloadFtyp = { dataBlockPos ->
                assertEquals(6000L, dataBlockPos) // 10000 - 4000
                6000L // Starts immediately at dataBlockPos
            }
        )

        assertNotNull(result)
        assertEquals(6000L, result!!.offset)
        assertEquals(3500L, result.length)
    }
}
