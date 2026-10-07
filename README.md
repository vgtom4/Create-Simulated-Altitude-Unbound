# Create Simulated: Altitude Unbound

Lets each Altitude Sensor from Create Simulated be calibrated over an altitude range of your own
instead of the world's build height.

NeoForge 1.21.1 · soft dependency on Create Simulated 1.3+.

## The problem

Simulated's Altitude Sensor turns height into redstone in two steps:

1. `toNormalHeight(y)` maps the sensor's world Y onto `0..1` between `minBuildHeight` and
   `maxBuildHeight`;
2. the two sliders on its screen pick a window inside that `0..1`, and the window is what spans
   redstone 0 to 15.

Step 1 is the ceiling. An aircraft cruising at Y 900 in a world that builds to 320 normalises to
`2.2`, well past the top of the scale, and no slider position can bring it back — both sliders are
clamped to `0..1`. The same happens from the other side in a flat skyblock world, where the whole
flight envelope is squeezed into a sliver of the bar and the sliders have no precision left.

## What this mod changes

It replaces the two end points of step 1 with a range you type in. Everything downstream is
untouched: the sliders, the redstone output, the dial, the display source, the ComputerCraft
peripheral. They now simply work against a scale that reaches as high as the aircraft does.

A sensor that has never been calibrated behaves exactly as it did before, and writes exactly the
NBT it did before.

## Using it

Right-click a sensor. Two boxes sit on its screen: one above the gauge for the top of the scale, one
below it for the bottom.

- Leave a box **blank** and that end follows the world's build limit — what the sensor did before
  this mod. The grey hint in the box is that limit, so you can see what blank means.
- Type an altitude in either one and the gauge re-scales as you type: the numbers beside the two
  handles are read back through the sensor, so they follow immediately. Nothing to confirm; the
  values go to the server when the screen closes, alongside Simulated's own.
- The text turns red if the range is upside down or too narrow. The gauge keeps the last good scale
  rather than going haywire mid-keystroke.
- Either box may reach past the world's build limits. That is the point.

The handles are untouched, and the **wheel** now drives them: hover one of them, its rail, or the
lit bar between the two, and scroll. One notch is one redstone level; **shift** for a fine step,
**ctrl** to slide both handles together without resizing the window. Over the middle of the bar the
nearer handle takes the scroll.

Each sensor carries its own range, so a cruise sensor and an approach sensor can be calibrated
differently on the same aircraft. Engineers' Goggles show the scale in force and whether it is
custom, under the height and air pressure Simulated already reports. A Clipboard copies the
calibration along with the handle positions.

## How it works

Simulated has no extension point here, so this is four small mixins:

| Class | What it does |
| --- | --- |
| `AltitudeSensorBlockEntityMixin` | Holds the range, rewrites `toNormalHeight` / `toWorldHeight`, persists to NBT, extends the goggle tooltip and the Clipboard payload |
| `AltitudeSensorScreenMixin` | Adds the two altitude boxes around the gauge, and wheel control for the handles |
| `AltitudeSensorMovementBehaviourMixin` | Same range for the dial on a moving Create contraption, which renders without the block entity |
| `AltitudeUnboundMixinPlugin` | Skips all of the above when Simulated is not installed |

The range lives in the block entity's NBT, which Simulated already writes on both the save path and
the client packet — so persistence and client sync come for free, and the only packet this mod
defines is the client asking the server to apply a new range.

## Building

```
./gradlew build
```

`./gradlew runClient` expects Create, Create Aeronautics and Sable in `run/mods/`.
