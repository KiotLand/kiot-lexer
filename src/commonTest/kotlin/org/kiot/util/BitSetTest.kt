package org.kiot.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BitSetTest {
	@Test
	fun test() {
		val size = 100
		val bitset = BitSet(size)
		val arr = BooleanArray(size)
		repeat(size / 2) {
			val index = Random.nextInt(size)
			bitset.set(index)
			arr[index] = true
		}
		repeat(size / 2) {
			val index = Random.nextInt(size)
			bitset.clear(index)
			arr[index] = false
		}
		for (i in arr.indices) assertEquals(arr[i], bitset[i])
	}
}