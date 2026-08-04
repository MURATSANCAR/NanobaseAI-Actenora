# Root-Cause Summary (Phase 1B)

## control (f9c699f)

- runs: 5
- criticalGatePassCount: 0

### By reasonCode

- `ACTION_COMPOUND_NOT_SPLIT`: 11
- `UNCLASSIFIED_MISS`: 5
- `NOT_OBSERVABLE`: 5

### By gold item

- `A-06`: 5
- `Q-01`: 5
- `Q-04`: 5
- `Q-07`: 5
- `Q-10`: 5
- `Q-02`: 4
- `Q-03`: 4
- `Q-06`: 4
- `Q-08`: 4
- `Q-09`: 4
- `Q-12`: 4
- `A-01`: 2
- `F-01`: 2
- `F-02`: 2
- `A-02`: 1
- `A-05`: 1
- `A-03`: 1

## candidate (472172a)

- runs: 5
- criticalGatePassCount: 0

### By reasonCode

- `ACTION_COMPOUND_NOT_SPLIT`: 11
- `UNCLASSIFIED_MISS`: 5
- `NOT_OBSERVABLE`: 5

### By gold item

- `Q-01`: 5
- `Q-02`: 5
- `Q-03`: 5
- `Q-04`: 5
- `Q-06`: 5
- `Q-07`: 5
- `Q-08`: 5
- `Q-09`: 5
- `Q-10`: 5
- `Q-12`: 5
- `A-06`: 4
- `F-01`: 3
- `F-02`: 3
- `A-03`: 2
- `A-01`: 1

## Candidate improvements

- {'goldId': 'A-01', 'controlMisses': 2, 'candidateMisses': 1}
- {'goldId': 'A-02', 'controlMisses': 1, 'candidateMisses': 0}
- {'goldId': 'A-05', 'controlMisses': 1, 'candidateMisses': 0}
- {'goldId': 'A-06', 'controlMisses': 5, 'candidateMisses': 4}

## Candidate regressions

- {'goldId': 'A-03', 'controlMisses': 1, 'candidateMisses': 2}
- {'goldId': 'F-01', 'controlMisses': 2, 'candidateMisses': 3}
- {'goldId': 'F-02', 'controlMisses': 2, 'candidateMisses': 3}
- {'goldId': 'Q-02', 'controlMisses': 4, 'candidateMisses': 5}
- {'goldId': 'Q-03', 'controlMisses': 4, 'candidateMisses': 5}
- {'goldId': 'Q-06', 'controlMisses': 4, 'candidateMisses': 5}
- {'goldId': 'Q-08', 'controlMisses': 4, 'candidateMisses': 5}
- {'goldId': 'Q-09', 'controlMisses': 4, 'candidateMisses': 5}
- {'goldId': 'Q-12', 'controlMisses': 4, 'candidateMisses': 5}

## Top systemic failures

- {'goldId': 'Q-01', 'missCount': 10, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-04', 'missCount': 10, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-07', 'missCount': 10, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-10', 'missCount': 10, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'A-06', 'missCount': 9, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-02', 'missCount': 9, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-03', 'missCount': 9, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}
- {'goldId': 'Q-06', 'missCount': 9, 'note': 'UNCLASSIFIED_MISS / NOT_OBSERVABLE (no intermediate artifacts)'}

## Recommended next fixes (max 2)

- **FIX-OQ-RECALL**: Investigate open-question recall collapse — 94 Q-* misses across 10 runs in final notes

## Observability

Campaign run packages contain final.note.json (+ meta) only. Therefore stage attribution for misses is NOT_OBSERVABLE; reasonCode UNCLASSIFIED_MISS is used when only final-note absence is proven.
- **FIX-A06-UTF8-ACTION-RECALL**: Investigate A-06 UTF-8 header action loss / over-specialization — A-06 missed in 9/10 final notes; Cue 51 split fails whenever A-06 misses while A-07 often PASSes
