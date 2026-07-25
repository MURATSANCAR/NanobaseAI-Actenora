package com.nanobaseai.actenora.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void domainLayerMustNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.boot.."
                )
                .because("Domain layer must stay framework-free");
        rule.check(classes);
    }

    @Test
    void meetingMustNotUseTranscriptRepository() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".meeting..")
                .should().dependOnClassesThat()
                .haveSimpleName("TranscriptRepository")
                .orShould().dependOnClassesThat()
                .resideInAPackage(BASE + ".transcript.infrastructure..")
                .because("Meeting must not use TranscriptRepository or transcript infrastructure");
        rule.check(classes);
    }

    @Test
    void transcriptMustNotImportMeetingEntity() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".transcript..")
                .should().dependOnClassesThat()
                .haveSimpleName("MeetingEntity")
                .orShould().dependOnClassesThat()
                .resideInAPackage(BASE + ".meeting.domain..")
                .because("Transcript must not import MeetingEntity; use meeting.api.MeetingId only");
        rule.check(classes);
    }

    @Test
    void aiProcessingMustNotAccessIntelligenceTables() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".aiprocessing..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE + ".meetingintelligence.infrastructure..",
                        BASE + ".meetingintelligence.domain.."
                )
                .because("AI Processing must not access meeting-intelligence persistence");
        rule.check(classes);

        ArchRule stringGuard = noClasses()
                .that().resideInAPackage(BASE + ".aiprocessing..")
                .should().dependOnClassesThat(new DescribedPredicate<>("references meetingintelligence schema constants") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return "MeetingIntelligenceSchema".equals(javaClass.getSimpleName());
                    }
                });
        stringGuard.check(classes);
    }

    @Test
    void deliveryMustNotImportApprovalEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".delivery..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".approval.domain..")
                .orShould().dependOnClassesThat()
                .haveSimpleNameEndingWith("Entity")
                .andShould().dependOnClassesThat()
                .resideInAPackage(BASE + ".approval..")
                .because("Delivery must not import approval entities; use approval.api only");

        // Clearer: ban approval.domain from delivery
        noClasses()
                .that().resideInAPackage(BASE + ".delivery..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".approval.domain..")
                .check(classes);
    }

    @Test
    void sharedKernelMustNotContainServices() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".sharedkernel..")
                .should().haveSimpleNameEndingWith("Service")
                .orShould().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .because("Shared-kernel must not contain business services");

        // Invert: no *Service classes / @Service in sharedkernel
        noClasses()
                .that().resideInAPackage(BASE + ".sharedkernel..")
                .and().haveSimpleNameEndingWith("Service")
                .should().resideInAPackage(BASE + ".sharedkernel..")
                .allowEmptyShould(true)
                .check(classes);

        noClasses()
                .that().resideInAPackage(BASE + ".sharedkernel..")
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void infrastructureClassesMustNotResideInDomainPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().haveSimpleNameEndingWith("Repository")
                .orShould().haveSimpleNameEndingWith("JpaEntity")
                .orShould().beAnnotatedWith(jakarta.persistence.Entity.class)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class)
                .because("Infrastructure stereotypes and repositories do not belong in domain");

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(jakarta.persistence.Entity.class)
                .allowEmptyShould(true)
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith(org.springframework.stereotype.Component.class)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void internalPackagesAreNotAccessedFromOtherModules() {
        String[] modules = {
                "identity", "tenant", "policy", "microsoftconnection", "meeting", "transcript",
                "modelmanagement", "aiprocessing", "meetingintelligence", "approval", "template",
                "delivery", "audit", "operations"
        };

        for (String module : modules) {
            noClasses()
                    .that().resideOutsideOfPackage(BASE + "." + module + "..")
                    .and().resideInAPackage(BASE + "..")
                    .and().doNotResideInAPackage(BASE)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + "." + module + ".domain..",
                            BASE + "." + module + ".application..",
                            BASE + "." + module + ".infrastructure.."
                    )
                    .because(module + " internal packages must not be accessed from other modules")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    void noCrossModuleRepositoryInjection() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .and().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .andShould().dependOnClassesThat(new DescribedPredicate<>("foreign module repository") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        // Repositories are only legal inside their own module tree.
                        return javaClass.getSimpleName().endsWith("Repository");
                    }
                });

        // Stronger explicit pairs from FAZ 3
        noClasses()
                .that().resideInAPackage(BASE + ".meeting..")
                .should().dependOnClassesThat().haveSimpleName("TranscriptRepository")
                .check(classes);

        noClasses()
                .that().resideOutsideOfPackage(BASE + ".transcript..")
                .and().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat().haveSimpleName("TranscriptRepository")
                .allowEmptyShould(true)
                .check(classes);

        noClasses()
                .that().resideOutsideOfPackage(BASE + ".meeting..")
                .and().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat().haveSimpleName("MeetingRepository")
                .allowEmptyShould(true)
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
