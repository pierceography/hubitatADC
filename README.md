# hubitatADC
Hubitat Alarm.com App and Driver

# Hubitat ADC App v0.1.1 (In development)
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

Feedback on how to improve this project is always welcome.

## To Do List
- Encrypt password while in storage
- Fix defaultValue bug in poll setting
