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
    fun parse(yamlText: String): List<WorkflowInput> {
        return try {
            val loaded: Any? = Yaml().load(yamlText)
            val root = loaded as? Map<*, *> ?: return emptyList()

            // 找 "on" 键（YAML 1.1 可能把 on 解析成布尔 true）
            val onRaw = root.entries.firstOrNull { it.key == "on" || it.key == true }?.value
            val onBlock = onRaw as? Map<*, *> ?: return emptyList()

            val wdRaw = onBlock.entries.firstOrNull { it.key == "workflow_dispatch" }?.value
            val wd = wdRaw as? Map<*, *> ?: return emptyList()

            val inputsRaw = wd.entries.firstOrNull { it.key == "inputs" }?.value
            val inputs = inputsRaw as? Map<*, *> ?: return emptyList()

            inputs.mapNotNull { entry ->
                val name = entry.key as? String ?: return@mapNotNull null
                val spec = entry.value as? Map<*, *> ?: emptyMap<Any?, Any?>()

                val type = (spec["type"] as? String) ?: "string"
                val optionsRaw = spec["options"] as? List<*>

                WorkflowInput(
                    name = name,
                    description = (spec["description"] as? String)?.trim().orEmpty(),
                    required = (spec["required"] as? Boolean) ?: false,
                    defaultValue = spec["default"]?.toString().orEmpty(),
                    type = type,
                    options = optionsRaw?.mapNotNull { it?.toString() } ?: emptyList(),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
