@file:Suppress("NOTHING_TO_INLINE")

package org.kiot.util

/**
 * An implementation of [MutableList] of primitive types, reducing package wrapping
 * and unwrapping in general [MutableList] obtained from [mutableListOf].
 *
 * Most of the code is copied from ArrayList in Java XD
 *
 * @author Mivik
 */
abstract class PrimitiveList<T> : MutableList<T> {
	companion object {
		private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8
	}

	override var size: Int = 0
		protected set

	protected abstract val elementsSize: Int
	protected abstract fun extendCapacity(newCapacity: Int)
	abstract fun moveElements(fromIndex: Int, toIndex: Int, count: Int)

	protected inline fun ensureIndex(index: Int) {
		if (index < 0 || index >= size) throw IndexOutOfBoundsException()
	}

	protected inline fun ensureCursor(cursor: Int) {
		if (cursor < 0 || cursor > size) throw IndexOutOfBoundsException()
	}

	private inline fun ensureRange(fromIndex: Int, toIndex: Int) {
		ensureIndex(fromIndex)
		ensureCursor(toIndex)
		if (fromIndex > toIndex) error("fromIndex > toIndex")
	}

	fun resize(size: Int) {
		ensureCapacity(size)
		this.size = size
	}

	protected fun ensureCapacity(minCapacity: Int) {
		val oldCapacity: Int = elementsSize
		if (minCapacity<=oldCapacity) return
		var newCapacity = oldCapacity + (oldCapacity shr 1)
		if (newCapacity <= minCapacity) newCapacity = minCapacity
		if (newCapacity > MAX_ARRAY_SIZE) {
			if (minCapacity < 0) error("out of memory")
			newCapacity = if (minCapacity > MAX_ARRAY_SIZE) Int.MAX_VALUE else MAX_ARRAY_SIZE
		}
		try {
			extendCapacity(newCapacity)
		} catch (t: Throwable) {
			println("Crashes! $newCapacity")
			throw RuntimeException()
		}
	}

	override fun contains(element: T): Boolean =
		indexOf(element) != -1

	override fun containsAll(elements: Collection<T>): Boolean =
		elements.all { contains(it) }

	override fun isEmpty(): Boolean = size == 0

	override fun iterator(): MutableIterator<T> = Iterator()

	override fun listIterator(): MutableListIterator<T> = Iterator()

	override fun listIterator(index: Int): MutableListIterator<T> = Iterator()

	@Suppress("MemberVisibilityCanBePrivate")
	inline fun removeIf(predict: (T) -> Boolean): Boolean {
		var lst = -1
		var i = 0
		var removed = false
		while (i < size) {
			if (predict(this[i])) {
				if (lst == -1) lst = i
			} else if (lst != -1) {
				removed = true
				removeRange(lst, i)
				i = lst
				lst = -1
			}
			++i
		}
		if (lst != -1) {
			removed = true
			removeRange(lst, size)
		}
		return removed
	}

	override fun add(element: T): Boolean {
		ensureCapacity(size + 1)
		set(size++, element)
		return true
	}

	override fun add(index: Int, element: T) {
		ensureCursor(index)
		ensureCapacity(size + 1)
		moveElements(index, index + 1, size)
		this[index] = element
		++size
	}

	override fun clear() {
		size = 0
	}

	override fun addAll(elements: Collection<T>): Boolean =
		addAll(size, elements)

	private fun fastRemove(index: Int) {
		moveElements(index + 1, index, size)
		--size
	}

	fun removeRange(fromIndex: Int, toIndex: Int) {
		ensureRange(fromIndex, toIndex)
		moveElements(toIndex, fromIndex, size)
		size -= toIndex - fromIndex
	}

	override fun removeAll(elements: Collection<T>): Boolean =
		removeIf { it in elements }

	override fun removeAt(index: Int): T {
		ensureIndex(index)
		return this[index].also { fastRemove(index) }
	}

	// I really don't want to implement this...
	override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = error("not supported")

	override fun retainAll(elements: Collection<T>): Boolean =
		removeIf { it !in elements }

	override fun remove(element: T): Boolean {
		val index = indexOf(element)
		if (index != -1) {
			fastRemove(index)
			return true
		}
		return false
	}

	override fun toString(): String {
		if (isEmpty()) return "[]"
		return buildString {
			append('[')
			append(this@PrimitiveList[0])
			for (i in 1 until size) {
				append(", ")
				append(this@PrimitiveList[i])
			}
			append(']')
		}
	}

	inner class Iterator(private var cursor: Int = 0, private var lastRet: Int = -1) : MutableListIterator<T> {
		override fun hasPrevious(): Boolean = cursor != 0
		override fun previousIndex(): Int = cursor - 1
		override fun previous(): T {
			(cursor - 1).let {
				cursor = it
				lastRet = it
				return get(it)
			}
		}

		override fun hasNext(): Boolean = cursor != size
		override fun nextIndex(): Int = cursor
		override fun next(): T {
			cursor.let {
				++cursor
				lastRet = it
				return get(it)
			}
		}

		override fun set(element: T) {
			require(lastRet >= 0)
			set(lastRet, element)
		}

		override fun add(element: T) {
			add(cursor, element)
			++cursor
			lastRet = -1
		}

		override fun remove() {
			require(lastRet >= 0)
			removeAt(lastRet)
			cursor = lastRet
			lastRet = -1
		}
	}
}

class IntList(initialCapacity: Int = 0) : PrimitiveList<Int>() {
	companion object {
		private val EMPTY_DATA = intArrayOf()
	}

	var elements = EMPTY_DATA
		private set

	override val elementsSize: Int
		get() = elements.size

	override fun extendCapacity(newCapacity: Int) {
		if (newCapacity >= 1000000) {
			println("wtf? size is $size but you let me extend to $newCapacity")
			throw Throwable()
		}
		elements = elements.copyOf(newCapacity)
	}

	override fun moveElements(fromIndex: Int, toIndex: Int, count: Int) {
		elements.copyInto(elements, toIndex, fromIndex, count)
	}

	init {
		elements = when {
			initialCapacity > 0 -> IntArray(initialCapacity)
			initialCapacity == 0 -> EMPTY_DATA
			else -> error("Illegal capacity: $initialCapacity")
		}
	}

	fun copy(): IntList = IntList(size).also { it.addAll(this) }

	override fun get(index: Int): Int {
		ensureIndex(index)
		return elements[index]
	}

	override fun set(index: Int, element: Int): Int {
		ensureIndex(index)
		return elements[index].also { elements[index] = element }
	}

	override fun indexOf(element: Int): Int = elements.indexOf(element)

	override fun lastIndexOf(element: Int): Int =
		elements.lastIndexOf(element)

	override fun addAll(index: Int, elements: Collection<Int>): Boolean {
		ensureCursor(index)
		val arr = elements.toIntArray()
		ensureCapacity(size + arr.size)
		this.elements.copyInto(this.elements, index + arr.size, index, size)
		arr.copyInto(this.elements, index)
		size += arr.size
		return arr.isNotEmpty()
	}

	override fun hashCode(): Int {
		var hashCode = 1
		for (i in this) hashCode = 31 * hashCode + i
		return hashCode
	}

	override fun equals(other: Any?): Boolean {
		if (other !is IntList) return false
		if (size != other.size) return false
		for (i in indices) if (elements[i] != other.elements[i]) return false
		return true
	}
}

inline fun intListOf(vararg elements: Int): IntList =
	IntList(elements.size).apply { addAll(elements.asList()) }

inline fun emptyIntList() = IntList()

class BooleanList(initialCapacity: Int = 0) : PrimitiveList<Boolean>() {
	companion object {
		private val EMPTY_DATA = booleanArrayOf()
	}

	private var elements = EMPTY_DATA

	override val elementsSize: Int
		get() = elements.size

	override fun extendCapacity(newCapacity: Int) {
		elements = elements.copyOf(newCapacity)
	}

	override fun moveElements(fromIndex: Int, toIndex: Int, count: Int) {
		elements.copyInto(elements, toIndex, fromIndex, count)
	}

	init {
		elements = when {
			initialCapacity > 0 -> BooleanArray(initialCapacity)
			initialCapacity == 0 -> EMPTY_DATA
			else -> error("Illegal capacity: $initialCapacity")
		}
	}

	fun copy(): BooleanList = BooleanList(size).also { it.addAll(this) }

	override fun get(index: Int): Boolean {
		ensureIndex(index)
		return elements[index]
	}

	override fun set(index: Int, element: Boolean): Boolean {
		ensureIndex(index)
		return elements[index].also { elements[index] = element }
	}

	override fun indexOf(element: Boolean): Int = elements.indexOf(element)

	override fun lastIndexOf(element: Boolean): Int =
		elements.lastIndexOf(element)

	override fun addAll(index: Int, elements: Collection<Boolean>): Boolean {
		ensureCursor(index)
		val arr = elements.toBooleanArray()
		ensureCapacity(size + arr.size)
		this.elements.copyInto(this.elements, index + arr.size, index, size)
		arr.copyInto(this.elements, index)
		size += arr.size
		return arr.isNotEmpty()
	}

	override fun hashCode(): Int {
		var hashCode = 1
		for (i in this) hashCode = 31 * hashCode + (if (i) 1 else 0)
		return hashCode
	}

	override fun equals(other: Any?): Boolean {
		if (other !is BooleanList) return false
		if (size != other.size) return false
		for (i in indices) if (elements[i] != other.elements[i]) return false
		return true
	}

	fun toBitSet(): BitSet =
		BitSet(size).also {
			for (i in indices) if (this[i]) it.set(i)
		}
}

inline fun booleanListOf(vararg elements: Boolean): BooleanList =
	BooleanList(elements.size).apply { addAll(elements.asList()) }

inline fun emptyBooleanList() = BooleanList()

class CharList(initialCapacity: Int = 0) : PrimitiveList<Char>() {
	companion object {
		private val EMPTY_DATA = charArrayOf()
	}

	private var elements = EMPTY_DATA

	override val elementsSize: Int
		get() = elements.size

	override fun extendCapacity(newCapacity: Int) {
		elements = elements.copyOf(newCapacity)
	}

	override fun moveElements(fromIndex: Int, toIndex: Int, count: Int) {
		elements.copyInto(elements, toIndex, fromIndex, count)
	}

	init {
		elements = when {
			initialCapacity > 0 -> CharArray(initialCapacity)
			initialCapacity == 0 -> EMPTY_DATA
			else -> error("Illegal capacity: $initialCapacity")
		}
	}

	fun copy(): CharList = CharList(size).also { it.addAll(this) }

	override fun get(index: Int): Char {
		ensureIndex(index)
		return elements[index]
	}

	override fun set(index: Int, element: Char): Char {
		ensureIndex(index)
		return elements[index].also { elements[index] = element }
	}

	override fun indexOf(element: Char): Int = elements.indexOf(element)

	override fun lastIndexOf(element: Char): Int =
		elements.lastIndexOf(element)

	override fun addAll(index: Int, elements: Collection<Char>): Boolean {
		ensureCursor(index)
		val arr = elements.toCharArray()
		ensureCapacity(size + arr.size)
		this.elements.copyInto(this.elements, index + arr.size, index, size)
		arr.copyInto(this.elements, index)
		size += arr.size
		return arr.isNotEmpty()
	}

	override fun hashCode(): Int {
		var hashCode = 1
		for (i in this) hashCode = 31 * hashCode + i.toInt()
		return hashCode
	}

	override fun equals(other: Any?): Boolean {
		if (other !is CharList) return false
		if (size != other.size) return false
		for (i in indices) if (elements[i] != other.elements[i]) return false
		return true
	}
}

inline fun charListOf(vararg elements: Char): CharList =
	CharList(elements.size).apply { addAll(elements.asList()) }

inline fun emptyCharList() = CharList()

@Suppress("UNCHECKED_CAST")
class NullableList<T : Any>(initialCapacity: Int = 0) : PrimitiveList<T?>() {
	companion object {
		private val EMPTY_DATA = emptyArray<Any?>()
	}

	private var elements = EMPTY_DATA

	override val elementsSize: Int
		get() = elements.size

	override fun extendCapacity(newCapacity: Int) {
		elements = elements.copyOf(newCapacity)
	}

	override fun moveElements(fromIndex: Int, toIndex: Int, count: Int) {
		elements.copyInto(elements, toIndex, fromIndex, count)
	}

	init {
		elements = when {
			initialCapacity > 0 -> arrayOfNulls(initialCapacity)
			initialCapacity == 0 -> EMPTY_DATA
			else -> error("Illegal capacity: $initialCapacity")
		}
	}

	fun copy(): NullableList<T> = NullableList<T>(size).also { it.addAll(this) }

	override fun get(index: Int): T? {
		ensureIndex(index)
		return elements[index] as? T
	}

	override fun set(index: Int, element: T?): T? {
		ensureIndex(index)
		return elements[index].also { elements[index] = element } as? T
	}

	override fun indexOf(element: T?): Int = elements.indexOf(element)

	override fun lastIndexOf(element: T?): Int =
		elements.lastIndexOf(element)

	override fun addAll(index: Int, elements: Collection<T?>): Boolean {
		ensureCursor(index)
		val arr = arrayOfNulls<Any>(elements.size)
		var i = 0
		for (element in elements) arr[i++] = element
		ensureCapacity(size + arr.size)
		this.elements.copyInto(this.elements, index + arr.size, index, size)
		arr.copyInto(this.elements, index)
		size += arr.size
		return arr.isNotEmpty()
	}

	override fun hashCode(): Int {
		var hashCode = 1
		for (i in this) hashCode = 31 * hashCode + (i?.hashCode() ?: 0)
		return hashCode
	}

	override fun equals(other: Any?): Boolean {
		if (other !is NullableList<*>) return false
		if (size != other.size) return false
		for (i in indices) if (elements[i] != other.elements[i]) return false
		return true
	}
}

inline fun <T : Any> nullableListOf(vararg elements: T): NullableList<T> =
	NullableList<T>(elements.size).apply { addAll(elements.asList()) }

inline fun <T : Any> emptyNullableList() = NullableList<T>()
