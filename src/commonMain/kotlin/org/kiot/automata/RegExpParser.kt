package org.kiot.automata

import org.kiot.util.emptyCharList

open class RegExpException(override val message: String? = null) : RuntimeException()

@Suppress("NOTHING_TO_INLINE")
private inline fun Char.description() = "$this (0x${toInt().toString(16)})"

private class IllegalEscapeException(char: Char) :
	RegExpException("Illegal escape char: ${char.description()}")

private class UnexpectedCharException(char: Char) :
	RegExpException("Unexpected char: ${char.description()}")

@Suppress("NOTHING_TO_INLINE")
class RegExpParser(chars: CharSequence) {
	companion object {
		const val LEGAL_ESCAPE_CHAR = "-()*+.[]?\\^{}|"
	}

	private val chars = "($chars)"
	private var i = 0

	private inline fun error(message: String? = null): Nothing = throw RegExpException(message)
	private inline fun unexpected(char: Char): Nothing = throw UnexpectedCharException(char)
	private inline fun has(count: Int) = i + count <= chars.length
	private inline fun check() = reserve(1)
	private inline fun expect(actual: Char, expected: Char) {
		if (actual != expected) error("Expected $expected, got ${actual.description()}")
	}

	private inline fun reserve(count: Int) {
		if (!has(count)) error("Unexpected termination")
	}

	private inline fun readChar(): Char {
		val char = chars[i++]
		return if (char == '\\') {
			check()
			chars[i].also { if (it !in LEGAL_ESCAPE_CHAR) throw IllegalEscapeException(it) }
		} else char
	}

	private fun readInt(): Int? {
		if (i == chars.length || chars[i] !in '0'..'9') return null
		var ret = 0
		while (i != chars.length) {
			val char = chars[i]
			if (char in '0'..'9') ret = ret * 10 + (char - '0')
			else return ret
			++i
		}
		return ret
	}

	private fun tryReadCharClass(): CharClass? {
		return when (chars[i]) {
			'\\' -> {
				++i
				check()
				val char = chars[i]
				return when (chars[i].toLowerCase()) {
					'w' -> CharClass.letter
					'd' -> CharClass.digit
					's' -> CharClass.blank
					else -> {
						--i
						return null
					}
				}.let {
					++i
					if (char in 'A'..'Z') it.inverse() else it
				}
			}
			'.' -> CharClass.any.also { ++i }
			else -> null
		}
	}

	private fun readCharClass(): CharClass {
		++i
		check()
		val inverse = chars[i] == '^'
		if (inverse) ++i
		var lastChar: Char? = null
		val readChars = emptyCharList()
		val readRanges = mutableListOf<PlainCharRange>()
		loop@ while (true) {
			check()
			when (chars[i]) {
				'^' -> unexpected('^')
				'-' -> {
					if (lastChar == null) unexpected('-')
					++i
					check()
					readChars.removeAt(readChars.lastIndex)
					val endChar = readChar()
					if (lastChar > endChar) error("Illegal char range: ${lastChar.description()} to ${endChar.description()}")
					readRanges += lastChar plainTo endChar
				}
				']' -> break@loop
				else -> {
					val charClass = tryReadCharClass()
					if (charClass == null) readChars += readChar().also { lastChar = it }
					else readRanges.addAll(charClass.ranges)
				}
			}
		}
		++i
		var ret = CharClass.empty
		for (range in readRanges) ret = ret.merge(CharClass(range))
		readChars.sort()
		if (readChars.isNotEmpty()) {
			var tot = 1
			for (i in 1 until readChars.size) if (readChars[i] != readChars[tot - 1]) readChars[tot++] = readChars[i]
			readChars.resize(tot)
			ret = ret.merge(CharClass.from(*readChars.toCharArray()))
		}
		return if (inverse) ret.inverse() else ret
	}

	fun readExpression(): NFABuilder {
		++i
		check()
		val ret = NFABuilder()
		var last: NFABuilder? = null
		var orLeftOperand: NFABuilder? = null
		var lastIsChar = false
		fun commit() {
			if (last != null)
				orLeftOperand?.let {
					ret.append(NFABuilder.branch(it, last!!))
					orLeftOperand = null
					lastIsChar = false
					last = null
					return
				}
			last?.let { ret.append(it) }
			lastIsChar = false
			last = null
		}

		loop@ while (true) {
			check()
			when (chars[i++]) {
				'(' -> {
					--i
					commit()
					last = readExpression()
				}
				')' -> {
					commit()
					break@loop
				}
				'[' -> {
					--i
					commit()
					last = NFABuilder.from(readCharClass())
				}
				'+' -> last!!.oneOrMore().also { commit() }
				'*' -> last!!.any().also { commit() }
				'?' -> last!!.unnecessary().also { commit() }
				'|' -> {
					if (last == null) unexpected('|')
					orLeftOperand =
						if (orLeftOperand == null) last
						else NFABuilder.branch(orLeftOperand!!, last!!)
					lastIsChar = false
					last = null
				}
				'{' -> {
					val atLeast = readInt() ?: error("Expected integer")
					check()
					expect(chars[i++], ',')
					check()
					val atMost = readInt()
					expect(chars[i++], '}')
					if (atMost == null) last!!.repeatAtLeast(atLeast)
					else last!!.repeat(atLeast, atMost)
					commit()
				}
				else -> {
					--i
					val charClass = tryReadCharClass()
					if (charClass == null) {
						if (!lastIsChar) {
							commit()
							lastIsChar = true
							last = NFABuilder()
						}
						last!!.append(readChar())
					} else {
						commit()
						last = NFABuilder.from(charClass)
					}
				}
			}
		}
		return ret
	}
}