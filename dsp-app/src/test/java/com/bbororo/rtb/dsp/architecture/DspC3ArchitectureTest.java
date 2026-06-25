package com.bbororo.rtb.dsp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DspC3ArchitectureTest {

    @Test
    void dsp_internal_components_must_not_depend_on_adapters() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.bbororo.rtb.dsp.bid..",
                        "com.bbororo.rtb.dsp.campaign..",
                        "com.bbororo.rtb.dsp.match..",
                        "com.bbororo.rtb.dsp.price..",
                        "com.bbororo.rtb.dsp.response.."
                )
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.dsp.adapter..")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void campaign_lookup_must_not_depend_on_later_bid_decision_steps() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp.campaign..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.match..",
                        "com.bbororo.rtb.dsp.price..",
                        "com.bbororo.rtb.dsp.response.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void matcher_must_not_depend_on_pricing_or_response_building() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp.match..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.price..",
                        "com.bbororo.rtb.dsp.response.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void pricing_must_not_depend_on_response_building() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp.price..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.dsp.response..")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
