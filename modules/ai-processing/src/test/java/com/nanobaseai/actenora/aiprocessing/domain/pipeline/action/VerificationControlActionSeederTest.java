package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationControlActionSeederTest {

    private final VerificationControlActionSeeder seeder = new VerificationControlActionSeeder();

    @Test
    void seedsA01FromBurakSelfCommitmentSegment() {
        SegmentInput seg = new SegmentInput(
                "seg-4",
                4,
                "Burak",
                0,
                1000,
                "Notlarda owner veya tarih yoksa otomatik görev oluşturulmamasını özellikle kontrol edeceğim.",
                true
        );
        VerificationControlActionSeeder.Result result = seeder.seed(List.of(), List.of(), List.of(seg));
        assertEquals(1, result.seeded());
        assertEquals(1, result.actions().size());
        ActionItemCandidate a = result.actions().getFirst();
        assertEquals("Burak", a.owner());
        assertTrue(a.text().toLowerCase().contains("kontrol"));
        assertTrue(a.text().toLowerCase().contains("owner") || a.text().toLowerCase().contains("tarih"));
        assertTrue(a.evidenceSegmentIds().contains("seg-4"));
    }

    @Test
    void promotesMatchingCommitmentWhenNoActionExists() {
        CommitmentCandidate c = new CommitmentCandidate(
                "Owner veya tarihi bulunmayan kayıtların otomatik görev oluşturmadığını kontrol edeceğim.",
                "Burak",
                List.of("seg-x"),
                0.9
        );
        VerificationControlActionSeeder.Result result = seeder.seed(List.of(), List.of(c), List.of());
        assertEquals(1, result.seeded());
        assertEquals("Burak", result.actions().getFirst().owner());
    }

    @Test
    void doesNotDuplicateWhenActionAlreadyPresent() {
        ActionItemCandidate existing = new ActionItemCandidate(
                "Owner veya tarihi bulunmayan kayıtların otomatik görev oluşturmadığını kontrol etmek",
                "Burak",
                null,
                List.of("seg-4"),
                0.9
        );
        SegmentInput seg = new SegmentInput(
                "seg-4", 4, "Burak", 0, 1000,
                "Notlarda owner veya tarih yoksa otomatik görev oluşturulmamasını özellikle kontrol edeceğim.",
                true
        );
        VerificationControlActionSeeder.Result result = seeder.seed(List.of(existing), List.of(), List.of(seg));
        assertEquals(0, result.seeded());
        assertEquals(1, result.actions().size());
    }
}
