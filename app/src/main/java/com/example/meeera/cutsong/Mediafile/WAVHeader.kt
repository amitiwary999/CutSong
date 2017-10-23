/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.meeera.cutsong.Mediafile

class WAVHeader(private val mSampleRate: Int         // sampling frequency in Hz (e.g. 44100).
                , private val mChannels: Int           // number of channels.
                , private val mNumSamples: Int         // total number of samples per channel.
) {
    var wavHeader: ByteArray? = null
        private set          // the complete header.
    private val mNumBytesPerSample: Int  // number of bytes per sample, all channels included.

    init {
        mNumBytesPerSample = 2 * mChannels  // assuming 2 bytes per sample (for 1 channel)
        wavHeader = null
        setHeader()
    }

    override fun toString(): String {
        var str = ""
        if (wavHeader == null) {
            return str
        }
        val num_32bits_per_lines = 8
        var count = 0
        for (b in wavHeader!!) {
            val break_line = count > 0 && count % (num_32bits_per_lines * 4) == 0
            val insert_space = count > 0 && count % 4 == 0 && !break_line
            if (break_line) {
                str += '\n'
            }
            if (insert_space) {
                str += ' '
            }
            str += String.format("%02X", b)
            count++
        }

        return str
    }

    private fun setHeader() {
        val header = ByteArray(46)
        var offset = 0
        var size: Int

        // set the RIFF chunk
        System.arraycopy(byteArrayOf('R'.toByte(), 'I'.toByte(), 'F'.toByte(), 'F'.toByte()), 0, header, offset, 4)
        offset += 4
        size = 36 + mNumSamples * mNumBytesPerSample
        header[offset++] = (size and 0xFF).toByte()
        header[offset++] = (size shr 8 and 0xFF).toByte()
        header[offset++] = (size shr 16 and 0xFF).toByte()
        header[offset++] = (size shr 24 and 0xFF).toByte()
        System.arraycopy(byteArrayOf('W'.toByte(), 'A'.toByte(), 'V'.toByte(), 'E'.toByte()), 0, header, offset, 4)
        offset += 4

        // set the fmt chunk
        System.arraycopy(byteArrayOf('f'.toByte(), 'm'.toByte(), 't'.toByte(), ' '.toByte()), 0, header, offset, 4)
        offset += 4
        System.arraycopy(byteArrayOf(0x10, 0, 0, 0), 0, header, offset, 4)  // chunk size = 16
        offset += 4
        System.arraycopy(byteArrayOf(1, 0), 0, header, offset, 2)  // format = 1 for PCM
        offset += 2
        header[offset++] = (mChannels and 0xFF).toByte()
        header[offset++] = (mChannels shr 8 and 0xFF).toByte()
        header[offset++] = (mSampleRate and 0xFF).toByte()
        header[offset++] = (mSampleRate shr 8 and 0xFF).toByte()
        header[offset++] = (mSampleRate shr 16 and 0xFF).toByte()
        header[offset++] = (mSampleRate shr 24 and 0xFF).toByte()
        val byteRate = mSampleRate * mNumBytesPerSample
        header[offset++] = (byteRate and 0xFF).toByte()
        header[offset++] = (byteRate shr 8 and 0xFF).toByte()
        header[offset++] = (byteRate shr 16 and 0xFF).toByte()
        header[offset++] = (byteRate shr 24 and 0xFF).toByte()
        header[offset++] = (mNumBytesPerSample and 0xFF).toByte()
        header[offset++] = (mNumBytesPerSample shr 8 and 0xFF).toByte()
        System.arraycopy(byteArrayOf(0x10, 0), 0, header, offset, 2)
        offset += 2

        // set the beginning of the data chunk
        System.arraycopy(byteArrayOf('d'.toByte(), 'a'.toByte(), 't'.toByte(), 'a'.toByte()), 0, header, offset, 4)
        offset += 4
        size = mNumSamples * mNumBytesPerSample
        header[offset++] = (size and 0xFF).toByte()
        header[offset++] = (size shr 8 and 0xFF).toByte()
        header[offset++] = (size shr 16 and 0xFF).toByte()
        header[offset++] = (size shr 24 and 0xFF).toByte()

        wavHeader = header
    }

    companion object {

        fun getWAVHeader(sampleRate: Int, numChannels: Int, numSamples: Int): ByteArray? {
            return WAVHeader(sampleRate, numChannels, numSamples).wavHeader
        }
    }
}
