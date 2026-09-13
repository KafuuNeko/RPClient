package me.kafuuneko.rpclient.libs.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class RequestLogBackupRulesTest {
    @Test
    fun backupRules_excludeLogDatabaseAndSidecarsButNotPrimaryDatabase() {
        val root = parseXml("backup_rules.xml")
        val excludes = root.getElementsByTagName("exclude").asElements()

        assertEquals(ExpectedPaths, excludes.databasePaths())
    }

    @Test
    fun dataExtractionRules_excludeLogsFromCloudBackupAndDeviceTransfer() {
        val root = parseXml("data_extraction_rules.xml")
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element

        assertEquals(ExpectedPaths, cloud.getElementsByTagName("exclude").asElements().databasePaths())
        assertEquals(ExpectedPaths, transfer.getElementsByTagName("exclude").asElements().databasePaths())
        assertTrue(root.getElementsByTagName("include").length == 0)
    }

    private fun parseXml(name: String): Element {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            workingDirectory.resolve("src/main/res/xml/$name"),
            workingDirectory.resolve("app/src/main/res/xml/$name")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("Cannot locate $name from $workingDirectory")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).map { item(it) as Element }
    }

    private fun List<Element>.databasePaths(): Set<String> {
        val temporary = filter { it.getAttribute("domain") == "root" }
        assertEquals(setOf("app_repository/staging/"), temporary.map { it.getAttribute("path") }.toSet())
        return filter { it.getAttribute("domain") == "database" }.mapTo(mutableSetOf()) { it.getAttribute("path") }
    }

    private companion object {
        val ExpectedPaths = setOf(
            "request_logs.sqlite",
            "request_logs.sqlite-journal",
            "request_logs.sqlite-shm",
            "request_logs.sqlite-wal"
        )
    }
}
