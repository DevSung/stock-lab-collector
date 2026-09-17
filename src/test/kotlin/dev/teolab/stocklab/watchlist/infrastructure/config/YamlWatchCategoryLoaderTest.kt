package dev.teolab.stocklab.watchlist.infrastructure.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlWatchCategoryLoaderTest {

    private val loader = YamlWatchCategoryLoader()

    @Test
    fun `resources 의 카테고리 파일을 전부 읽는다`() {
        val categories = loader.loadAll()

        assertTrue(categories.isNotEmpty(), "watchlist yml 을 하나도 못 읽었다")
        val names = categories.map { it.category }.toSet()
        assertTrue("semiconductor" in names, "실제 파일이 안 읽혔다: $names")
        assertTrue("battery" in names)
    }

    @Test
    fun `종목 목록과 설명을 그대로 가져온다`() {
        val semiconductor = loader.loadAll().single { it.category == "semiconductor" }

        assertEquals("반도체", semiconductor.description)
        assertTrue("005930" in semiconductor.symbols)
        // yml 의 따옴표 있는 코드가 숫자로 뭉개지면 앞의 0 이 날아간다
        assertTrue(semiconductor.symbols.all { it.length == 6 }, "종목코드 형식이 깨졌다: ${semiconductor.symbols}")
    }

    @Test
    fun `카테고리명이 겹치지 않는다`() {
        val names = loader.loadAll().map { it.category }

        assertEquals(names.size, names.distinct().size, "카테고리명 중복: $names")
    }

    @Test
    fun `파일이 없는 경로면 빈 목록이다`() {
        val empty = YamlWatchCategoryLoader(locationPattern = "classpath*:nowhere-at-all/*.yml")

        assertTrue(empty.loadAll().isEmpty())
    }
}
