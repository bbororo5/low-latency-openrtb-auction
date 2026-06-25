package com.bbororo.rtb.ssp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SspModuleArchitectureTest {

    @Test
    void ssp_must_not_depend_on_dsp_application_internals() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.bbororo.rtb.dsp..");

        rule.check(classes);
    }
}
