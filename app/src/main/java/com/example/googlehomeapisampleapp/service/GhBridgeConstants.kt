package com.example.googlehomeapisampleapp.service

object GhBridgeConstants {
    // Actions
    const val ACTION_SERVICE_STATUS = "com.example.googlehomeapisampleapp.service.SERVICE_STATUS"
    const val ACTION_REQUEST_SERVICE_STATUS = "com.example.googlehomeapisampleapp.service.REQUEST_SERVICE_STATUS"
    const val ACTION_TOGGLE_SERVICE = "com.example.googlehomeapisampleapp.service.TOGGLE_SERVICE"
    const val ACTION_START_SERVICE = "com.example.googlehomeapisampleapp.service.START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.example.googlehomeapisampleapp.service.STOP_SERVICE"
    const val ACTION_ERROR = "com.example.googlehomeapisampleapp.service.ERROR"

    // Extras
    const val EXTRA_SERVICE_INFO = "serviceInfo"
    const val EXTRA_SERVICE_STATE = "serviceState"
    const val EXTRA_ERROR_MESSAGE = "errorMessage"

    // Service States
    const val STATE_STARTING = "STARTING"
    const val STATE_WAITING_FOR_WIFI = "WAITING_FOR_WIFI"
    const val STATE_RUNNING = "RUNNING"
    const val STATE_STOPPED = "STOPPED"
    const val STATE_STOPPING = "STOPPING"
    const val STATE_FAILED = "FAILED"
}
