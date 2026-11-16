# Patchbay Launcher packaging for mods

This is the publishing contract for this repository. The discovery and install
behavior below was verified against [Patchbay Launcher 0.9.1][launcher].
The launcher reads GitHub Releases and the mod's existing `mod.json`. This
document is for maintainers and fork authors.

## Publish a mod

1. Keep the source manifest at `<mod-id>/mod.json` in the repository's default
   branch. Use the same stable ID in the directory name and `mod.id`. The
   launcher can read this file for the display name, author, description and
   requirements. The release ZIP supplies the manifest used during installation.
2. Build a separate ZIP for each mod with the [release workflow](releasing.md).
   The ZIP must contain exactly one top-level directory named for `mod.id`. Put
   `mod.json`, the built helper JAR and the mod's resources inside that directory.
3. Name the ZIP `<mod-id>-<version>.zip`, with the exact `mod.version` from the
   packaged manifest. Use `major.minor.patch` with an optional suffix such as
   `-dev.9`. Launcher 0.9.1 accepts letters, digits and dots after the hyphen.
   Keep `v` prefixes and `+build` metadata out of asset versions.
4. Attach the built ZIP to a published GitHub Release. Prereleases work.
   Several mods can share a release because the launcher reads each asset name
   independently of the tag. GitHub's generated **Source code** ZIP serves as a
   source download and lacks the built helper.
5. Increase `mod.version` and the ZIP filename version together for every
   update. The launcher compares the version from the asset name against the
   installed manifest. Replacing the asset at the same version leaves existing
   installs at that version.

For example, [Note Intervals](../note-intervals/mod.json) ships as
`note-intervals-0.1.0-dev.8.zip`, with `note-intervals/mod.json` inside.

## Add a fork or a separate mod repository

Publish the ZIPs as release assets in the fork. In the launcher, open
**Sources → Add source**, choose **Mods**, enter `owner/repo`, and use
`*.zip` as the file filter. Refresh **Mods** and verify that the intended ID
and version appear.

The launcher merges mods across enabled sources by ID and keeps the highest
version. Disable the original source when testing a fork with the same ID, or
give an independently installable fork its own ID in the source directory,
manifest, ZIP directory and asset filename.

## Discovery limits

- Launcher 0.9.1 requests one page of 30 GitHub Releases per source. An asset
  can fall out of the catalog as newer releases are published.
- The catalog chooses versions by ID and filename. It does not use
  `built_against` to select a ZIP for a particular Bitwig version, OS or
  architecture. Publish one ZIP per mod version and document its compatibility.
- Declare dependencies in the manifest's `requires` list. The launcher can
  queue a required mod it finds in the catalog and reports missing ones. Verify
  the required frameworks and versions when testing in Patchbay.

Implementation references: [asset naming and repository metadata][metadata],
[release catalog and installed versions][catalog], and [ZIP validation and
installation][install].

[launcher]: https://github.com/arius77R/patchbay-launcher/tree/v0.9.1
[metadata]: https://github.com/arius77R/patchbay-launcher/blob/v0.9.1/src/Mods.java#L28-L76
[catalog]: https://github.com/arius77R/patchbay-launcher/blob/v0.9.1/src/Mods.java#L235-L315
[install]: https://github.com/arius77R/patchbay-launcher/blob/v0.9.1/src/Mods.java#L331-L468
