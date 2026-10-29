package com.marketbrief.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object ApplicationConfigLoader {

    const val PATH_ENV = "MARKET_BRIEF_CONFIG"
    const val DEFAULT_PATH = "config/application.yml"

    // The default is relative to the process working directory. Packaged or
    // service launches must set MARKET_BRIEF_CONFIG to an absolute path.
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    fun load(env: Map<String, String> = System.getenv()): ApplicationConfig =
        load(Path.of(env[PATH_ENV]?.takeIf { it.isNotBlank() } ?: DEFAULT_PATH))

    fun load(path: Path): ApplicationConfig {
        val text = try {
            Files.readString(path)
        } catch (e: IOException) {
            throw IllegalStateException("Cannot read application config at $path", e)
        }
        return parse(text)
    }

    fun parse(text: String): ApplicationConfig = try {
        yaml.decodeFromString(ApplicationConfig.serializer(), text)
    } catch (e: SerializationException) {
        throw IllegalStateException("Invalid application config: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("Invalid application config: ${e.message}", e)
    }
}
