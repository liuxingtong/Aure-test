package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 数学表达式计算器。
 *
 * <p>刻意不依赖 GraalJS/Nashorn：桌面端只需要四则运算和常见数学函数，
 * 自带递归下降解析器体积更小、启动更快，也不会因为 JDK 模块/运行时差异在打包后失效。</p>
 *
 * <p>只接受数字、字母、括号、逗号和运算符；函数和常量通过白名单分发，
 * 不存在执行任意代码的入口。</p>
 */
@Component
public class CalculateTool implements AiTool {
    private static final Pattern SAFE_CHARS = Pattern.compile("[0-9a-zA-Z_+\\-*/%^().,! \\t\\n\\r]*");
    private static final int MAX_LENGTH = 1000;

    @Override public String name() { return "calculate"; }

    @Override public String description() {
        return "计算数学表达式。支持：四则运算 + - * / %、幂 ^ 与 **、阶乘 ! (例如 5! 或 (2+3)!)、"
                + "开方 sqrt(x) / cbrt(x)、对数 ln(x) / log(x) / log(x,base)、指数 exp(x)、"
                + "三角函数 sin/cos/tan（参数为弧度，可用 deg2rad 转角度）、反三角 asin/acos/atan、"
                + "双曲 sinh/cosh/tanh、绝对值 abs、取整 floor/ceil/round/trunc、"
                + "常量 pi 与 e、最大最小 max/min、幂 pow(x,n)。";
    }

    @Override
    public String execute(JsonNode arguments) {
        var raw = arguments.path("expression").asText("");
        if (raw.isBlank()) throw new IllegalArgumentException("expression 不能为空");
        var expression = raw.replaceAll("\\s+", "");
        if (expression.length() > MAX_LENGTH) throw new IllegalArgumentException("expression 过长（最多 " + MAX_LENGTH + " 字符）");
        if (!SAFE_CHARS.matcher(expression).matches()) {
            throw new IllegalArgumentException("expression 含非法字符。仅允许数字、字母、运算符、括号、逗号和感叹号。");
        }
        try {
            double result = new Evaluator(expression).evaluate();
            if (Double.isNaN(result) || Double.isInfinite(result)) throw new IllegalArgumentException("计算结果无效");
            return format(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("表达式无法计算：" + exception.getMessage());
        }
    }

    private static String format(double number) {
        if (number == Math.rint(number) && Math.abs(number) < 1e15) {
            return Long.toString((long) Math.rint(number));
        }
        return Double.toString(number);
    }

    /** 递归下降解析器：expression -> term -> unary -> power -> postfix -> primary。 */
    private static final class Evaluator {
        private final String source;
        private int position;

        private Evaluator(String source) {
            this.source = source;
        }

        private double evaluate() {
            var value = expression();
            skipWhitespace();
            if (position != source.length()) {
                throw error("无法识别的内容：" + source.substring(position));
            }
            return value;
        }

        private double expression() {
            var value = term();
            while (true) {
                if (consume('+')) value += term();
                else if (consume('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            var value = unary();
            while (true) {
                if (consume('*')) value *= unary();
                else if (consume('/')) value /= unary();
                else if (consume('%')) value %= unary();
                else return value;
            }
        }

        private double unary() {
            if (consume('+')) return unary();
            if (consume('-')) return -unary();
            return power();
        }

        private double power() {
            var base = postfix();
            if (consume('^')) return Math.pow(base, unary());
            if (peek() == '*' && peek(1) == '*') {
                position += 2;
                return Math.pow(base, unary());
            }
            return base;
        }

        private double postfix() {
            var value = primary();
            while (consume('!')) {
                value = factorial(value);
            }
            return value;
        }

        private double primary() {
            skipWhitespace();
            if (consume('(')) {
                var value = expression();
                expect(')');
                return value;
            }
            if (peek() == '.' || Character.isDigit(peek())) return number();
            if (Character.isLetter(peek()) || peek() == '_') return identifier();
            throw error("缺少数字或表达式");
        }

        private double number() {
            int start = position;
            while (Character.isDigit(peek())) position++;
            if (peek() == '.') {
                position++;
                while (Character.isDigit(peek())) position++;
            }
            if (peek() == 'e' || peek() == 'E') {
                int exponentStart = position;
                position++;
                if (peek() == '+' || peek() == '-') position++;
                while (Character.isDigit(peek())) position++;
                if (position == exponentStart + 1) position = exponentStart;
            }
            try {
                return Double.parseDouble(source.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("数字格式无效");
            }
        }

        private double identifier() {
            int start = position;
            while (Character.isLetterOrDigit(peek()) || peek() == '_') position++;
            var name = source.substring(start, position).toLowerCase(Locale.ROOT);
            skipWhitespace();
            if (peek() != '(') {
                return switch (name) {
                    case "pi" -> Math.PI;
                    case "e" -> Math.E;
                    default -> throw error("未知常量或函数：" + name);
                };
            }
            position++;
            var args = new ArrayList<Double>();
            skipWhitespace();
            if (peek() != ')') {
                args.add(expression());
                while (consume(',')) args.add(expression());
            }
            expect(')');
            return apply(name, args);
        }

        private double apply(String name, List<Double> args) {
            return switch (name) {
                case "sqrt" -> single(name, args, x -> Math.sqrt(x));
                case "cbrt" -> single(name, args, x -> Math.cbrt(x));
                case "ln" -> single(name, args, x -> Math.log(x));
                case "log2" -> single(name, args, x -> Math.log(x) / Math.log(2));
                case "exp" -> single(name, args, x -> Math.exp(x));
                case "sin" -> single(name, args, x -> Math.sin(x));
                case "cos" -> single(name, args, x -> Math.cos(x));
                case "tan" -> single(name, args, x -> Math.tan(x));
                case "asin" -> single(name, args, x -> Math.asin(x));
                case "acos" -> single(name, args, x -> Math.acos(x));
                case "atan" -> single(name, args, x -> Math.atan(x));
                case "sinh" -> single(name, args, x -> Math.sinh(x));
                case "cosh" -> single(name, args, x -> Math.cosh(x));
                case "tanh" -> single(name, args, x -> Math.tanh(x));
                case "abs" -> single(name, args, x -> Math.abs(x));
                case "floor" -> single(name, args, x -> Math.floor(x));
                case "ceil" -> single(name, args, x -> Math.ceil(x));
                case "round" -> single(name, args, x -> (double) Math.round(x));
                case "trunc" -> single(name, args, x -> x < 0 ? Math.ceil(x) : Math.floor(x));
                case "deg2rad" -> single(name, args, x -> x * Math.PI / 180.0);
                case "rad2deg" -> single(name, args, x -> x * 180.0 / Math.PI);
                case "log" -> {
                    if (args.size() == 1) yield Math.log10(args.get(0));
                    if (args.size() == 2) yield Math.log(args.get(0)) / Math.log(args.get(1));
                    throw error("log 需要 1 或 2 个参数");
                }
                case "pow" -> {
                    require(name, args, 2);
                    yield Math.pow(args.get(0), args.get(1));
                }
                case "max" -> {
                    requireAtLeast(name, args, 1);
                    yield args.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                }
                case "min" -> {
                    requireAtLeast(name, args, 1);
                    yield args.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                }
                default -> throw error("未知函数：" + name);
            };
        }

        private double single(String name, List<Double> args, java.util.function.DoubleUnaryOperator operator) {
            require(name, args, 1);
            return operator.applyAsDouble(args.get(0));
        }

        private void require(String name, List<Double> args, int count) {
            if (args.size() != count) throw error(name + " 需要 " + count + " 个参数");
        }

        private void requireAtLeast(String name, List<Double> args, int count) {
            if (args.size() < count) throw error(name + " 至少需要 " + count + " 个参数");
        }

        private double factorial(double value) {
            if (value < 0 || value > 20 || value != Math.rint(value)) {
                throw error("阶乘仅支持 0..20 之间的整数");
            }
            long result = 1;
            for (int i = 2; i <= (int) value; i++) result *= i;
            return result;
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (peek() != expected) return false;
            position++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("缺少 '" + expected + "'");
        }

        private char peek() {
            return position < source.length() ? source.charAt(position) : '\0';
        }

        private char peek(int offset) {
            int index = position + offset;
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private void skipWhitespace() {
            while (Character.isWhitespace(peek())) position++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "（位置 " + position + "）");
        }
    }
}
