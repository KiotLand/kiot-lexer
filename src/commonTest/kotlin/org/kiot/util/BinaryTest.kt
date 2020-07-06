package org.kiot.util

import org.kiot.automata.CharClass
import org.kiot.automata.CompressedDFA
import org.kiot.automata.GeneralDFA
import org.kiot.automata.NFA
import org.kiot.automata.NFATest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BinaryTest {
	@Test
	fun test() {
		assertEquals(
			CharClass.letter,
			CharClass.letter.binarize().debinarize()
		)
		run {
			val bitset = BitSet(100)
			repeat(bitset.size / 2) {
				bitset[Random.nextInt(bitset.size)] = true
			}
			assertEquals(
				bitset,
				bitset.binarize().debinarize()
			)
		}
		intListOf(1, 3, 5, 2, 4).let {
			assertEquals(it, it.binarize().debinarize())
		}
		run {
			val dfa = NFATest.buildThree().toDFA().minimize().binarize().debinarize<GeneralDFA>()
			(Random.nextInt(0, 2000) * 3).let {
				assertTrue(dfa.match(it.toString()))
				assertFalse(dfa.match((it + 1).toString()))
				assertFalse(dfa.match((it + 2).toString()))
			}
		}
		run {
			val nfa = NFATest.buildThree().binarize().debinarize<NFA>()
			(Random.nextInt(0, 2000) * 3).let {
				assertTrue(nfa.match(it.toString()))
				assertFalse(nfa.match((it + 1).toString()))
				assertFalse(nfa.match((it + 2).toString()))
			}
		}
		run {
			val nfa = NFATest.buildThree().toDFA().compressed().binarize().debinarize<CompressedDFA>()
			(Random.nextInt(0, 2000) * 3).let {
				assertTrue(nfa.match(it.toString()))
				assertFalse(nfa.match((it + 1).toString()))
				assertFalse(nfa.match((it + 2).toString()))
			}
		}
	}
}