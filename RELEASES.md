# Windows Releases

The desktop app checks `trustrankseo/video-downloader` for the latest GitHub Release.

## Publish an update

1. Change `APP_VERSION` in `app.py`.
2. Commit and push the changes to the `desktop-windows` branch.
3. Create and push a tag matching the version, for example `v1.1.0`.
4. GitHub Actions builds `VideoDownloaderSetup-v1.1.0.exe` and attaches it to the Release.

Installed users receive an update prompt the next time the app opens. They can also use **Check App Update** in the sidebar.
