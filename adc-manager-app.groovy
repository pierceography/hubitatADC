/**
 *
 *  File: adc-manager.groovy
 *  Platform: Hubitat
 *
 *
 *  Requirements:
 *     1) RESTful API to transform simple device requests into alarm.com states
 *     2) Device driver for individual switches
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
 * App State Variables:
 * username: (email of user)
 * password: (password of user)
 * panelID: 1234569-123 (panel ID, determined at app install/update)
 * currentStatus: disarm|armstay|armaway
 * afg: YPkyO88gkyVQyPphpBOojw==  (ajaxrequestuniquekey; used for authentication, refreshed every call)
 * sessionID: mjdgfmippicwkc52jwfcqu2l (ASP.NET sessionID; used for authentication, refreshed every call)
 *
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2020-05-15  Jeff Pierce  Original Creation
 *    2020-05-18  Jeff Pierce  Added switch management, moved panel status from device to app
 *    2020-05-19  Jeff Pierce  Moved all alarm.com API calls away from separate service and to app
 *    2020-05-20  Jeff Pierce  Code cleanup, fixed some install/uninstall bugs, added disarmOff behavior
 *
 */

String appVersion() { return "0.1.1" }
String appModified() { return "2020-05-20"}
String appAuthor() { return "Jeff Pierce" }

 definition(
    name: "Alarm.com Manager",
    namespace: "jmpierce",
    author: "Jeff Pierce",
    description: "Allows you to connect your Alarm.com alarm system with Hubitat for switch-level control of system states",
    category: "Security",
    iconUrl: "https://images-na.ssl-images-amazon.com/images/I/71yQ11GAAiL.png",
    iconX2Url: "https://images-na.ssl-images-amazon.com/images/I/71yQ11GAAiL.png",
    singleInstance: true
) 


preferences {
	page(name: "mainPage", title: "Alarm.com Manager Setup", install: true, uninstall: true)
}


def installed() {
	debug("Installed with settings: ${settings}", "installed()")

	// app installed, acquire panelID
	getPanelID()

	// create child devices
	createChildDevices()

	initialize()
}


def uninstalled() {
	debug("Uninstalling with settings: ${settings}", "uninstalled()")
	unschedule()

	removeChildDevices()
}


def updated() {
	log.debug("password: ${password}, settings.password: ${settings.password}")

	debug("Updated with settings: ${settings}", "updated()")

	unsubscribe()

	// app updated, re-acquire panelID
	getPanelID()

	// update child devices after app updated
	updateChildDevices()

	initialize()
}


def initialize() {
	debug("ADC initializing", "initialize()")
	// remove location subscription aftwards
	unsubscribe()
	state.subscribe = false

	// setup the system to poll ADC web services for updates
	if("${pollEvery}" == "1") {
		debug("Panel polling set for every 1 minute", "initialize()")
		runEvery1Minute(pollSystemStatus)
	} else if("${pollEvery}" == "5") {
		debug("Panel polling set for every 5 minutes", "initialize()")
		runEvery5Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "10") {
		debug("Panel polling set for every 10 minutes", "initialize()")
		runEvery10Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "15") {
		debug("Panel polling set for every 15 minutes", "initialize()")
		runEvery15Minutes(pollSystemStatus)
	} else if("${pollEvery}" == "30") {
		debug("Panel polling set for every 30 minutes", "initialize()")
		runEvery30Minutes(pollSystemStatus)
	} else {
		debug("Panel polling disabled", "initialize()")
		log.warn("ADC Panel polling disabled -- Panel updates will not be reflected in the hub")
	}

	// immediately get an updated status
	getSystemStatus()
}


def mainPage() {
    dynamicPage(name: "main", title: "Alarm.com Setup", uninstall: true, install: true) {
		section {
			input "username", "text", title: "Alarm.com Username (Email)", required: true
		}
		section {
			input "password", "password", title: "Alarm.com Password", required: true
		}
		section {
			input "pollEvery", "enum", title: "Poll Panel Every X Minutes", options: ["1", "5", "10", "15", "30", "Never"], defaultValue: 1, required: true
		}
		section {
			input "disarmOff", "enum", title: "How should switching disarm to off behave?", options: ["Do Nothing", "Arm Stay", "Arm Away"], defaultValue: "Do Nothing", required: true
		}
		section {
			input "debugMode", "bool", title: "Enable debugging", defaultValue: true
		}
	}
}


/******************************************************************************
# Purpose: Wrapper for getSystemStatus
# 
# Details: 
# 
******************************************************************************/
def pollSystemStatus() {
	return getSystemStatus();
}


/******************************************************************************
# Purpose: Handles the calls to the alarm.com API to set the panel state
# 
# Details: This method will be called from the device driver
# 
******************************************************************************/
def switchStateUpdated(switchType, switchState) {
	debug("Setting ${switchType} to ${switchState}", "switchStateUpdated()")

	updateSwitch(switchType, switchState)

	if(switchType == "disarm") {
		if(switchState == "on") {
			setSystemStatus(switchType)
			toggleOtherSwitchesTo(switchType, "off")
		} else {
			if(settings.disarmOff == "Arm Stay") {
				def device = getChildDevice("${state.panelID}-armstay")
				device.on()
				debug("Default disarmOff behavior set to arm stay", "switchStateUpdated()")
			} else if(settings.disarmOff == "Arm Away") {
				def device = getChildDevice("${state.panelID}-armaway")
				device.on()
				debug("Default disarmOff behavior set to arm away", "switchStateUpdated()")
			} else {
				// do nothing
				// you can't turn off disarm
				// flip disarm back to "on"
				debug("Default disarmOff behavior set to do nothing, switching back to on", "switchStateUpdated()")
				updateSwitch(switchType, "on")
			}
		}
	} else if(switchType == "armstay" || switchType == "armaway") {
		if(switchState == "on") {
			setSystemStatus(switchType)
			toggleOtherSwitchesTo(switchType, "off")
		} else {
			def device = getChildDevice("${state.panelID}-disarm")
			device.on()
		}
	}
}


/******************************************************************************
# Purpose: Return boolean value indicated if debug mode is activated
# 
# Details: 
# 
******************************************************************************/
def getDebugMode() {
	return debugMode
}

/***** PRIVATE METHODS *****/

/******************************************************************************
# Purpose: Map keys to human friendly values
# 
# Details: 
# 
******************************************************************************/
private getLabelMap() {
	return [
		"disarm" : "Disarm",
		"armstay" : "Arm Stay",
		"armaway" : "Arm Away"
	]
}


/******************************************************************************
# Purpose: Return the core switch types
# 
# Details: Pretty simple: disarm, arm stay, arm away
# 
******************************************************************************/
private getSwitchTypes() {
	return ['disarm', 'armstay', 'armaway']
}


/******************************************************************************
# Purpose: Update a switch's state from within this app
# 
# Details: To be used from within this app; To update switch states within
# the driver, use switchStateUpdated()
******************************************************************************/
private updateSwitch(switchType, switchState) {
	def device = getChildDevice("${state.panelID}-${switchType}")
	def currentState = device.getCurrentSwitchState()
	debug("Setting ${state.panelID}-${switchType} to ${switchState}", "updateSwitch()")
	device.sendEvent([name: "switch", value: switchState])
}


/******************************************************************************
# Purpose: Toggle switches other than the specified type to another value
# 
# Details: Almost always used to toggle other switches to off
# 
******************************************************************************/
private toggleOtherSwitchesTo(switchTypeExclude, switchState) {
	debug("Toggling all switches that are not ${switchTypeExclude} to ${switchState}", "toggleOtherSwitchesTo()")

	getSwitchTypes().each{switchType ->
		// ignore the switch type being excluded
		if(switchType == switchTypeExclude) {
			return;
		}

		updateSwitch(switchType, switchState)
	}
}

/******************************************************************************
# Purpose: Get the authentication values used for API calls
# 
# Details: Two values of note are returned after a successful login
# afg/ajaxrequestuniquekey (returned as a cookie and header)
# ASP.NET_SessionId (returned as a cookie)
******************************************************************************/
private getSystemAuthID() {
	debug("Getting refreshed authentication credentials", "getSystemAuthID()")

	// Hubitat likes to escape certain characters when transporting their form
	// values, so we need to revert them to their originals
	def settingsPassword = URLEncoder.encode(unHtmlValue(password))
	def loginString = "IsFromNewSite=1&txtUserName=${username}&txtPassword=${settingsPassword}"
	
	def params = [
		uri: "https://www.alarm.com/web/Default.aspx",
		body: loginString,
		requestContentType: "application/x-www-form-urlencoded",
		headers : [
			"Host" : "www.alarm.com",
			"Content-Type" : "application/x-www-form-urlencoded",
			"Connection" : "close"
		]
	]

	try {
		httpPost(params) { resp -> 
			def afg = null;
			def sessionID = null;

			// parse through the cookies to find the two authentication
			// values we need, store in state memory
			resp.getHeaders('Set-Cookie').each { cookie ->
				def cookieObj = getCookie(cookie.toString())

				if(cookieObj.key == "afg") {
					afg = cookieObj.value
				} else if(cookieObj.key == "ASP.NET_SessionId") {
					sessionID = cookieObj.value
				}
			}

			debug("Received sessionID (${sessionID}) and afg (${afg})", "getSystemAuthID()")

			// store the ASP session ID and afg as state values
			state.sessionID = sessionID
			state.afg = afg
		}
	} catch(e) {
		logError("Authentication Error: Username or password not accepted; Please update these values in the ADC settings", "getSystemAuthID()")
	}
}


/******************************************************************************
# Purpose: Get alarm.com panel identification value
# 
# Details: The partition ID (or panel ID) is needed for all API calls
# The account ID must first be fetched, then used to fetch the partition ID
******************************************************************************/
private getPanelID() {
	// first we need to refresh our auth values
	getSystemAuthID()

	def accountID = null

	// first we need to get the account ID
	// this will only be used for fetching the partition ID
	// the partition ID is basically the panel ID
	params = [
		uri : "https://www.alarm.com/web/api/identities",
		headers : getStandardHeaders(),
		requestContentType : "application/json"
	]

	try {
		// fetch the account ID
		httpGet(params) { resp ->
			def json = parseJson(resp.data.text)
			accountID = json.data[0].relationships.accountInformation.data.id

			debug("Received accountID (${accountID})", "getPanelID()")
		}
	} catch(e) {
		logError("getPanelID:GettingAccountID", e)
		return
	}

	params = [
		uri : "https://www.alarm.com/web/api/systems/systems/${accountID}",
		headers : getStandardHeaders(),
		requestContentType : "application/json"
	]

	try {
		// use the account ID to fetch the panel ID
		httpGet(params) { resp ->
			def json = parseJson(resp.data.text)
			state.panelID = json.data.relationships.partitions.data[0].id

			debug("Received panelID (${state.panelID})", "getPanelID()")
		}
	} catch(e) {
		logError("getPanelID:GettingPanelID", e)
		return
	}
}


/******************************************************************************
# Purpose: Get the current status of the alarm system
# 
# Details: Determine whether the system is disarmed, armed stay, or armed away
#
******************************************************************************/
private getSystemStatus() {
	// first we need to refresh our auth values
	getSystemAuthID()

	params = [
		uri : "https://www.alarm.com/web/api/devices/partitions/${state.panelID}",
		headers : getStandardHeaders()
	]

	try {
		httpGet(params) { resp ->
			def json = parseJson(resp.data.text)
			def current_status = json.data.attributes.state
			def status_key = null

			if("${current_status}" == "1") {
				status_key = "disarm"
			} else if("${current_status}" == "2") {
				status_key = "armstay"
			} else if("${current_status}" == "3") {
				status_key = "armaway"
			}

			debug("Alarm.com returned a panel status of: ${current_status} - ${status_key}", "getSystemStatus()")
			updateHubStatus(status_key)
		}
	} catch(e) {
		logError("getSystemStatus", e)
	}
}


/******************************************************************************
# Purpose: Set the panel to a specified status (disarm, arm stay, arm away)
# 
# Details: status_key will be either: disarm, armaway, armstay
# (currently does not allow for delayed arming)
******************************************************************************/
private setSystemStatus(status_key) {
	// first we need to refresh our auth values
	getSystemAuthID()

	debug("Attempting to set a panel status of: ${status_key}", "setSystemStatus()")

	def adc_command = null;
	def post_data = '{"statePollOnly":false}'

	if(status_key == "disarm") {
		adc_command = "disarm"
	} else if(status_key == "armstay") {
		adc_command = "armStay"
	} else if(status_key == "armaway") {
		adc_command = "armAway"
	}

	params = [
		uri : "https://www.alarm.com/web/api/devices/partitions/${state.panelID}/${adc_command}",
		headers : getStandardHeaders(),
		body : post_data
	]
	
	try {
		httpPost(params) { resp -> 
			debug("Alarm.com accepted status of: ${status_key}", "setSystemStatus()")
			settings.currentStatus = status_key
		}
	} catch(e) {
		logError("setSystemStatus", e)
	}
}


/******************************************************************************
# Purpose: Determine if the panel status has been updated, set switches
# 
# Details: This is generally used if the panel has been updated outside of
# this app (e.g. from the panel or another app)
******************************************************************************/
private updateHubStatus(switchType) {
	if(state.currentStatus != switchType) {
		debug("System status updated to: ${switchType}", "updateHubStatus()")

		updateSwitch(switchType, "on")
		toggleOtherSwitchesTo(switchType, "off")
		state.currentStatus = switchType
	} else {
		//log.debug("No update to system status: ${switchType}")
	}
}


/******************************************************************************
# Purpose: Create child devices; One for each: disarm, armstay, armaway
# 
# Details: This will usually be done when the ADC app has been newly installed
# or updated; If a child device already exists, it will be ignored
******************************************************************************/
private createChildDevices() {
	getSwitchTypes().each{switchType ->
		def existingDevice = getChildDevice("${state.panelID}-${switchType}")

		if(!existingDevice) {
			debug("Creating child device: ${state.panelID}-${switchType}", "createChildDevices()")
			createChildDevice(switchType)
		}
	}
}


/******************************************************************************
# Purpose: Create the child device as specified
# 
# Details: Use the panel ID and switch type (disarm, armstay, armaway) as the
# device identification value
******************************************************************************/
private createChildDevice(deviceType) {
	def labelMap = getLabelMap()
	def label = labelMap[deviceType]

	try {
		// create the child device
		addChildDevice("jmpierce", "Alarm.com Panel Switch", "${state.panelID}-${deviceType}", null, [label : "ADC ${label}", isComponent: false, name: "ADC ${label}"])
		createdDevice = getChildDevice("${state.panelID}-${deviceType}")
		createdDevice.setActionType(deviceType)

		debug("Child device ${state.panelID}-${deviceType} created", "createChildDevice()")
	} catch(e) {
		logError("Failed to add child device with error: ${e}", "createChildDevice()")
	}
}


/******************************************************************************
# Purpose: Create child devices that do not exist
# 
# Details: Usually done during an app update (restores any deleted devices)
# 
******************************************************************************/
private updateChildDevices() {
	def switchTypes = getSwitchTypes()

	switchTypes.each {switchType ->
		def device = getChildDevice("${state.panelID}-${switchType}")

		if(!device) {
			debug("ADC device does not exist, creating: ${state.panelID}-${switchType}", "updateChildDevices()")
			createChildDevice(switchType)
		}
	}
}


/******************************************************************************
# Purpose: Delete all child devices
# 
# Details: 
# 
******************************************************************************/
private removeChildDevices() {
	def switchTypes = getSwitchTypes()

	try {
		switchTypes.each {switchType ->
			debug("Removing child device: ${state.panelID}-${switchType}", "removeChildDevices()")
			deleteChildDevice("${state.panelID}-${switchType}")
		}
	} catch(e) {
		logError("removeChildDevices", e)
	}
}


/******************************************************************************
# Purpose: Restore any escaped HTML values stored in state memory
# 
# Details: Ex: &lt; = <
# 
******************************************************************************/
private unHtmlValue(valueToDecode) {
	valueToDecode = valueToDecode.replace(/&lt;/, "<")
	valueToDecode = valueToDecode.replace(/&gt;/, ">")

	return valueToDecode
}


/******************************************************************************
# Purpose: Parse a cookie out of a Set-Cookie HTTP header
# 
# Details: Return a map of a key and value
# 
******************************************************************************/
private getCookie(cookie) {
	cookie = cookie.replace("Set-Cookie: ", '')
	def pieces = cookie.split(';')
	def kv = pieces[0].split('=', 2)

	try {
		return cookieObj = [
			key : kv[0],
			value : kv[1]
		]
	} catch(e) {
		return []
	}
}


/******************************************************************************
# Purpose: Define the standard alarm.com headers expected for API calls
# 
# Details: 
# 
******************************************************************************/
private getStandardHeaders(options = []) {
	def headers = [
        "Accept" : "application/vnd.api+json",
        "ajaxrequestuniquekey" : state.afg,
        "Connection" : "close",
        "User-Agent" : "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:74.0) Gecko/20100101 Firefox/74.0",
//        "Content-Type" : "application/json",
        "Host" : "www.alarm.com"
	]

	if(state.sessionID) {
		headers['Cookie'] = getCookieString()
	}

	return headers
}


/******************************************************************************
# Purpose: Define the necessary cookie string for standard alarm.com API calls
# 
# Details: This is the minimum cookie definition string needed
# 
******************************************************************************/
private getCookieString() {
	return "ASP.NET_SessionId=${state.sessionID}; CookieTest=1; IsFromNewSite=1; afg=${state.afg};"
}


/******************************************************************************
# Purpose: Log a debug message
# 
# Details: 
# 
******************************************************************************/
private debug(logMessage, fromMethod="") {
	if(debugMode) {
		def fMethod = ""

		if(fromMethod) {
			fMethod = ".${fromMethod}"
		}

		log.debug("ADC-App${fMethod}: ${logMessage}")
	}
}


/******************************************************************************
# Purpose: Log an error
# 
# Details: 
# 
******************************************************************************/
private logError(fromMethod, e) {
	log.error("ADC ERROR (${fromMethod}): ${e}")
}
