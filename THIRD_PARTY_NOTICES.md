# Third-Party Notices

This project includes third-party open-source components and data files.
The combined repository and released APKs are licensed under GPL-3.0-only
because they include Rime Ice dictionaries. See [NOTICE](NOTICE).

## Rime Ice

- Project: 雾凇拼音 / Rime Ice
- Source: https://github.com/iDvel/rime-ice
- License: GPL-3.0-only
- Included files:
  - `app/src/main/assets/rime/rime_ice.dict.yaml`
  - `app/src/main/assets/rime/cn_dicts/8105.dict.yaml`
  - `app/src/main/assets/rime/cn_dicts/base.dict.yaml`
  - `app/src/main/assets/rime/cn_dicts/ext.dict.yaml`
  - `app/src/main/assets/rime/cn_dicts/tencent.dict.yaml`
  - `app/src/main/assets/rime/cn_dicts/others.dict.yaml`
  - `app/src/main/assets/rime/LICENSE.rime-ice`

These dictionaries are the primary simplified Chinese lexicon for the
bundled `openphone_pinyin` and `openphone_t9` schemas. A distribution
that includes them must be conveyed under GPL-3.0-only.

`tencent.dict.yaml` is derived from Tencent AI Lab word embeddings and
redistributed as part of Rime Ice under GPL-3.0-only.

## Rime Prelude

- Project: rime-prelude
- Source: https://github.com/rime/rime-prelude
- License: LGPL-3.0
- Included files:
  - `app/src/main/assets/rime/default.yaml`
  - `app/src/main/assets/rime/key_bindings.yaml`
  - `app/src/main/assets/rime/punctuation.yaml`
  - `app/src/main/assets/rime/symbols.yaml`
  - `app/src/main/assets/rime/LICENSE.rime-prelude`

## Rime / librime

- Project: librime
- Source: https://github.com/rime/librime
- License: BSD-3-Clause
- Included files: `app/src/main/cpp/librime/` (engine sources)

## OpenCC

- Project: OpenCC
- Source: https://github.com/BYVoid/OpenCC
- License: Apache License 2.0
- Included files:
  - `app/src/main/assets/rime/opencc/t2s.json`
  - `app/src/main/assets/rime/opencc/TSCharacters.txt`
  - `app/src/main/assets/rime/opencc/TSPhrases.txt`
  - `app/src/main/assets/rime/LICENSE.opencc`
  - OpenCC sources used at build time from `librime/deps/opencc`

OpenCC is used by the Rime simplifier filter for Chinese conversion and
candidate normalization.

## Build-time native dependencies

These libraries are compiled into the native Rime bridge. Some are
present locally under `app/src/main/cpp/librime/deps/` and may be
fetched during CMake configuration.

| Component | License | Source |
|---|---|---|
| Boost | Boost Software License 1.0 | https://www.boost.org/ |
| glog | BSD-3-Clause | https://github.com/google/glog |
| LevelDB | BSD-3-Clause | https://github.com/google/leveldb |
| yaml-cpp | MIT | https://github.com/jbeder/yaml-cpp |
| marisa-trie | BSD-2-Clause OR LGPL-2.1-or-later | https://github.com/s-yata/marisa-trie |
| OpenCC | Apache License 2.0 | https://github.com/BYVoid/OpenCC |

This project uses the BSD-2-Clause option of marisa-trie.

## Original application code

Original Open iOS Keyboard sources are additionally available
under Apache License 2.0. See [NOTICE](NOTICE) and
[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt).
