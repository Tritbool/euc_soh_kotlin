package io.github.eucsoh.desktop

import io.github.eucsoh.SohAnalyzer
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("EUC SoH - Desktop")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(600, 400)

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val label = JLabel("EUC SoH - Kotlin Desktop")
        label.alignmentX = JComponent.CENTER_ALIGNMENT
        panel.add(label)

        panel.add(Box.createVerticalStrut(20))

        val statusArea = JTextArea(10, 40)
        statusArea.isEditable = false
        statusArea.text = "Ready to analyze CSV files\n\nSelect a folder containing CSV logs"
        val scrollPane = JScrollPane(statusArea)
        panel.add(scrollPane)

        panel.add(Box.createVerticalStrut(10))

        val btnSelectFolder = JButton("Select Folder")
        btnSelectFolder.alignmentX = JComponent.CENTER_ALIGNMENT
        btnSelectFolder.addActionListener {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            val result = chooser.showOpenDialog(frame)

            if (result == JFileChooser.APPROVE_OPTION) {
                val folder = chooser.selectedFile
                statusArea.text = "Selected: ${folder.absolutePath}\n\nAnalyzing..."

                // Run analysis
                Thread {
                    try {
                        val csvFiles = folder.listFiles { f -> f.extension == "csv" }
                            ?.map { it.absolutePath } ?: emptyList()

                        if (csvFiles.isEmpty()) {
                            SwingUtilities.invokeLater {
                                statusArea.text = "No CSV files found in folder"
                            }
                            return@Thread
                        }

                        val analyzer = SohAnalyzer(csvSource = DesktopCsvSource())

                        val result = runBlocking {
                            analyzer.analyzeFolderForReq(
                                csvPaths = csvFiles,
                                optimalFrac = 0.5,
                                parallel = true
                            )
                        }

                        SwingUtilities.invokeLater {
                            statusArea.text = "Analysis complete!\n\n" +
                                    //"Logs analyzed: ${result.stats.rowsCount()}\n" +
                                    "Alarms: ${result.alarms.size}\n" +
                                    "Ns: ${result.nsGlobal}\n" +
                                    "V_nominal: ${result.vNominal}V\n" +
                                    "R_pack_nominal: ${result.rPackNominal}Ω\n" +
                                    "Ea: ${"%.1f".format(result.eaJPerMol / 1000)}kJ/mol"
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            statusArea.text = "Error: ${e.message}\n${e.stackTraceToString()}"
                        }
                    }
                }.start()
            }
        }
        panel.add(btnSelectFolder)

        frame.contentPane = panel
        frame.isVisible = true
    }
}
