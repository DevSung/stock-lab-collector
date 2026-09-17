package dev.teolab.stocklab.watchlist.infrastructure.config

import dev.teolab.stocklab.watchlist.domain.WatchCategory
import dev.teolab.stocklab.watchlist.domain.WatchCategoryLoader
import org.slf4j.LoggerFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.yaml.snakeyaml.Yaml

/**
 * `classpath:watchlist` 아래의 모든 yml 을 전부 읽어 카테고리로 만든다.
 *
 * 카테고리는 DB 가 아니라 설정 파일이 진실이다. 파일에서 지우면 DB 에서도 빠진다.
 */
class YamlWatchCategoryLoader(
    private val resolver: ResourcePatternResolver = PathMatchingResourcePatternResolver(),
    private val locationPattern: String = DEFAULT_PATTERN,
) : WatchCategoryLoader {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadAll(): List<WatchCategory> {
        val resources = runCatching { resolver.getResources(locationPattern) }.getOrElse {
            log.warn("카테고리 파일을 찾지 못했다: {}", locationPattern, it)
            return emptyList()
        }

        val categories = resources.mapNotNull { resource ->
            val fileName = resource.filename ?: "(이름 없음)"
            runCatching { parse(resource.inputStream.use { Yaml().load<Map<String, Any?>>(it) }, fileName) }
                .onFailure { log.error("카테고리 파일을 읽지 못했다: {} — {}", fileName, it.message) }
                .getOrNull()
        }

        val duplicated = categories.groupBy { it.category }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "카테고리명이 중복됐다: $duplicated" }

        log.info("카테고리 {}개 로드: {}", categories.size, categories.map { "${it.category}(${it.symbols.size})" })
        return categories
    }

    private fun parse(raw: Map<String, Any?>?, fileName: String): WatchCategory {
        requireNotNull(raw) { "$fileName 이 비어 있다" }
        val category = raw["category"]?.toString()
            // category 키가 없으면 파일명을 카테고리로 쓴다
            ?: fileName.substringBeforeLast('.')
        @Suppress("UNCHECKED_CAST")
        val symbols = (raw["symbols"] as? List<Any?>)
            ?.map { it.toString().trim() }
            ?: error("$fileName 에 symbols 목록이 없다")

        return WatchCategory(
            category = category,
            description = raw["description"]?.toString(),
            symbols = symbols,
        )
    }

    companion object {
        const val DEFAULT_PATTERN = "classpath*:watchlist/*.yml"
    }
}
