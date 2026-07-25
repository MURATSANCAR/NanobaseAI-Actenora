package com.nanobaseai.actenora.template.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.application.DocumentRenderService;
import com.nanobaseai.actenora.template.application.DocumentRenderWorker;
import com.nanobaseai.actenora.template.application.TemplateStudioService;
import com.nanobaseai.actenora.template.application.port.out.DocumentRenderer;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.SchemaJsonParser;
import com.nanobaseai.actenora.template.infrastructure.TemplateApiFacade;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryMeetingTemplateRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryNoteTemplateLockRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderJobRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderedDocumentRepository;
import com.nanobaseai.actenora.template.infrastructure.render.HtmlPdfDocumentRenderer;
import com.nanobaseai.actenora.template.infrastructure.storage.InMemoryObjectStorage;
import com.nanobaseai.actenora.template.infrastructure.storage.MinioObjectStorage;
import com.nanobaseai.actenora.template.infrastructure.worker.DocumentRenderWorkerRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;

@Configuration
public class TemplateModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper templateObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean(InstantClock.class)
    InstantClock templateInstantClock() {
        return new InstantClock(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "actenora.object-storage", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage minioObjectStorage(
            @Value("${actenora.object-storage.endpoint}") String endpoint,
            @Value("${actenora.object-storage.region:us-east-1}") String region,
            @Value("${actenora.object-storage.access-key}") String accessKey,
            @Value("${actenora.object-storage.secret-key}") String secretKey,
            @Value("${actenora.object-storage.bucket}") String bucket) {
        return new MinioObjectStorage(URI.create(endpoint), region, accessKey, secretKey, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage inMemoryObjectStorage() {
        return new InMemoryObjectStorage();
    }

    @Bean
    @ConditionalOnMissingBean(MeetingTemplateRepository.class)
    MeetingTemplateRepository meetingTemplateRepository() {
        return new InMemoryMeetingTemplateRepository();
    }

    @Bean
    @ConditionalOnMissingBean(NoteTemplateLockRepository.class)
    NoteTemplateLockRepository noteTemplateLockRepository() {
        return new InMemoryNoteTemplateLockRepository();
    }

    @Bean
    @ConditionalOnMissingBean(RenderJobRepository.class)
    RenderJobRepository renderJobRepository() {
        return new InMemoryRenderJobRepository();
    }

    @Bean
    @ConditionalOnMissingBean(RenderedDocumentRepository.class)
    RenderedDocumentRepository renderedDocumentRepository() {
        return new InMemoryRenderedDocumentRepository();
    }

    @Bean
    SchemaJsonParser schemaJsonParser(ObjectMapper objectMapper) {
        return new SchemaJsonParser(objectMapper);
    }

    @Bean
    DocumentRenderer documentRenderer(ObjectMapper objectMapper) {
        return new HtmlPdfDocumentRenderer(objectMapper);
    }

    @Bean
    TemplateStudioService templateStudioService(
            MeetingTemplateRepository templateRepository,
            NoteTemplateLockRepository lockRepository,
            SchemaJsonParser schemaJsonParser,
            InstantClock clock) {
        return new TemplateStudioService(templateRepository, lockRepository, schemaJsonParser, clock);
    }

    @Bean
    DocumentRenderService documentRenderService(
            MeetingTemplateRepository templateRepository,
            NoteTemplateLockRepository lockRepository,
            RenderJobRepository jobRepository,
            RenderedDocumentRepository documentRepository,
            InstantClock clock) {
        return new DocumentRenderService(
                templateRepository, lockRepository, jobRepository, documentRepository, clock);
    }

    @Bean
    DocumentRenderWorker documentRenderWorker(
            RenderJobRepository jobRepository,
            RenderedDocumentRepository documentRepository,
            MeetingTemplateRepository templateRepository,
            DocumentRenderer documentRenderer,
            ObjectStorage objectStorage,
            InstantClock clock) {
        return new DocumentRenderWorker(
                jobRepository, documentRepository, templateRepository, documentRenderer, objectStorage, clock);
    }

    @Bean
    TemplateApi templateApi(
            TemplateStudioService studioService,
            DocumentRenderService renderService,
            RenderedDocumentRepository documentRepository) {
        return new TemplateApiFacade(studioService, renderService, documentRepository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "actenora.template.worker", name = "enabled", havingValue = "true")
    DocumentRenderWorkerRunner documentRenderWorkerRunner(
            DocumentRenderWorker worker,
            @Value("${actenora.template.worker.batch-size:10}") int batchSize) {
        return new DocumentRenderWorkerRunner(worker, batchSize);
    }
}
