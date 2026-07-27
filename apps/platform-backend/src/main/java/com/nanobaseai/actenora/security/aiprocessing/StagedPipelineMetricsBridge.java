package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageCompletionService;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes staged-pipeline counters into OTel gauges.
 */
@Component
public class StagedPipelineMetricsBridge {

    private final StageCompletionService completion;
    private final MetricRecorder metrics;

    public StagedPipelineMetricsBridge(StageCompletionService completion, MetricRecorder metrics) {
        this.completion = completion;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "PT30S")
    void publish() {
        metrics.gauge(ActenoraMetric.MEETING_TRIAGE_EARLY_EXIT, completion.earlyExitTotal());
    }
}
