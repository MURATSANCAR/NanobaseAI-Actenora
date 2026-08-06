package com.nanobaseai.actenora.sharedkernel.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonIdentityNormalizerTest {

    @Test
    void canonicalizesTitlesWhitespaceAndEmailDisplayNames() {
        assertEquals("Ali BAĞATIR", PersonIdentityNormalizer.displayName(
                "  Ali   BAĞATIR (MÜŞTERİ ÇÖZÜMLERİ GMY) "));
        assertEquals("Ali Bagatir", PersonIdentityNormalizer.displayName("ali.bagatir@example.com"));
        assertEquals("ali bagatir", PersonIdentityNormalizer.identityKey("Ali BAĞATIR (GMY)"));
        assertEquals("ali bagatir", PersonIdentityNormalizer.identityKey("ali.bagatir@example.com"));
        assertTrue(PersonIdentityNormalizer.canonicalRoster(
                List.of("Speaker 1 (Microsoft Teams)")).isEmpty());
    }

    @Test
    void resolvesAliasesOnlyWhenIdentityIsUnambiguous() {
        List<String> roster = List.of(
                "Murat Sancar",
                "murat.sancar@example.com",
                "Ali BAĞATIR (GMY)"
        );
        assertEquals("Murat Sancar", PersonIdentityNormalizer.resolveUnique("Murat", roster).orElseThrow());
        assertEquals("Ali BAĞATIR", PersonIdentityNormalizer.resolveUnique(
                "ali.bagatir@example.com", roster).orElseThrow());
        assertEquals("Ali BAĞATIR", PersonIdentityNormalizer.resolveUnique("Ali Bagatir", roster).orElseThrow());
    }

    @Test
    void refusesAmbiguousFirstNamesAndSupportsOneCharacterAsrDrift() {
        assertTrue(PersonIdentityNormalizer.resolveUnique(
                "Ali", List.of("Ali BAĞATIR", "Ali Yılmaz")).isEmpty());
        assertEquals("Burak Ayık Kesisoğlu", PersonIdentityNormalizer.resolveUnique(
                "Burag", List.of("Burak Ayık Kesisoğlu")).orElseThrow());
    }

    @Test
    void collapsesUniqueShortAndAsrSpeakerAliasesIntoFullRosterIdentity() {
        assertEquals(List.of("Murat Sancar"), PersonIdentityNormalizer.canonicalRoster(
                List.of("Murat", "Murat Sancar", "murat.sancar@example.com"))
                .values().stream().toList());
        assertEquals(List.of("Burak Ayık Kesisoğlu"), PersonIdentityNormalizer.canonicalRoster(
                List.of("BURAG", "Burak Ayık Kesisoğlu"))
                .values().stream().toList());
        assertEquals(3, PersonIdentityNormalizer.canonicalRoster(
                List.of("Ali", "Ali BAĞATIR", "Ali Yılmaz"))
                .size());
    }

    @Test
    void softMatchEquatesShortAndFullNamesButNotDistinctPeople() {
        assertTrue(PersonIdentityNormalizer.softMatch("Murat", "Murat Sancar"));
        assertTrue(PersonIdentityNormalizer.softMatch("Murat Sancar", "murat.sancar@example.com"));
        assertTrue(!PersonIdentityNormalizer.softMatch("Burak", "Murat"));
        assertTrue(!PersonIdentityNormalizer.softMatch("Can", "Selin"));
    }
}
