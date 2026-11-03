package com.nexa.social.core.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.nexa.social.NexaApplication

/** Access the [NexaApplication] from a ViewModel initializer's creation extras. */
fun CreationExtras.nexaApplication(): NexaApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NexaApplication
