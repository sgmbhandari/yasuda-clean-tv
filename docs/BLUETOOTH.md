# Bluetooth findings

On the tested SK708D/API28 TV, Android declares no Bluetooth hardware feature
and starts no Bluetooth manager. Generic Realtek driver/HAL files and a
Bluetooth rfkill node exist, but these software artifacts do not prove a
populated, working radio. USB enumeration showed an AltoBeam Wi-Fi adapter
(007a:8888) and no Bluetooth controller.

A reversible diagnostic feature declaration allowed Android's Bluetooth manager
to start. The bundled Bluetooth stack then aborted in `alarm.cc`: creating a
`CLOCK_BOOTTIME_ALARM` timer (clock 9) failed with kernel error 524. The vendor
USB service also reported that a Bluetooth module/dongle was not present.
There was no working adapter/HCI session or verified A2DP audio output.

The diagnostic XML was removed from vendor permissions, the original disabled
Bluetooth setting restored and the TV rebooted. No Bluetooth enablement hack is
included in deployment. This establishes that this firmware does not currently
provide usable Bluetooth; it does not prove the physical absence of every
possible unconnected controller on every SK708D variant.

A USB Bluetooth dongle alone is not a verified solution because of the kernel/
stack failure. An external audio transmitter connected to an available TV audio
output is a separate option; confirm the output type and transmitter
compatibility before choosing hardware. No accessory has been tested by this
project.
