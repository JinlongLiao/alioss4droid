package io.github.jinlongliao.ossbrowser.oss

import org.junit.Assert.assertEquals
import org.junit.Test

/** 只验证本地路径策略，任何非法目录移动都应在发请求之前被拒绝。 */
class ObjectMovePolicyTest {
    /** 自身子目录会被递归再扫描，必须提前拒绝。 */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMovingDirectoryIntoItsDescendant() {
        validateObjectMovePaths("folder/", "folder/child/")
    }

    /** 不完整的目录目标不能被误当成文件 key。 */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsDirectoryDestinationWithoutSlash() {
        validateObjectMovePaths("folder/", "renamed")
    }

    /** 正常重命名、同路径不执行和普通文件移动均维持原契约。 */
    @Test
    fun acceptsNormalRenamesAndFileMoves() {
        validateObjectMovePaths("folder/", "renamed/")
        validateObjectMovePaths("folder/", "folder/")
        validateObjectMovePaths("folder/file.txt", "other/file.txt")
    }

    /** 父目录失效必须包括源和目标目录，同时根目录维持空前缀表示。 */
    @Test
    fun parentPrefixPreservesDirectoryBoundaries() {
        assertEquals("folder/", objectParentPrefix("folder/file.txt"))
        assertEquals("folder/", objectParentPrefix("folder/child/"))
        assertEquals("", objectParentPrefix("file.txt"))
        assertEquals("", objectParentPrefix("folder/"))
    }
}
