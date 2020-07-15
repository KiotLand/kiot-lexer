<h1 align="center">kiot-lexer</h1>
<h4 align="center">A state-based grateful lexer written in Kotlin. </h4>
<p>
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.5.3-blue.svg?cacheSeconds=2592000" />
  <a href="./LICENSE.md" target="_blank">
    <img alt="License: GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-yellow.svg" />
  </a>
</p>

#### WARNING: Still under construction...

## What is kiot-lexer?

A state-based lexer written in pure Kotlin.

## Why kiot-lexer?

To create lexers in pure Kotlin without other languages like jflex or ANTLR.

kiot-lexer implemented almost everything from automata (NFA and DFA) to RegExp parsing (not stable), which provides you efficiency beyond ordinary lexer implementations.

Also, kiot-lexer is a Kotlin MPP (multi-platform) project, which means you can use it almost everywhere: JVM, Native, Web... Don't limit your imagination!

## Example & Usage?

You would like to get kiot-lexer first:

```kotlin
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}

dependencies {
	implementation 'com.github.KiotLand.kiot-lexer:kiot-lexer:1.0.5.3'
	// kiot-lexer-js and kiot-lexer-jvm are also alternatives.
}
```

#### Example 1. Simple lexing

Build a simple lexer without states and data.

```kotlin
import org.kiot.automata.CharClass
import org.kiot.automata.NFABuilder
import org.kiot.lexer.Lexer
import org.kiot.util.intListOf

// IntList is a better implementation of MutableList<Int>
val list = intListOf()
val lexer = Lexer.simple {
	NFABuilder.from(' ') then { list.add(1) }
	NFABuilder.from(CharClass.digit).oneOrMore() then { list.add(2) }
	NFABuilder.from(CharClass.letter).oneOrMore() then { list.add(3) }
}
// The code above might seems hard for you... But it's a good way to get familiar with kiot-lexer.
// The code below is equivalent to the above one, with RegExp as matching pattern.
val lexer = Lexer.simple {
	" " then { list.add(1) }
	"\\d+" then { list.add(2) }
	"\\w+" then { list.add(3) }
}

// ... and here we come to lexing.

list.clear()
lexer.lex("i have 2 ideas")
// list = [3, 1, 3, 1, 2, 1, 3]

list.clear()
lexer.lex("!!")
// Crashes: Mismatch in [0, 0]
```

#### Example 2. Lexing with data

As you may have noted, only specific functions are called when patterns match, which means we'll need to have some variables to store our lexing results -- the implementation above is pretty dirty since it relies on local variables. However, kiot-lexer provides an elegant way to do this:

```kotlin
import org.kiot.lexer.Lexer

data class SimpleData(val words: MutableList<String> = mutableListOf(), val numbers: MutableList<Int> = mutableListOf())

val lexer = Lexer.simpleWithData({ SimpleData() }) {
	"\\w+" then { data.words += string() }
	"\\d+" then { data.numbers += string().toInt() }
	" " then ignore // We accept space but do nothing
}

val data = lexer.lex("number 42 is the answer")
/*
    data = SimpleData(
        mutableListOf("number", "is", "the", "answer")
        mutableListOf(42)
    )
*/
```

#### Example 3. Switching states

You may have some states in your lexer, like the state in a string, the state in a method body or something else. We can switch between these states like this:

```kotlin
import org.kiot.lexer.LexerState
import org.kiot.lexer.Lexer

val lexer = Lexer.build {
	// default state is always 0
	state(default) {
		": " then { switchState(1) }
		"\\w+" then { println("word: ${string()}") }
	}
	state(1) {
		".+" then { println("definition: ${string()}") }
	}
}

lexer.lex("KiotLand: A land where Kotlin lovers gather.")
/*
    word: KiotLand
    definition: A land where Kotlin lovers gather.
*/
```

#### Example 4. Lexer building options

Have a look at the following example:

```kotlin
import org.kiot.lexer.Lexer

val lexer = Lexer.simple {
	// `withName` gives this rule a name so we can debug more clearly
	"\\d" then { println("a digit") } withName "digit"
	"." then { println("a char") } withName "any"
}

/*
	org.kiot.automata.MarksConflictException:
		FunctionMark(digit) conflicts with FunctionMark(any) under this pattern: [0..9]
*/
```

The lexer above can recognize pattern [0..9] as either "digit" or "any", so we got an error. In kiot-lexer, we promote avoiding creating conflicts in your lexer, but, if you want the rules in your lexer to be checked in the order they are defined, you can use:

```kotlin
import org.kiot.lexer.Lexer

val lexer = Lexer.simple {
	options.strict = false
	"\\d" then { println("a digit") } withName "digit"
	"." then { println("a char") } withName "any"
}

lexer.lex("1")
// output: a digit

lexer.lex("d")
// output: a char
```

## Notice

The API is not stable now... If you want to know more, please see code in `commonTest`.
