package dev.teolab.stocklab.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * 심플 DDD 계층 규칙을 강제한다.
 *
 *   domain        순수 Kotlin. 프레임워크를 모른다.
 *   application   유스케이스. 도메인만 안다. Spring 과 HTTP 를 모른다.
 *   infrastructure  Spring / RestClient / JPA 등 바깥세상. 안쪽을 참조해도 된다.
 */
class LayerDependencyTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT)

    @Test
    fun `domain 은 프레임워크에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "tools.jackson..", "com.fasterxml..", "org.slf4j..")
            .check(classes)
    }

    @Test
    fun `domain 은 바깥 계층을 모른다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..bootstrap..")
            .check(classes)
    }

    @Test
    fun `application 은 infrastructure 와 Spring 을 모른다`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "org.springframework..")
            .check(classes)
    }

    @Test
    fun `토큰 발급 포트는 application 과 infrastructure 에서만 쓴다`() {
        // 호출 지점이 늘어나면 서로의 토큰을 무효화한다. 이 규칙이 그 확산을 막는다.
        noClasses()
            .that().resideOutsideOfPackages("..application..", "..infrastructure..", "..domain..")
            .should().dependOnClassesThat().haveFullyQualifiedName("dev.teolab.stocklab.toss.domain.TokenIssuer")
            .check(classes)
    }

    companion object {
        private const val ROOT = "dev.teolab.stocklab"
    }
}
