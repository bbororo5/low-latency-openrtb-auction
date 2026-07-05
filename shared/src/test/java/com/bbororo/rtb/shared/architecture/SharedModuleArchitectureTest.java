package com.bbororo.rtb.shared.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class SharedModuleArchitectureTest {

    @Test
    void shared_models_must_depend_only_on_shared_and_jdk_packages() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.shared");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.shared..")
                .and().resideOutsideOfPackage("com.bbororo.rtb.shared.openrtb.codec..")
                .and().resideOutsideOfPackage("com.bbororo.rtb.shared.observability..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.shared..",
                        "java.."
                );

        rule.check(classes);
    }

    @Test
    void shared_openrtb_codec_may_depend_on_jackson() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.shared");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.shared.openrtb.codec..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.shared..",
                        "com.fasterxml.jackson..",
                        "java.."
                );

        rule.check(classes);
    }

    @Test
    void shared_observability_may_depend_on_httpserver_and_micrometer() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.shared");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.shared.observability..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.shared..",
                        "com.sun.net.httpserver..",
                        "io.micrometer..",
                        "java.."
                );

        rule.check(classes);
    }
}
