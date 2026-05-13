package com.ers.emergencyresponseapp.firebase.model.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.firebase.model.ResponderProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ers.emergencyresponseapp.firebase.model.repository.FirebaseUserRepository


class ResponderListViewModel : ViewModel() {

    private val repository = FirebaseUserRepository()

    private val _responders = MutableStateFlow<List<ResponderProfile>>(emptyList())
    val responders: StateFlow<List<ResponderProfile>> = _responders

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            repository.observeAllResponders().collect { list ->
                _responders.value = list
                _isLoading.value = false
            }
        }
    }
}
