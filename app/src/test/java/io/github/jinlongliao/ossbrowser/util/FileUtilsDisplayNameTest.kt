package io.github.jinlongliao.ossbrowser.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.provider.OpenableColumns
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** 本地文档提供方验证名称语义和协程取消信号，不访问网络或真实云端。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FileUtilsDisplayNameTest {
    /** 正常提供方名称与 URI/file fallback 保持旧同步 API 语义，游标正常关闭。 */
    @Test
    fun backgroundResolutionPreservesProviderAndUriFallbackNames(): Unit = runBlocking {
        val provider = DisplayNameTestProvider()
        ShadowContentResolver.registerProviderInternal("display-name-test", provider)
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://display-name-test/document/original.txt")
        assertEquals("云端名称.pdf", FileUtils.resolveDisplayName(context, uri))
        assertFalse(provider.queriedOnMain.get())
        assertTrue(requireNotNull(provider.lastCursor).isClosed)
        provider.includeDisplayName = false
        assertEquals("original.txt", FileUtils.resolveDisplayName(context, uri))
        assertEquals("local.txt", FileUtils.resolveDisplayName(context, Uri.parse("file:///tmp/local.txt")))
    }

    /** 提供方查询阻塞时，取消协程必须主动通知 CancellationSignal，不能等待查询自行返回。 */
    @Test
    fun cancellationStopsSlowProviderQuery(): Unit = runBlocking {
        val provider = DisplayNameTestProvider().apply { blockQuery = true }
        ShadowContentResolver.registerProviderInternal("display-name-test", provider)
        val context = RuntimeEnvironment.getApplication()
        val query = async(Dispatchers.Default) {
            FileUtils.resolveDisplayName(context, Uri.parse("content://display-name-test/document/slow"))
        }
        try {
            assertTrue(provider.queryStarted.await(5, TimeUnit.SECONDS))
            query.cancelAndJoin()
            assertTrue(provider.cancellationReceived.get())
            assertTrue(query.isCancelled)
            assertFalse(provider.queriedOnMain.get())
        } finally {
            provider.queryRelease.countDown()
            query.cancelAndJoin()
        }
    }
}

/** 可控的无凭据文档提供方，只有查询元数据能力；阻塞由取消信号或测试 finally 释放。 */
private class DisplayNameTestProvider : ContentProvider() {
    /** 缺列时验证 URI fallback。 */
    var includeDisplayName = true
    /** 模拟需要取消的慢云文档。 */
    var blockQuery = false
    /** 查询进入和退出的确定性同步，不以毫秒性能断言判定结果。 */
    val queryStarted = CountDownLatch(1)
    val queryRelease = CountDownLatch(1)
    /** 记录取消信号和主线程访问。 */
    val cancellationReceived = AtomicBoolean(false)
    val queriedOnMain = AtomicBoolean(false)
    /** 用于断言游标释放。 */
    var lastCursor: MatrixCursor? = null

    /** 此假提供方不需要外部初始化。 */
    override fun onCreate(): Boolean = true

    /** 旧同步入口复用相同本地数据。 */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = query(uri, projection, selection, selectionArgs, sortOrder, null)

    /** 模拟可被 CancellationSignal 停止的提供方查询。 */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?, cancellationSignal: CancellationSignal?
    ): Cursor? {
        queriedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
        cancellationSignal?.setOnCancelListener {
            cancellationReceived.set(true)
            queryRelease.countDown()
        }
        queryStarted.countDown()
        if (blockQuery) check(queryRelease.await(5, TimeUnit.SECONDS)) { "慢查询未被取消" }
        cancellationSignal?.throwIfCanceled()
        val columns = if (includeDisplayName) arrayOf(OpenableColumns.DISPLAY_NAME) else arrayOf("unused")
        return MatrixCursor(columns).apply { addRow(arrayOf("云端名称.pdf")); lastCursor = this }
    }

    /** 未实现的数据变更接口保证测试无法访问文件对象。 */
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("不支持写入")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("不支持删除")
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = error("不支持更新")
}
