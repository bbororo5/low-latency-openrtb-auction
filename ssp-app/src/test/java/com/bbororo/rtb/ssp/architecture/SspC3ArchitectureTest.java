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
                        "com.bbororo.rtb.ssp.request..",
                        "com.bbororo.rtb.ssp.auction..",
                        "com.bbororo.rtb.ssp.dsp..",
                        "com.bbororo.rtb.ssp.judge..",
                        "com.bbororo.rtb.ssp.winner.."
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
                .that().resideInAPackage("com.bbororo.rtb.ssp.request..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.dsp..",
                        "com.bbororo.rtb.ssp.judge..",
                        "com.bbororo.rtb.ssp.winner.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void ssp_bid_judge_must_not_depend_on_winner_selector() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp.judge..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.ssp.winner..")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void ssp_winner_selector_must_not_depend_on_dsp_gateway() {
        var classes = new ClassFileImporter().importPackages("com.bbororo.rtb.ssp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.bbororo.rtb.ssp.winner..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.bbororo.rtb.ssp.dsp..")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
