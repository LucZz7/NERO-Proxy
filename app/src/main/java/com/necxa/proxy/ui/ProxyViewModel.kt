package com.necxa.proxy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.necxa.proxy.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ProxyViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    val form = MutableStateFlow(store.load() ?: FormState())
    val errors = MutableStateFlow<List<String>>(emptyList())
    val steps = MutableStateFlow<List<StepResult>>(emptyList())
    val testing = MutableStateFlow(false)
    val saved = MutableStateFlow(store.load() != null)

    fun update(f: FormState) { form.value = f }

    fun save() {
        val (cfg, e) = ConfigValidator.validate(form.value)
        errors.value = e
        if (cfg != null) { store.save(form.value); saved.value = true }
    }

    fun test() {
        val (cfg, e) = ConfigValidator.validate(form.value)
        errors.value = e
        if (cfg == null || testing.value) return
        viewModelScope.launch {
            testing.value = true; steps.value = emptyList()
            steps.value = ConnectionTester.run(cfg, form.value.testUrl)
            testing.value = false
        }
    }
}
