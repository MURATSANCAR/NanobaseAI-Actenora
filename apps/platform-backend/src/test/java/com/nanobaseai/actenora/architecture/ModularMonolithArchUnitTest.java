package com.nanobaseai.actenora.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * FAZ 3 ArchUnit suite — enforces modular monolith boundaries in code.
 */
class ModularMonolithArchUnitTest {

    private static final String BASE = "com.nanobaseai.actenora";

    private static final String[] BOUNDED_CONTEXTS = {
            "identity", "tenant", "policy", "microsoftconnection", "meeting", "transcript",
            "modelmanagement", "aiprocessing", "meetingintelligence", "approval", "template",
            "delivery", "audit", "operations"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void domainLayerMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("Domain layer must stay framework-free")
                .check(classes);
    }

    @Test
    void meetingMustNotUseTranscriptRepository() {
        noClasses()
                .that().resideInAPackage(BASE + ".meeting..")
                .should().dependOnClassesThat().haveSimpleName("TranscriptRepository")
                .because("Meeting must not use TranscriptRepository")
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".meeting..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".transcript.infrastructure..")
                .because("Meeting must not depend on transcript infrastructure")
                .check(classes);
    }

    @Test
    void transcriptMustNotImportMeetingEntity() {
        noClasses()
                .that().resideInAPackage(BASE + ".transcript..")
                .should().dependOnClassesThat().haveSimpleName("MeetingEntity")
                .because("Transcript must not import MeetingEntity")
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".transcript..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".meeting.domain..")
                .because("Transcript must not import meeting domain types")
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".transcript..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".meeting..")
                .because("Transcript must not depend on meeting module (opaque meetingOccurrenceId via contract only)")
                .check(classes);
    }

    @Test
    void aiProcessingMustNotAccessIntelligenceTables() {
        noClasses()
                .that().resideInAPackage(BASE + ".aiprocessing..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".meetingintelligence.infrastructure..",
                        BASE + ".meetingintelligence.domain.."
                )
                .because("AI Processing must not access meeting-intelligence tables/persistence")
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".aiprocessing..")
                .should().dependOnClassesThat().haveSimpleName("MeetingIntelligenceSchema")
                .check(classes);
    }

    @Test
    void deliveryMustNotImportApprovalEntities() {
        noClasses()
                .that().resideInAPackage(BASE + ".delivery..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".approval.domain..")
                .because("Delivery must not import approval entities")
                .check(classes);
    }

    @Test
    void sharedKernelMustNotContainServices() {
        noClasses()
                .that().resideInAPackage(BASE + ".sharedkernel..")
                .and().haveSimpleNameEndingWith("Service")
                .should().resideOutsideOfPackage(BASE + ".sharedkernel..")
                .allowEmptyShould(true)
                .because("Shared-kernel must not contain *Service types")
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".sharedkernel..")
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .allowEmptyShould(true)
                .because("Shared-kernel must not contain @Service beans")
                .check(classes);
    }

    @Test
    void infrastructureClassesMustNotResideInDomainPackages() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .because("Repositories are infrastructure, not domain")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(jakarta.persistence.Entity.class)
                .allowEmptyShould(true)
                .because("JPA entities must not live in domain packages")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(org.springframework.stereotype.Component.class)
                .allowEmptyShould(true)
                .because("Spring infrastructure stereotypes must not live in domain packages")
                .check(classes);
    }

    @Test
    void apiExternalInternalClassesAreNotAccessedFromOtherModules() {
        for (String module : BOUNDED_CONTEXTS) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + "..")
                    .and().resideOutsideOfPackage(BASE + "." + module + "..")
                    // Composition root may wire ports/adapters across BCs.
                    .and().resideOutsideOfPackage(BASE + ".security..")
                    .and().resideOutsideOfPackage(BASE + ".platform..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + "." + module + ".domain..",
                            BASE + "." + module + ".application..",
                            BASE + "." + module + ".infrastructure.."
                    )
                    .because(module + " internal packages are not part of the public API")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    @Test
    void permissionEnumLivesInIdentityPublicApi() {
        classes()
                .that().haveSimpleName("Permission")
                .and().resideInAPackage(BASE + ".identity..")
                .should().resideInAPackage(BASE + ".identity.api..")
                .because("Permission must live in identity.api for cross-module use")
                .check(classes);

        noClasses()
                .that().resideOutsideOfPackage(BASE + ".identity..")
                .and().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "identity.domain.Permission",
                                javaClass -> javaClass.getPackageName().equals(BASE + ".identity.domain")
                                        && javaClass.getSimpleName().equals("Permission")
                        )
                )
                .allowEmptyShould(true)
                .because("Permission must not leak from identity.domain")
                .check(classes);
    }

    @Test
    void crossModuleRepositoryInjectionIsForbidden() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".transcript..")
                .and().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat().haveSimpleName("TranscriptRepository")
                .allowEmptyShould(true)
                .because("TranscriptRepository must not be injected outside transcript")
                .check(classes);

        noClasses()
                .that().resideOutsideOfPackage(BASE + ".meeting..")
                .and().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat().haveSimpleName("MeetingRepository")
                .allowEmptyShould(true)
                .because("MeetingRepository must not be injected outside meeting")
                .check(classes);
    }

    @Test
    void noCyclicDependenciesBetweenBoundedContexts() {
        slices()
                .matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
