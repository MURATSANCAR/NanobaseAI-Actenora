package com.nanobaseai.actenora.template.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed HTML sanitizer for rendered note fragments.
 * Strips scripts, event handlers, dangerous tags/attributes, and javascript: URLs.
 */
public final class HtmlSanitizer {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "html", "head", "meta", "style", "body", "div", "span", "p", "br", "hr",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "table", "thead",
            "tbody", "tr", "th", "td", "img", "strong", "em", "b", "i", "u", "a",
            "section", "header", "footer", "main", "article"
    );

    private static final Set<String> ALLOWED_ATTRS = Set.of(
            "class", "id", "style", "src", "alt", "href", "title", "colspan", "rowspan",
            "width", "height", "lang", "charset", "content", "http-equiv", "role"
    );

    private static final Set<String> VOID_TAGS = Set.of(
            "meta", "br", "hr", "img", "link", "input", "col", "area", "base", "wbr"
    );

    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*(=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'=<>`]+))?");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_JS = Pattern.compile("(?i)expression\\s*\\(|javascript\\s*:");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        Objects.requireNonNull(html, "html");
        String withoutComments = COMMENT.matcher(html).replaceAll("");
        String withoutScripts = SCRIPT_BLOCK.matcher(withoutComments).replaceAll("");
        Matcher matcher = TAG.matcher(withoutScripts);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String slash = matcher.group(1);
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String attrs = matcher.group(3) == null ? "" : matcher.group(3);
            if (!ALLOWED_TAGS.contains(tag)) {
                matcher.appendReplacement(out, "");
                continue;
            }
            if (!slash.isEmpty()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("</" + tag + ">"));
                continue;
            }
            String cleanedAttrs = cleanAttributes(attrs);
            if (VOID_TAGS.contains(tag)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("<" + tag + cleanedAttrs + " />"));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement("<" + tag + cleanedAttrs + ">"));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String cleanAttributes(String rawAttrs) {
        if (rawAttrs == null || rawAttrs.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        Matcher matcher = ATTR.matcher(rawAttrs);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            String assignment = matcher.group(2);
            if (name.startsWith("on") || !ALLOWED_ATTRS.contains(name)) {
                continue;
            }
            if (assignment == null) {
                cleaned.append(' ').append(name);
                continue;
            }
            String value = matcher.group(3);
            if (value == null) {
                continue;
            }
            String unquoted = stripQuotes(value).trim();
            String lower = unquoted.toLowerCase(Locale.ROOT);
            if (lower.startsWith("javascript:") || lower.startsWith("data:text/html")
                    || STYLE_JS.matcher(unquoted).find()) {
                continue;
            }
            if (("href".equals(name) || "src".equals(name)) && lower.startsWith("data:")
                    && !lower.startsWith("data:image/")) {
                continue;
            }
            cleaned.append(' ').append(name).append('=').append('"')
                    .append(unquoted.replace("\"", "&quot;"))
                    .append('"');
        }
        return cleaned.toString();
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
