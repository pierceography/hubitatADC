# hubitatADC
Hubitat Alarm.com App and Driver

# Hubitat ADC App v1.1.0
Hubitat Elevation app and device handler for alarm.com

## What can I do with this app?
This app and device driver lets you control your alarm.com panel through three
switches (Disarm, Arm Stay, and Arm Away).  The app will poll the alarm.com
API periodically to fetch the latest state and update the switches accordingly;
Allowing for the app and the panel to function independently.

## To Use
On your Hubitat Elevation's admin page, select "Drivers Code", then click the
"New Driver" button.  Paste the Groovy driver code from this repository into 
the editor window and click the "SAVE" button.

Next, on the admin page, select "Apps Code", then click the "New App" button.
Paste the Groovy app code from this repository into the editor window and click
the "SAVE" button.

Navigate to the "Apps" section, and click the "New User App" button.  Select
"Alarm.com Manager", and enter your ADC credentials in the provided form fields.
Click "Done", and the app will attempt to retrieve your panel identification
number and create the child devices for controlling your panel.

Once your panel ID has been discovered, your panel can be controlled via the
three newly created child devices: ADC Disarm, ADC Arm Stay, ADC Arm Away.
Feel free to rename these to whatever suits you.

At any given time, one of the three switches will always be "on".  For example,
if your panel is armed stay, both arm away and disarm will be "off".  To
disarm your panel, you can either turn "on" disarm or turn "off" arm stay.  If
you wish to represent your panel with a single (most used) mode, add either
arm stay or arm away to a dashboard or HUD of your choosing.

Password encryption notes:  It is recommended that you select password
encryption so that your alarm.com password is encrypted on the HE device.
If you enable encryption, once your password is set it will not be populated
in the preferences form.  If you update the app preferences, you can leave the
password field blank and the existing encrypted value will be retained.  If
you do not elect to encrypt your password, it will be re-populated in the
preferences form for future updates.

It is also highly recommended that you use a secondary (service) account
with reduced privleges that limit its ability to alter any alarm.com settings
other than panel modes.

Feedback on how to improve this project is always welcome.

## To Do List
- Fix defaultValue bug in poll setting
- Add flag to remove the disarm switch for those who would prefer to just use
arm stay and arm away as toggles
