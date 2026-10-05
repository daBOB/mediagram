package rust

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.runner.RunWith
import testing.CoreContract
import uniffi.mediagram_core.Core
import java.io.File
import java.util.UUID

/**
 * [CoreContract] against the real generated `Core`, built the way
 * `app/.../di/CoreModule.kt` builds one — on the tablet, on a fresh data
 * directory with no Telegram session, same as [CoreLoadsTest] next to it.
 *
 * A UUID subdirectory per test class instance (JUnit4 makes a fresh one per
 * `@Test` method) is what keeps the profile-mutating cases from seeing each
 * other's writes; a shared directory would make "a created profile is
 * listed" depend on test order. Built on the case's first [core] call,
 * since whether it has synced is the case's to say.
 */
@RunWith(AndroidJUnit4::class)
class RealCoreContractTest : CoreContract() {
    private var built: Core? = null

    override fun core(synced: Boolean): Core =
        built ?: run {
            val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "contract-${UUID.randomUUID()}")
            if (synced) markOneEmptyRound(dir)
            Core(dir.absolutePath, apiId = 0, apiHash = "test-only-dummy-hash", deviceName = "core-contract-test").also { built = it }
        }

    @After
    fun closeCore() {
        built?.retireLocalState()
        built?.close()
    }

    /**
     * Leaves in [dir]'s `state.db` the mark a sync round's import writes
     * (`state/sync/first_round.rs`): a real round needs Telegram, which this
     * suite never reaches. Written before the core first opens the file;
     * its migrations create every table around this one, which they create
     * only if missing.
     */
    private fun markOneEmptyRound(dir: File) {
        dir.mkdirs()
        val flags = SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        SQLiteDatabase.openDatabase(File(dir, "state.db").path, null, flags).use { db ->
            db.execSQL("CREATE TABLE state_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO state_meta(key, value) VALUES ('first_round_imported', '1')")
        }
    }
}
