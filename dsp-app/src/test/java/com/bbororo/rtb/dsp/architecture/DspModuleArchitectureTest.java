package com.bbororo.rtb.dsp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class DspModuleArchitectureTest {

    @Test
    void dsp_must_depend_only_on_dsp_shared_and_jdk_packages() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp..",
                        "com.bbororo.rtb.shared..",
                        "com.sun.net.httpserver..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
