package me.kafuuneko.rpclient.libs.agent

import kotlin.random.Random
import me.kafuuneko.rpclient.libs.agent.tool.builtin.DiceNotation
import me.kafuuneko.rpclient.libs.agent.tool.builtin.RestrictedCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证有界解析、优先级及异常算式，禁止以脚本引擎扩大执行权限。 */
class LocalMathTest {
    @Test
    fun arithmeticPrecedenceAndFunctions() {
        assertEquals(-4.0, RestrictedCalculator.calculate("-2^2"), 0.0)
        assertEquals(512.0, RestrictedCalculator.calculate("2^3^2"), 0.0)
        assertEquals(0.25, RestrictedCalculator.calculate("2^-2"), 0.0)
        assertEquals(7.0, RestrictedCalculator.calculate("sqrt(9)+abs(-4)"), 0.0)
        assertEquals(1.0, RestrictedCalculator.calculate("sin(pi/2)"), 0.00001)
    }

    @Test
    fun arithmeticRejectsUnboundedAndNonFiniteInput() {
        listOf(
            "1/0",
            "sqrt(-1)",
            "2^99999",
            "1+",
            "1;System.exit(0)",
            "NaN",
            "max(1,2)",
            "-".repeat(40) + "1",
            "(".repeat(40) + "1" + ")".repeat(40)
        ).forEach { expression ->
            assertThrows(
                expression,
                IllegalArgumentException::class.java
            ) { RestrictedCalculator.calculate(expression) }
        }
    }

    @Test
    fun diceKeepsAndModifiersAreBounded() {
        val result = DiceNotation.roll("4d6kh3-2", Random(11))
        assertEquals(result.rolls.sortedDescending().take(3), result.kept)
        assertEquals(result.kept.sum() - 2, result.total)
        assertTrue(result.rolls.all { it in 1..6 })
        assertEquals(2, DiceNotation.roll("4d6kl2", Random(11)).kept.size)
        listOf(
            "101d6",
            "1d10001",
            "3d6kh4",
            "0d6",
            "1d0",
            "2d6+1+1",
            "1d6kh0"
        ).forEach { expression ->
            assertThrows(IllegalArgumentException::class.java) {
                DiceNotation.roll(
                    expression
                )
            }
        }
    }
}
