package me.rain.git.github

import org.yaml.snakeyaml.Yaml

/** workflow_dispatch.inputs 的一项 */
data class WorkflowInput(
    val name: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String,
    /** string | choice | boolean | environment */
    val type: String,
    val options: List<String>,
)

object WorkflowInputParser {
    /** 从 workflow YAML 文本解析 workflow_dispatch.inputs；解析失败返回空表 */
    @Suppress("UNCHECKED_CAST")
    fun parse(yamlText: String): List<WorkflowInput> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load(yamlText) as? Map<String, Any?> ?: return emptyList()
            // YAML 1.1：`on:` 可能被解析成布尔 true
            val onBlock = (root["on"] ?: root[true]) as? Map<String, Any?> ?: return emptyList()
            val wd = onBlock["workflow_dispatch"] as? Map<String, Any?> ?: return emptyList()
            val inputs = wd["inputs"] as? Map<String, Any?> ?: return emptyList()

            inputs.map { (name, specRaw) ->
                val spec = specRaw as? Map<String, Any?> ?: emptyMap()
                val type = (spec["type"] as? String) ?: "string"
                val optionsRaw = spec["options"] as? List<Any?>
                WorkflowInput(
                    name = name,
                    description = (spec["description"] as? String)?.trim().orEmpty(),
                    required = (spec["required"] as? Boolean) ?: false,
                    defaultValue = spec["default"]?.toString().orEmpty(),
                    type = type,
                    options = optionsRaw?.map { it.toString() } ?: emptyList(),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
