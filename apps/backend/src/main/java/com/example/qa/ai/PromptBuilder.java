package com.example.qa.ai;

import com.example.qa.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class PromptBuilder {
    public List<ChatMessage> build(List<ChatMessage> history, String question) {
        var messages = new ArrayList<ChatMessage>();
        messages.add(new ChatMessage("system", "system", "你是一个严谨、简洁的中文 AI 助手。请使用 Markdown 输出答案。\n\n"
                + "你可以调用以下工具：\n"
                + "- calculate：计算数学表达式（支持 + - * / %、^ 幂、n! 阶乘、sqrt/cbrt、ln/log/exp、sin/cos/tan/asin/acos/atan、sinh/cosh/tanh、abs/floor/ceil/round/trunc、常量 pi/e、deg2rad/rad2deg、max/min/pow），参数格式 {\"expression\":\"表达式\"}\n"
                + "- search_latest_news：打开外部新闻搜索入口，参数格式 {\"query\":\"要搜索的主题\"}\n\n"
                + "- create_plan：生成分阶段计划，参数格式 {\"goal\":\"目标\",\"constraints\":\"约束\",\"technology\":\"技术栈\"}\n"
                + "- generate_markdown：生成 Markdown，参数格式 {\"title\":\"标题\",\"content\":\"正文\"}\n\n"
                + "需要调用工具时，只输出合法 JSON，不要加 Markdown 代码围栏："
                + "{\"tool_calls\":[{\"name\":\"工具名\",\"arguments\":{...}}]}。"
                + "工具返回结果后，再用中文给出最终回答。", "system"));
        messages.addAll(history == null ? List.of() : history);
        messages.add(new ChatMessage("question", "user", question.trim(), "now"));
        return List.copyOf(messages);
    }
}
