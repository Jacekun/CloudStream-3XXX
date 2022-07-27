# CloudStream-3XXX r1607
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/8ad17143a4e7da6506e63cde280c149cea5af58d).
+ Fix issues found after merging Cs3 base.
+ [Provider] Add Pandamovies (WIP).
+ Enable adult contents on some providers.
+ Add: version text on main settings UI.
+ Add: Toggle to use search-only sites for NSFW providers. Defaults to false (hide).
+ Enabled pre-release:
  + Changed App name for pre-release variant.
  + Changed launcher icon for pre-release.
  + Fix InApp Updater for pre release.
+ Changed filename of apk saved on InApp Updater.
+ Upgrade gradle to 7.2.1.
+ Rename StreamLare to Streamlare to be consistent to upstream.


# CloudStream-3XXX r1344
+ Fix: JavSub homepage and search.
+ Upgrade gradle to 7.1.3
+ Replace deprecated versionCode call to getLongVersionCode()
+ Append version code to app version on settings check update preference.

# CloudStream-3XXX r1323
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/e64a8755439a0e4fb6401549a64f79431794fbab).
+ Fixed issues after merging upstream.
+ Better updater performance.
+ Fixed TvType for Hentai and XXX sources.
+ Fixed ACRA link
+ Cleanup on JavFree and JavHD titles.
+ Various other cleanups.

# CloudStream-3XXX r1201
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/4447196ebc1fa576017230ee4f550d77355affa6).
+ [Feature] Random button on Homepage.
+ Added premium sub and dubs on Krunchy [#25](https://github.com/Jacekun/CloudStream-3XXX/pull/25) (KillerDogeEmpire).
+ Fix: Preferred media for filtering.
+ JavFree: Fix stream
+ Better UI for non-streaming providers.
+ Fixed StreamSB extractor.
+ Better updater logic.
+ Updated gradle version
+ Updated scripts.
+ Various cleanups.

# CloudStream-3XXX r1157
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/ad2f2ec4127b77799d0dfa1263e7731446db33eb).
+ Fixed Krunchy provider [#21](https://github.com/Jacekun/CloudStream-3XXX/pull/21).
+ UX Improvement: Hide 'Play Movie' button when streaming is not available for provider. Cleanup on various providers.
+ Enabled download on Hanime provider.
+ Various cleanups on providers.

# CloudStream-3XXX r1090
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/9eec4df8ba2c44b969f98a495f12e2317952fda3).
+ Enable some providers and extractors disabled from upstream.
+ Various cleanups and optimizations.

# CloudStream-3XXX r1026
+ Upgrade gradle version.
+ Better updater code.
+ Changed ACRA link.

# CloudStream-3XXX r1021
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/8f9ac96de51e8f9d6529831c94b7f4714f3d8371).
+ Fix: Issues after merging base.
+ Added hentai providers: Hanime, Hahomoe (#11), JKHentai, Hentaila (#10).
+ Changed array caption for NSFW.

# CloudStream-3XXX r972
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/a237ee5b7ade3d19e80cac7de1d663d17d890e88).
+ Fix: Issues after merging base.
+ Added 2 new sites: Xvideos (#5) and Pornhub (#6).

# CloudStream-3XXX r954
+ Fix for PlayLt extractor not working properly.

# CloudStream-3XXX r952
+ [CloudStream-3 base update](https://github.com/LagradOst/CloudStream-3/tree/c191d16b01f70e8cd2a89f48810dc0b00f2ab51f).
+ Fix: Issues after merging base.
+ Add new Extractor: PlayLt, mainly used by OpJAV.
+ Removed non-working Extractor: DoodSh.
+ Minor refactor to StreamLare extractor.
+ Javcl: Skip empty links on search result.
+ JavSub: Stop trying to fetch DoodWs links.
+ JavSub: Add recommendations and tags.
+ JavHD: Remove prefix and suffix from title.
+ JavHD: Load asynchronously for faster loading of info.
+ JavHD: Add recommendations and tags.
+ OpJAV: Add new link source.
+ OpJAV: Add tags.
+ OpJAV: Load asynchronously for faster loading of info.
+ Various sources refactors and cleanups.

# CloudStream-3XXX r905
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/tree/dcb97a1f63326ce70a428acd0a81cfa2b1c8bd86).
+ Fix: Issues after merging base.
+ Javhdicu: Load all scene as episodes. Parse all scenes properly.
+ Javhdicu: Fix remove JAV HD prefix from title. Faster load of links.
+ Javsubco: Minor refactor for faster load of links.

# CloudStream-3XXX r881
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/tree/e0925cfded9aa57c2b276c5e159f1e00cb49cb8a).
+ Fix: Issues after merging base.
+ Include torrent on movies and tvseries filter.
+ Javtubewatch: fix loading of image and info.
+ Opjavcom: Now able to watch from app.

# CloudStream-3XXX r846
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/e00afdfa0d9f5873b12808bd59b5e56823a22271).
+ Fix: Issues after merging base.
+ Code cleanups.

# CloudStream-3XXX r833
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/296f58a0b2df75923f9dcdcedcbe89dbc9291f08).
+ Fix: Issues after merging base.
+ Actually fix the 'All' filter.
+ Changed icons and banners for debug.
+ javhdicu, javsubco: refactored for consistency.
+ javsubco: Changed DoodLa to DoodWs extractor.
+ javtube: fix showing tags url on search results.

# CloudStream-3XXX r791
+ Fix show all tvtypes on 'All' filter.

# CloudStream-3XXX r789
+ Rename app title to match repo name.
+ Show Jav and Hentai when Preferred Media is set to 'All'.
+ Fix misleading caption 'JAV and Hentai'. Changed Homepage button to 'NSFW'.
+ Add Jav source: JavTube.
+ Javhdicu, javsubco: Refactor to allow loading links from all extractors.
+ Javhdicu, javsubco: Allow downloading.
+ Javhdicu: Removed site name on HomePage entries' title.
+ Javsubco: Refactor to allow Skip loading of all links.
+ Vlxx: Refactor to add DdosBypass.

# CloudStream-3XXX r776
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/2a1e0d98a31df4b01660c2478a3088faa4d8ca9a).
+ Fix: Issues after merging base.
+ Removed timeouts for all JAV providers.
+ Changed repo link on App settings view.
+ Add: JAV and Hentai to homepage selection choices.
+ javhdicu: trim site name on titles.
+ javsubco: fix showing entries without title or proper link.
+ opjavcom: add ajax request to fetch links.
+ javfreesh: migrate json objects.

# CloudStream-3XXX r713
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/444488849eb766627aa3075140021e479d6749bb).
+ Add JAV source: Javcl
+ Vlxx: minor refactor


# CloudStream-3XXX r700
+ Fix: Release update checker.
+ Javfreesh: Fix missing image on load.
+ JavHD: Fix incorrect year. Refactored code.

# CloudStream-3XXX r690
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/64ce14e290670c5214ca05d900acb87218700fb0).
+ Fix: After applying preferred media setting, update last saved active api and types.
+ Changed update url to point to this repo, and changed update implementation for release.
+ Change package build apk name.
+ JAV HD: Fetch links and stream.
+ JavSub: Fix load correct image.
## dev
+ Bump versionCode on build action.
+ Add ``StreamLare`` extractor.
+ Minor refactor to ``FEmbed`` extractor.
+ Change scope of ``Extractor`` ``name`` property to be changeable.

# CloudStream-3XXX r651
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/29327688911741a075c03e2b586d09e38db8388a).
+ Changed Icon.
+ Javsub: Fetch ``fembed`` stream links.
+ vlxx: add null-checker for titles.

# CloudStream-3XXX r636
+ Add: Vlxx site, [PR #4](https://github.com/Jacekun/CloudStream-3XXX/pull/4). *Thanks to [**@duongnv1996**](https://github.com/duongnv1996)*.
+ Add: Javsub streamable links for StreamTape server.

# CloudStream-3XXX r623
+ CloudStream-3 base update, [head commit](https://github.com/LagradOst/CloudStream-3/commit/54effd6c80bf21ee66e49afbd8b7078f4a115d37).
+ Fix: Changing '*Preferred Media*' doesn't apply to search filter default selection.

# CloudStream-3XXX r613
+ CloudStream-3 base update.

# CloudStream-3XXX v2.2.3
+ Searchable JAV sites.
+ '*Preferred Media*' setting applies to '*Search*' filters.
+ Signed with different key from Main app.
+ Crunchyroll site.