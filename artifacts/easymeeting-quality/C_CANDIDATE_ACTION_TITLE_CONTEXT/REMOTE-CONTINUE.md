# C kampanyası Cursor kapalıyken de devam eder

Nanobase üzerinde iki detached `screen` oturumu çalışıyor:

1. `actenora-c-candidate` — 5 gerçek LLM koşusu + jar swap/restore
2. `actenora-c-finalize` — kampanya bitince skor + `final-report.md` + `stability.json`

## Durum kontrol

```bash
ssh nanobase 'cat /data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT/campaign.STATUS.txt; cat /data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT/FINALIZE.STATUS.txt 2>/dev/null; screen -ls | grep actenora-c'
```

## Artifact yolu (nanobase)

`/data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT/`

## Pull (bitince)

```bash
rsync -az nanobase:/data/nanobaseai/actenora/eval/standup-ba/phase1b/C_CANDIDATE_ACTION_TITLE_CONTEXT/ \
  artifacts/easymeeting-quality/C_CANDIDATE_ACTION_TITLE_CONTEXT/
```

Commit oluşturulmadı. Production deploy yok (eval jar swap + restore).
