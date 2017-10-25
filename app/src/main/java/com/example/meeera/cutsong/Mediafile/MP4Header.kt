package com.example.meeera.cutsong.Mediafile

import kotlin.experimental.or

/**
 * Created by meeera on 24/10/17.
 */

class Atom {  // note: latest versions of spec simply call it 'box' instead of 'atom'.
    // get the size of the this atom.
    var size: Int = 0
        private set  // includes atom header (8 bytes)
    var typeInt: Int = 0
        private set
    private var mData: ByteArray? = null  // an atom can either contain data or children, but not both.
    private var mChildren: Array<Atom?>? = null
    private var mVersion: Byte = 0  // if negative, then the atom does not contain version and flags data.
    private var mFlags: Int = 0

    // create an empty atom of the given type.
    constructor(type: String) {
        size = 8
        typeInt = getTypeInt(type)
        mData = null
        mChildren = null
        mVersion = -1
        mFlags = 0
    }

    // create an empty atom of type type, with a given version and flags.
    constructor(type: String, version: Byte, flags: Int) {
        size = 12
        typeInt = getTypeInt(type)
        mData = null
        mChildren = null
        mVersion = version
        mFlags = flags
    }

    // set the size field of the atom based on its content.
    private fun setSize() {
        var size = 8  // type + size
        if (mVersion >= 0) {
            size += 4 // version + flags
        }
        if (mData != null) {
            size += mData!!.size
        } else if (mChildren != null) {
            for (child in mChildren!!) {
                size += child!!.size
            }
        }
        this.size = size
    }

    private fun getTypeInt(type_str: String): Int {
        var type = 0
        type = type or ((type_str[0].toInt()) shl 24)
        type = type or (type_str[1].toInt() shl 16)
        type = type or (type_str[2].toInt() shl 8)
        type = type or type_str[3].toByte().toInt()
        return type
    }

    val typeStr: String
        get() {
            var type = ""
            type += (typeInt shr 24 and 0xFF).toByte().toChar()
            type += (typeInt shr 16 and 0xFF).toByte().toChar()
            type += (typeInt shr 8 and 0xFF).toByte().toChar()
            type += (typeInt and 0xFF).toByte().toChar()
            return type
        }

    fun setData(data: ByteArray?): Boolean {
        if (mChildren != null || data == null) {
            // TODO(nfaralli): log something here
            return false
        }
        mData = data
        setSize()
        return true
    }

    fun getData(): ByteArray? {
        return mData
    }

    fun addChild(child: Atom?): Boolean {
        if (mData != null || child == null) {
            // TODO(nfaralli): log something here
            return false
        }
        var numChildren = 1
        if (mChildren != null) {
            numChildren += mChildren!!.size
        }
        val children = arrayOfNulls<Atom>(numChildren)
        if (mChildren != null) {
            System.arraycopy(mChildren!!, 0, children, 0, mChildren!!.size)
        }
        children[numChildren - 1] = child
        mChildren = children
        setSize()
        return true
    }

    // return the child atom of the corresponding type.
    // type can contain grand children: e.g. type = "trak.mdia.minf"
    // return null if the atom does not contain such a child.
    fun getChild(type: String): Atom? {
        if (mChildren == null) {
            return null
        }
        val types = type.split("\\.".toRegex(), 2).toTypedArray()
        for (child in mChildren!!) {
            if (child!!.typeStr == types[0]) {
                return if (types.size == 1) {
                    child
                } else {
                    child.getChild(types[1])
                }
            }
        }
        return null
    }

    // return a byte array containing the full content of the atom (including header)
    val bytes: ByteArray
        get() {
            val atom_bytes = ByteArray(size)
            var offset = 0

            atom_bytes[offset++] = (size shr 24 and 0xFF).toByte()
            atom_bytes[offset++] = (size shr 16 and 0xFF).toByte()
            atom_bytes[offset++] = (size shr 8 and 0xFF).toByte()
            atom_bytes[offset++] = (size and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 24 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 16 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 8 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt and 0xFF).toByte()
            if (mVersion >= 0) {
                atom_bytes[offset++] = mVersion
                atom_bytes[offset++] = (mFlags shr 16 and 0xFF).toByte()
                atom_bytes[offset++] = (mFlags shr 8 and 0xFF).toByte()
                atom_bytes[offset++] = (mFlags and 0xFF).toByte()
            }
            if (mData != null) {
                System.arraycopy(mData!!, 0, atom_bytes, offset, mData!!.size)
            } else if (mChildren != null) {
                var child_bytes: ByteArray
                for (child in mChildren!!) {
                    child_bytes = child!!.bytes
                    System.arraycopy(child_bytes, 0, atom_bytes, offset, child_bytes.size)
                    offset += child_bytes.size
                }
            }
            return atom_bytes
        }

    // Used for debugging purpose only.
    override fun toString(): String {
        var str = ""
        val atom_bytes = bytes

        for (i in atom_bytes.indices) {
            if (i % 8 == 0 && i > 0) {
                str += '\n'
            }
            str += String.format("0x%02X", atom_bytes[i])
            if (i < atom_bytes.size - 1) {
                str += ','
                if (i % 8 < 7) {
                    str += ' '
                }
            }
        }
        str += '\n'
        return str
    }
}


class MP4Header {
    private var mFrameSize: IntArray? = null   // size of each AAC frames, in bytes. First one should be 2.
    private var mMaxFrameSize: Int = 0   // size of the biggest frame.
    private var mTotSize: Int = 0        // size of the AAC stream.
    private var mBitrate: Int = 0       // bitrate used to encode the AAC stream.
    private var mTime: ByteArray = kotlin.ByteArray(4)       // time used for 'creation time' and 'modification time' fields.
    lateinit var mDurationMS: ByteArray // duration of stream in milliseconds.
    lateinit var mNumSamples: ByteArray  // number of samples in the stream.
    private var mHeader: ByteArray? = null      // the complete header.
    private var mSampleRate: Int = 0    // sampling frequency in Hz (e.g. 44100).
    private var mChannels: Int = 0       // number of channels.

    // Creates a new MP4Header object that should be used to generate an .m4a file header.
    constructor(sampleRate: Int, numChannels: Int, frame_size: IntArray?, bitrate: Int) {
        if (frame_size == null || frame_size.size < 2 || frame_size[0] != 2) {
            //TODO(nfaralli): log something here
            return
        }
        mSampleRate = sampleRate
        mChannels = numChannels
        mFrameSize = frame_size
        mBitrate = bitrate
        mMaxFrameSize = mFrameSize!![0]
        mTotSize = mFrameSize!![0]
        for (i in 1..mFrameSize!!.size - 1) {
            if (mMaxFrameSize < mFrameSize!![i]) {
                mMaxFrameSize = mFrameSize!![i]
            }
            mTotSize += mFrameSize!![i]
        }
        var time = System.currentTimeMillis() / 1000
        time += ((66 * 365 + 16) * 24 * 60 * 60).toLong()  // number of seconds between 1904 and 1970
        mTime[0] = (time shr 24 and 0xFF).toByte()
        mTime[1] = (time shr 16 and 0xFF).toByte()
        mTime[2] = (time shr 8 and 0xFF).toByte()
        mTime[3] = (time and 0xFF).toByte()
        val numSamples = 1024 * (frame_size.size - 1)  // 1st frame does not contain samples.
        var durationMS = numSamples * 1000 / mSampleRate
        if (numSamples * 1000 % mSampleRate > 0) {  // round the duration up.
            durationMS++
        }
        mNumSamples = byteArrayOf((numSamples shr 26 and 0XFF).toByte(), (numSamples shr 16 and 0XFF).toByte(), (numSamples shr 8 and 0XFF).toByte(), (numSamples and 0XFF).toByte())
        mDurationMS = byteArrayOf((durationMS shr 26 and 0XFF).toByte(), (durationMS shr 16 and 0XFF).toByte(), (durationMS shr 8 and 0XFF).toByte(), (durationMS and 0XFF).toByte())
        setHeader()
    }

    fun getMP4Header(): ByteArray? {
        return mHeader
    }

    companion object {
        fun getMP4Header(
                sampleRate: Int, numChannels: Int, frame_size: IntArray, bitrate: Int): ByteArray? {
            return MP4Header(sampleRate, numChannels, frame_size, bitrate).mHeader
        }
    }

    override fun toString(): String {
        var str = ""
        if (mHeader == null) {
            return str
        }
        val num_32bits_per_lines = 8
        var count = 0
        for (b in mHeader!!) {
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
        // create the atoms needed to build the header.
        val a_ftyp : Atom = getFTYPAtom()
        val a_moov = getMOOVAtom()
        val a_mdat = Atom("mdat")  // create an empty atom. The AAC stream data should follow
        // immediately after. The correct size will be set later.

        // set the correct chunk offset in the stco atom.
        val a_stco = a_moov.getChild("trak.mdia.minf.stbl.stco")
        if (a_stco == null) {
            mHeader = null
            return
        }
        val data = a_stco.getData()
        val chunk_offset = a_ftyp.size + a_moov.size + a_mdat.size
        var offset = data!!.size - 4  // here stco should contain only one chunk offset.
        data[offset++] = (chunk_offset shr 24 and 0xFF).toByte()
        data[offset++] = (chunk_offset shr 16 and 0xFF).toByte()
        data[offset++] = (chunk_offset shr 8 and 0xFF).toByte()
        data[offset++] = (chunk_offset and 0xFF).toByte()

        // create the header byte array based on the previous atoms.
        val header = ByteArray(chunk_offset)  // here chunk_offset is also the size of the header
        offset = 0
        for (atom in arrayOf(a_ftyp, a_moov, a_mdat)) {
            val atom_bytes = atom.bytes
            System.arraycopy(atom_bytes, 0, header, offset, atom_bytes.size)
            offset += atom_bytes.size
        }

        //set the correct size of the mdat atom
        val size = 8 + mTotSize
        offset -= 8
        header[offset++] = (size shr 24 and 0xFF).toByte()
        header[offset++] = (size shr 16 and 0xFF).toByte()
        header[offset++] = (size shr 8 and 0xFF).toByte()
        header[offset++] = (size and 0xFF).toByte()

        mHeader = header
    }

    private fun getFTYPAtom(): Atom {
        val atom = Atom("ftyp")
        atom.setData(byteArrayOf('M'.toByte(), '4'.toByte(), 'A'.toByte(), ' '.toByte(), // Major brand
                0, 0, 0, 0, // Minor version
                'M'.toByte(), '4'.toByte(), 'A'.toByte(), ' '.toByte(), // compatible brands
                'm'.toByte(), 'p'.toByte(), '4'.toByte(), '2'.toByte(), 'i'.toByte(), 's'.toByte(), 'o'.toByte(), 'm'.toByte()))
        return atom
    }

    private fun getMOOVAtom(): Atom {
        val atom = Atom("moov")
        atom.addChild(getMVHDAtom())
        atom.addChild(getTRAKAtom())
        return atom
    }

    private fun getMVHDAtom(): Atom {
        val atom = Atom("mvhd", 0.toByte(), 0)
        atom.setData(byteArrayOf(mTime[0], mTime[1], mTime[2], mTime[3], // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3], // modification time.
                0, 0, 0x03, 0xE8.toByte(), // timescale = 1000 => duration expressed in ms.
                mDurationMS[0], mDurationMS[1], mDurationMS[2], mDurationMS[3], // duration in ms.
                0, 1, 0, 0, // rate = 1.0
                1, 0, // volume = 1.0
                0, 0, // reserved
                0, 0, 0, 0, // reserved
                0, 0, 0, 0, // reserved
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // unity matrix
                0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0, 0, 0, 0, 0, // pre-defined
                0, 0, 0, 0, // pre-defined
                0, 0, 0, 0, // pre-defined
                0, 0, 0, 0, // pre-defined
                0, 0, 0, 0, // pre-defined
                0, 0, 0, 0, // pre-defined
                0, 0, 0, 2   // next track ID
        ))
        return atom
    }

    private fun getTRAKAtom(): Atom {
        val atom = Atom("trak")
        atom.addChild(getTKHDAtom())
        atom.addChild(getMDIAAtom())
        return atom
    }

    private fun getTKHDAtom(): Atom {
        val atom = Atom("tkhd", 0.toByte(), 0x07)  // track enabled, in movie, and in preview.
        atom.setData(byteArrayOf(mTime[0], mTime[1], mTime[2], mTime[3], // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3], // modification time.
                0, 0, 0, 1, // track ID
                0, 0, 0, 0, // reserved
                mDurationMS[0], mDurationMS[1], mDurationMS[2], mDurationMS[3], // duration in ms.
                0, 0, 0, 0, // reserved
                0, 0, 0, 0, // reserved
                0, 0, // layer
                0, 0, // alternate group
                1, 0, // volume = 1.0
                0, 0, // reserved
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // unity matrix
                0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0, 0, 0, 0, 0, // width
                0, 0, 0, 0   // height
        ))
        return atom
    }

    private fun getMDIAAtom(): Atom {
        val atom = Atom("mdia")
        atom.addChild(getMDHDAtom())
        atom.addChild(getHDLRAtom())
        atom.addChild(getMINFAtom())
        return atom
    }

    private fun getMDHDAtom(): Atom {
        val atom = Atom("mdhd", 0.toByte(), 0)
        atom.setData(byteArrayOf(mTime[0], mTime[1], mTime[2], mTime[3], // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3], // modification time.
                (mSampleRate shr 24).toByte(), (mSampleRate shr 16).toByte(), // timescale = Fs =>
                (mSampleRate shr 8).toByte(), mSampleRate.toByte(), // duration expressed in samples.
                mNumSamples[0], mNumSamples[1], mNumSamples[2], mNumSamples[3], // duration
                0, 0, // languages
                0, 0      // pre-defined
        ))
        return atom
    }

    private fun getHDLRAtom(): Atom {
        val atom = Atom("hdlr", 0.toByte(), 0)
        atom.setData(byteArrayOf(0, 0, 0, 0, // pre-defined
                's'.toByte(), 'o'.toByte(), 'u'.toByte(), 'n'.toByte(), // handler type
                0, 0, 0, 0, // reserved
                0, 0, 0, 0, // reserved
                0, 0, 0, 0, // reserved
                'S'.toByte(), 'o'.toByte(), 'u'.toByte(), 'n'.toByte(), // name (used only for debugging and inspection purposes).
                'd'.toByte(), 'H'.toByte(), 'a'.toByte(), 'n'.toByte(), 'd'.toByte(), 'l'.toByte(), 'e'.toByte(), '\u0000'.toByte()))
        return atom
    }

    private fun getMINFAtom(): Atom {
        val atom = Atom("minf")
        atom.addChild(getSMHDAtom())
        atom.addChild(getDINFAtom())
        atom.addChild(getSTBLAtom())
        return atom
    }

    private fun getSMHDAtom(): Atom {
        val atom = Atom("smhd", 0.toByte(), 0)
        atom.setData(byteArrayOf(0, 0, // balance (center)
                0, 0      // reserved
        ))
        return atom
    }

    private fun getDINFAtom(): Atom {
        val atom = Atom("dinf")
        atom.addChild(getDREFAtom())
        return atom
    }

    private fun getDREFAtom(): Atom {
        val atom = Atom("dref", 0.toByte(), 0)
        val url = getURLAtom().bytes
        val data = ByteArray(4 + url.size)
        data[3] = 0x01  // entry count = 1
        System.arraycopy(url, 0, data, 4, url.size)
        atom.setData(data)
        return atom
    }

    private fun getURLAtom(): Atom {
        return Atom("url ", 0.toByte(), 0x01)
    }

    private fun getSTBLAtom(): Atom {
        val atom = Atom("stbl")
        atom.addChild(getSTSDAtom())
        atom.addChild(getSTTSAtom())
        atom.addChild(getSTSCAtom())
        atom.addChild(getSTSZAtom())
        atom.addChild(getSTCOAtom())
        return atom
    }

    private fun getSTSDAtom(): Atom {
        val atom = Atom("stsd", 0.toByte(), 0)
        val mp4a = getMP4AAtom().bytes
        val data = ByteArray(4 + mp4a.size)
        data[3] = 0x01  // entry count = 1
        System.arraycopy(mp4a, 0, data, 4, mp4a.size)
        atom.setData(data)
        return atom
    }

    // See also Part 14 section 5.6.1 of ISO/IEC 14496 for this atom.
    private fun getMP4AAtom(): Atom {
        val atom = Atom("mp4a")
        val ase = byteArrayOf(// Audio Sample Entry data
                0, 0, 0, 0, 0, 0, // reserved
                0, 1, // data reference index
                0, 0, 0, 0, // reserved
                0, 0, 0, 0, // reserved
                (mChannels shr 8).toByte(), mChannels.toByte(), // channel count
                0, 0x10, // sample size
                0, 0, // pre-defined
                0, 0, // reserved
                (mSampleRate shr 8).toByte(), mSampleRate.toByte(), 0, 0)// sample rate
        val esds = getESDSAtom().bytes
        val data = ByteArray(ase.size + esds.size)
        System.arraycopy(ase, 0, data, 0, ase.size)
        System.arraycopy(esds, 0, data, ase.size, esds.size)
        atom.setData(data)
        return atom
    }

    private fun getESDSAtom(): Atom {
        val atom = Atom("esds", 0.toByte(), 0)
        atom.setData(getESDescriptor())
        return atom
    }

    // Returns an ES Descriptor for an ISO/IEC 14496-3 audio stream, AAC LC, 44100Hz, 2 channels,
    // 1024 samples per frame per channel. The decoder buffer size is set so that it can contain at
    // least 2 frames. (See section 7.2.6.5 of ISO/IEC 14496-1 for more details).
    private fun getESDescriptor(): ByteArray {
        val samplingFrequencies = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
        // First 5 bytes of the ES Descriptor.
        val ESDescriptor_top = byteArrayOf(0x03, 0x19, 0x00, 0x00, 0x00)
        // First 4 bytes of Decoder Configuration Descriptor. Audio ISO/IEC 14496-3, AudioStream.
        val decConfigDescr_top = byteArrayOf(0x04, 0x11, 0x40, 0x15)
        // Audio Specific Configuration: AAC LC, 1024 samples/frame/channel.
        // Sampling frequency and channels configuration are not set yet.
        val audioSpecificConfig = byteArrayOf(0x05, 0x02, 0x10, 0x00)
        val slConfigDescr = byteArrayOf(0x06, 0x01, 0x02)  // specific for MP4 file.
        var offset: Int
        var bufferSize = 0x300
        while (bufferSize < 2 * mMaxFrameSize) {
            // TODO(nfaralli): what should be the minimum size of the decoder buffer?
            // Should it be a multiple of 256?
            bufferSize += 0x100
        }

        // create the Decoder Configuration Descriptor
        val decConfigDescr = ByteArray(2 + decConfigDescr_top[1])
        System.arraycopy(decConfigDescr_top, 0, decConfigDescr, 0, decConfigDescr_top.size)
        offset = decConfigDescr_top.size
        decConfigDescr[offset++] = (bufferSize shr 16 and 0xFF).toByte()
        decConfigDescr[offset++] = (bufferSize shr 8 and 0xFF).toByte()
        decConfigDescr[offset++] = (bufferSize and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 24 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 16 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 8 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 24 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 16 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate shr 8 and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate and 0xFF).toByte()
        var index: Int
        index = 0
        while (index < samplingFrequencies.size) {
            if (samplingFrequencies[index] == mSampleRate) {
                break
            }
            index++
        }
        if (index == samplingFrequencies.size) {
            // TODO(nfaralli): log something here.
            // Invalid sampling frequency. Default to 44100Hz...
            index = 4
        }
        audioSpecificConfig[2] = audioSpecificConfig[2] or (index shr 1 and 0x07).toByte()
        audioSpecificConfig[3] = audioSpecificConfig[3] or (index and 1 shl 7 or (mChannels and 0x0F shl 3)).toByte()
        System.arraycopy(
                audioSpecificConfig, 0, decConfigDescr, offset, audioSpecificConfig.size)

        // create the ES Descriptor
        val ESDescriptor = ByteArray(2 + ESDescriptor_top[1])
        System.arraycopy(ESDescriptor_top, 0, ESDescriptor, 0, ESDescriptor_top.size)
        offset = ESDescriptor_top.size
        System.arraycopy(decConfigDescr, 0, ESDescriptor, offset, decConfigDescr.size)
        offset += decConfigDescr.size
        System.arraycopy(slConfigDescr, 0, ESDescriptor, offset, slConfigDescr.size)
        return ESDescriptor
    }

    private fun getSTTSAtom(): Atom {
        val atom = Atom("stts", 0.toByte(), 0)
        val numAudioFrames = mFrameSize!!.size - 1
        atom.setData(byteArrayOf(0, 0, 0, 0x02, // entry count
                0, 0, 0, 0x01, // first frame contains no audio
                0, 0, 0, 0, (numAudioFrames shr 24 and 0xFF).toByte(), (numAudioFrames shr 16 and 0xFF).toByte(), (numAudioFrames shr 8 and 0xFF).toByte(), (numAudioFrames and 0xFF).toByte(), 0, 0, 0x04, 0)// delay between frames = 1024 samples (cf. timescale = Fs)
        )
        return atom
    }

    private fun getSTSCAtom(): Atom {
        val atom = Atom("stsc", 0.toByte(), 0)
        val numFrames = mFrameSize!!.size
        atom.setData(byteArrayOf(0, 0, 0, 0x01, // entry count
                0, 0, 0, 0x01, // first chunk
                (numFrames shr 24 and 0xFF).toByte(), (numFrames shr 16 and 0xFF).toByte(), // samples per
                (numFrames shr 8 and 0xFF).toByte(), (numFrames and 0xFF).toByte(), // chunk
                0, 0, 0, 0x01)// sample description index
        )
        return atom
    }

    private fun getSTSZAtom(): Atom {
        val atom = Atom("stsz", 0.toByte(), 0)
        val numFrames = mFrameSize!!.size
        val data = ByteArray(8 + 4 * numFrames)
        var offset = 0
        data[offset++] = 0  // sample size (=0 => each frame can have a different size)
        data[offset++] = 0
        data[offset++] = 0
        data[offset++] = 0
        data[offset++] = (numFrames shr 24 and 0xFF).toByte()  // sample count
        data[offset++] = (numFrames shr 16 and 0xFF).toByte()
        data[offset++] = (numFrames shr 8 and 0xFF).toByte()
        data[offset++] = (numFrames and 0xFF).toByte()
        for (size in mFrameSize!!) {
            data[offset++] = (size shr 24 and 0xFF).toByte()
            data[offset++] = (size shr 16 and 0xFF).toByte()
            data[offset++] = (size shr 8 and 0xFF).toByte()
            data[offset++] = (size and 0xFF).toByte()
        }
        atom.setData(data)
        return atom
    }

    private fun getSTCOAtom(): Atom {
        val atom = Atom("stco", 0.toByte(), 0)
        atom.setData(byteArrayOf(0, 0, 0, 0x01, // entry count
                0, 0, 0, 0  // chunk offset. Set to 0 here. Must be set later. Here it should be
        )// the size of the complete header, as the AAC stream will follow
                // immediately.
        )
        return atom
    }
}