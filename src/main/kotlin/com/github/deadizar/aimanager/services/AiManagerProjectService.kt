package com.github.deadizar.aimanager.services

import com.github.deadizar.aimanager.AiManagerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AiManagerProjectService(project: Project) {

    init {
        thisLogger().info(AiManagerBundle["projectService", project.name])
    }
}

