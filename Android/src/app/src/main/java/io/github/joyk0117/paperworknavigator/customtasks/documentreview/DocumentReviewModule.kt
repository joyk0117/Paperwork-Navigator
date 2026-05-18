package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import io.github.joyk0117.paperworknavigator.customtasks.common.CustomTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object DocumentReviewModule {
    @Provides
    @IntoSet
    fun provideDocumentReviewTask(task: DocumentReviewTask): CustomTask = task
}
