# rime-ice dictionary snapshot

WindIme vendors the Chinese dictionary data from:

- Project: `iDvel/rime-ice`
- URL: https://github.com/iDvel/rime-ice
- License: GNU GPL v3.0 only
- Imported snapshot: local `trimelib/all_dicts` snapshot, 2026-07
- Source directory: `trimelib/all_dicts` supplied for WindIme integration
- The source snapshot is recorded by the per-file SHA-256 values below.

Included tables:

- `cn_dicts/8105.dict.yaml` version `2026-07-11`
- `cn_dicts/base.dict.yaml` (cleaned local snapshot)
- `cn_dicts/ext.dict.yaml` version `2026-06-20`
- `cn_dicts/others.dict.yaml` version `2026-03-08`
- `en_dicts/en.dict.yaml` version `2026-07-11`
- `en_dicts/en_ext.dict.yaml` version `2026-06-14`

Included file SHA-256 values:

- `8105`: `ddad7554a5bdecbbeb557ee703ecee548c828722a262ec9e5aee9caad8e52cf8`
- `base`: `f0af38499e3bda36e616ce656ceba66a3bb18717102d0db9519d2ef8ec650a33`
- `ext`: `543859f891dec5335b831840d895e1a1c4ef500648ae52b5e7c2963a2a2d256`
- `others`: `6a6b1a77d94c7cdf9203cf426e67f350215d2d73259fe3769c97d2a18f521c28`
- `en`: `bd3d3c73436eb81ca593518ae51e47ed655c2061e19b4924395a6048bd481bc4`
- `en_ext`: `e19c1fa65b653c8ed4eb3a83b2d5d88629ddd0d2ae444fa30c6b8a34a2c1eb2d`

WindIme modifications:

- Added a minimal `rime_ice.schema.yaml` suitable for the physical-key input pipeline.
- Added a local `rime_ice.dict.yaml` import entry point.
- Added a separate optional English dictionary/schema entry point.
- Excluded the optional 41,448-character table, Tencent dictionary, Lua,
  OpenCC/Emoji data and grammar models to control storage, memory and plugin
  requirements on Android 5.x flip phones.

The Tencent dictionary remains in the upstream/local source tree as a future
optional network-loaded extension. It is deliberately absent from the APK and
from the default `rime_ice` import list.

The dictionary source files themselves are otherwise unmodified. The complete
GPL v3 license is bundled as `LICENSE.rime-ice.txt` in this directory.
