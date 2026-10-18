package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class CalculateTool implements AiTool {
    @Override public String name() { return "calculate"; }
    @Override public String description() { return "计算只包含数字、括号和 + - * / 的算式"; }

    @Override
    public String execute(JsonNode arguments) {
        var expression = arguments.path("expression").asText("").replaceAll("\\s+", "");
        if (expression.isBlank() || !expression.matches("[0-9+*/().-]+")) {
            throw new IllegalArgumentException("expression 只允许数字、括号和 + - * /");
        }
        var values = new ArrayDeque<Double>();
        var operators = new ArrayDeque<Character>();
        for (int i = 0; i < expression.length();) {
            char token = expression.charAt(i);
            if (Character.isDigit(token) || token == '.') {
                int end = i + 1;
                while (end < expression.length() && (Character.isDigit(expression.charAt(end)) || expression.charAt(end) == '.')) end++;
                values.push(Double.parseDouble(expression.substring(i, end)));
                i = end;
            } else if (token == '(') {
                operators.push(token); i++;
            } else if (token == ')') {
                while (!operators.isEmpty() && operators.peek() != '(') apply(values, operators.pop());
                if (operators.isEmpty()) throw new IllegalArgumentException("括号不匹配");
                operators.pop(); i++;
            } else {
                while (!operators.isEmpty() && operators.peek() != '(' && precedence(operators.peek()) >= precedence(token)) apply(values, operators.pop());
                operators.push(token); i++;
            }
        }
        while (!operators.isEmpty()) {
            if (operators.peek() == '(') throw new IllegalArgumentException("括号不匹配");
            apply(values, operators.pop());
        }
        if (values.size() != 1) throw new IllegalArgumentException("无效算式");
        var result = values.pop();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("计算结果无效");
        return result == Math.rint(result) ? Long.toString(result.longValue()) : Double.toString(result);
    }

    private static int precedence(char operator) { return operator == '+' || operator == '-' ? 1 : 2; }

    private static void apply(Deque<Double> values, char operator) {
        if (values.size() < 2) throw new IllegalArgumentException("无效算式");
        var right = values.pop(); var left = values.pop();
        values.push(switch (operator) {
            case '+' -> left + right;
            case '-' -> left - right;
            case '*' -> left * right;
            case '/' -> right == 0 ? Double.NaN : left / right;
            default -> throw new IllegalArgumentException("不支持的运算符");
        });
    }
}
