package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DueDateInTranscriptRule implements ValidationRule {

    private static final Pattern TIME = Pattern.compile(
            "(?iu)(?:saat\\s+)?(\\d{1,2})(?:[.:](\\d{2}))?"
    );

    @Override
    public String ruleId() {
        return ValidationRuleCodes.DUE_DATE_IN_TRANSCRIPT;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        String corpus = context.transcriptCorpus();
        for (ValidationCandidate candidate : context.candidates()) {
            String evidenceCorpus = context.evidenceCorpus(candidate);
            boolean hasDueDate = candidate.dueDateText().isPresent();
            boolean hasDueAt = candidate.dueAtText().isPresent();
            boolean hasRelativeDateText = candidate.relativeDateText().isPresent();
            if (!hasDueDate && !hasDueAt && !hasRelativeDateText) {
                results.add(pass(candidate, "No due date claimed"));
                continue;
            }

            if (candidate.dateResolutionSource().map("RELATIVE_DATE_EVIDENCE"::equalsIgnoreCase).orElse(false)
                    && candidate.dateResolutionStatus().map("RESOLVED"::equalsIgnoreCase).orElse(false)) {
                if (isGroundedResolvedRelativeDate(candidate, evidenceCorpus)) {
                    results.add(pass(candidate, "Resolved relative due date is grounded by evidence"));
                } else {
                    String messageNeedle = candidate.dueDateText().orElse(candidate.relativeDateText().orElse(""));
                    results.add(fail(candidate, "Due date is not supported by transcript text: " + messageNeedle));
                }
                continue;
            }

            if (hasDueDate) {
                String due = candidate.dueDateText().orElseThrow();
                if (ValidationContext.corpusContains(corpus, due)) {
                    results.add(pass(candidate, "Due date appears in transcript"));
                    continue;
                }
            }

            if (hasRelativeDateText) {
                String relative = candidate.relativeDateText().orElseThrow();
                if (corpusContainsTurkishNormalized(evidenceCorpus, relative)) {
                    results.add(pass(candidate, "Relative due date appears in cited evidence"));
                    continue;
                }
            }

            String messageNeedle = candidate.dueAtText()
                    .orElse(candidate.dueDateText().orElse(candidate.relativeDateText().orElse("")));
            results.add(fail(candidate, "Due date is not supported by transcript text: " + messageNeedle));
        }
        return results;
    }

    private static boolean isGroundedResolvedRelativeDate(
            ValidationCandidate candidate,
            String evidenceCorpus
    ) {
        String source = candidate.dateResolutionSource().orElse("");
        String status = candidate.dateResolutionStatus().orElse("");
        if (!"RELATIVE_DATE_EVIDENCE".equalsIgnoreCase(source) || !"RESOLVED".equalsIgnoreCase(status)) {
            return false;
        }
        if (candidate.relativeDateText().isEmpty()
                || candidate.dueDateText().isEmpty()
                || candidate.dueAtText().isEmpty()
                || candidate.dateResolutionReferenceTime().isEmpty()
                || candidate.dateResolutionTimezone().isEmpty()
                || candidate.evidenceSegmentIds().isEmpty()) {
            return false;
        }
        String relative = candidate.relativeDateText().orElseThrow();
        try {
            ZoneId timezone = ZoneId.of(candidate.dateResolutionTimezone().orElseThrow());
            OffsetDateTime reference = OffsetDateTime.parse(candidate.dateResolutionReferenceTime().orElseThrow());
            OffsetDateTime dueAt = OffsetDateTime.parse(candidate.dueAtText().orElseThrow());
            Optional<OffsetDateTime> expected = resolveRelativeDate(relative, reference, timezone);
            if (expected.isEmpty() || !dueAt.equals(expected.orElseThrow())) {
                return false;
            }
            if (!relativeDateIsGroundedByEvidence(relative, evidenceCorpus, reference, timezone, dueAt)) {
                return false;
            }
            LocalDate dueAtLocalDate = dueAt.atZoneSameInstant(timezone).toLocalDate();
            return candidate.dueDateText().orElseThrow().equals(dueAtLocalDate.toString());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean relativeDateIsGroundedByEvidence(
            String relative,
            String evidenceCorpus,
            OffsetDateTime reference,
            ZoneId timezone,
            OffsetDateTime dueAt
    ) {
        if (corpusContainsTurkishNormalized(evidenceCorpus, relative)) {
            return true;
        }
        // Models may add/remove semantically empty Turkish time words (for example
        // "bugün saat 16.00" versus the cited "bugün 16.00"). In that case the
        // evidence itself must still independently resolve to the exact claimed instant.
        return resolveRelativeDate(evidenceCorpus, reference, timezone)
                .map(dueAt::equals)
                .orElse(false);
    }

    private static Optional<OffsetDateTime> resolveRelativeDate(
            String relative,
            OffsetDateTime reference,
            ZoneId timezone
    ) {
        String normalized = normalizeTurkishForResolution(relative);
        ZonedDateTime meeting = reference.atZoneSameInstant(timezone);
        LocalDate base = meeting.toLocalDate();
        LocalDate day = resolveDay(normalized, base);
        if (day == null) {
            return Optional.empty();
        }
        LocalTime time = resolveTime(normalized);
        if (time == null) {
            time = LocalTime.of(17, 0);
        }
        return Optional.of(day.atTime(time).atZone(timezone).toOffsetDateTime());
    }

    private static LocalDate resolveDay(String normalized, LocalDate base) {
        if (normalized.contains("bugun")) {
            return base;
        }
        if (normalized.contains("yarin")) {
            return base.plusDays(1);
        }
        if (normalized.contains("hafta sonu") || normalized.contains("haftasonu")) {
            return base.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        }
        if (normalized.contains("onumuzdeki hafta") || normalized.contains("gelecek hafta")) {
            return base.plusWeeks(1).with(DayOfWeek.MONDAY);
        }
        DayOfWeek weekday = weekday(normalized);
        return weekday == null ? null : base.with(TemporalAdjusters.nextOrSame(weekday));
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
        if (normalized.contains("oglen")) {
            return LocalTime.NOON;
        }
        if (normalized.contains("aksam")) {
            return LocalTime.of(18, 0);
        }
        if (normalized.contains("sabah")) {
            return LocalTime.of(9, 0);
        }
        Matcher matcher = TIME.matcher(normalized);
        LocalTime found = null;
        while (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            if (hour <= 23 && minute <= 59) {
                found = LocalTime.of(hour, minute);
            }
        }
        return found;
    }

    private static boolean corpusContainsTurkishNormalized(String corpus, String needle) {
        String corpusNorm = normalizeTurkishLoose(corpus);
        String needleNorm = normalizeTurkishLoose(needle);
        if (needleNorm.isBlank()) {
            return true;
        }
        return corpusNorm.contains(needleNorm);
    }

    private static String normalizeTurkishLoose(String value) {
        if (value == null) {
            return "";
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        // Turkish-specific replacements to make diacritic-insensitive matching cheap & deterministic.
        v = v.replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u');

        // Remove any remaining diacritic marks (lightweight safeguard for other characters).
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

        // Normalize punctuation to whitespace so time formats like "16.00" vs "16:00" still match loosely.
        v = v.replaceAll("[^a-z0-9]+", " ");
        v = v.trim().replaceAll("\\s+", " ");
        return v;
    }

    private static String normalizeTurkishForResolution(String value) {
        if (value == null) {
            return "";
        }
        String v = value.strip().toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u');
        return Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
