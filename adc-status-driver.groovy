/**
 * File: adc-status-driver.groovy
 * Platform: Hubitat
 *
 * Read-only Alarm.com armed/disarmed status indicator.
 *
 * Copyright 2026 rjl77
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: "Alarm.com Armed Status",
        namespace: "jmpierce",
        author: "rjl77"
    ) {
        capability "Switch"
        capability "Refresh"

        attribute "alarmMode", "string"
        attribute "statusValid", "string"
        attribute "lastSuccessfulPoll", "string"
    }
}

def installed() {
    initializeStatus()
}

def initializeStatus() {
    if (device.currentValue("alarmMode") == null) {
        sendEvent(name: "alarmMode", value: "unknown")
    }

    if (device.currentValue("statusValid") == null) {
        sendEvent(name: "statusValid", value: "false")
    }
}

def refresh() {
    parent.pollSystemStatus()
}

// Required by Hubitat's Switch capability.
// These commands deliberately do not control the alarm.
def on() {
    log.warn "ADC Armed Status is read-only; ON ignored"
}

def off() {
    log.warn "ADC Armed Status is read-only; OFF ignored"
}

// Called by the parent app after a successful status poll.
def updateStatus(mode) {
    if (!(mode in ["disarmed", "stay", "away"])) {
        if (mode != "unknown") {
            log.warn "Unexpected alarm mode: ${mode}"
        }

        if (device.currentValue("statusValid") != "false") {
            sendEvent(name: "statusValid", value: "false")
        }

        if (device.currentValue("alarmMode") != "unknown") {
            sendEvent(name: "alarmMode", value: "unknown")
        }

        return
    }

    sendEvent(
        name: "lastSuccessfulPoll",
        value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    )

    def armed = (mode != "disarmed")

    if (device.currentValue("switch") != (armed ? "on" : "off")) {
        sendEvent(name: "switch", value: armed ? "on" : "off")
    }

    if (device.currentValue("alarmMode") != mode) {
        sendEvent(name: "alarmMode", value: mode)
    }

    if (device.currentValue("statusValid") != "true") {
        sendEvent(name: "statusValid", value: "true")
    }
}