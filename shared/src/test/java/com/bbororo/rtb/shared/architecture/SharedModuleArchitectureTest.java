package com.bbororo.rtb.shared.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class SharedModuleArchitectureTest {

    @Test
    void shared_must_depend_only_on_shared_and_jdk_packages() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.shared");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.shared..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.shared..",
                        "java.."
                );

        rule.check(classes);
    }
}
