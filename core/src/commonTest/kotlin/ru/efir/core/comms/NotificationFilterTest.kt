package ru.efir.core.comms

import ru.efir.core.protocol.NotificationPosted
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationFilterTest {

    private fun notification(
        id: String = "n1",
        pkg: String = "com.example.app",
        ongoing: Boolean = false,
    ) = NotificationPosted(
        id = id,
        packageName = pkg,
        postedAt = 1_000,
        ongoing = ongoing,
    )

    @Test
    fun allowsByDefault() {
        assertTrue(NotificationFilter().allows(notification()))
    }

    @Test
    fun disabledBlocksEverything() {
        val filter = NotificationFilter(enabled = false)
        assertFalse(filter.allows(notification()))
    }

    @Test
    fun excludedPackageIsBlocked() {
        val filter = NotificationFilter(excludedPackages = setOf("com.spam"))
        assertFalse(filter.allows(notification(pkg = "com.spam")))
        assertTrue(filter.allows(notification(pkg = "com.other")))
    }

    @Test
    fun ongoingCanBeHidden() {
        val filter = NotificationFilter(hideOngoing = true)
        assertFalse(filter.allows(notification(ongoing = true)))
        assertTrue(filter.allows(notification(ongoing = false)))
    }

    @Test
    fun allowlistTakesPrecedenceOverExclude() {
        val filter = NotificationFilter(
            excludedPackages = setOf("com.a"),
            onlyPackagesAllowlist = setOf("com.a", "com.b"),
        )
        // Белый список задан → чёрный игнорируется, важны только пакеты из белого.
        assertTrue(filter.allows(notification(pkg = "com.a")))
        assertFalse(filter.allows(notification(pkg = "com.c")))
    }

    @Test
    fun togglePackageAddsAndRemoves() {
        val filter = NotificationFilter()
        val withPkg = filter.togglePackage("com.x")
        assertTrue("com.x" in withPkg.excludedPackages)

        val back = withPkg.togglePackage("com.x")
        assertFalse("com.x" in back.excludedPackages)
    }
}
