package com.gekkotron.nidoo.feature.security

import com.gekkotron.nidoo.history.DailyTotals
import com.gekkotron.nidoo.history.EnergyScale
import com.gekkotron.nidoo.history.HistoryClient
import com.gekkotron.nidoo.history.HistoryRange
import com.gekkotron.nidoo.history.HistoryRef
import com.gekkotron.nidoo.history.HistoryResult
import com.gekkotron.nidoo.history.LastChange
import com.gekkotron.nidoo.history.Series
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHistoryClient : HistoryClient {
    override val available = MutableStateFlow(true)
    val asked = mutableListOf<List<HistoryRef>>()
    var answer: HistoryResult<Map<HistoryRef, LastChange>> = HistoryResult.Ok(emptyMap())

    override suspend fun series(ref: HistoryRef, range: HistoryRange): HistoryResult<Series> = HistoryResult.Unavailable("unused")
    override suspend fun daily(ref: HistoryRef, days: Int, scale: EnergyScale): HistoryResult<DailyTotals> = HistoryResult.Unavailable("unused")
    override suspend fun today(ref: HistoryRef, scale: EnergyScale): HistoryResult<Double?> = HistoryResult.Unavailable("unused")
    override suspend fun lastChanges(refs: List<HistoryRef>): HistoryResult<Map<HistoryRef, LastChange>> {
        asked += refs
        return answer
    }
}
