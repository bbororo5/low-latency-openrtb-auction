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
                        "com.bbororo.rtb.dsp.bidhandler..",
                        "com.bbororo.rtb.dsp.campaignlookup..",
                        "com.bbororo.rtb.dsp.matcher..",
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.dsp.bidbuilder.."
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
                .that().resideInAPackage("com.bbororo.rtb.dsp.campaignlookup..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.matcher..",
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.dsp.bidbuilder.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void matcher_must_not_depend_on_pricing_or_response_building() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp.matcher..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.dsp.bidbuilder.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void pricing_must_not_depend_on_response_building() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.dsp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.dsp.pricing..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.dsp.bidbuilder..")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
