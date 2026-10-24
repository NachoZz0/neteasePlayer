package com.example.metingmusic

import com.example.metingmusic.data.remote.ReleaseInfo
import com.example.metingmusic.data.remote.compareVersions
import com.example.metingmusic.data.remote.findUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较与「有没有新版本」的判定。
 *
 * 这里最容易踩的坑是按字符串比较：`1.10` 会被判成小于 `1.9`。
 */
class UpdateCheckTest {

    private fun release(tag: String, prerelease: Boolean = false) =
        ReleaseInfo(tagName = tag, htmlUrl = "https://github.com/x/y/releases/tag/$tag", prerelease = prerelease)

    @Test
    fun `相同版本返回 0`() {
        assertEquals(0, compareVersions("1.3.1", "1.3.1"))
        assertEquals(0, compareVersions("v1.3.1", "1.3.1"))
    }

    @Test
    fun `按数字段比较而不是字符串`() {
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(compareVersions("1.9.0", "1.10.0") < 0)
    }

    @Test
    fun `段数不同时缺的位按 0 算`() {
        assertEquals(0, compareVersions("1.3", "1.3.0"))
        assertTrue(compareVersions("1.3.1", "1.3") > 0)
    }

    @Test
    fun `大版本优先`() {
        assertTrue(compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `带 v 前缀也能比`() {
        assertTrue(compareVersions("v1.4.0", "1.3.1") > 0)
    }

    @Test
    fun `有更新时返回最新那一版`() {
        val found = findUpdate(
            listOf(release("v1.3.2"), release("v1.4.0"), release("v1.3.1")),
            currentVersion = "1.3.1"
        )
        assertEquals("1.4.0", found?.version)
    }

    @Test
    fun `已经是最新时返回 null`() {
        assertNull(findUpdate(listOf(release("v1.3.1")), currentVersion = "1.3.1"))
        assertNull(findUpdate(listOf(release("v1.2.0")), currentVersion = "1.3.1"))
    }

    @Test
    fun `忽略预发布版本`() {
        val found = findUpdate(
            listOf(release("v1.4.0", prerelease = true), release("v1.3.2")),
            currentVersion = "1.3.1"
        )
        assertEquals("1.3.2", found?.version)
    }

    @Test
    fun `空列表不会崩`() {
        assertNull(findUpdate(emptyList(), currentVersion = "1.3.1"))
    }
}
