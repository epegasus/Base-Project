package com.hypersoft.baseproject.presentation.dashboard.effect

sealed class DashboardEffect {
    object ShowExitDialog : DashboardEffect()
    data class ShowError(val message: String) : DashboardEffect()
}