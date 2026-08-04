# EasyMeeting Phase 1B — Lineage re-run final report

**Kampanya:** `COMPLETE` at `2026-08-04T10:05:34Z`  
**Fixture:** `01_15dk_daily_standup.vtt` · editorial finalization · production deploy **yok**  
**Karar:** `COMPOUND_ACTION_ROOT_CAUSE_CONFIRMED`

---

## 1. Evaluation build’leri

| | B (control) | A (candidate) |
|---|---|---|
| Build | `B_LINEAGE_f9c699f` | `A_LINEAGE_472172a` |
| Base commit | `f9c699f` | `472172a` |
| Ortak L hash | `c5737ad8…8fb7f` | aynı |
| Jar SHA-256 | `7da164e4…e22e9` | `a1b921a5…efbdc` |
| CrossType | yok | var |
| Title backfiller | yok | var (kodda) |

L = observability-only lineage overlay. Prompt / gold / gate / split / cross-type davranışı değiştirilmedi. Prod jar deploy edilmedi; eval `app.jar` swap + restore.

---

## 2. Smoke

| | Sonuç |
|---|---|
| B smoke | PASS — 84 lineage event |
| A smoke | PASS — 91 lineage event |
| Artifact | `final.note` + `lineage.json` + `action-post-processing` AVAILABLE |
| Output equivalence | lineage kapalı/açık post-process testleri PASS (unit) |

---

## 3. Cue 51 yaşam döngüsü (B ve A, 5+5)

**Gold beklenti**

- A-06 Can: UTF-8 e-posta başlığı düzeltmesi  
- A-07 Burak: Outlook / Apple Mail regresyonu + yarın öğlen

**Gözlem (10/10 koşu aynı pattern)**

| Stage | B | A |
|---|---|---|
| Gate / LLM raw | NOT_OBSERVABLE (ara artifact yok) | aynı |
| Compound | Ayrı adaylar; `ACTION_COMPOUND_NOT_SPLIT` | aynı |
| Can clause | Var ama bağlamsız: `Başlık düzeltmesini yapacak.` | aynı |
| Burak clause | PASS + dueDate | PASS + dueDate |
| Title backfill lineage | 0 event | 0 event (metin değişmedi / tetiklenmedi) |
| Dedup / cross-type drop | Can’i düşürmedi | Can’i düşürmedi |
| Final note | Can UTF-8 **yok**; Burak **var** | aynı |

**Reason codes:** `ACTION_TITLE_CONTEXT_LOSS`, `ACTION_TITLE_BACKFILL_NOT_TRIGGERED` — B 5/5, A 5/5.

---

## 4. Cue 27 yaşam döngüsü

**Gold:** Selin düzeltme + bugün 16.00 · Can correlation ID  

**Sonuç:** B 5/5 PASS, A 5/5 PASS. Compound split bu cue için darboğaz değil.

---

## 5. Kanıtlanan root cause

Sistemik: **`ACTION_TITLE_CONTEXT_LOSS`**

Kanıt:

1. Lineage’de Can aksiyonu final’e kadar yaşıyor.  
2. Metin sürekli `Başlık düzeltmesini yapacak.` — UTF-8 / cue-49 bağlamı yok.  
3. `ACTION_TITLE_BACKFILL` stage event’i 0 (tetiklenmedi veya no-op).  
4. Compound SPLIT yok; hipotez “splitter kısmi ayırdı” **çürütüldü**.  
5. Burak her koşuda PASS → ilk clause miss / final mapping loss değil.

Artifact: `cue-51-trace.*`, `compound-action-root-cause.*`, her run `lineage.json` + `final.note.json`.

---

## 6. A’nın farkı

Cue 51’de A, B’den **iyi değil** (ikisi de 0/5 UTF-8 Can).  
Cue 27’de ikisi de tam PASS.  
A’daki CrossType / title-backfiller kodu bu miss’i düzeltmedi.

---

## 7. Önerilen tek düzeltme

**Ana:** `ActionTitleEvidenceBackfiller` — Can / “başlık düzelt” clause’una cue-49 UTF-8 bağlamını geri yaz.  

**Destek (gerekirse):** LLM’in zayıf başlık üretmesini azaltmak için extraction prompt’a bak — ama önce backfill; gate/cross-type/tokenizer/typed-resolver **henüz yok**.

---

## 8. Testler

```
./mvnw -pl modules/ai-processing -Dtest='LineageOutputEquivalenceTest,ItemLineageRecorderTest' test
Tests run: 23 · Failures: 0 · Errors: 0 · Skipped: 0
```

---

## 9. Git / artifact konumları

Nanobase: `/data/nanobaseai/actenora/eval/standup-ba/phase1b/lineage-rerun/`  
Local: `artifacts/easymeeting-quality/lineage-rerun/`

---

## 10. Faz kararı

```
COMPOUND_ACTION_ROOT_CAUSE_CONFIRMED
```

Reason codes: `ACTION_TITLE_CONTEXT_LOSS`, `ACTION_TITLE_BACKFILL_NOT_TRIGGERED`  
(Compound decomposer / LLM first-clause miss bu kampanyada **onaylanmadı**.)
