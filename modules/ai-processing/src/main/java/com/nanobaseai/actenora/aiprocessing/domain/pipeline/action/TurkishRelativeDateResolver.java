package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Turkish relative date phrases against a meeting reference instant + timezone.
 * Unsupported phrases stay as relative text with UNRESOLVED status (never silently dropped).
 */
public final class TurkishRelativeDateResolver {

    public enum Status {
        RESOLVED,
        UNRESOLVED,
        EMPTY
    }

    public record Result(Status status, OffsetDateTime dueAt, String relativeDateText) {
        public Optional<OffsetDateTime> dueAtOptional() {
            return Optional.ofNullable(dueAt);
        }
    }

    private static final Pattern TIME = Pattern.compile(
            "(?iu)(?:saat\\s+)?(\\d{1,2})(?:[.:](\\d{2}))?"
    );
    private static final Pattern DAYPART = Pattern.compile(
            "(?iu)\\b(öğlen(?:e)?|ogle(?:ne)?|akşam(?:a)?|aksam(?:a)?|sabah(?:a)?)\\b"
    );
    private static final Pattern MONTH_RELATIVE = Pattern.compile(
            "(?iu)\\b(?:ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|"
                    + "eylül|eylul|ekim|kasım|kasim|aralık|aralik)(?:\\s+ay[ıi])?['’]?(?:dan|den|tan|ten)\\s+sonra\\b"
    );

    public Result resolve(String relativeDateText, OffsetDateTime meetingStartedAt, ZoneId timezone) {
        Objects.requireNonNull(timezone, "timezone");
        if (relativeDateText == null || relativeDateText.isBlank()) {
            return new Result(Status.EMPTY, null, null);
        }
        String raw = relativeDateText.strip();
        String normalized = normalize(raw);
        ZonedDateTime meeting = (meetingStartedAt == null
                ? OffsetDateTime.now(timezone)
                : meetingStartedAt)
                .atZoneSameInstant(timezone);
        LocalDate base = meeting.toLocalDate();

        LocalDate day = resolveDay(normalized, base);
        if (day == null) {
            return new Result(Status.UNRESOLVED, null, raw);
        }
        LocalTime time = resolveTime(normalized);
        if (time == null) {
            time = LocalTime.of(17, 0); // default end-of-business when only a day is given
        }
        OffsetDateTime dueAt = day.atTime(time).atZone(timezone).toOffsetDateTime();
        return new Result(Status.RESOLVED, dueAt, raw);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('İ', 'i')
                .replace('I', 'ı')
                .replace('ı', 'i') // fold for matching
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static LocalDate resolveDay(String normalized, LocalDate base) {
        if (normalized.contains("bugun")) {
            return base;
        }
        if (normalized.contains("yarin")) {
            return base.plusDays(1);
        }
        if (normalized.contains("hafta sonu") || normalized.contains("haftasonu")) {
            LocalDate saturday = base.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            return saturday;
        }
        if (normalized.contains("onumuzdeki hafta") || normalized.contains("gelecek hafta")) {
            return base.plusWeeks(1).with(DayOfWeek.MONDAY);
        }
        DayOfWeek weekday = weekday(normalized);
        if (weekday != null) {
            return base.with(TemporalAdjusters.nextOrSame(weekday));
        }
        return null;
    }

    private static DayOfWeek weekday(String normalized) {
        if (normalized.contains("pazartesi")) {
            return DayOfWeek.MONDAY;
        }
        if (normalized.contains("sali") && !normalized.contains("pazar")) {
            return DayOfWeek.TUESDAY;
        }
        if (normalized.contains("carsamba")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (normalized.contains("persembe")) {
            return DayOfWeek.THURSDAY;
        }
        if (normalized.contains("cuma") && !normalized.contains("cumartesi")) {
            return DayOfWeek.FRIDAY;
        }
        if (normalized.contains("cumartesi")) {
            return DayOfWeek.SATURDAY;
        }
        if (normalized.contains("pazar")) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }

    private static LocalTime resolveTime(String normalized) {
        Matcher daypart = DAYPART.matcher(normalized);
        if (daypart.find()) {
            String part = daypart.group(1).toLowerCase(Locale.ROOT)
                    .replace('ö', 'o').replace('ş', 's').replace('ğ', 'g');
            if (part.startsWith("oglen") || part.startsWith("ogle")) {
                return LocalTime.NOON;
            }
            if (part.startsWith("aksam")) {
                return LocalTime.of(18, 0);
            }
            if (part.startsWith("sabah")) {
                return LocalTime.of(9, 0);
            }
        }
        Matcher time = TIME.matcher(normalized);
        // Prefer the last numeric time token (e.g. "bugun 16.00'ya kadar")
        LocalTime found = null;
        while (time.find()) {
            int hour = Integer.parseInt(time.group(1));
            int minute = time.group(2) == null ? 0 : Integer.parseInt(time.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                found = LocalTime.of(hour, minute);
            }
        }
        return found;
    }

    /**
     * Detects whether free text contains a Turkish date cue (for audit / renderer).
     */
    public static boolean containsDateCue(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String n = normalize(text);
        return n.contains("bugun")
                || n.contains("yarin")
                || n.contains("hafta sonu")
                || n.contains("onumuzdeki hafta")
                || n.contains("gelecek hafta")
                || MONTH_RELATIVE.matcher(text).find()
                || weekday(n) != null
                || TIME.matcher(n).find() && (n.contains("kadar") || n.contains("saat"));
    }

    /**
     * Extracts the first relative-date phrase span from clause text, if present.
     */
    public Optional<String> extractPhrase(String clauseText) {
        if (clauseText == null || clauseText.isBlank()) {
            return Optional.empty();
        }
        Pattern phrase = Pattern.compile(
                "(?iu)\\b("
                        + "(?:bugün|bugun|yarın|yarin)"
                        + "(?:\\s+(?:saat\\s+)?\\d{1,2}(?:[.:]\\d{2})?)?"
                        + "(?:\\s+(?:öğlen(?:e)?|ogle(?:ne)?|akşam(?:a)?|aksam(?:a)?|sabah(?:a)?))?"
                        + "(?:\\s*'?ya\\s+kadar|\\s+kadar)?"
                        + "|(?:pazartesi|salı|sali|çarşamba|carsamba|perşembe|persembe|cuma|cumartesi|pazar)"
                        + "(?:\\s+gününe\\s+kadar|\\s+gunune\\s+kadar|\\s+kadar)?"
                        + "|(?:hafta\\s*sonu(?:na)?\\s+kadar|önümüzdeki\\s+hafta|onumuzdeki\\s+hafta|gelecek\\s+hafta)"
                        + "|(?:ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|"
                        + "eylül|eylul|ekim|kasım|kasim|aralık|aralik)(?:\\s+ay[ıi])?['’]?(?:dan|den|tan|ten)\\s+sonra"
                        + ")\\b"
        );
        Matcher matcher = phrase.matcher(clauseText);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).strip());
    }

    public String stripPhrase(String clauseText, String phrase) {
        if (clauseText == null || phrase == null || phrase.isBlank()) {
            return clauseText;
        }
        return clauseText.replace(phrase, " ").replaceAll("\\s+", " ").strip()
                .replaceAll("(?iu)\\s+'?ya\\s*$", "")
                .replaceAll("(?iu)\\s+kadar\\s*$", "")
                .strip();
    }
}
