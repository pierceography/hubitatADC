/* groovylint-disable FactoryMethodName, JavadocConsecutiveEmptyLines, JavadocEmptyFirstLine, JavadocEmptyLastLine, LineLength, MethodCount, ParameterName */
/**
 *
 *  File: adc-manager.groovy
 *  Platform: Hubitat
 *
 *  https://github.com/pierceography/hubitatADC
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
 * twoFactorAuthenticationId: (twoFactorAuthenticationId from Alarm.com cookie)
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
 *    2020-05-22  Jeff Pierce  Removed unnecessary debugging statements
 *    2020-05-26  Jeff Pierce  Added password encryption, fixed unschedule() issue, cleaned up code more
 *    2020-05-27  Jeff Pierce  Fixed a bug that created switches even with a bad auth attempt
 *    2020-05-31  Jeff Pierce  Added additional polling options, fixed default value in polling bug
 *    2022-03-23  John Russell Fixed: Issue #1 Two-Factor Authentication Problem
 *
 */

String appVersion() { return "1.1.2" }
String appModified() { return "2020-05-31" }
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
	passwordEncryption()

	debug("Installed with settings: ${settings}", "installed()")

	if (sanityCheck()) {
		// app installed, acquire panelID
		getPanelID()

		if (!state.afg || !state.sessionID) {
			logError("Authentication failed -- Unable to finish install!", "installed()")
			return
		}

		// create child devices
		createChildDevices()

		initialize()
	}
}

def uninstalled() {
	debug("Uninstalling with settings: ${settings}", "uninstalled()")
	unschedule()

	removeChildDevices()
}

def updated() {
	unsubscribe()
	unschedule()

	// handle password encryption
	passwordEncryption()

	debug("Updated with settings: ${settings}", "updated()")

	if (sanityCheck()) {
		// app updated, re-acquire panelID
		getPanelID()

		if (!state.afg || !state.sessionID) {
			logError("Authentication failed -- Unable to finish update!", "updated()")
			return
		}

		// update child devices after app updated
		updateChildDevices()

		initialize()
	}
}

def initialize() {
	debug("Initializing Alarm.com Manager", "initialize()")

	unsubscribe()
	unschedule()

	// remove location subscription aftwards
	state.subscribe = false

	// setup the system to poll ADC web services for updates
	if ("${pollEvery}" == "1 Minute") {
		debug("Panel polling set for every 1 minute", "initialize()")
		runEvery1Minute(pollSystemStatus)
	} else if ("${pollEvery}" == "5 Minutes") {
		debug("Panel polling set for every 5 minutes", "initialize()")
		runEvery5Minutes(pollSystemStatus)
	} else if ("${pollEvery}" == "10 Minutes") {
		debug("Panel polling set for every 10 minutes", "initialize()")
		runEvery10Minutes(pollSystemStatus)
	} else if ("${pollEvery}" == "15 Minutes") {
		debug("Panel polling set for every 15 minutes", "initialize()")
		runEvery15Minutes(pollSystemStatus)
	} else if ("${pollEvery}" == "30 Minutes") {
		debug("Panel polling set for every 30 minutes", "initialize()")
		runEvery30Minutes(pollSystemStatus)
	} else if ("${pollEvery}" == "60 Minutes") {
		debug("Panel polling set for every 60 minutes", "initialize()")
		runEvery1Hour(pollSystemStatus)
	} else if ("${pollEvery}" == "3 Hours") {
		debug("Panel polling set for every 3 hours", "initialize()")
		runEvery3Hours(pollSystemStatus)
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
			input "password", "password", title: "Alarm.com Password", required: false
		}
		section {
			input "twoFactorAuthenticationId", "twoFactorAuthenticationId", title: "twoFactorAuthenticationId from browser", required: false
		}
		section {
			input "pollEvery", "enum", title: "How often should the panel be polled for updates?", options: ["1 Minute", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "60 Minutes", "3 Hours", "Never"], defaultValue: "30 Minutes", required: true
		}
		section {
			input "disarmOff", "enum", title: "How should switching disarm to off behave?", options: ["Do Nothing", "Arm Stay", "Arm Away"], defaultValue: "Do Nothing", required: true
		}
		section {
			input "encryptPassword", "bool", title: "Encrypt Password", description: "The password will be encrypted when stored on the hub", defaultValue: true
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

	// update the child device
	updateSwitch(switchType, switchState)

	// determine what actions should be taken based on disarm/arm stay/arm away
	if (switchType == "disarm") {
		// disarm was set to "on"
		if (switchState == "on") {
			// disarm the panel, set all other ADC switches to "off"
			setSystemStatus(switchType)
			toggleOtherSwitchesTo(switchType, "off")
		} else {
			// disarm was set to "off"
			// determine how we will treat "turning disarm off"
			if (settings.disarmOff == "Arm Stay") {
				// disarmOff preference set to arm stay
				// set panel for arm stay
				def device = getChildDevice("${state.panelID}-armstay")
				device.on()
				debug("Default disarmOff behavior set to arm stay", "switchStateUpdated()")
			} else if (settings.disarmOff == "Arm Away") {
				// disarmOff preference set to arm away
				// set panel for arm away
				def device = getChildDevice("${state.panelID}-armaway")
				device.on()
				debug("Default disarmOff behavior set to arm away", "switchStateUpdated()")
			} else {
				// do nothing
				// disarmOff set to do nothing, or not set at all
				// since one ADC switch always needs to be "on", set disarm back to "on"
				debug("Default disarmOff behavior set to do nothing, switching back to on", "switchStateUpdated()")
				updateSwitch(switchType, "on")
			}
		}
	} else if (switchType == "armstay" || switchType == "armaway") {
		if (switchState == "on") {
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
# Purpose: Handle the password encryption user preference
# 
# Details: If password encryption is enabled, check to see if a password has
# been provided.  If so, encrypt it and store as a non-user input setting,
# then clear out the user provided password.  If no encryption requested,
# clear out any residual encryption values from settings.
******************************************************************************/
private passwordEncryption() {
	if (settings.password && settings.encryptPassword) {
		app.updateSetting("encryptedPassword", [value: encrypt(settings.password), type: "string"])
		settings.encryptedPassword = encrypt(settings.password)

		// clear out the unecrypted password
		app.updateSetting("password", [value: "", type: "password"])
		settings.password = ""

		debug("Password encryption requested, and successfully completed")
	} else if (settings.password) { // password not encrypted, clear any residual encryption values
		app.updateSetting("encryptedPassword", [value: "", type: "string"])
		settings.encryptedPassword = ""

		debug("Password encryption not requested, stored as plain text")
	}
}

/******************************************************************************
# Purpose: Perform checks to ensure application is ready to start
# 
# Details: Check to ensure a password has been provided
# Return true if ready, false if problems and log message to system logs
******************************************************************************/
private sanityCheck() {
	if (settings.encryptPassword && !settings.encryptedPassword) {
		log.error("ADC FATAL ERROR: No encrypted password has been specified; Please enter a password in the application preferences screen.")
		return false
	} else if (!settings.password && !settings.encryptPassword) {
		log.error("ADC FATAL ERROR: No password has been specified; Please enter a password in the application preferences screen.")
		return false
	} else {
		return true;
	}
}

/******************************************************************************
# Purpose: Update a switch's state from within this app
# 
# Details: To be used from within this app; To update switch states within
# the driver, use switchStateUpdated()
******************************************************************************/
private updateSwitch(switchType, switchState) {
	def device = getChildDevice("${state.panelID}-${switchType}")
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
		if (switchType == switchTypeExclude) {
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
	// values, so we need to revert them to their originals (unHtmlValue)
	// Determine if password has been encrypted locally
	def settingsPassword = ""
	
	if (settings.encryptPassword) {
		settingsPassword = URLEncoder.encode(unHtmlValue(decrypt(settings.encryptedPassword)))
	} else {
		settingsPassword = URLEncoder.encode(unHtmlValue(password))
	}

	def loginString = "IsFromNewSite=1&txtUserName=${username}&txtPassword=${settingsPassword}"
	
	def params = [
		uri: "https://www.alarm.com/web/Default.aspx",
		body: loginString,
		requestContentType: "application/x-www-form-urlencoded",
		headers : [
			"Host" : "www.alarm.com",
			"Content-Type" : "application/x-www-form-urlencoded",
			"Connection" : "close",
			"Cookie": "twoFactorAuthenticationId=${twoFactorAuthenticationId}"
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

				if (cookieObj.key == "afg") {
					afg = cookieObj.value
				} else if (cookieObj.key == "ASP.NET_SessionId") {
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

	// ensure we have a valid panelID
	if (!state.panelID) {
		getPanelID()
	}

	params = [
		uri : "https://www.alarm.com/web/api/devices/partitions/${state.panelID}",
		headers : getStandardHeaders()
	]

	try {
		httpGet(params) { resp ->
			def json = parseJson(resp.data.text)
			def current_status = json.data.attributes.state
			def status_key = null

			if ("${current_status}" == "1") {
				status_key = "disarm"
			} else if ("${current_status}" == "2") {
				status_key = "armstay"
			} else if ("${current_status}" == "3") {
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

	if (status_key == "disarm") {
		adc_command = "disarm"
	} else if (status_key == "armstay") {
		adc_command = "armStay"
	} else if (status_key == "armaway") {
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
	if (state.currentStatus != switchType) {
		debug("System status updated to: ${switchType}", "updateHubStatus()")

		updateSwitch(switchType, "on")
		toggleOtherSwitchesTo(switchType, "off")
		state.currentStatus = switchType
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

		if (!existingDevice) {
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

		if (!device) {
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

	if (state.sessionID) {
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
	if (debugMode) {
		def fMethod = ""

		if (fromMethod) {
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
