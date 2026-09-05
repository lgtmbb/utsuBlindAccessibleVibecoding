# Utsu2 keyboard shortcuts

This is a complete, source-verified list of every keyboard shortcut in this fork -- the ones
that already existed in upstream Utsu 0.4.5, and the ones added by this fork. It was compiled by
reading every `setAccelerator(...)` call and every `KeyCodeCombination(...)` check in the
codebase (`UtsuController.java`, `SongController.java`, `VoicebankController.java`,
`UtsuApp.java`), specifically to confirm none of this fork's additions collide with anything
already bound -- see "Added by this fork" below for the one collision that was caught and fixed
(Ctrl+N) before it shipped.

## Global (all tabs)

| Shortcut | Action |
|---|---|
| Ctrl+N | New Song |
| Ctrl+Shift+N | New Voicebank |
| Ctrl+O | Open Song |
| Ctrl+Shift+O | Open Voicebank |
| Ctrl+S | Save |
| Ctrl+Shift+S | Save As |
| Ctrl+W | Export to WAV |
| Ctrl+, | Preferences (Ctrl+Comma on Windows/Linux; Cmd+Comma on macOS) |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+X | Cut |
| Ctrl+C | Copy |
| Ctrl+V | Paste |
| Ctrl+D | Delete |
| Ctrl+A | Select All |
| Ctrl+R | Refresh Editor |
| Ctrl+E | Note Properties |
| Ctrl+= | Zoom In (horizontal) |
| Ctrl+- | Zoom Out (horizontal) |
| Ctrl+Shift+= | Zoom In (vertical) |
| Ctrl+Shift+- | Zoom Out (vertical) |
| Ctrl+P | Properties |
| Ctrl+Shift+P | Portamento Editor |
| Ctrl+Shift+V | Vibrato Editor |
| Ctrl+Shift+E | Envelope Editor |
| Ctrl+Shift+I | Insert Lyrics |
| Ctrl+Shift+X | Prefix/Suffix |
| Ctrl+Shift+C | Reclist Converter |
| Ctrl+Shift+/ | Help |
| Tab | Switch tabs (a non-accelerator global filter behavior, distinct from Song-tab note navigation below) |

## Song tab

| Shortcut | Action |
|---|---|
| Space | Play/pause |
| Ctrl+Space | Play/pause from selection start |
| V | Paste at cursor |
| W | Paste at cursor (alternate) |
| B | Open Note Properties for current note |
| Backspace | Delete selected note(s) |
| Enter | Edit lyric of the focused note |
| Tab / Right Arrow | Focus next note |
| Shift+Tab / Left Arrow | Focus previous note |

### Added by this fork (all newly checked against the table above, plus Windows-reserved and NVDA-reserved combinations, before being picked)

| Shortcut | Action | Why this key was free |
|---|---|---|
| Up Arrow | Move focused note's pitch up one semitone | Not bound to anything above |
| Down Arrow | Move focused note's pitch down one semitone | Not bound to anything above |
| Ctrl+Left Arrow | Move focused note earlier in time by one quantize step | Not bound to anything above |
| Ctrl+Right Arrow | Move focused note later in time by one quantize step | Not bound to anything above |
| Shift+Left Arrow | Shrink focused note's duration by one quantize step | Not bound to anything above |
| Shift+Right Arrow | Extend focused note's duration by one quantize step | Not bound to anything above |
| Ctrl+Alt+N | Insert a new note right after the focused note (also in the new Note menu) | **Requested as Ctrl+Shift+N, but that collides with the pre-existing "New Voicebank" accelerator above -- caught and moved to Ctrl+Alt+N.** No accelerator in this codebase uses the Ctrl+Alt combination; Windows' own global combinations using Ctrl+Alt are Ctrl+Alt+Del and Ctrl+Alt+arrow (screen rotation, on some graphics drivers), neither of which uses a letter key; NVDA's own commands are bound to its modifier key (Insert, or Caps Lock if reconfigured), not Ctrl+Alt+letter |
| Alt+N | Open "Create Note at Position" dialog (also in the new Note menu) | **Originally bound to Ctrl+N, which collided with the pre-existing "New Song" accelerator above -- caught and moved to Alt+N.** No accelerator in this codebase uses the Alt modifier; every `Menu`/`MenuItem` in `UtsuScene.fxml` has `mnemonicParsing="false"`, so Alt+letter cannot collide with a menu mnemonic in this app either; Windows' own global Alt-combinations (Alt+Tab, Alt+F4, Alt+Space, Alt+Esc) do not use letter keys; NVDA's own commands are bound to its modifier key (Insert, or Caps Lock if reconfigured), not plain Alt+letter, on native desktop apps |

None of the above use the Insert key, which NVDA itself uses as its default command modifier --
using it here would conflict with NVDA's own shortcuts on any system using NVDA's default
keyboard layout.

## Voicebank tab

| Shortcut | Action |
|---|---|
| Space | Play/pause |
| Ctrl+Space | Play/pause from selection start |
| Ctrl+Shift+Space | (reserved; see VoicebankController.java) |

### Added by this fork

| Shortcut | Action |
|---|---|
| (menu only, via the new "Voicebank" menu) | Extract a Voicebank .zip File... |

## Notes

- "Ctrl" above means the JavaFX `SHORTCUT_DOWN` modifier, which is Ctrl on Windows/Linux and Cmd
  on macOS. This fork targets Windows, but the mapping is automatic if this is ever built for
  another platform.
- This list should be updated whenever a new shortcut is added anywhere in the codebase, and
  re-checked against this same table first.
