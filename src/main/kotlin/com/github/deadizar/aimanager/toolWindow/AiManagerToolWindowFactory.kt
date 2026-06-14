package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory


class AiManagerToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().info("AI Manager tool window factory initialized")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val ui = AiManagerToolWindow(project.service<AiManagerService>())
        ui.refresh()
        val content = ContentFactory.getInstance().createContent(ui.panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

}

