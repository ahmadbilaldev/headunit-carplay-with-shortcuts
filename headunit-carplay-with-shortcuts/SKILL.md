---
name: headunit-carplay-with-shortcuts
description: Enable native wired Apple CarPlay on an aftermarket Autochips / BWIC Android head unit that already has the Apple MFi chip, and remap steering-wheel buttons (voice key single, double and long press) to open apps or cameras, all over ADB without root. Use this whenever someone asks to get CarPlay working on their car's Android screen without a dongle, asks why CarPlay "isn't showing" on an Android head unit, or wants a steering-wheel or panel button on such a unit to do something else, such as open a camera, maps or CarPlay.
---

# Head unit: CarPlay and key shortcuts

Both jobs follow the same method: read what the firmware already contains, check the hardware with
read-only commands, change the smallest reversible thing, and confirm with the owner pressing the real
button or plugging in the real phone. Read the repo README for the full findings on the BWIC Yato1 B100.

## Ground rules

- Snapshot `getprop`, the three `settings` tables and the package list first. Keep it out of git.
- No root, no flashing, no edits to `/system` or `/vendor`, no disabling of vendor car packages.
- Do not sign apps with platform or system keys, even where a debug firmware would accept them. Both
  jobs here work with an ordinary app.
- Build apps from source you have read. Do not install prebuilt APKs from a repo.
- A dialog asking the owner's consent on the unit (Play Protect "Install anyway", "open with") is the
  owner's to tap. Tell them it is waiting.
- A press of the real button and a look at the real screen are the only confirmation. An injected key,
  or a click that "returned true", is not. Say plainly what is unverified.

## A. Wired CarPlay

1. **Check the hardware, read-only.** All four must hold, or stop and say CarPlay cannot be enabled:
   - `pm list packages | grep autochips.carplay` finds the service.
   - `getprop ro.atc.carplay.support` is 1.
   - `i2cdetect -y -r 6 0x10 0x11` (run a few times; the chip sleeps) shows the MFi chip. Its address
     must equal `persist.carplay.i2c.slave_addr` (decimal). If it differs, changing that property
     needs root: stop and tell the owner.
   - The boot log shows the iAP layer reading the chip.
2. **Build the screen app** with `carplay/build-screen-app.sh`, after reading its source. It should be
   an activity that hands a surface to the unit's `CarplayManager`, and a watcher service.
3. **Install it.** Expect Play Protect to block it for its old target version; the owner approves.
4. **Switch on** `carplay_on` in both `global` and `system`, then `adb reboot`.
5. **Remove competitors.** Disable any CarPlay-receiver or dongle app that claims the iPhone's USB
   attach (`pm disable-user` for ones the owner does not use), or tell the owner not to pick them.
6. **Owner plugs in the iPhone** by cable with any dongle unplugged. Watch
   `logcat | grep -E "CarPlayControl|AirPlay|Authentificate"`. If the session starts but the screen
   stays on Home, `am start -n app.carplayscreen/.MainActivity`.
7. **Audio.** If music is silent, check `dumpsys media_session` and the unit's source
   (`settings get system bw_current_source_name`). The phone often keeps playing over Bluetooth:
   have the owner open BT Music, or choose the CarPlay output on the phone.
8. Leave wireless CarPlay alone unless the owner asks, and then explain it needs root and a `/vendor`
   edit before doing anything.

## B. Remapping a steering-wheel or panel key

1. **Confirm it is not a normal key.** `dumpsys input` shows no event when the owner presses it.
2. **Find the vendor path.** Pull the vendor APKs (`pm path`, `adb pull`) and read them with
   `aapt2 dump xmltree` and `dexdump`: look for a car service with an input-listener interface, its
   AIDL descriptors and transaction numbers, and a key-code table. On BWIC units these are in the
   README.
3. **Check the service is bindable** by an ordinary app: its `<service>` has an intent filter and no
   `android:permission`. If it demands a signature permission, stop and tell the owner it cannot be
   done without system signing.
4. **Adapt `apps/voicekey/`.** Set the key codes, priority and the three actions. Log every key event
   at error level. Keep classes static-nested if building with JDK 21 and build-tools 34.
5. **Install, start, read the log** for the registration line, then ask the owner to press the button.
   Read the log again for the key event before believing it.
6. **If the target app needs taps** to reach the right screen, use a narrowly scoped accessibility
   service: one package, armed only for a few seconds after a press, and looping until a visible state
   proves the result (here: the front icon becomes disabled). Enable it with
   `settings put secure enabled_accessibility_services`.
7. **Make it survive a restart.** Many head units force-stop third-party apps at shutdown, so
   `BOOT_COMPLETED` never arrives and accessibility settings are cleared. Check
   `dumpsys package <pkg> | grep stopped=` after a real restart. Anchor the app with something the
   system re-binds (a notification listener enabled with `cmd notification allow_listener`), and have
   it repair its own settings (`pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`). Do not call
   the job done until it has worked after a restart.
8. **Multi-press.** Count releases and fire after a 350 ms gap; arm a 600 ms timer on key-down for a
   long press. Tell the owner single presses now wait for the gap.

## Finish

Record each installed app with its undo line. Tell the owner what is confirmed, what is untested
(usually Siri, calls, behaviour after a cold boot), and that the unit's open ADB port should stay off
networks they do not control.
