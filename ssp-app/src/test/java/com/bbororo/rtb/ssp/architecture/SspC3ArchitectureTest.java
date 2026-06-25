package com.bbororo.rtb.ssp.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SspC3ArchitectureTest {

    @Test
    void ssp_internal_components_must_not_depend_on_adapters() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.bbororo.rtb.ssp.requesthandler..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.ssp.dspgateway..",
                        "com.bbororo.rtb.ssp.bidjudge..",
                        "com.bbororo.rtb.ssp.winnerselector.."
                )
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.ssp.adapter..")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void ssp_request_component_must_not_skip_auction_flow() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp.requesthandler..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.dspgateway..",
                        "com.bbororo.rtb.ssp.bidjudge..",
                        "com.bbororo.rtb.ssp.winnerselector.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void ssp_bid_judge_must_not_depend_on_winner_selector() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp.bidjudge..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.ssp.winnerselector..")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void ssp_winner_selector_must_not_depend_on_dsp_gateway() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp.winnerselector..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.ssp.dspgateway..")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
