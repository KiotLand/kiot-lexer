package org.kiot.automaton

import kotlin.test.*

internal class CharClassTest {
	@Test
	fun test() {
		assertTrue('5' in CharClass('0' plainTo '1', '3' plainTo '5'))
	}

	@Test
	fun testIndexOf() {
		assertEquals(
			1,
			CharClass('a' plainTo 'b', 'd' plainTo 'e', 'g' plainTo 'h').indexOf('d')
		)
		assertEquals(
			-1,
			CharClass('0' plainTo '5', '7' plainTo '9').indexOf('6')
		)
	}

	@Test
	fun testMerge() {
		assertEquals(
			CharClass('0' plainTo '1'),
			CharClass('1' plainTo '1').merge(CharClass('0' plainTo '0'))
		)
		assertEquals(
			CharClass('0' plainTo '6', '8' plainTo '9'),
			CharClass('0' plainTo '4', '9' plainTo '9').merge(CharClass('3' plainTo '6', '8' plainTo '8'))
		)
		assertEquals(
			CharClass('0' plainTo '3'),
			CharClass('0' plainTo '0', '2' plainTo '2').merge(CharClass('1' plainTo '1', '3' plainTo '3'))
		)
	}

	@Test
	fun testInverse() {
		assertEquals(
			CharClass(Char.MIN_VALUE plainTo Char.MAX_VALUE),
			CharClass.empty.inverse()
		)
		assertEquals(
			CharClass(Char.MIN_VALUE plainTo ('0' - 1), ('9' + 1) plainTo Char.MAX_VALUE),
			CharClass.digit.inverse()
		)
		assertEquals(
			CharClass.digit,
			CharClass.digit.inverse().inverse()
		)
	}

	@Test
	fun testFromChars() {
		assertEquals(
			CharClass('0' plainTo '5'),
			CharClass.fromSorted("012345")
		)
		assertEquals(
			CharClass(Char.MIN_VALUE plainTo Char.MIN_VALUE, 'a' plainTo 'a', Char.MAX_VALUE plainTo Char.MAX_VALUE),
			CharClass.fromSorted(Char.MIN_VALUE, 'a', Char.MAX_VALUE)
		)
	}
}