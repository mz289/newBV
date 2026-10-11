package dev.frost819.newbv.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.frost819.newbv.data.db.AppDatabase
import dev.frost819.newbv.data.db.dao.SearchHistoryDao
import dev.frost819.newbv.data.db.dao.UserDao
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 数据层 Hilt 模块。
 *
 * 提供 Room 数据库、DAO、Prefs（含其内部 DataStore）的单例绑定。
 * 所有绑定注册到 [SingletonComponent]，全应用生命周期共享。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * 提供 [AppDatabase] 单例。
     *
     * 使用应用上下文构建，数据库名由 [AppDatabase.DATABASE_NAME] 定义。
     * 不使用手动单例（区别于原版 BV），由 Hilt 管理生命周期。
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .build()

    /** 提供搜索历史 DAO。 */
    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    /** 提供账户 DAO。 */
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    /**
     * 提供 [Prefs] 单例（进程级全局对象）。
     *
     * Prefs 的初始化（[Prefs.init]，阻塞读取 DataStore 首帧）在此完成：
     * DataStore 是 Prefs 的实现细节，由本方法内部创建，不单独暴露。
     * 这样"初始化先于任何读取"由依赖图结构保证——任何注入 [Prefs]
     * 或传递依赖它的组件，构造时磁盘数据必然已加载，
     * 不再依赖 Application.onCreate 中的手工调用顺序。
     *
     * DataStore 持久化文件为 `datastore/Settings.preferences_pb`，
     * 协程作用域使用 IO 调度器 + SupervisorJob。
     */
    @Provides
    @Singleton
    fun providePrefs(
        @ApplicationContext context: Context,
    ): Prefs {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { java.io.File(context.filesDir, "datastore/Settings.preferences_pb") },
            )
        Prefs.init(dataStore)
        return Prefs
    }
}
