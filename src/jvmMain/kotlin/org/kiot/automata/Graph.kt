package org.kiot.automata

import com.mivik.kot.kot

fun StaticNFA.buildGraph() = kot {
	digraph {
		node("-1").let {
			it.label = "Final"
			it.color = "red"
		}
		node(beginCell.toString()).color = "blue"
		for (i in indices) {
			val cur = node(i.toString())
			cur.label = if (isDummy(i)) i.toString() else "$i: ${charClassOf(i)}"
			for (j in outsOf(i)) link(cur, node(j.toString()))
		}
	}
}

fun GeneralDFA.buildGraph() = kot {
	digraph {
		for (i in indices) {
			val cur = node(i.toString())
			cur.label = i.toString()
			if (isFinal(i)) cur.color = "red"
			val ranges = charRangesOf(i)
			val outs = outsOf(i)
			for (j in ranges.indices) {
				link(cur, node(outs[j].toString())).label = ranges[j].expand()
			}
		}
	}
}