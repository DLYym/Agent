package com.yk.demoai.util;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一处理 Schema 文本和用户问题中的中英文业务词，尽量让“订单/金额/客户”这类自然语言
 * 能命中表注释、字段注释和关键词，而不是完全依赖向量语义。
 */
public final class SchemaTermTokenizer {

    private static final Pattern IDENTIFIER_SPLIT_PATTERN = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern ASCII_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern HAN_SEQUENCE_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Pattern QUERY_NOISE_PATTERN = Pattern.compile(
            "(查询|查找|找出|统计|输出|显示|列出|所有|最近|过去|近|前|后|位|个|条|天|周|月|年|的|和|与|并|中|里|按|按照|请|帮我|一下|一下子|top|TOP|limit|LIMIT|\\d+)"
    );

    private static final Set<String> STOP_TERMS = Set.of(
            "核心表", "干扰表", "主表", "明细表", "信息表", "记录表", "归档表",
            "信息", "记录", "归档", "数据", "名称"
    );

    private static final Map<String, List<String>> QUERY_SYNONYMS = new LinkedHashMap<>();

    static {
        QUERY_SYNONYMS.put("客户", List.of("用户"));
        QUERY_SYNONYMS.put("顾客", List.of("用户"));
        QUERY_SYNONYMS.put("消费者", List.of("用户"));
        QUERY_SYNONYMS.put("金额", List.of("订单金额", "实付金额", "支付金额", "消费金额", "amount", "pay_amount", "total_amount"));
        QUERY_SYNONYMS.put("种类", List.of("分类"));
        QUERY_SYNONYMS.put("类别", List.of("分类"));
        QUERY_SYNONYMS.put("商品种类", List.of("商品分类", "分类"));
    }

    private SchemaTermTokenizer() {
    }

    public static List<String> extractSchemaTerms(String text) {
        return List.copyOf(extractTerms(text, false));
    }

    public static List<String> extractQueryTerms(String text) {
        return List.copyOf(extractTerms(text, true));
    }

    private static Set<String> extractTerms(String text, boolean queryMode) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return terms;
        }

        String normalizedText = queryMode
                ? QUERY_NOISE_PATTERN.matcher(text).replaceAll(" ")
                : text;

        extractAsciiTerms(normalizedText, terms);
        extractHanTerms(normalizedText, terms);
        if (queryMode) {
            expandQuerySynonyms(normalizedText, terms);
        }
        terms.removeIf(term -> !StringUtils.hasText(term) || STOP_TERMS.contains(term));
        return terms;
    }

    private static void extractAsciiTerms(String text, Set<String> terms) {
        Matcher matcher = ASCII_IDENTIFIER_PATTERN.matcher(text);
        while (matcher.find()) {
            String identifier = matcher.group();
            terms.add(identifier.toLowerCase(Locale.ROOT));
            terms.addAll(splitIdentifier(identifier));
        }
    }

    private static void extractHanTerms(String text, Set<String> terms) {
        Matcher matcher = HAN_SEQUENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sequence = matcher.group();
            addHanTerm(sequence, terms);
            addHanNgrams(sequence, 2, terms);
            if (sequence.length() <= 6) {
                addHanNgrams(sequence, 3, terms);
            }
        }
    }

    private static void expandQuerySynonyms(String text, Set<String> terms) {
        for (Map.Entry<String, List<String>> entry : QUERY_SYNONYMS.entrySet()) {
            if (!text.contains(entry.getKey()) && !terms.contains(entry.getKey())) {
                continue;
            }
            terms.add(entry.getKey());
            for (String synonym : entry.getValue()) {
                if (containsHan(synonym)) {
                    addHanTerm(synonym, terms);
                    addHanNgrams(synonym, 2, terms);
                } else {
                    terms.add(synonym.toLowerCase(Locale.ROOT));
                    terms.addAll(splitIdentifier(synonym));
                }
            }
        }
    }

    private static void addHanTerm(String value, Set<String> terms) {
        String term = value == null ? "" : value.trim();
        if (term.length() >= 2 && !STOP_TERMS.contains(term)) {
            terms.add(term);
        }
    }

    private static void addHanNgrams(String value, int ngramSize, Set<String> terms) {
        if (value == null || value.length() < ngramSize) {
            return;
        }
        for (int index = 0; index <= value.length() - ngramSize; index++) {
            addHanTerm(value.substring(index, index + ngramSize), terms);
        }
    }

    private static boolean containsHan(String value) {
        return value != null && HAN_SEQUENCE_PATTERN.matcher(value).find();
    }

    private static List<String> splitIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return List.of();
        }
        String normalized = identifier.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return Arrays.stream(IDENTIFIER_SPLIT_PATTERN.split(normalized))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
