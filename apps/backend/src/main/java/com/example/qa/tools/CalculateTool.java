package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

@Component
public class CalculateTool implements AiTool {
    // 允许的字符：数字、字母、运算符、括号、逗号、感叹号（阶乘）、空白。
    // 不允许: ; { } [ ] " ' ` $ # @ \ 等会形成多语句/转义/变量引用的字符。
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
        // 防御：禁止连续分号/花括号——SAFE_CHARS 已经排除了它们，但再保险一次
        if (expression.contains(";") || expression.contains("{") || expression.contains("}")) {
            throw new IllegalArgumentException("expression 不支持多语句或代码块");
        }
        // 把 n! 翻译成显式函数调用，JS 不支持 n! 语法糖
        var js = expandFactorial(expression);
        try (var context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(false)
                .allowCreateThread(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            // 注入数学工具，全部为基本类型，不暴露宿主 API
            var bindings = context.getBindings("js");
            bindings.putMember("sqrt", (java.util.function.Function<Double, Double>) Math::sqrt);
            bindings.putMember("cbrt", (java.util.function.Function<Double, Double>) x -> Math.cbrt(x));
            bindings.putMember("ln", (java.util.function.Function<Double, Double>) Math::log);
            bindings.putMember("log2", (java.util.function.Function<Double, Double>) x -> Math.log(x) / Math.log(2));
            bindings.putMember("exp", (java.util.function.Function<Double, Double>) Math::exp);
            bindings.putMember("sin", (java.util.function.Function<Double, Double>) Math::sin);
            bindings.putMember("cos", (java.util.function.Function<Double, Double>) Math::cos);
            bindings.putMember("tan", (java.util.function.Function<Double, Double>) Math::tan);
            bindings.putMember("asin", (java.util.function.Function<Double, Double>) Math::asin);
            bindings.putMember("acos", (java.util.function.Function<Double, Double>) Math::acos);
            bindings.putMember("atan", (java.util.function.Function<Double, Double>) Math::atan);
            bindings.putMember("sinh", (java.util.function.Function<Double, Double>) Math::sinh);
            bindings.putMember("cosh", (java.util.function.Function<Double, Double>) Math::cosh);
            bindings.putMember("tanh", (java.util.Function<Double, Double>) Math::tanh);
            bindings.putMember("abs", (java.util.function.Function<Double, Double>) Math::abs);
            bindings.putMember("floor", (java.util.function.Function<Double, Double>) Math::floor);
            bindings.putMember("ceil", (java.util.function.Function<Double, Double>) Math::ceil);
            bindings.putMember("round", (java.util.function.Function<Double, Double>) Math::round);
            bindings.putMember("trunc", (java.util.function.Function<Double, Double>) Math::trunc);
            bindings.putMember("pi", Math.PI);
            bindings.putMember("e", Math.E);
            bindings.putMember("deg2rad", (java.util.function.Function<Double, Double>) d -> d * Math.PI / 180.0);
            bindings.putMember("rad2deg", (java.util.function.Function<Double, Double>) r -> r * 180.0 / Math.PI);
            bindings.putMember("max", (java.util.function.BiFunction<Double, Double, Double>) Math::max);
            bindings.putMember("min", (java.util.function.BiFunction<Double, Double, Double>) Math::min);
            bindings.putMember("pow", (java.util.function.BiFunction<Double, Double, Double>) Math::pow);
            bindings.putMember("__factorial", (java.util.function.Function<Long, Long>) CalculateTool::factorial);
            // 用 IIFE 包裹，强制为表达式；并把 ^ 翻译为 **（JS 风格）。JS 的 ** 优先级与 ^ 一致
            var script = "(function(){\n"
                    + "  function fact(n){ if(n<0||(n|0)!==n) throw new Error('阶乘仅支持非负整数'); return __factorial(n); }\n"
                    + "  function log(x, base){ if(base===undefined) return Math.log10(x); return Math.log(x)/Math.log(base); }\n"
                    + "  var __src = " + js + ";\n"
                    + "  return __src;\n"
                    + "})()";
            Value value = context.eval("js", script);
            if (!value.isNumber()) throw new IllegalArgumentException("表达式未产生数值结果");
            double number = value.asDouble();
            if (Double.isNaN(number) || Double.isInfinite(number)) throw new IllegalArgumentException("计算结果无效");
            return format(number);
        } catch (PolyglotException exception) {
            throw new IllegalArgumentException("表达式无法计算：" + exception.getMessage());
        }
    }

    /**
     * 把 n! / (expr)! 形式转成显式的 fact(n) / fact(expr) 调用。
     * 同时把 ^ 替换为 **（幂运算）。
     */
    static String expandFactorial(String input) {
        // ^ -> **
        var sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '^') {
                sb.append("**");
            } else {
                sb.append(c);
            }
        }
        var out = sb.toString();
        // 处理 n! → fact(n)，(expr)! → fact(expr)
        // 从右往左扫描，找到每个 !，向左吞一个"原子"（数字、标识符、或一对匹配的括号）
        sb = new StringBuilder(out.length() + 16);
        int i = 0;
        while (i < out.length()) {
            char c = out.charAt(i);
            if (c == '!') {
                // 弹出 sb 的最后一个原子
                int end = sb.length();
                if (end == 0) throw new IllegalArgumentException("阶乘位置不合法");
                if (sb.charAt(end - 1) == ')') {
                    int depth = 1;
                    int j = end - 2;
                    while (j >= 0 && depth > 0) {
                        char cc = sb.charAt(j);
                        if (cc == ')') depth++;
                        else if (cc == '(') depth--;
                        j--;
                    }
                    int start = j + 1;
                    String inside = sb.substring(start + 1, end - 1);
                    sb.replace(start, end, "fact(" + inside + ")");
                } else {
                    int j = end - 1;
                    while (j >= 0 && (Character.isLetterOrDigit(sb.charAt(j)) || sb.charAt(j) == '_')) j--;
                    int start = j + 1;
                    String token = sb.substring(start, end);
                    if (token.isEmpty()) throw new IllegalArgumentException("阶乘位置不合法");
                    sb.replace(start, end, "fact(" + token + ")");
                }
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static long factorial(long n) {
        if (n < 0 || n > 20) throw new IllegalArgumentException("阶乘仅支持 0..20 之间的整数");
        long r = 1;
        for (long k = 2; k <= n; k++) r *= k;
        return r;
    }

    private static String format(double number) {
        if (number == Math.rint(number) && Math.abs(number) < 1e15) {
            return Long.toString((long) Math.rint(number));
        }
        return Double.toString(number);
    }
}
