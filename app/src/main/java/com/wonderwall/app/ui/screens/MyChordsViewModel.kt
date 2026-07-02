package com.wonderwall.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wonderwall.app.data.AppDatabase
import com.wonderwall.app.data.CachedAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MyChordsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).analysisDao()

    private val _analyses = MutableStateFlow<List<CachedAnalysis>>(emptyList())
    val analyses: StateFlow<List<CachedAnalysis>> = _analyses

    init {
        viewModelScope.launch {
            _analyses.value = dao.getAll()
        }
    }

    fun delete(videoId: String) {
        viewModelScope.launch {
            dao.delete(videoId)
            _analyses.value = dao.getAll()
        }
    }
}
