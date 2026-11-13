# Release checklist (hdfv 0.25.0)

What each upload needs. Accounts, uploads and pushes are done by the user; each public step is agreed first.

## Before

- [ ] AeroCore 0.42.0 is published on the same platform (hdfv depends on it).
- [ ] `.\dev.ps1 :core:test :neoforge:runGameTestServer build` passes; `verifyReleaseJar` passes.
- [ ] The release test (AeroCore `scripts/release-test/release_test.py`): server base, jei, create; client base, jei;
      multiplayer. No suspicious lines.
- [ ] The JAR: `hdfv-neoforge-1.21.1-0.25.0.jar` (not `-sources`), from the local build folder
      (`.\dev.ps1 showBuildDirectories -q`, `neoforge\libs`).

## Modrinth

| Field | Value |
| --- | --- |
| Name | High Degree of Freedom Vehicles |
| Slug | hdfv |
| Summary | release/summary.txt |
| Description | release/page.md |
| Categories | Technology, Transportation (and Adventure if wanted) |
| Environment | client and server: required on both |
| License | LGPL-3.0-only |
| Source / issues | https://github.com/fiseleo/hdfv, /issues |
| Discord invite | https://discord.gg/4y2mubMg7e (never expires) |
| Icon | `%USERPROFILE%\.mcmod\release\hdfv\icon.png` (512 x 512) |
| Gallery | `%USERPROFILE%\.mcmod\release\hdfv\gallery\*.png` |
| Version | 0.25.0, release type **Beta**, loader NeoForge, game version 1.21.1 |
| Changelog | release/notes-0.25.0.md |
| Dependencies | AeroCore (required, version 0.42.0), JEI (optional), Create (optional) |

## CurseForge

Same texts and pictures; project type Mod, main category Technology, extra Transportation; release type Beta;
"NeoForge" and "1.21.1"; relations: AeroCore *Required Dependency*, JEI and Create *Optional Dependency*.

## GitHub

- [ ] The repository (`fiseleo/hdfv`, public, empty) created by the user; `git remote add origin` and push (agreed).
- [ ] A release `v0.25.0` from the tag, the notes, the JAR attached; marked pre-release (beta).

## After

- [ ] The pages' real links into README files and `displayURL` (a small commit, pushed when agreed).
