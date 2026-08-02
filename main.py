import flet as ft
import yt_dlp
import os
import threading
import time
from pathlib import Path
import re

APP_TITLE = "Video Downloader"

# Helper for Android Download Path
def get_download_dir():
    # If running on Android, this is the standard public Download directory
    android_path = Path("/storage/emulated/0/Download/VideoDownloader")
    if Path("/storage/emulated/0").exists():
        android_path.mkdir(parents=True, exist_ok=True)
        return str(android_path)
    
    # Fallback for Windows/Testing
    win_path = Path(os.path.expanduser("~")) / "Downloads" / "VideoDownloader"
    win_path.mkdir(parents=True, exist_ok=True)
    return str(win_path)

def main(page: ft.Page):
    page.title = APP_TITLE
    page.theme_mode = ft.ThemeMode.DARK
    page.window_width = 400
    page.window_height = 800
    page.padding = 20
    page.scroll = ft.ScrollMode.AUTO

    download_dir = get_download_dir()

    # --- UI Elements ---
    
    # 1. Single Download Tab
    url_input = ft.TextField(label="Paste Video URL", hint_text="https://...", width=300)
    
    quality_dropdown = ft.Dropdown(
        label="Quality",
        options=[
            ft.dropdown.Option("Best MP4"),
            ft.dropdown.Option("Audio Only (MP3)")
        ],
        value="Best MP4",
        width=300
    )

    status_text = ft.Text("Ready", color=ft.colors.BLUE_200)
    progress_bar = ft.ProgressBar(width=300, value=0, visible=False)
    
    log_text = ft.Text("", size=12, color=ft.colors.GREY_400)
    log_container = ft.Container(
        content=log_text,
        height=150,
        width=300,
        bgcolor=ft.colors.GREY_900,
        padding=10,
        border_radius=5,
    )

    # yt-dlp logger to route output to UI
    class FletYtdlpLogger:
        def debug(self, msg):
            self._update_log(msg)
        def warning(self, msg):
            self._update_log(f"WARN: {msg}")
        def error(self, msg):
            self._update_log(f"ERROR: {msg}")
            
        def _update_log(self, msg):
            if "download" in msg.lower() or "destination" in msg.lower() or "100%" in msg:
                # Keep log short
                lines = log_text.value.split('\n')[-4:]
                lines.append(msg)
                log_text.value = '\n'.join(lines)
                page.update()

    def progress_hook(d):
        if d['status'] == 'downloading':
            try:
                # Calculate progress
                p = d['_percent_str']
                p = re.sub(r'[^0-9.]', '', p)
                val = float(p) / 100.0
                progress_bar.value = val
                status_text.value = f"Downloading... {d['_percent_str']}"
                page.update()
            except:
                pass
        elif d['status'] == 'finished':
            progress_bar.value = 1.0
            status_text.value = "Processing video..."
            page.update()

    def start_download(e):
        url = url_input.value.strip()
        if not url:
            status_text.value = "Please enter a valid URL."
            status_text.color = ft.colors.RED_400
            page.update()
            return

        # Disable UI
        dl_button.disabled = True
        progress_bar.visible = True
        progress_bar.value = 0
        status_text.value = "Starting download..."
        status_text.color = ft.colors.BLUE_200
        log_text.value = ""
        page.update()

        # Run in thread
        threading.Thread(target=download_worker, args=(url, quality_dropdown.value), daemon=True).start()

    def download_worker(url, quality):
        try:
            ydl_options = {
                "outtmpl": f"{download_dir}/%(title)s.%(ext)s",
                "progress_hooks": [progress_hook],
                "logger": FletYtdlpLogger(),
                "restrictfilenames": False,
                "nocheckcertificate": True,
                "ignoreerrors": False,
                "nooverwrites": True,
                "extractor_args": {"youtube": ["player_client=android,web"]}
            }
            
            if quality == "Audio Only (MP3)":
                ydl_options["format"] = "bestaudio/best"
                ydl_options["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}]
            else:
                ydl_options["format"] = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                ydl_options["merge_output_format"] = "mp4"

            with yt_dlp.YoutubeDL(ydl_options) as ydl:
                ydl.download([url])
                
            status_text.value = f"Download Complete! Saved to:\\n{download_dir}"
            status_text.color = ft.colors.GREEN_400
            
        except Exception as e:
            status_text.value = f"Failed: {str(e)[:50]}..."
            status_text.color = ft.colors.RED_400
        finally:
            dl_button.disabled = False
            progress_bar.visible = False
            page.update()

    dl_button = ft.ElevatedButton("Download", on_click=start_download, icon=ft.icons.DOWNLOAD, width=300)

    # Combine into a view
    single_dl_view = ft.Column([
        ft.Text("Download Video", size=24, weight=ft.FontWeight.BOLD),
        ft.Text(f"Save Path: {download_dir}", size=12, color=ft.colors.GREY_500),
        url_input,
        quality_dropdown,
        dl_button,
        progress_bar,
        status_text,
        ft.Divider(),
        ft.Text("Logs:", size=12),
        log_container
    ], horizontal_alignment=ft.CrossAxisAlignment.CENTER)

    # Since it's an initial version for Android, we will keep it simple with Single Download.
    # We can expand to Bulk/Channel using Navigation in future updates.
    
    page.add(single_dl_view)

ft.app(target=main)
