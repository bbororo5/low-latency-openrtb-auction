package com.bbororo.rtb.ssp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class SspModuleArchitectureTest {

    @Test
    void ssp_must_depend_only_on_ssp_shared_and_jdk_packages() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp..",
                        "com.bbororo.rtb.shared..",
                        "com.fasterxml.jackson..",
                        "java.."
                );

        rule.check(classes);
    }
}
