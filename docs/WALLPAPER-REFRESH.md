# Local wallpaper rotation and Commons refresh

Clean Home changes wallpaper after 30 minutes while Home is visible. The selection time and pause setting survive process recreation. Returning Home or waking to Home refreshes an overdue selection; no alarm wakes the TV. Clock rewinds trigger one refresh. The existing image stays visible during preparation.

Every 48 hours, while Home is visible, a background worker retrieves a fresh batch of 12 photographs from Category:Featured pictures of landscapes. An overdue batch waits until Home is visible. Pausing rotation does not prevent collection refreshes.

Sources are HTTPS Commons API and official upload/thumbnail hosts. Requests need no account, key or backend. Image requests omit campaign query parameters. Files require JPEG metadata, landscape proportions, HD dimensions, a named author and a supported free licence. Categories/descriptions explicitly identifying generated art, painting, drawing, illustration or photomontage are rejected. These are automatic filters and community categorization, not human review of every future image.

Images are fully downloaded, validated and decoded before a complete batch is published atomically. Each download is capped at 2 MiB; cached images, including an in-progress batch, are capped at 50 MiB. The previous batch and selected image remain readable through a publication; stale files are pruned before the next refresh. A failed/interrupted/partial refresh preserves the last complete batch and retries after 30 minutes while Home is visible. Corrupt manifests fall back to the 24 bundled photographs.

The current wallpaper is not replaced merely because a batch arrived. The next scheduled or manual selection uses the new batch. Author, licence and Commons file page appear under About this image.

Implementation: Rotation.kt, RefreshPolicy.kt, CommonsFeed.kt, WallpaperCache.kt, WallpaperStore.kt and HomeActivity.kt. No background service, boot receiver, wake lock, alarm, listening network service or advertising/analytics SDK is added. Network permission is now intentional for Commons and owner-confirmed local ADB recovery; overlay permission is only for the transient input label.

Primary API references: [Image metadata](https://www.mediawiki.org/wiki/API:Imageinfo), [category members](https://www.mediawiki.org/wiki/API:Categorymembers), [API etiquette](https://www.mediawiki.org/wiki/API:Etiquette). The live API returned thumb.wikimedia.org thumbnail URLs during implementation; both upload and thumbnail hosts are narrowly allowed.
