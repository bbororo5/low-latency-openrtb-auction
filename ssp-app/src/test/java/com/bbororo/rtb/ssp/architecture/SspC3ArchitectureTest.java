package com.bbororo.rtb.ssp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class SspC3ArchitectureTest {

    @Test
    void request_handler_must_depend_only_on_request_auction_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.requesthandler..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.requesthandler..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void auction_flow_must_depend_only_on_orchestrated_ssp_components_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.auctionflow..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.ssp.dspgateway..",
                        "com.bbororo.rtb.ssp.bidjudge..",
                        "com.bbororo.rtb.ssp.winnerselector..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void inventory_must_depend_only_on_inventory_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.inventory..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.inventory..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void bid_request_builder_must_depend_only_on_bid_request_inventory_slot_request_auction_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.bidrequest..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.bidrequest..",
                        "com.bbororo.rtb.ssp.inventory..",
                        "com.bbororo.rtb.ssp.slotrequest..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void slot_request_handler_must_depend_only_on_slot_request_bid_request_inventory_auction_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.slotrequest..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.slotrequest..",
                        "com.bbororo.rtb.ssp.bidrequest..",
                        "com.bbororo.rtb.ssp.inventory..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void dsp_gateway_must_depend_only_on_dsp_gateway_shared_auction_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.dspgateway..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.dspgateway..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.shared..",
                        "com.fasterxml.jackson..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void bid_judge_must_depend_only_on_bid_judge_gateway_auction_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.bidjudge..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.bidjudge..",
                        "com.bbororo.rtb.ssp.dspgateway..",
                        "com.bbororo.rtb.ssp.auctionflow..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void winner_selector_must_depend_only_on_winner_bid_judge_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.ssp.winnerselector..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.ssp.winnerselector..",
                        "com.bbororo.rtb.ssp.bidjudge..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    private static JavaClasses mainClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.ssp");
    }
}
