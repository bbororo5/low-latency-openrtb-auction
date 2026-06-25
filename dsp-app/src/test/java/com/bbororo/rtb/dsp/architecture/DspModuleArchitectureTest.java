package com.bbororo.rtb.dsp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DspModuleArchitectureTest {

    @Test
    void dsp_must_not_depend_on_ssp_application_internals() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.bbororo.rtb.ssp..");

        rule.check(classes);
    }
}
