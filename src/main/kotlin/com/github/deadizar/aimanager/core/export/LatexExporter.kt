package com.github.deadizar.aimanager.core.export

import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session

enum class ExportMode {
    FULL_TRANSCRIPT,
    ASSISTANT_ONLY,
}

class LatexExporter {
    fun render(session: Session, mode: ExportMode): String {
        val body = session.messages
            .filter { mode == ExportMode.FULL_TRANSCRIPT || it.role == MessageRole.ASSISTANT }
            .joinToString("\n\n") { message ->
                val role = message.role.name
                "\\section*{$role}\n${escape(message.content)}"
            }

        return """
            \documentclass{article}
            \usepackage[utf8]{inputenc}
            \usepackage{geometry}
            \usepackage{xcolor}
            \usepackage{hyperref}
            \geometry{margin=2.2cm}
            \title{${escape(session.title)}}
            \author{AI Manager}
            \date{\today}
            \begin{document}
            \maketitle
            $body
            \end{document}
        """.trimIndent()
    }

    private fun escape(text: String): String = buildString(text.length + 16) {
        text.forEach { c ->
            append(
                when (c) {
                    '\\' -> "\\textbackslash{}"
                    '{' -> "\\{"
                    '}' -> "\\}"
                    '$' -> "\\$"
                    '&' -> "\\&"
                    '%' -> "\\%"
                    '#' -> "\\#"
                    '_' -> "\\_"
                    '^' -> "\\textasciicircum{}"
                    '~' -> "\\textasciitilde{}"
                    else -> c
                },
            )
        }
    }
}

