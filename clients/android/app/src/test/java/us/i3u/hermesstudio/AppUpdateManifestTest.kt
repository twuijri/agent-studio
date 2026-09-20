package us.i3u.hermesstudio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build 41 crashed on every phone that upgraded from an earlier build: the
 * start-up update check asked ConnectivityManager whether the connection is
 * metered, and Android throws a SecurityException for that call unless the
 * manifest declares ACCESS_NETWORK_STATE. A clean install never showed it
 * because the check only runs once a server is stored.
 */
class AppUpdateManifestTest {
    private fun source(path: String): String = File("src/main/$path").readText()

    @Test
    fun theManifestDeclaresNetworkStateForTheUpdateCheck() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun connectivityQueriesNeverThrowOutOfTheUpdater() {
        val updater = source("java/us/i3u/hermesstudio/AppUpdater.kt")
        val guarded = Regex("fun (isMetered|isOffline)\\(context: Context\\): Boolean = try \\{").findAll(updater).count()
        assertTrue("both connectivity helpers must catch SecurityException", guarded == 2)
        assertTrue(updater.contains("catch (_: SecurityException)"))
    }
}
