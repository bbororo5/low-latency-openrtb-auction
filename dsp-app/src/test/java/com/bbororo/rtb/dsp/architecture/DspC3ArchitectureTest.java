package com.bbororo.rtb.dsp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class DspC3ArchitectureTest {

    @Test
    void bid_handler_must_depend_only_on_bid_decision_components_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp.bidhandler..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.bidhandler..",
                        "com.bbororo.rtb.dsp.campaignlookup..",
                        "com.bbororo.rtb.dsp.matcher..",
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.dsp.bidbuilder..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void campaign_lookup_must_depend_only_on_campaign_lookup_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp.campaignlookup..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.campaignlookup..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void matcher_must_depend_only_on_matcher_campaign_lookup_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp.matcher..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.matcher..",
                        "com.bbororo.rtb.dsp.campaignlookup..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void pricing_must_depend_only_on_pricing_matcher_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp.pricing..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.dsp.matcher..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void bid_builder_must_depend_only_on_bid_builder_pricing_shared_and_jdk_packages() {
        var classes = mainClasses();

        ArchRule rule = classes()
                .that().resideInAPackage("com.bbororo.rtb.dsp.bidbuilder..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.bbororo.rtb.dsp.bidbuilder..",
                        "com.bbororo.rtb.dsp.pricing..",
                        "com.bbororo.rtb.shared..",
                        "java.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    private static JavaClasses mainClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bbororo.rtb.dsp");
    }
}
