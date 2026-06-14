package com.github.deadizar.aimanager.core.export

import com.github.deadizar.aimanager.core.model.Session
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ExportEngine(
    private val latexExporter: LatexExporter = LatexExporter(),
) {
    fun exportLatex(session: Session, mode: ExportMode, outputPath: Path): Result<Path> = runCatching {
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, latexExporter.render(session, mode), StandardCharsets.UTF_8)
        outputPath
    }
}

