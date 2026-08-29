package com.hippocampus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.HippocampusApplication;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.SimpleConditionEvent;

class HippocampusArchitectureTests {

    private static final String BASE_PACKAGE = "com.hippocampus";
    private static final List<String> FEATURE_MODULES = List.of(
            "identity",
            "learning",
            "progress",
            "review",
            "materials",
            "rag",
            "ai");
    private static final Set<String> APPROVED_MODULE_ROOTS = Set.of(
            "identity",
            "learning",
            "progress",
            "review",
            "materials",
            "rag",
            "ai",
            "shared",
            "bootstrap");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    static final ArchRule APPROVED_PACKAGE_ROOTS_RULE = classes()
            .should(belongToAnApprovedProductionPackage())
            .because("production classes must belong to an approved module, except for the application entry point");

    static final ArchRule DOMAIN_INDEPENDENCE_RULE = noClasses()
            .that().resideInAPackage(BASE_PACKAGE + "..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE_PACKAGE + "..api..",
                    BASE_PACKAGE + "..application..",
                    BASE_PACKAGE + "..infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence..",
                    "java.sql..",
                    "java.net.http..",
                    "com.google.genai..")
            .because("domain code must remain independent from transport, framework, persistence, provider, and infrastructure concerns")
            .allowEmptyShould(true);

    static final ArchRule APPLICATION_DIRECTION_RULE = noClasses()
            .that().resideInAPackage(BASE_PACKAGE + "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE_PACKAGE + "..api..",
                    BASE_PACKAGE + "..infrastructure..")
            .because("application code may depend inward on domain and ports but not outward on API or infrastructure")
            .allowEmptyShould(true);

    static final ArchRule API_APPLICATION_BOUNDARY_RULE = noClasses()
            .that().resideInAPackage(BASE_PACKAGE + "..api..")
            .or().areAnnotatedWith(RestController.class)
            .or().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE_PACKAGE + "..domain..",
                    BASE_PACKAGE + "..port..",
                    BASE_PACKAGE + "..infrastructure..",
                    "org.springframework.data..",
                    "jakarta.persistence..",
                    "java.sql..")
            .because("API adapters and controllers must call application use cases rather than bypassing them")
            .allowEmptyShould(true);

    static final ArchRule SHARED_INDEPENDENCE_RULE = noClasses()
            .that().resideInAPackage(BASE_PACKAGE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE_PACKAGE + ".identity..",
                    BASE_PACKAGE + ".learning..",
                    BASE_PACKAGE + ".progress..",
                    BASE_PACKAGE + ".review..",
                    BASE_PACKAGE + ".materials..",
                    BASE_PACKAGE + ".rag..",
                    BASE_PACKAGE + ".ai..",
                    BASE_PACKAGE + ".bootstrap..")
            .because("shared must contain only genuinely cross-cutting primitives and must not depend on feature modules")
            .allowEmptyShould(true);

    static final ArchRule BOOTSTRAP_DIRECTION_RULE = noClasses()
            .that().resideInAnyPackage(
                    BASE_PACKAGE + ".identity..",
                    BASE_PACKAGE + ".learning..",
                    BASE_PACKAGE + ".progress..",
                    BASE_PACKAGE + ".review..",
                    BASE_PACKAGE + ".materials..",
                    BASE_PACKAGE + ".rag..",
                    BASE_PACKAGE + ".ai..",
                    BASE_PACKAGE + ".shared..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".bootstrap..")
            .because("bootstrap may compose feature modules, but feature and shared code must not depend on bootstrap")
            .allowEmptyShould(true);

    @Test
    void productionClassesBelongToApprovedModuleRoots() {
        APPROVED_PACKAGE_ROOTS_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void domainCodeRemainsIndependentFromFrameworkAndInfrastructure() {
        DOMAIN_INDEPENDENCE_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationCodeDependsInward() {
        APPLICATION_DIRECTION_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void apiAdaptersDoNotBypassApplicationBoundaries() {
        API_APPLICATION_BOUNDARY_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void featureInfrastructureRemainsEncapsulated() {
        for (String module : FEATURE_MODULES) {
            String modulePackage = BASE_PACKAGE + "." + module;
            noClasses()
                    .that().resideOutsideOfPackages(
                            modulePackage + "..",
                            BASE_PACKAGE + ".bootstrap..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(modulePackage + ".infrastructure..")
                    .because(module + " infrastructure may be used only by its owning module or bootstrap")
                    .allowEmptyShould(true)
                    .check(PRODUCTION_CLASSES);
        }
    }

    @Test
    void sharedDoesNotDependOnFeatureModules() {
        SHARED_INDEPENDENCE_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void featureModulesDoNotDependOnBootstrap() {
        BOOTSTRAP_DIRECTION_RULE.check(PRODUCTION_CLASSES);
    }

    @Test
    void topLevelModulesRemainFreeOfCycles() {
        slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    private static ArchCondition<JavaClass> belongToAnApprovedProductionPackage() {
        return new ArchCondition<>("belong to an approved production package") {
            @Override
            public void check(JavaClass javaClass, com.tngtech.archunit.lang.ConditionEvents events) {
                if (isApprovedProductionClass(javaClass)) {
                    return;
                }

                events.add(SimpleConditionEvent.violated(
                        javaClass,
                        "%s resides outside the approved module roots; only %s may reside directly in %s"
                                .formatted(
                                        javaClass.getName(),
                                        HippocampusApplication.class.getName(),
                                        BASE_PACKAGE)));
            }
        };
    }

    private static boolean isApprovedProductionClass(JavaClass javaClass) {
        if (javaClass.getName().equals(HippocampusApplication.class.getName())) {
            return true;
        }

        String packageName = javaClass.getPackageName();
        return APPROVED_MODULE_ROOTS.stream()
                .map(module -> BASE_PACKAGE + "." + module)
                .anyMatch(modulePackage -> packageName.equals(modulePackage)
                        || packageName.startsWith(modulePackage + "."));
    }
}
