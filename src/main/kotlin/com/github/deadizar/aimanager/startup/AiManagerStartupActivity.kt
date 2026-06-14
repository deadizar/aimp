package com.github.deadizar.aimanager.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AiManagerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("AI Manager startup activity executed for ${project.name}")
    }
}
