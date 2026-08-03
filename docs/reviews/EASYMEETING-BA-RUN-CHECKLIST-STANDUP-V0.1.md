# B/A Koşu Checklist — `01_15dk_daily_standup`

> Dondurulmuş gold: `modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json`  
> Gold commit: `8f22436` (yalnızca gold JSON; aday kodu değiştirmez)  
> Hazırlanma: 2026-08-03 (past-meetings E2E `ALL DONE` sonrası güncel snapshot)

---

## 0. Dondurulmuş kimlikler (değiştirme)

| Alan | Değer |
|------|--------|
| Fixture | `01_15dk_daily_standup.vtt` |
| Fixture SHA-256 | `8fe0cdf537701d6f3b4bb6d05cd26e98ff1f6069936def2754b3b8d2ab5f2ae4` |
| Gold path | `modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json` |
| Gold SHA-256 | `beb7f1610d0ed36401bb5ab592a1f2bfa9b8411ce4588c37828a8570023cda46` |
| Gold version | `0.1` / schema `1.0` |
| **B — Kontrol** | `f9c699f` (`f9c699f4753d7017d64aede84c6ee7da056a5f66`) |
| **A — Aday** | `472172a` (`472172a035da047189b292f7f6ee677c115963ad`) |
| Tip HEAD (gold dosyası) | `8f22436` — A kodundan sonra; **yalnız gold ekler** |
| Ancestry | `f9c699f` → `472172a` → `8f22436` |

### Prompt hash’leri (A = `472172a` / tip ile aynı prompt içeriği)

| Prompt | SHA-256 |
|--------|---------|
| `chunk-extraction.v1.txt` | `3658d784add04c443eb93205a491edeafac5e71f4c89807b9d95512c471f62d7` |
| `editorial-summary.v1.txt` | `c9e9675832018cc8eadf49237ab82c5d18d4560871a9f37f0fc0462cbbca4936` |
| `system-meeting-analyst.v2.txt` | `660adb7807646f7e5e178147bbe50e395346f1e207ba4cf8e053ff8d54868b3c` |

### Ortak koşu ayarları (gold `evaluationConfig` + canlı prodlike)

| Parametre | Gold / kod | Prodlike (nanobase, 2026-08-03) |
|-----------|------------|----------------------------------|
| Finalization | `editorial` | `ACTENORA_AI_FINALIZATION_MODE=editorial` |
| Pipeline mode | ölçümde **aynı tutulmalı** | **Şu an `legacy`** (`ACTENORA_AI_PIPELINE_MODE=legacy`) |
| Gate threshold | `4.5` | env override yok → default `4.5` |
| Chunk target / overlap | `3500` / `250` | kod sabiti |
| Extraction max tokens | `6144` | kod sabiti |
| Temp / topP / topK | `0.1` / `0.85` / `20` | adapter sabiti |
| Served model (fast+final) | koşu loguna yaz | `nanobase-qwen36-35b-a3b-mtp` |
| Provider base | koşu loguna yaz | `http://host.docker.internal:8010` |
| Provider kind | | `nanobaseai` |
| Finalization max out | | `768` |
| Finalization timeout | | `1800s` |
| Provider read timeout | | `7200s` |
| Provider max attempts | | `5` |
| Repeat runs | ≥ **5** / ağaç | |

**Kritik uyarı:** Prodlike şu an `legacy` pipeline kullanıyor. B ve A aynı `ACTENORA_AI_PIPELINE_MODE` ile koşulmalı (`legacy` veya `staged` — karıştırma). Gold editorial varsayar; staged vs legacy farkı ayrı faktör olarak loglanmalı.

---

## 1. Artifact klasör yapısı

Kök (önerilen):

```text
artifacts/eval/standup-v0.1/
  MANIFEST.json
  gold/
    01_15dk_daily_standup.gold.v1.json          # kopya veya symlink
    01_15dk_daily_standup.vtt
  B_HEAD_f9c699f/
    env.snapshot.txt
    prompt.hashes.txt
    run_01/
      meta.json
      extraction.raw.jsonl          # chunk başına ham LLM JSON
      extraction.grounded.jsonl     # gate+grounding sonrası
      merge.validated.json          # merge + post-filter + validate
      final.note.json               # FinalNoteDraft / handoff draft
      minutes.editorial.json        # editorial summary çıktısı (varsa)
      quality.flags.json
      job.log.txt                   # model alias, latency, retry, gate skips
      scorecard.json                # scorer çıktısı (sonra)
    run_02/ ...
    run_03/
    run_04/
    run_05/
    aggregate.summary.json
  A_CAND_472172a/
    env.snapshot.txt
    prompt.hashes.txt
    run_01/ ... (aynı alt dosyalar)
    ...
    aggregate.summary.json
  COMPARE/
    B_vs_A.md
    assertion_matrix.tsv
    score_delta.tsv
```

Run id kalıpları (gold ile uyumlu):

```text
B_HEAD_f9c699f_run_0N
A_CAND_472172a_run_0N
```

---

## 2. Koşu öncesi dondurma checklist

Her ağaç için koşuya başlamadan:

- [ ] `git checkout <commit>` ve `git status` temiz
- [ ] Prompt SHA-256’leri yukarıdaki tabloyla birebir
- [ ] Fixture SHA-256 birebir
- [ ] `ACTENORA_AI_FINALIZATION_MODE=editorial`
- [ ] `ACTENORA_AI_PIPELINE_MODE` B ve A’da aynı (`legacy` önerilir = prodlike parity)
- [ ] Aynı served-model id (fast = final = `nanobase-qwen36-35b-a3b-mtp` veya loglanan eşdeğer)
- [ ] Signal-gate enabled + threshold 4.5 (override yoksa OK)
- [ ] Soğuk başlangıç ayrı; ölçülen 5 run warm olabilir ama her run bağımsız artifact yazar
- [ ] `env.snapshot.txt` içine printenv (secret maskeli) + commit + image id yaz

`env.snapshot.txt` minimum alanlar:

```text
git_commit=
pipeline_mode=
finalization_mode=
fast_served_model_id=
final_served_model_id=
provider_base_url=
provider_kind=
signal_gate_enabled=
signal_gate_threshold=
chunk_target=
chunk_overlap=
temperature=
top_p=
top_k=
host_image_or_jar=
timestamp_utc=
```

---

## 3. B / A koşu komutları (iske düzey)

> Amaç: aynı VTT → extraction / merge / final artifact. Gerçek LLM gerekir.  
> Aşağıdaki iskelet prodlike veya local OpenAI-compatible runtime’a uyacak şekilde doldurulur.  
> Mevcut `scripts/run-realistic-vtt-suite.sh` upload+job takip eder; **ham chunk JSON’ları otomatik dump etmiyorsa** job/artifact store’dan veya pipeline debug dump ile tamamlanmalı.

### 3.1 Ortak

```bash
export FIXTURE="modules/ai-processing/src/test/resources/aiprocessing/eval/01_15dk_daily_standup.vtt"
export GOLD="modules/ai-processing/src/test/resources/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json"
export OUT_ROOT="artifacts/eval/standup-v0.1"
export ACTENORA_AI_FINALIZATION_MODE=editorial
export ACTENORA_AI_PIPELINE_MODE=legacy   # B ve A'da AYNI
mkdir -p "$OUT_ROOT/gold" "$OUT_ROOT/COMPARE"
cp "$GOLD" "$OUT_ROOT/gold/"
cp "$FIXTURE" "$OUT_ROOT/gold/"
```

### 3.2 B — Kontrol (`f9c699f`)

```bash
git checkout f9c699f
# rebuild/deploy veya local spring-boot:run — ölçüm ortamına göre
TREE_DIR="$OUT_ROOT/B_HEAD_f9c699f"
mkdir -p "$TREE_DIR"
# env snapshot + prompt hashes kaydet
for N in 01 02 03 04 05; do
  RUN_DIR="$TREE_DIR/run_${N}"
  mkdir -p "$RUN_DIR"
  # 1) VTT upload / admit
  # 2) job tamamlanınca:
  #    - extraction.raw.jsonl
  #    - merge.validated.json
  #    - final.note.json
  #    - job.log.txt (model, tokens, latency, retries, SKIPPED_LOW_SIGNAL, qualityFlags)
  # 3) meta.json: {runId, commit, jobId, meetingId, startedAt, finishedAt}
done
```

### 3.3 A — Aday (`472172a`)

```bash
git checkout 472172a
TREE_DIR="$OUT_ROOT/A_CAND_472172a"
# B ile aynı adımlar, aynı model/config
```

### 3.4 Artifact zorunlu alanlar (`meta.json`)

```json
{
  "runId": "B_HEAD_f9c699f_run_01",
  "tree": "B",
  "commit": "f9c699f4753d7017d64aede84c6ee7da056a5f66",
  "jobId": "",
  "meetingOccurrenceId": "",
  "transcriptId": "",
  "pipelineMode": "legacy",
  "finalizationMode": "editorial",
  "servedModelFast": "nanobase-qwen36-35b-a3b-mtp",
  "servedModelFinal": "nanobase-qwen36-35b-a3b-mtp",
  "promptHashes": {
    "chunkExtraction": "",
    "editorialSummary": "",
    "systemV2": ""
  },
  "gateSkips": [],
  "retries": 0,
  "qualityFlags": []
}
```

---

## 4. Otomatik scorer checklist (gold şemasına bağlı)

Scorer girdisi: `final.note.json` (+ mümkünse `merge.validated.json`) × gold JSON.

### 4.1 Zorunlu sayaçlar (`expectedRequiredCounts` / `EXACT_REQUIRED_COUNT`)

| Assertion | Beklenen |
|-----------|----------|
| `ASSERT-DECISION-COUNT` | decisions **2** |
| `ASSERT-ACTION-COUNT` | actionItems **7** |
| `ASSERT-RISK-COUNT` | risks **2** |
| `ASSERT-FACT-COUNT` | importantFacts **2** |
| `ASSERT-OPEN-QUESTION-COUNT` | openQuestions **12** |

### 4.2 Kritik yasaklar

| Assertion | Kural |
|-----------|--------|
| `ASSERT-STATUS-QUO-FP` | cue **19, 43** → decision **0** (`critical`) |
| `ASSERT-META-LEAKAGE` | cue 3,17,41,53–58 → hiçbir output tipinde **0** |
| Hallucinated decision | gold dışı karar **0** |
| Owner / date hallucination | **0** |

### 4.3 Compound + tarih bağlama

| Assertion | Kural |
|-----------|--------|
| `ASSERT-COMPOUND-SPLIT-27` | cue 27 → **A-03 + A-04** (2 aksiyon) |
| `ASSERT-COMPOUND-SPLIT-51` | cue 51 → **A-06 + A-07** (2 aksiyon) |
| `ASSERT-DATE-NO-CROSSOVER-27` | `bugün 16.00` yalnız **A-03/Selin**; **A-04/Can** tarihsiz |
| `ASSERT-DATE-NO-CROSSOVER-51` | `yarın öğlene kadar` yalnız **A-07/Burak**; **A-06/Can** tarihsiz |

### 4.4 Fact / OQ / proposal

| Assertion | Kural |
|-----------|--------|
| `ASSERT-FACT-UNIQUENESS` | F-01, F-02 tekil (6/8 ve 30/32 tekrarı çoğaltma) |
| `ASSERT-OQ-NO-CROSS-TOPIC-DEDUP` | Q-02/08, Q-03/09, Q-04/10, Q-06/12 birleştirilmesin |
| `ASSERT-SUPERSEDED-PROPOSAL-P01` | P-01 final proposals’ta kalmasın (D-01) |

### 4.5 Optional (recall fail değil)

`optionalItems[].missingIsRecallFailure = false` — audit/sprint/iletişim riskleri, cue 28/52.

### 4.6 Kabul kapıları (`acceptanceGates`)

| Kapı | Eşik |
|------|------|
| overallScoreMinimum | **85** |
| criticalDecisionRecallMinimum | **0.95** |
| actionRecallMinimum | **0.90** |
| hallucinatedDecisionMaximum | **0** |
| ownerHallucinationMaximum | **0** |
| dateHallucinationMaximum | **0** |
| statusQuoDecisionFalsePositiveMaximum | **0** |
| crossTypeDuplicateRateMaximum | **0.03** |
| closingMetaLeakageMaximum | **0** |

### 4.7 Root-cause kodları (kaçırılan / fazla madde)

Her miss/FP için zorunlu etiket:

```text
GATE_FALSE_NEGATIVE
LLM_MISSED_ITEM
LLM_HALLUCINATION
POST_FILTER_FALSE_DROP
CROSS_TYPE_LEAKAGE
DUPLICATE_ITEM
OWNER_HALLUCINATION
DATE_HALLUCINATION
CONTEXT_LOSS
FINAL_SUMMARY_DISTORTION
STATUS_QUO_DECISION_FALSE_POSITIVE
```

`scorecard.json` önerilen şekil:

```json
{
  "runId": "A_CAND_472172a_run_01",
  "goldVersion": "0.1",
  "counts": {"decisions": {"expected": 2, "actual": 0, "tp": 0, "fp": 0, "fn": 0}},
  "assertions": [{"id": "ASSERT-STATUS-QUO-FP", "pass": true}],
  "items": [{"goldId": "D-01", "status": "TP|FP|FN", "rootCause": null}],
  "gates": {"passed": false, "failures": []},
  "overallScore": 0
}
```

---

## 5. Karşılaştırma (B vs A)

5 run sonrası her ağaç için:

1. Assertion pass rate (run ortalaması + worst run)
2. Decision/action/risk precision & recall (gold id eşlemesi: evidence ∩ paraphrase)
3. Status-quo FP rate (19/43)
4. Compound split success (27, 51)
5. Date crossover incidents
6. Gate skip sayısı ve `GATE_FALSE_NEGATIVE` sayısı
7. Editorial summary: structured item çelişkisi var mı? (`FINAL_SUMMARY_DISTORTION`)

`COMPARE/B_vs_A.md` şablonu:

```text
# B (f9c699f) vs A (472172a)

## Gate sonuçları
## Karar/aksiyon/risk Δ
## Compound/date Δ
## Leakage/status-quo Δ
## Sonuç: A iyileşti mi / nötr / geriledi?
## Kabul kapısı: PASS/FAIL
```

---

## 6. Güncel ortam notları (2026-08-03)

### Past-meetings E2E (nanobase) — tamamlandı

`2026-08-03T11:15:02Z` — `ALL DONE`

| Meeting | Sonuç | latest_ai |
|---------|--------|-----------|
| teams entegrasyon toplantısı | `ALREADY_READY` | `SUCCEEDED` |
| Fibabank (cal) | `SKIP_NO_TEAMS` | — |
| Yutomat (cal) | `SKIP_NO_TEAMS` | — |
| Yapay Zeka Görüşmesi | `ALREADY_READY` | `SUCCEEDED` |

Bu E2E, standup gold B/A koşusu **değildir**; yalnızca ortamın ayakta ve AI job’ların SUCCEEDED üretebildiğini gösterir.

### Ölçüm için net kararlar

1. **Kod baseline’ları:** B=`f9c699f`, A=`472172a` (gold ile kilitli). `8f22436` sadece gold dosyası.
2. **Pipeline:** B ve A’da aynı mode; prodlike parity için **`legacy`**.
3. **Finalization:** `editorial`.
4. **Model:** `nanobase-qwen36-35b-a3b-mtp` (fast+final).
5. **Scorer kaynağı:** dondurulmuş gold JSON; ID/tolerans/assertion’lar oradan okunur.

---

## 7. Hemen sonraki uygulama sırası

1. `artifacts/eval/standup-v0.1/` ağacını oluştur.
2. B=`f9c699f` için 5 run artifact yaz.
3. A=`472172a` için 5 run artifact yaz.
4. Gold assertion scorer’ı çalıştır → `scorecard.json` × 10.
5. `COMPARE/B_vs_A.md` üret; kabul kapılarına göre PASS/FAIL ver.

Bu checklist, gold v0.1 şeması ve 2026-08-03 prodlike env snapshot’ı ile uyumludur.
