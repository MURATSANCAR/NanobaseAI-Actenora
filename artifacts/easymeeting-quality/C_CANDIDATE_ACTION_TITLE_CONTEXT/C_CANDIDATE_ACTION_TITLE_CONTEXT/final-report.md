# C_CANDIDATE_ACTION_TITLE_CONTEXT — final report

Generated: finalize watcher on nanobase
Decision: **C_CANDIDATE_QUALITY_GATE_FAILED**
Reason codes: CUE51_NOT_5_OF_5, A06_NOT_5_OF_5, CUE27_NOT_5_OF_5

## Hard gates
- A-06 pass rate: 0.6
- A-07 pass rate: 0.0
- A-03 pass rate: 0.0
- A-04 pass rate: 0.0
- Cue 51 pass rate: 0.0
- Cue 27 pass rate: 0.0
- Action recall: [None, None, None, None, None]
- Critical gate: None
- Backfill UPDATE counts: [0, 1, 0, 1, 1]

## Production impact
- Production deploy: NO
- Prompt/gate/splitter/cross-type/finalization unchanged
- Only low-specificity action title context backfill added
- Ambiguous context => NO_UPDATE

## Runtime
- P50 ms: 733515.216
- P95 ms: 797322.004
