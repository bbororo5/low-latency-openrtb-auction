package com.bbororo.rtb.shared.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SharedModuleArchitectureTest {

    @Test
    void shared_must_not_depend_on_ssp_or_dsp_modules() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.shared");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp..",
                        "com.bbororo.rtb.dsp.."
                );

        rule.check(classes);
    }
}
