package dev.teolab.stocklab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class StockLabApplication

fun main(args: Array<String>) {
    runApplication<StockLabApplication>(*args)
}
