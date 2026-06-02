package cn.limpu.hita.di

import cn.limpu.hita.agent.core.AgentProvider
import cn.limpu.hita.agent.timetable.TimetableAgentFactory
import cn.limpu.hita.agent.timetable.TimetableAgentInput
import cn.limpu.hita.agent.timetable.TimetableAgentOutput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideTimetableAgentProvider(): AgentProvider<TimetableAgentInput, TimetableAgentOutput> {
        return TimetableAgentFactory.createProvider()
    }
}
