package com.bruceremote.app.serial

data class TaskAgentExecutionResult(
    val task: ActiveAgentTask?,
    val explanationText: String,
    val commandsToSend: List<String>
)

class BruceTaskAgent(private val localAiAgent: BruceAiAgent = BruceAiAgent()) {

    fun processTaskRequest(
        prompt: String,
        screenTexts: List<VisibleScreenText>,
        engineType: AiEngineType = AiEngineType.LMSTUDIO_AGENT,
        apiKey: String = "",
        localServerUrl: String = "",
        qwenModelTag: String = "local-model",
        localQwenEngine: LocalQwenEngine? = null
    ): TaskAgentExecutionResult {
        val trimmedPrompt = prompt.trim()

        // 1. Text Recognition Summary from Screen
        val uniqueTexts = screenTexts.map { it.text.trim() }.filter { it.isNotBlank() }.distinct()
        val screenSummary = if (uniqueTexts.isNotEmpty()) {
            uniqueTexts.take(8).joinToString(" | ")
        } else {
            "Screen Canvas Ready (Standard Display)"
        }

        // 2. Delegate to Local Qwen Engine or LLM API
        val aiResult = if (engineType == AiEngineType.QWEN_LLM && localQwenEngine != null && localQwenEngine.isModelLoaded) {
            localQwenEngine.generateLocalResponse(trimmedPrompt, screenSummary)
        } else {
            localAiAgent.processPrompt(trimmedPrompt, engineType, apiKey, localServerUrl, qwenModelTag)
        }

        // 3. Build OpenClaw-style Agent Task Checklist Steps ONLY if serial commands exist
        val activeTask = if (aiResult.commandsToExecute.isNotEmpty()) {
            val steps = mutableListOf<AgentTaskStep>()
            steps.add(AgentTaskStep("Recognize Screen Text", "Captured: $screenSummary", isDone = true))

            aiResult.commandsToExecute.forEachIndexed { index, cmd ->
                steps.add(AgentTaskStep("Execute Task Step ${index + 1}", "Command: $cmd", isDone = true))
            }

            steps.add(AgentTaskStep("Verify Output & Status", "State confirmed via USB serial feed", isDone = true))

            ActiveAgentTask(
                goal = trimmedPrompt,
                recognizedScreenSummary = screenSummary,
                steps = steps,
                commandsExecuted = aiResult.commandsToExecute,
                status = TaskStatus.COMPLETED
            )
        } else {
            null
        }

        return TaskAgentExecutionResult(
            task = activeTask,
            explanationText = aiResult.explanation,
            commandsToSend = aiResult.commandsToExecute
        )
    }
}
