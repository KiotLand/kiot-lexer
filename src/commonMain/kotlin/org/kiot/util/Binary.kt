@file:Suppress("NOTHING_TO_INLINE")

package org.kiot.util

import kotlin.reflect.KClass

interface Binarizable

abstract class Binarizer<T> {
	abstract fun binarize(bin: Binary, value: T)

	abstract fun debinarize(bin: Binary): T

	fun measure(value: T): Int {
		actualStaticSize.let { if (it != -1) return it }
		return dynamicMeasure(value)
	}

	protected open fun dynamicMeasure(value: T): Int = error("unsupported")

	val hasStaticSize: Boolean
		get() = actualStaticSize != -1

	protected open val actualStaticSize: Int
		get() = -1

	val staticSize: Int
		get() = if (hasStaticSize) actualStaticSize else error("size is not static")
}

class Binary(val array: ByteArray, var index: Int = 0, val endIndex: Int = array.size) {
	companion object {
		val TYPE_MAP = mutableMapOf<KClass<*>, Binarizer<*>>(
			Boolean::class to object : Binarizer<Boolean>() {
				override fun binarize(bin: Binary, value: Boolean) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.boolean()
				override val actualStaticSize: Int
					get() = 1
			},
			Char::class to object : Binarizer<Char>() {
				override fun binarize(bin: Binary, value: Char) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.char()
				override val actualStaticSize: Int
					get() = 2
			},
			Byte::class to object : Binarizer<Byte>() {
				override fun binarize(bin: Binary, value: Byte) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.byte()
				override val actualStaticSize: Int
					get() = 1
			},
			Short::class to object : Binarizer<Short>() {
				override fun binarize(bin: Binary, value: Short) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.short()
				override val actualStaticSize: Int
					get() = 2
			},
			Int::class to object : Binarizer<Int>() {
				override fun binarize(bin: Binary, value: Int) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.int()
				override val actualStaticSize: Int
					get() = 4
			},
			Long::class to object : Binarizer<Long>() {
				override fun binarize(bin: Binary, value: Long) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.long()
				override val actualStaticSize: Int
					get() = 8
			},
			Float::class to object : Binarizer<Float>() {
				override fun binarize(bin: Binary, value: Float) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.float()
				override val actualStaticSize: Int
					get() = 4
			},
			Double::class to object : Binarizer<Double>() {
				override fun binarize(bin: Binary, value: Double) = bin.put(value)
				override fun debinarize(bin: Binary) = bin.double()
				override val actualStaticSize: Int
					get() = 8
			}
		)

		inline fun <reified T : Binarizable> register(binarizer: Binarizer<T>) {
			TYPE_MAP[T::class] = binarizer
		}

		@Suppress("UNCHECKED_CAST")
		inline fun <reified T> binarizer(): Binarizer<T> = TYPE_MAP[T::class] as Binarizer<T>

		inline fun <reified T : Binarizable> measureListSize(list: List<T>): Int = measureListSize(list, binarizer())
		inline fun <reified T> measureListSize(list: List<T>, binarizer: Binarizer<T>): Int =
			4 + (if (binarizer.hasStaticSize) binarizer.staticSize * list.size else list.sumBy { binarizer.measure(it) })


		inline fun <reified T> listBinarizer(): Binarizer<List<T>> = object : Binarizer<List<T>>() {
			val binarizer = binarizer<T>()

			override fun binarize(bin: Binary, value: List<T>) = bin.run {
				put(value.size)
				for (element in value) write(element, binarizer)
			}

			override fun debinarize(bin: Binary): List<T> = Array(bin.int()) { bin.read(binarizer) }.asList()

			override fun dynamicMeasure(value: List<T>): Int = measureListSize(value, binarizer)
		}

		inline fun <reified T : Binarizable> measure(value: T) = binarizer<T>().measure(value)

		inline fun measure(value: BooleanArray) = 4 + value.size
		inline fun measure(value: CharArray) = 4 + value.size * 2
		inline fun measure(value: ByteArray) = 4 + value.size
		inline fun measure(value: ShortArray) = 4 + value.size * 2
		inline fun measure(value: IntArray) = 4 + value.size * 4
		inline fun measure(value: LongArray) = 4 + value.size * 8
		inline fun measure(value: FloatArray) = 4 + value.size * 4
		inline fun measure(value: DoubleArray) = 4 + value.size * 8
	}

	inline fun <R> require(count: Int, block: () -> R): R {
		require(index + count <= endIndex) { "requires $count bytes" }
		return block()
	}

	inline fun <reified T : Binarizable> read(): T = binarizer<T>().debinarize(this)
	fun <T> read(binarizer: Binarizer<T>): T = binarizer.debinarize(this)

	inline fun <reified T : Binarizable> readList() = readList(binarizer<T>())
	inline fun <reified T> readList(binarizer: Binarizer<T>): List<T> = Array(int()) { read(binarizer) }.asList()

	inline fun <reified T : Binarizable> readMutableList() = readMutableList(binarizer<T>())
	inline fun <reified T> readMutableList(binarizer: Binarizer<T>): MutableList<T> =
		MutableList(int()) { read(binarizer) }

	inline fun boolean(): Boolean = byte() != 0.toByte()
	inline fun char(): Char = short().toChar()

	inline fun byte(): Byte = require(1) { array[index++] }
	inline fun short(): Short = require(2) {
		(((byte().toInt() and 0xFF) shl 8) or
				(byte().toInt() and 0xFF)).toShort()
	}

	fun int(): Int = require(4) {
		((byte().toInt() and 0xFF) shl 24) or
				((byte().toInt() and 0xFF) shl 16) or
				((byte().toInt() and 0xFF) shl 8) or
				(byte().toInt() and 0xFF)
	}

	fun long(): Long = require(8) {
		((byte().toLong() and 0xFF) shl 56) or
				((byte().toLong() and 0xFF) shl 48) or
				((byte().toLong() and 0xFF) shl 40) or
				((byte().toLong() and 0xFF) shl 32) or
				((byte().toLong() and 0xFF) shl 24) or
				((byte().toLong() and 0xFF) shl 16) or
				((byte().toLong() and 0xFF) shl 8) or
				(byte().toLong() and 0xFF)
	}

	fun float(): Float = Float.fromBits(int())
	fun double(): Double = Double.fromBits(long())

	inline fun booleanArray(): BooleanArray = BooleanArray(int()) { boolean() }
	inline fun charArray(): CharArray = CharArray(int()) { char() }
	inline fun byteArray(): ByteArray =
		int().let { require(it) { array.copyOfRange(index, index + it).apply { index += it } } }

	inline fun shortArray(): ShortArray = ShortArray(int()) { short() }
	inline fun intArray(): IntArray = IntArray(int()) { int() }
	inline fun longArray(): LongArray = LongArray(int()) { long() }
	inline fun floatArray(): FloatArray = FloatArray(int()) { float() }
	inline fun doubleArray(): DoubleArray = DoubleArray(int()) { double() }

	inline fun <reified T : Binarizable> write(value: T) = binarizer<T>().binarize(this, value)
	fun <T> write(value: T, binarizer: Binarizer<T>) = binarizer.binarize(this, value)

	inline fun <reified T : Binarizable> putList(list: List<T>) = putList(list, binarizer())

	inline fun <reified T> putList(list: List<T>, binarizer: Binarizer<T>) {
		put(list.size)
		for (element in list) write(element, binarizer)
	}

	inline fun put(value: Boolean) = require(1) {
		put(if (value) 1.toByte() else 0.toByte())
	}

	inline fun put(value: Char) = put(value.toShort())

	inline fun put(value: Byte) = require(1) { array[index++] = value }
	inline fun put(value: Short) = require(2) {
		val int = value.toInt()
		put((int ushr 8).toByte())
		put(int.toByte())
	}

	fun put(value: Int) = require(4) {
		put((value ushr 24).toByte())
		put((value ushr 16).toByte())
		put((value ushr 8).toByte())
		put(value.toByte())
	}

	fun put(value: Long) = require(8) {
		put((value ushr 56).toByte())
		put((value ushr 48).toByte())
		put((value ushr 40).toByte())
		put((value ushr 32).toByte())
		put((value ushr 24).toByte())
		put((value ushr 16).toByte())
		put((value ushr 8).toByte())
		put(value.toByte())
	}

	fun put(value: Float) = put(value.toRawBits())
	fun put(value: Double) = put(value.toRawBits())

	fun put(value: BooleanArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: CharArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: ByteArray) {
		put(value.size)
		value.copyInto(array, index)
		index += value.size
	}

	fun put(value: ShortArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: IntArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: LongArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: FloatArray) {
		put(value.size)
		for (v in value) put(v)
	}

	fun put(value: DoubleArray) {
		put(value.size)
		for (v in value) put(v)
	}
}

inline fun ByteArray.asBinary(index: Int = 0, endIndex: Int = size) = Binary(this, index, endIndex)

inline fun <reified T : Binarizable> T.binarize(): ByteArray = binarize(Binary.binarizer())

inline fun <reified T> T.binarize(binarizer: Binarizer<T>): ByteArray {
	return ByteArray(binarizer.measure(this)).also { binarizer.binarize(it.asBinary(), this) }
}

inline fun <reified T : Binarizable> ByteArray.debinarize(): T = asBinary().read()
inline fun <reified T> ByteArray.debinarize(binarizer: Binarizer<T>): T = asBinary().read(binarizer)

inline fun <reified T : Binarizable> T.measureSize(): Int = Binary.binarizer<T>().measure(this)
inline fun <reified T> T.measureSize(binarizer: Binarizer<T>): Int = binarizer.measure(this)
