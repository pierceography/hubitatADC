/**
 *
 *  File: disarm-driver.groovy
 *  Platform: Hubitat
 *
 *
 *  Requirements:
 *     1) RESTful API to transform simple device requests into alarm.com states
 *
 *  Copyright 2020 Jeffrey M Pierce
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2020-05-14  Jeff Pierce    Original Creation
 *    2020-05-19  Jeff Pierce    Migrated all API calls to parent app
 *
 */

def version() {"0.1.1"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "Alarm.com Panel Switch", namespace: "jmpierce", author: "Jeff Pierce") {
        capability "Initialize"
        capability "Refresh"
		capability "Switch"
    }
}

preferences {
	input(name: "actionType", type: "enum", title: "Switch Performs Action", options: ["disarm", "armstay", "armaway"], required: true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def setActionType(actionType) {
	try {
		device.updateSetting("actionType", [value: actionType, type: "enum"])
		//log.debug("Setting updated to: ${actionType}")
	} catch(e) {
		log.error("Failed to update actionType with error: ${e}")
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    //log.info "refresh() called"
	//initialize()
	getSwitchStatus()
}


def installed() {
    debug("Switch installed", "updated()")
    updated()
}


def updated() {
    debug("Switch updated", "updated()")
    //Unschedule any existing schedules
    unschedule()
    
    //Create a 30 minute timer for debug logging
 //   if (logEnable) runIn(1800,logsOff)
    
//	runEvery1Minute(switchPollStatus)
//    refresh()
}


def getSwitchStatus() {
	parent.pollSystemStatus()
}


def getCurrentSwitchState() {
	return device.currentValue("switch")
}


def on() {
	debug("ADC ${actionType} switched to ON", "on()")
	parent.switchStateUpdated(actionType, "on")
}


def off() {
	debug("ADC ${actionType} switched to OFF", "off()")
	parent.switchStateUpdated(actionType, "off")
}

	
def initialize() {
    state.version = version()
	
    refresh()
}


/******************************************************************************
# Purpose: Log a debug message
# 
# Details: 
# 
******************************************************************************/
private debug(logMessage, fromMethod="") {
	if(parent.getDebugMode()) {
		def fMethod = ""

		if(fromMethod) {
			fMethod = ".${fromMethod}"
		}

		log.debug("ADC-Device${fMethod}: ${logMessage}")
	}
}
