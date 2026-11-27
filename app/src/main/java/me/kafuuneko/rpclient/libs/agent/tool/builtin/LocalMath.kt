package me.kafuuneko.rpclient.libs.agent.tool.builtin

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** 掷骰结果在执行器派发之后生成，调用重试只读取已保存的结果。 */
data class DiceResult(val rolls: List<Int>, val kept: List<Int>, val modifier: Int, val total: Int)

/** 有界骰子语法，不接受代码或额外的任意修饰词。 */
object DiceNotation {
    private val mPattern =
        Regex("^([1-9][0-9]{0,2})[dD]([1-9][0-9]{0,4})(?:k([hl])([1-9][0-9]{0,2}))?([+-][0-9]{1,6})?$")

    /** 支持 NdM、kh/kl 与正负修正，骰数最多 100、面数最多 10,000。 */
    fun roll(notation: String, random: Random = Random.Default): DiceResult {
        // 先完成语法和体积校验，只有合法调用才能消耗随机状态。
        val match =
            requireNotNull(mPattern.matchEntire(notation.filterNot(Char::isWhitespace))) { "Invalid dice notation" }
        val count = match.groupValues[1].toInt()
        val sides = match.groupValues[2].toInt()
        val keep = match.groupValues[4].toIntOrNull() ?: count
        require(count <= 100 && sides <= 10_000 && keep <= count) { "Dice bounds exceeded" }
        val modifier = match.groupValues[5].toIntOrNull() ?: 0
        val rolls = List(count) { random.nextInt(1, sides + 1) }
        val kept = when (match.groupValues[3]) {
            "h" -> rolls.sortedDescending().take(keep)
            "l" -> rolls.sorted()
                .take(keep)

            else -> rolls
        }
        return DiceResult(rolls, kept, modifier, kept.sum() + modifier)
    }
}

/**
 * 受限算术解析器，独立于 JavaScript Regex 引擎。
 * - 递归下降处理优先级和右结合幂运算，不加载类、不访问变量或文件。
 * - 输入、深度和运算步数均有上限，非有限结果直接拒绝。
 */
object RestrictedCalculator {
    /** 计算四则、幂、括号和白名单数学函数，返回有限数值。 */
    fun calculate(expression: String): Double = Parser(expression).calculate()

    private class Parser(private val mInput: String) {
        private var mPosition = 0
        private var mSteps = 0
        private var mDepth = 0

        /** 完整消费表达式，拒绝尾随代码、非有限结果和超限输入。 */
        fun calculate(): Double {
            require(mInput.length in 1..1024) { "Invalid expression length" }
            val result = expression()
            whitespace()
            require(mPosition == mInput.length && result.isFinite()) { "Invalid arithmetic result" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) value = when {
                take('+') -> checked(value + term())
                take('-') -> checked(value - term())
                else -> return value
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) value = when {
                take('*') -> checked(value * unary())
                take('/') -> checked(value / unary())
                take(
                    '%'
                ) -> checked(value % unary())

                else -> return value
            }
        }

        private fun unary(): Double = when {
            take('+') -> unaryBounded()
            take('-') -> -unaryBounded()
            else -> power()
        }

        private fun unaryBounded(): Double {
            require(++mDepth <= 32) { "Expression is too deep" }
            return unary().also { mDepth-- }
        }

        private fun power(): Double {
            val value = primary()
            if (!take('^')) return value
            require(++mDepth <= 32) { "Expression is too deep" }
            return checked(value.pow(unary())).also { mDepth-- }
        }

        private fun primary(): Double {
            require(++mSteps <= 512 && ++mDepth <= 32) { "Expression is too complex" }
            val result = when {
                take('(') -> expression().also { require(take(')')) { "Missing closing parenthesis" } }
                else -> atom()
            }
            mDepth--
            return result
        }

        private fun atom(): Double {
            // 名称只能解析白名单常量或单参数数学函数，不查找宿主变量。
            whitespace()
            val start = mPosition
            if (mPosition < mInput.length && mInput[mPosition].isLetter()) {
                while (mPosition < mInput.length && mInput[mPosition].isLetterOrDigit()) mPosition++
                val name = mInput.substring(start, mPosition).lowercase()
                if (name == "pi") return Math.PI
                if (name == "e") return Math.E
                require(take('(')) { "Function argument required" }
                val value = expression()
                require(take(')')) { "Missing closing parenthesis" }
                return checked(
                    when (name) {
                        "sqrt" -> sqrt(value)
                        "abs" -> abs(value)
                        "sin" -> sin(value)
                        "cos" -> cos(
                            value
                        )

                        "tan" -> tan(value)
                        "ln" -> ln(value)
                        "log10" -> log10(value)
                        "floor" -> floor(value)
                        "ceil" -> ceil(value)
                        "round" -> round(value)
                        else -> error("Unsupported arithmetic function")
                    }
                )
            }
            while (mPosition < mInput.length && (mInput[mPosition].isDigit() || mInput[mPosition] == '.')) mPosition++
            require(mPosition > start) { "Number required" }
            return checked(mInput.substring(start, mPosition).toDouble())
        }

        private fun checked(value: Double): Double {
            require(++mSteps <= 512 && value.isFinite()) { "Invalid arithmetic result" }
            return value
        }

        private fun take(char: Char): Boolean {
            whitespace()
            if (mPosition >= mInput.length || mInput[mPosition] != char) return false
            mPosition++
            return true
        }

        private fun whitespace() {
            while (mPosition < mInput.length && mInput[mPosition].isWhitespace()) mPosition++
        }
    }
}
