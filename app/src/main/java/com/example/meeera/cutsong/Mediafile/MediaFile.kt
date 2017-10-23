package com.example.meeera.cutsong.Mediafile

import android.media.*
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*

/**
 * Created by meeera on 24/10/17.
 */
class MediaFile() {
    private var mProgressListener: ProgressListener? = null
    private var mInputFile: File? = null

    // Member variables representing frame data
    private var mFileType: String? = null
    private var mFileSize: Int = 0
    private var mAvgBitRate: Int = 0  // Average bit rate in kbps.
    private var mSampleRate: Int = 0
    private var mChannels: Int = 0
    private var mNumSamples: Int = 0  // total number of samples per channel in audio file
    private var mDecodedBytes: ByteBuffer? = null  // Raw audio data
    private var mDecodedSamples: ShortBuffer? = null  // shared buffer with mDecodedBytes.
    // mDecodedSamples has the following format:
    // {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
    // where sicj is the ith sample of the jth channel (a sample is a signed short)
    // M is the number of channels (e.g. 2 for stereo) and N is the number of samples per channel.

    // Member variables for hack (making it work with old version, until app just uses the samples).
    private var mNumFrames: Int = 0
    //private var mFrameGains: List<Int?>? = null
   // private var mFrameLens: IntArray? = null
    //private var mFrameOffsets: IntArray? = null

    // Progress listener interface.
    interface ProgressListener {
        /**
         * Will be called by the SoundFile class periodically
         * with values between 0.0 and 1.0.  Return true to continue
         * loading the file or recording the audio, and false to cancel or stop recording.
         */
        fun reportProgress(fractionComplete: Double): Boolean
    }

    // TODO(nfaralli): what is the real list of supported extensions? Is it device dependent?
    fun getSupportedExtensions(): Array<String> {
        return arrayOf("mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "ogg")
    }

    fun isFilenameSupported(filename: String): Boolean {
        val extensions = getSupportedExtensions()
        for (i in extensions.indices) {
            if (filename.endsWith("." + extensions[i])) {
                return true
            }
        }
        return false
    }

    // Create and return a SoundFile object using the file fileName.
    @Throws(java.io.FileNotFoundException::class, IOException::class)
    fun create(fileName: String): MediaFile? {
        // First check that the file exists and that its extension is supported.
        val f = File(fileName)
        if (!f.exists()) {
            throw java.io.FileNotFoundException(fileName)
        }
        val name = f.name.toLowerCase()
        val components = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (components.size < 2) {
            return null
        }
        if (!Arrays.asList(*getSupportedExtensions()).contains(components[components.size - 1])) {
            return null
        }
        val soundFile = MediaFile()
        // soundFile.setProgressListener(progressListener);
        soundFile.ReadFile(f)
        return soundFile
    }

    // Create and return a SoundFile object by recording a mono audio stream.
    fun record(progressListener: ProgressListener?): MediaFile? {
        if (progressListener == null) {
            // must have a progessListener to stop the recording.
            return null
        }
        val soundFile = MediaFile()
        soundFile.setProgressListener(progressListener)
        soundFile.RecordAudio()
        return soundFile
    }

    fun getFiletype(): String? {
        return mFileType
    }

    fun getFileSizeBytes(): Int {
        return mFileSize
    }

    fun getAvgBitrateKbps(): Int {
        return mAvgBitRate
    }

    fun getSampleRate(): Int {
        return mSampleRate
    }

    fun getChannels(): Int {
        return mChannels
    }

    fun getNumSamples(): Int {
        return mNumSamples  // Number of samples per channel.
    }

    // Should be removed when the app will use directly the samples instead of the frames.
    fun getNumFrames(): Int {
        return mNumFrames
    }

    // Should be removed when the app will use directly the samples instead of the frames.
    fun getSamplesPerFrame(): Int {
        return 1024  // just a fixed value here...
    }

    fun getSamples(): ShortBuffer? {
        return if (mDecodedSamples != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                // Hack for Nougat where asReadOnlyBuffer fails to respect byte ordering.
                // See https://code.google.com/p/android/issues/detail?id=223824
                mDecodedSamples
            } else {
                mDecodedSamples!!.asReadOnlyBuffer()
            }
        } else {
            null
        }
    }

    private fun setProgressListener(progressListener: ProgressListener) {
        mProgressListener = progressListener
    }

    @Throws(java.io.FileNotFoundException::class, IOException::class)
    private fun ReadFile(inputFile: File) {
        var extractor: MediaExtractor? = MediaExtractor()
        var format: MediaFormat? = null
        var i: Int

        mInputFile = inputFile
        val components = mInputFile!!.path.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        mFileType = components[components.size - 1]
        mFileSize = mInputFile!!.length().toInt()
        extractor!!.setDataSource(mInputFile!!.path)
        val numTracks = extractor.trackCount
        // find and select the first audio track present in the file.
        i = 0
        while (i < numTracks) {
            format = extractor.getTrackFormat(i)
            if (format!!.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                extractor.selectTrack(i)
                break
            }
            i++
        }
        mChannels = format!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // Expected total number of samples per channel.
        val expectedNumSamples = (format.getLong(MediaFormat.KEY_DURATION) / 1000000f * mSampleRate + 0.5f).toInt()

        var codec: MediaCodec? = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME))
        codec!!.configure(format, null, null, 0)
        codec.start()

        var decodedSamplesSize = 0  // size of the output buffer containing decoded samples.
        var decodedSamples: ByteArray? = null
        val inputBuffers = codec.inputBuffers
        var outputBuffers = codec.outputBuffers
        var sample_size: Int
        val info = MediaCodec.BufferInfo()
        var presentation_time: Long
        var tot_size_read = 0
        var done_reading = false

        // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream at 44.1kHz).
        // For longer streams, the buffer size will be increased later on, calculating a rough
        // estimate of the total size needed to store all the samples in order to resize the buffer
        // only once.
        mDecodedBytes = ByteBuffer.allocate(1 shl 20)
        var firstSampleData: Boolean? = true
        while (true) {
            // read data from file and feed it to the decoder input buffers.
            val inputBufferIndex = codec!!.dequeueInputBuffer(100)
            if (!done_reading && inputBufferIndex >= 0) {
                sample_size = extractor!!.readSampleData(inputBuffers[inputBufferIndex], 0)
                if (firstSampleData!!
                        && format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
                        && sample_size == 2) {
                    // For some reasons on some devices (e.g. the Samsung S3) you should not
                    // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                    // crash. These two bytes do not contain music data but basic info on the
                    // stream (e.g. channel configuration and sampling frequency), and skipping them
                    // seems OK with other devices (MediaCodec has already been configured and
                    // already knows these parameters).
                    extractor.advance()
                    tot_size_read += sample_size
                } else if (sample_size < 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    done_reading = true
                } else {
                    presentation_time = extractor.sampleTime
                    codec.queueInputBuffer(inputBufferIndex, 0, sample_size, presentation_time, 0)
                    extractor.advance()
                    tot_size_read += sample_size
                    if (mProgressListener != null) {
                        if (!mProgressListener!!.reportProgress((tot_size_read.toFloat() / mFileSize).toDouble())) {
                            // We are asked to stop reading the file. Returning immediately. The
                            // SoundFile object is invalid and should NOT be used afterward!
                            extractor.release()
                            extractor = null
                            codec.stop()
                            codec.release()
                            codec = null
                            return
                        }
                    }
                }
                firstSampleData = false
            }

            // Get decoded stream from the decoder output buffers.
            val outputBufferIndex = codec.dequeueOutputBuffer(info, 100)
            if (outputBufferIndex >= 0 && info.size > 0) {
                if (decodedSamplesSize < info.size) {
                    decodedSamplesSize = info.size
                    decodedSamples = ByteArray(decodedSamplesSize)
                }
                outputBuffers[outputBufferIndex].get(decodedSamples, 0, info.size)
                outputBuffers[outputBufferIndex].clear()
                // Check if buffer is big enough. Resize it if it's too small.
                if (mDecodedBytes!!.remaining() < info.size) {
                    // Getting a rough estimate of the total size, allocate 20% more, and
                    // make sure to allocate at least 5MB more than the initial size.
                    val position = mDecodedBytes!!.position()
                    var newSize = (position * (1.0 * mFileSize / tot_size_read) * 1.2).toInt()
                    if (newSize - position < info.size + 5 * (1 shl 20)) {
                        newSize = position + info.size + 5 * (1 shl 20)
                    }
                    var newDecodedBytes: ByteBuffer? = null
                    // Try to allocate memory. If we are OOM, try to run the garbage collector.
                    var retry = 10
                    while (retry > 0) {
                        try {
                            newDecodedBytes = ByteBuffer.allocate(newSize)
                            break
                        } catch (oome: OutOfMemoryError) {
                            // setting android:largeHeap="true" in <application> seem to help not
                            // reaching this section.
                            retry--
                        }

                    }
                    if (retry == 0) {
                        // Failed to allocate memory... Stop reading more data and finalize the
                        // instance with the data decoded so far.
                        break
                    }
                    //ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                    mDecodedBytes!!.rewind()
                    newDecodedBytes!!.put(mDecodedBytes)
                    mDecodedBytes = newDecodedBytes
                    mDecodedBytes!!.position(position)
                }
                mDecodedBytes!!.put(decodedSamples, 0, info.size)
                codec.releaseOutputBuffer(outputBufferIndex, false)
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.outputBuffers
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // We could check that codec.getOutputFormat(), which is the new output format,
                // is what we expect.
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || mDecodedBytes!!.position() / (2 * mChannels) >= expectedNumSamples) {
                // We got all the decoded data from the decoder. Stop here.
                // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                // calls to dequeueOutputBuffer may result in the application crashing, without
                // even an exception being thrown... Hence the second check.
                // (for mono AAC files, the S3 will actually double each sample, as if the stream
                // was stereo. The resulting stream is half what it's supposed to be and with a much
                // lower pitch.)
                break
            }
        }
        mNumSamples = mDecodedBytes!!.position() / (mChannels * 2)  // One sample = 2 bytes.
        mDecodedBytes!!.rewind()
        mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
        mDecodedSamples = mDecodedBytes!!.asShortBuffer()
        mAvgBitRate = (mFileSize * 8 * (mSampleRate.toFloat() / mNumSamples) / 1000).toInt()

        extractor!!.release()
        extractor = null
        codec!!.stop()
        codec.release()
        codec = null

        // Temporary hack to make it work with the old version.
        mNumFrames = mNumSamples / getSamplesPerFrame()
        if (mNumSamples % getSamplesPerFrame() != 0) {
            mNumFrames++
        }
        //mFrameLens = IntArray(mNumFrames)
        //mFrameOffsets = IntArray(mNumFrames)
        var j: Int
        var gain: Double
        var value: Double
        val frameLens = (1000 * mAvgBitRate / 8 * (getSamplesPerFrame().toFloat() / mSampleRate)).toInt()
        i = 0
        while (i < mNumFrames) {
            gain = -1.0
            j = 0
            while (j < getSamplesPerFrame()) {
                value = 0.0
                for (k in 0..mChannels - 1) {
                    if (mDecodedSamples!!.remaining() > 0) {
                        value += Math.abs(mDecodedSamples!!.get().toInt())
                    }
                }
                value /= mChannels
                if (gain < value) {
                    gain = value
                }
                j++
            }
          //  mFrameLens[i] = frameLens  // totally not accurate...
          /*  mFrameOffsets[i] = (i.toFloat() * (1000 * mAvgBitRate / 8).toFloat() * //  = i * frameLens

                    (getSamplesPerFrame().toFloat() / mSampleRate)).toInt()*/
            i++
        }
        mDecodedSamples!!.rewind()
        // DumpSamples();  // Uncomment this line to dump the samples in a TSV file.
    }

    private fun RecordAudio() {
        if (mProgressListener == null) {
            // A progress listener is mandatory here, as it will let us know when to stop recording.
            return
        }
        mInputFile = null
        mFileType = "raw"
        mFileSize = 0
        mSampleRate = 44100
        mChannels = 1  // record mono audio.
        val buffer = ShortArray(1024)  // buffer contains 1 mono frame of 1024 16 bits samples
        var minBufferSize = AudioRecord.getMinBufferSize(
                mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // make sure minBufferSize can contain at least 1 second of audio (16 bits sample).
        if (minBufferSize < mSampleRate * 2) {
            minBufferSize = mSampleRate * 2
        }
        val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                mSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
        )

        // Allocate memory for 20 seconds first. Reallocate later if more is needed.
        mDecodedBytes = ByteBuffer.allocate(20 * mSampleRate * 2)
        mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
        mDecodedSamples = mDecodedBytes!!.asShortBuffer()
        audioRecord.startRecording()
        while (true) {
            // check if mDecodedSamples can contain 1024 additional samples.
            if (mDecodedSamples!!.remaining() < 1024) {
                // Try to allocate memory for 10 additional seconds.
                val newCapacity = mDecodedBytes!!.capacity() + 10 * mSampleRate * 2
                var newDecodedBytes: ByteBuffer? = null
                try {
                    newDecodedBytes = ByteBuffer.allocate(newCapacity)
                } catch (oome: OutOfMemoryError) {
                    break
                }

                val position = mDecodedSamples!!.position()
                mDecodedBytes!!.rewind()
                newDecodedBytes!!.put(mDecodedBytes)
                mDecodedBytes = newDecodedBytes
                mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
                mDecodedBytes!!.rewind()
                mDecodedSamples = mDecodedBytes!!.asShortBuffer()
                mDecodedSamples!!.position(position)
            }
            // TODO(nfaralli): maybe use the read method that takes a direct ByteBuffer argument.
            audioRecord.read(buffer, 0, buffer.size)
            mDecodedSamples!!.put(buffer)
            // Let the progress listener know how many seconds have been recorded.
            // The returned value tells us if we should keep recording or stop.
            if (!mProgressListener!!.reportProgress(
                    (mDecodedSamples!!.position().toFloat() / mSampleRate).toDouble())) {
                break
            }
        }
        audioRecord.stop()
        audioRecord.release()
        mNumSamples = mDecodedSamples!!.position()
        mDecodedSamples!!.rewind()
        mDecodedBytes!!.rewind()
        mAvgBitRate = mSampleRate * 16 / 1000

        // Temporary hack to make it work with the old version.
        mNumFrames = mNumSamples / getSamplesPerFrame()
        if (mNumSamples % getSamplesPerFrame() != 0) {
            mNumFrames++
        }
      //  mFrameLens = null  // not needed for recorded audio
       // mFrameOffsets = null  // not needed for recorded audio
        var i: Int
        var j: Int
        var gain: Int
        var value: Int
        i = 0
        while (i < mNumFrames) {
            gain = -1
            j = 0
            while (j < getSamplesPerFrame()) {
                if (mDecodedSamples!!.remaining() > 0) {
                    value = Math.abs(mDecodedSamples!!.get().toInt())
                } else {
                    value = 0
                }
                if (gain < value) {
                    gain = value
                }
                j++
            }
            i++
        }
        mDecodedSamples!!.rewind()
        // DumpSamples();  // Uncomment this line to dump the samples in a TSV file.
    }

    // should be removed in the near future...
    @Throws(IOException::class)
    fun WriteFile(outputFile: File, startFrame: Int, numFrames: Int) {
        val startTime = startFrame.toFloat() * getSamplesPerFrame() / mSampleRate
        val endTime = (startFrame + numFrames).toFloat() * getSamplesPerFrame() / mSampleRate
        Log.d("startTime", "hi $startTime $endTime")
        WriteFile(outputFile, startTime, endTime)
    }

    @Throws(IOException::class)
    fun WriteFile(outputFile: File, startTime: Float, endTime: Float) {
        val startOffset = (startTime * mSampleRate).toInt() * 2 * mChannels
        var numSamples = ((endTime - startTime) * mSampleRate).toInt()
        // Some devices have problems reading mono AAC files (e.g. Samsung S3). Making it stereo.
        val numChannels = if (mChannels == 1) 2 else mChannels

        val mimeType = "audio/mp4a-latm"
        val bitrate = 64000 * numChannels  // rule of thumb for a good quality: 64kbps per channel.
        var codec: MediaCodec? = MediaCodec.createEncoderByType(mimeType)
        val format = MediaFormat.createAudioFormat(mimeType, mSampleRate, numChannels)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        // Get an estimation of the encoded data based on the bitrate. Add 10% to it.
        var estimatedEncodedSize = ((endTime - startTime).toDouble() * (bitrate / 8).toDouble() * 1.1).toInt()
        var encodedBytes = ByteBuffer.allocate(estimatedEncodedSize)
        val inputBuffers = codec.inputBuffers
        var outputBuffers = codec.outputBuffers
        val info = MediaCodec.BufferInfo()
        var done_reading = false
        var presentation_time: Long = 0

        val frame_size = 1024  // number of samples per frame per channel for an mp4 (AAC) stream.
        var buffer = ByteArray(frame_size * numChannels * 2)  // a sample is coded with a short.
        mDecodedBytes!!.position(startOffset)
        numSamples += 2 * frame_size  // Adding 2 frames, Cf. priming frames for AAC.
        var tot_num_frames = 1 + numSamples / frame_size  // first AAC frame = 2 bytes
        if (numSamples % frame_size != 0) {
            tot_num_frames++
        }
        val frame_sizes = IntArray(tot_num_frames)
        var num_out_frames = 0
        var num_frames = 0
        var num_samples_left = numSamples
        var encodedSamplesSize = 0  // size of the output buffer containing the encoded samples.
        var encodedSamples: ByteArray? = null
        while (true) {
            // Feed the samples to the encoder.
            val inputBufferIndex = codec.dequeueInputBuffer(100)
            if (!done_reading && inputBufferIndex >= 0) {
                if (num_samples_left <= 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    done_reading = true
                } else {
                    inputBuffers[inputBufferIndex].clear()
                    if (buffer.size > inputBuffers[inputBufferIndex].remaining()) {
                        // Input buffer is smaller than one frame. This should never happen.
                        continue
                    }
                    // bufferSize is a hack to create a stereo file from a mono stream.
                    val bufferSize = if (mChannels == 1) buffer.size / 2 else buffer.size
                    if (mDecodedBytes!!.remaining() < bufferSize) {
                        for (i in mDecodedBytes!!.remaining()..bufferSize - 1) {
                            buffer[i] = 0  // pad with extra 0s to make a full frame.
                        }
                        mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
                    } else {
                        mDecodedBytes!!.get(buffer, 0, bufferSize)
                    }
                    if (mChannels == 1) {
                        var i = bufferSize - 1
                        while (i >= 1) {
                            buffer[2 * i + 1] = buffer[i]
                            buffer[2 * i] = buffer[i - 1]
                            buffer[2 * i - 1] = buffer[2 * i + 1]
                            buffer[2 * i - 2] = buffer[2 * i]
                            i -= 2
                        }
                    }
                    num_samples_left -= frame_size
                    inputBuffers[inputBufferIndex].put(buffer)
                    presentation_time = (num_frames++.toDouble() * frame_size.toDouble() * 1e6 / mSampleRate).toLong()
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, buffer.size, presentation_time, 0)
                }
            }

            // Get the encoded samples from the encoder.
            val outputBufferIndex = codec.dequeueOutputBuffer(info, 100)
            if (outputBufferIndex >= 0 && info.size > 0 && info.presentationTimeUs >= 0) {
                if (num_out_frames < frame_sizes.size) {
                    frame_sizes[num_out_frames++] = info.size
                }
                if (encodedSamplesSize < info.size) {
                    encodedSamplesSize = info.size
                    encodedSamples = ByteArray(encodedSamplesSize)
                }
                outputBuffers[outputBufferIndex].get(encodedSamples, 0, info.size)
                outputBuffers[outputBufferIndex].clear()
                codec.releaseOutputBuffer(outputBufferIndex, false)
                if (encodedBytes.remaining() < info.size) {  // Hopefully this should not happen.
                    estimatedEncodedSize = (estimatedEncodedSize * 1.2).toInt()  // Add 20%.
                    val newEncodedBytes = ByteBuffer.allocate(estimatedEncodedSize)
                    val position = encodedBytes.position()
                    encodedBytes.rewind()
                    newEncodedBytes.put(encodedBytes)
                    encodedBytes = newEncodedBytes
                    encodedBytes.position(position)
                }
                encodedBytes.put(encodedSamples, 0, info.size)
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.outputBuffers
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // We could check that codec.getOutputFormat(), which is the new output format,
                // is what we expect.
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                // We got all the encoded data from the encoder.
                break
            }
        }
        val encoded_size = encodedBytes.position()
        encodedBytes.rewind()
        codec.stop()
        codec.release()
        codec = null

        // Write the encoded stream to the file, 4kB at a time.
        buffer = ByteArray(4096)
        try {
            val outputStream = FileOutputStream(outputFile)
            outputStream.write(
                    MP4Header.getMP4Header(mSampleRate, numChannels, frame_sizes, bitrate))
            while (encoded_size - encodedBytes.position() > buffer.size) {
                encodedBytes.get(buffer)
                outputStream.write(buffer)
            }
            val remaining = encoded_size - encodedBytes.position()
            if (remaining > 0) {
                encodedBytes.get(buffer, 0, remaining)
                outputStream.write(buffer, 0, remaining)
            }
            outputStream.close()
        } catch (e: IOException) {
            Log.e("Ringdroid", "Failed to create the .m4a file.")
            Log.e("Ringdroid", getStackTrace(e))
        }

    }

    // Method used to swap the left and right channels (needed for stereo WAV files).
    // buffer contains the PCM data: {sample 1 right, sample 1 left, sample 2 right, etc.}
    // The size of a sample is assumed to be 16 bits (for a single channel).
    // When done, buffer will contain {sample 1 left, sample 1 right, sample 2 left, etc.}
    private fun swapLeftRightChannels(buffer: ByteArray) {
        val left = ByteArray(2)
        val right = ByteArray(2)
        if (buffer.size % 4 != 0) {  // 2 channels, 2 bytes per sample (for one channel).
            // Invalid buffer size.
            return
        }
        var offset = 0
        while (offset < buffer.size) {
            left[0] = buffer[offset]
            left[1] = buffer[offset + 1]
            right[0] = buffer[offset + 2]
            right[1] = buffer[offset + 3]
            buffer[offset] = right[0]
            buffer[offset + 1] = right[1]
            buffer[offset + 2] = left[0]
            buffer[offset + 3] = left[1]
            offset += 4
        }
    }

    // should be removed in the near future...
    @Throws(IOException::class)
    fun WriteWAVFile(outputFile: File, startFrame: Int, numFrames: Int) {
        val startTime = startFrame.toFloat() * getSamplesPerFrame() / mSampleRate
        val endTime = (startFrame + numFrames).toFloat() * getSamplesPerFrame() / mSampleRate
        WriteWAVFile(outputFile, startTime, endTime)
    }

    @Throws(IOException::class)
    fun WriteWAVFile(outputFile: File, startTime: Float, endTime: Float) {
        val startOffset = (startTime * mSampleRate).toInt() * 2 * mChannels
        val numSamples = ((endTime - startTime) * mSampleRate).toInt()

        // Start by writing the RIFF header.
        val outputStream = FileOutputStream(outputFile)
        outputStream.write(WAVHeader.getWAVHeader(mSampleRate, mChannels, numSamples))

        // Write the samples to the file, 1024 at a time.
        val buffer = ByteArray(1024 * mChannels * 2)  // Each sample is coded with a short.
        mDecodedBytes!!.position(startOffset)
        var numBytesLeft = numSamples * mChannels * 2
        while (numBytesLeft >= buffer.size) {
            if (mDecodedBytes!!.remaining() < buffer.size) {
                // This should not happen.
                for (i in mDecodedBytes!!.remaining()..buffer.size - 1) {
                    buffer[i] = 0  // pad with extra 0s to make a full frame.
                }
                mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
            } else {
                mDecodedBytes!!.get(buffer)
            }
            if (mChannels == 2) {
                swapLeftRightChannels(buffer)
            }
            outputStream.write(buffer)
            numBytesLeft -= buffer.size
        }
        if (numBytesLeft > 0) {
            if (mDecodedBytes!!.remaining() < numBytesLeft) {
                // This should not happen.
                for (i in mDecodedBytes!!.remaining()..numBytesLeft - 1) {
                    buffer[i] = 0  // pad with extra 0s to make a full frame.
                }
                mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
            } else {
                mDecodedBytes!!.get(buffer, 0, numBytesLeft)
            }
            if (mChannels == 2) {
                swapLeftRightChannels(buffer)
            }
            outputStream.write(buffer, 0, numBytesLeft)
        }
        outputStream.close()
    }

    // Debugging method dumping all the samples in mDecodedSamples in a TSV file.
    // Each row describes one sample and has the following format:
    // "<presentation time in seconds>\t<channel 1>\t...\t<channel N>\n"
    // File will be written on the SDCard under media/audio/debug/
    // If fileName is null or empty, then the default file name (samples.tsv) is used.
    private fun DumpSamples(fileName: String?) {
        var fileName = fileName
        var externalRootDir = Environment.getExternalStorageDirectory().path
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/"
        }
        var parentDir = externalRootDir + "media/audio/debug/"
        // Create the parent directory
        val parentDirFile = File(parentDir)
        parentDirFile.mkdirs()
        // If we can't write to that special path, try just writing directly to the SDCard.
        if (!parentDirFile.isDirectory) {
            parentDir = externalRootDir
        }
        if (fileName == null || fileName.isEmpty()) {
            fileName = "samples.tsv"
        }
        val outFile = File(parentDir + fileName)

        // Start dumping the samples.
        var writer: BufferedWriter? = null
        var presentationTime = 0f
        mDecodedSamples!!.rewind()
        var row: String
        try {
            writer = BufferedWriter(FileWriter(outFile))
            for (sampleIndex in 0..mNumSamples - 1) {
                presentationTime = sampleIndex.toFloat() / mSampleRate
                row = java.lang.Float.toString(presentationTime)
                for (channelIndex in 0..mChannels - 1) {
                    row += "\t" + mDecodedSamples!!.get()
                }
                row += "\n"
                writer.write(row)
            }
        } catch (e: IOException) {
            Log.w("Ringdroid", "Failed to create the sample TSV file.")
            Log.w("Ringdroid", getStackTrace(e))
        }

        // We are done here. Close the file and rewind the buffer.
        try {
            writer!!.close()
        } catch (e: Exception) {
            Log.w("Ringdroid", "Failed to close sample TSV file.")
            Log.w("Ringdroid", getStackTrace(e))
        }

        mDecodedSamples!!.rewind()
    }

    // Helper method (samples will be dumped in media/audio/debug/samples.tsv).
    private fun DumpSamples() {
        DumpSamples(null)
    }

    // Return the stack trace of a given exception.
    private fun getStackTrace(e: Exception): String {
        val writer = StringWriter()
        e.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}