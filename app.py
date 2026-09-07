from __future__ import annotations

import concurrent.futures
import os
import re
import json
import hashlib
import random
import shutil
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from datetime import datetime
from pathlib import Path
from urllib.parse import quote_plus, urlparse
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from xml.etree import ElementTree

import imageio_ffmpeg
import sv_ttk
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from PIL import Image, ImageTk


APP_TITLE = "Video Downloader by Zubair Abbas"
APP_VERSION = "1.1.0"
GITHUB_REPOSITORY = "trustrankseo/video-downloader"
GITHUB_LATEST_RELEASE_API = f"https://api.github.com/repos/{GITHUB_REPOSITORY}/releases/latest"
DOWNLOAD_DIR = Path.home() / "Downloads" / "VideoDownloaderByZubairAbbas"
APP_DATA_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "VideoDownloaderByZubairAbbas"
LAST_LOG = APP_DATA_DIR / "last_download.log"
SETTINGS_FILE = APP_DATA_DIR / "settings.json"
HISTORY_FILE = APP_DATA_DIR / "download_history.jsonl"
DIAGNOSTICS_DIR = APP_DATA_DIR / "diagnostics"
YTDLP_UPDATE_DIR = APP_DATA_DIR / "yt_dlp_runtime"
LICENSE_FILE = Path(os.environ.get("APPDATA", str(APP_DATA_DIR))) / "VideoDownloaderByZA" / ".license.dat"
URL_RE = re.compile(r"^https?://", re.IGNORECASE)
DEFAULT_API_URL = "http://127.0.0.1:5000"
APIFY_TIKTOK_ACTOR_ENDPOINT = (
    "https://api.apify.com/v2/actors/clockworks~tiktok-profile-scraper/"
    "run-sync-get-dataset-items"
)

if YTDLP_UPDATE_DIR.exists():
    sys.path.insert(0, str(YTDLP_UPDATE_DIR))

import yt_dlp
from ddgs import DDGS

PLATFORM_PATTERNS = {
    "YouTube": ("youtube.com", "youtu.be"),
    "Instagram": ("instagram.com",),
    "TikTok": ("tiktok.com",),
    "Facebook": ("facebook.com", "fb.watch"),
    "RedNote / Xiaohongshu": ("xiaohongshu.com", "xhslink.com"),
    "Snapchat": ("snapchat.com",),
    "X / Twitter": ("twitter.com", "x.com"),
    "Vimeo": ("vimeo.com",),
}

QUALITY_FORMATS = {
    "Only MP4 Video": "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]",
    "Best quality": "bv*+ba/best",
    "Best MP4 compatible": "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best",
    "Up to 1080p": "bv*[height<=1080]+ba/b[height<=1080]/best[height<=1080]/best",
    "Up to 720p": "bv*[height<=720]+ba/b[height<=720]/best[height<=720]/best",
    "Audio only MP3": "bestaudio/best",
    "Audio only M4A": "bestaudio[ext=m4a]/bestaudio/best",
}
FALLBACK_FORMAT = "best/worst"
VIDEO_EXTENSIONS = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv", ".m4v"}
COOKIE_SOURCES = {
    "No browser cookies": "",
    "Use Edge cookies": "edge",
    "Use Chrome cookies": "chrome",
    "Use Firefox cookies": "firefox",
    "Use cookies.txt file": "file",
}

CREATOR_PLATFORMS = {
    "YouTube": ("youtube.com", "youtu.be"),
    "Instagram": ("instagram.com",),
    "TikTok": ("tiktok.com",),
    "Facebook": ("facebook.com",),
    "Snapchat": ("snapchat.com",),
    "X / Twitter": ("x.com", "twitter.com"),
    "Vimeo": ("vimeo.com",),
    "RedNote / Xiaohongshu": ("xiaohongshu.com", "xhslink.com"),
}
CREATOR_LANGUAGES = (
    "Any language", "English", "Urdu", "Hindi", "Arabic", "Spanish", "French",
    "German", "Portuguese", "Turkish", "Indonesian", "Bengali", "Japanese",
    "Korean", "Chinese",
)
CREATOR_NICHES = {
    "Entertainment": ("Hollywood movie clips", "film reviews", "celebrity news", "TV series recaps", "anime edits", "comedy sketches"),
    "Gaming": ("mobile gaming", "PC gaming", "console reviews", "game walkthroughs", "esports highlights", "indie games"),
    "Technology": ("AI tools", "smartphone reviews", "software tutorials", "coding", "cybersecurity", "consumer gadgets"),
    "Business": ("entrepreneurship", "small business", "ecommerce", "digital marketing", "freelancing", "startup news"),
    "Finance": ("personal finance", "stock market", "cryptocurrency", "real estate investing", "side hustles", "budgeting"),
    "Education": ("English learning", "science lessons", "history", "mathematics", "exam preparation", "career guidance"),
    "Lifestyle": ("daily vlogs", "minimal living", "productivity", "home organization", "relationships", "personal development"),
    "Health & Fitness": ("home workouts", "weight loss", "bodybuilding", "yoga", "healthy recipes", "mental wellness"),
    "Beauty & Fashion": ("makeup tutorials", "skincare", "mens fashion", "womens fashion", "hair care", "affordable outfits"),
    "Food": ("street food", "restaurant reviews", "quick recipes", "baking", "healthy meals", "traditional cuisine"),
    "Travel": ("budget travel", "luxury travel", "city guides", "road trips", "hotel reviews", "travel tips"),
    "Sports": ("football highlights", "cricket analysis", "combat sports", "basketball news", "sports commentary", "athlete interviews"),
    "Automotive": ("car reviews", "motorcycles", "vehicle restoration", "electric vehicles", "car modification", "driving tips"),
    "Home & DIY": ("woodworking", "home renovation", "gardening", "interior design", "crafts", "repair tutorials"),
    "Family": ("parenting", "kids activities", "family vlogs", "pregnancy", "education for kids", "family cooking"),
    "Music": ("music covers", "instrument tutorials", "music production", "artist interviews", "live performances", "song reactions"),
    "News & Culture": ("world news", "local news", "documentaries", "pop culture", "book reviews", "social commentary"),
    "Pets & Nature": ("pet care", "wildlife", "nature photography", "farming", "aquariums", "outdoor survival"),
}


def get_ffmpeg_path() -> str | None:
    system_ffmpeg = shutil.which("ffmpeg")
    if system_ffmpeg:
        return system_ffmpeg
    try:
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return None


def get_node_path() -> str | None:
    return shutil.which("node")


def detect_platform(url: str) -> str:
    host = urlparse(url).netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    for platform, domains in PLATFORM_PATTERNS.items():
        if any(host.endswith(domain) for domain in domains):
            return platform
    return "Supported site"


def load_settings() -> dict:
    APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
    if SETTINGS_FILE.exists():
        try:
            return json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_settings(settings: dict) -> None:
    APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
    SETTINGS_FILE.write_text(json.dumps(settings, indent=2), encoding="utf-8")


def get_device_id(settings: dict) -> str:
    device_id = settings.get("device_id")
    if not device_id:
        device_id = hashlib.sha256(f"{uuid.getnode()}-{os.getlogin()}".encode("utf-8", errors="ignore")).hexdigest()[:24]
        settings["device_id"] = device_id
        save_settings(settings)
    return device_id


def api_post(api_url: str, path: str, payload: dict, timeout: int = 10) -> dict:
    url = api_url.rstrip("/") + path
    data = json.dumps(payload).encode("utf-8")
    request = Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        try:
            body = json.loads(exc.read().decode("utf-8"))
        except Exception:
            body = {"error": str(exc)}
        return {"ok": False, **body}
    except URLError as exc:
        return {"ok": False, "error": f"Admin server not reachable: {exc.reason}"}
    except Exception as exc:
        return {"ok": False, "error": str(exc)}


def get_license_status_text() -> str:
    """Display license information saved by the launcher without exposing the key."""
    try:
        encrypted = LICENSE_FILE.read_bytes()
        obfuscation_key = b"ZubairAbbasVD2026"
        raw = bytes(value ^ obfuscation_key[index % len(obfuscation_key)] for index, value in enumerate(encrypted))
        key = json.loads(raw.decode("utf-8")).get("k", "")
        parts = key.split("-")
        if len(parts) == 7:
            return "License: Lifetime" if parts[5] == "LIFE" else f"License expires: {parts[5][0:4]}-{parts[5][4:6]}-{parts[5][6:8]}"
        return "License: Lifetime"
    except Exception:
        return "License: Managed by launcher"


class DownloaderApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("1240x940")
        self.minsize(1000, 780)
        self.configure(bg="#eef2f4")

        self.settings = load_settings()
        self.device_id = get_device_id(self.settings)
        self.api_url = tk.StringVar(value=self.settings.get("api_url", DEFAULT_API_URL))
        self.username = tk.StringVar(value=self.settings.get("username", ""))
        self.password = tk.StringVar()
        self.access_status = tk.StringVar(value="Access not checked")
        self.access_granted = False
        self.download_dir = tk.StringVar(value=str(DOWNLOAD_DIR))
        self.url = tk.StringVar()
        self.quality = tk.StringVar(value="Only MP4 Video")
        self.cookies = tk.StringVar(value="No browser cookies")
        self.playlist_mode = tk.BooleanVar(value=False)
        self.skip_duplicates = tk.BooleanVar(value=True)
        self.filename_template = tk.StringVar(value="title + id")
        self.folder_layout = tk.StringVar(value="Single folder")
        self.download_subtitles = tk.BooleanVar(value=False)
        self.download_thumbnail = tk.BooleanVar(value=False)
        self.status = tk.StringVar(value="Ready")
        self.progress = tk.DoubleVar(value=0)
        self.current_process = None
        self.download_start_timestamp = 0.0
        self.latest_downloaded_file: Path | None = None
        self.clip_source_status = tk.StringVar(value="Latest video: complete a download or choose a video")
        self.bulk_running = False
        self.failed_urls: list[str] = []
        self.bulk_item_ids: dict[str, str] = {}
        self.bulk_pause_event = threading.Event()
        self.bulk_pause_event.set()
        self.scheduled_time = tk.StringVar(value=self.settings.get("scheduled_time", ""))
        self.schedule_enabled = tk.BooleanVar(value=bool(self.scheduled_time.get()))
        self.last_schedule_run = self.settings.get("last_schedule_run", "")

        # Channel download state
        self.channel_url = tk.StringVar()
        self.channel_info = tk.StringVar(value="Paste a channel/profile URL from any platform and click Fetch")
        self.channel_batch_size = tk.StringVar(value="5 at a time")
        self.channel_videos: list[dict] = []
        self.channel_video_item_ids: dict[int, str] = {}
        self.channel_downloading = False
        self.channel_stop_requested = False
        self.channel_completed_count = 0
        self.channel_failed_count = 0
        self.apify_token = tk.StringVar(value=self.settings.get("apify_token", ""))
        self.apify_max_results = tk.IntVar(value=int(self.settings.get("apify_max_results", 1000)))

        self.creator_query = tk.StringVar()
        self.creator_platform = tk.StringVar(value="YouTube")
        self.creator_language = tk.StringVar(value="English")
        self.creator_limit = tk.IntVar(value=20)
        self.creator_status = tk.StringVar(value="Search by niche or choose a random niche")
        self.creator_results: list[dict] = []

        # Video editor state
        self.editor_video_path: Path | None = None
        self.editor_video_duration = 0.0
        self.editor_clips: list[tuple[float, float]] = []
        self.editor_source_status = tk.StringVar(value="No video imported")
        self.editor_mode = tk.StringVar(value="Single Video")
        self.editor_duration_value = tk.IntVar(value=20)
        self.editor_duration_unit = tk.StringVar(value="Seconds")
        self.editor_manual_start = tk.StringVar(value="00:00:00")
        self.editor_manual_end = tk.StringVar(value="00:00:20")
        self.editor_filter = tk.StringVar(value="Original")
        self.editor_lut_path = tk.StringVar(value="")
        self.editor_adjustments = {name: tk.DoubleVar(value=0) for name in ("Brightness", "Contrast", "Saturation", "Temperature")}
        self.editor_filter_intensity = tk.IntVar(value=100)
        self.editor_filter_intensity_text = tk.StringVar(value="100%")
        self.editor_effect = tk.StringVar(value="None")
        self.editor_transition = tk.StringVar(value="None")
        self.editor_aspect_ratio = tk.StringVar(value="Original")
        self.editor_resolution = tk.StringVar(value="Original")
        self.editor_fps = tk.StringVar(value="Original")
        self.editor_text = tk.StringVar()
        self.editor_text_position = tk.StringVar(value="Bottom")
        self.editor_text_size = tk.IntVar(value=42)
        self.editor_status = tk.StringVar(value="Import a video to begin editing")
        self.editor_stop_requested = False
        self.editor_preview_process = None
        self.editor_preview_playing = False
        self.editor_preview_position = tk.DoubleVar(value=0.0)
        self.editor_preview_time = tk.StringVar(value="00:00:00.00 / 00:00:00.00")
        self.editor_preview_end = 0.0
        self.editor_preview_image = None
        self.editor_preview_generation = 0
        self.editor_preview_refresh_job = None
        self.editor_preview_refresh_process = None
        self.editor_preview_refresh_generation = 0
        self.editor_undo_stack: list[dict] = []
        self.editor_redo_stack: list[dict] = []
        self.editor_history_current: dict | None = None
        self.editor_history_ready = False

        self._build_style()
        self._build_ui()
        self.protocol("WM_DELETE_WINDOW", self.close_app)
        self.after(30000, self.check_schedule)
        self.after(2500, self.check_for_app_update)

    def _build_style(self) -> None:
        palette = {
            "background": "#eef2f4",
            "surface": "#ffffff",
            "surface_alt": "#f7f9fa",
            "text": "#17232d",
            "muted": "#64727d",
            "border": "#d4dde2",
            "accent": "#00a89d",
            "accent_hover": "#008f86",
            "accent_soft": "#d9f5f1",
            "heading": "#e7edef",
        }
        self.palette = palette
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure(".", font=("Segoe UI", 10), background=palette["background"], foreground=palette["text"])
        style.configure("TFrame", background=palette["background"])
        style.configure("TLabel", background=palette["background"], foreground=palette["text"])
        style.configure("Title.TLabel", font=("Segoe UI Semibold", 19), foreground="#101820")
        style.configure("Section.TLabel", font=("Segoe UI Semibold", 12), foreground="#101820")
        style.configure("Muted.TLabel", foreground=palette["muted"])
        style.configure(
            "TButton",
            background=palette["surface"],
            foreground=palette["text"],
            bordercolor=palette["border"],
            lightcolor=palette["surface"],
            darkcolor=palette["surface"],
            padding=(11, 7),
            relief="flat",
        )
        style.map(
            "TButton",
            background=[("active", palette["accent_soft"]), ("pressed", "#c4ece7"), ("disabled", "#e7ecef")],
            foreground=[("disabled", "#95a1a9")],
            bordercolor=[("focus", palette["accent"]), ("active", palette["accent"])],
        )
        style.configure(
            "Accent.TButton",
            font=("Segoe UI Semibold", 10),
            background=palette["accent"],
            foreground="#ffffff",
            bordercolor=palette["accent"],
            lightcolor=palette["accent"],
            darkcolor=palette["accent"],
            padding=(13, 8),
        )
        style.map(
            "Accent.TButton",
            background=[("active", palette["accent_hover"]), ("pressed", "#007b74"), ("disabled", "#9bcac6")],
            foreground=[("active", "#ffffff"), ("pressed", "#ffffff"), ("disabled", "#edf7f6")],
        )
        style.configure("TEntry", fieldbackground=palette["surface"], foreground=palette["text"], bordercolor=palette["border"], padding=7)
        style.configure("TSpinbox", fieldbackground=palette["surface"], foreground=palette["text"], bordercolor=palette["border"], arrowsize=14, padding=5)
        style.configure("TCombobox", fieldbackground=palette["surface"], foreground=palette["text"], bordercolor=palette["border"], arrowcolor=palette["muted"], padding=5)
        style.map("TCombobox", fieldbackground=[("readonly", palette["surface"]), ("disabled", "#e7ecef")], foreground=[("readonly", palette["text"]), ("disabled", "#95a1a9")])
        style.configure("TCheckbutton", background=palette["background"], foreground=palette["text"], padding=(0, 3))
        style.map("TCheckbutton", background=[("active", palette["background"])], indicatorcolor=[("selected", palette["accent"])])
        style.configure("TNotebook", background=palette["background"], borderwidth=0, tabmargins=(0, 0, 0, 7))
        style.configure("TNotebook.Tab", background="#dfe6e9", foreground=palette["muted"], padding=(14, 9), borderwidth=0)
        style.map("TNotebook.Tab", background=[("selected", palette["surface"]), ("active", palette["accent_soft"])], foreground=[("selected", palette["text"]), ("active", palette["text"])])
        style.configure("Treeview", background=palette["surface"], fieldbackground=palette["surface"], foreground=palette["text"], bordercolor=palette["border"], rowheight=29, relief="flat")
        style.map("Treeview", background=[("selected", palette["accent_soft"])], foreground=[("selected", palette["text"])])
        style.configure("Treeview.Heading", background=palette["heading"], foreground=palette["text"], font=("Segoe UI Semibold", 9), padding=(7, 7), relief="flat")
        style.map("Treeview.Heading", background=[("active", "#d8e2e6")])
        style.configure("TProgressbar", background=palette["accent"], troughcolor=palette["surface"], bordercolor=palette["border"], lightcolor=palette["accent"], darkcolor=palette["accent"])
        style.configure("TPanedwindow", background=palette["background"], sashwidth=5)

    def _build_ui(self) -> None:
        root = ttk.Frame(self, padding=12)
        root.pack(fill="both", expand=True)

        # Top Bar
        top_bar = ttk.Frame(root)
        top_bar.pack(fill="x", pady=(0, 10))
        ttk.Label(top_bar, text=APP_TITLE, style="Title.TLabel").pack(side="left")
        ttk.Label(top_bar, text=get_license_status_text(), style="Muted.TLabel").pack(side="right")
        self.access_granted = True
        self.access_status.set("Licensed")

        # Main Layout (PanedWindow)
        paned = ttk.PanedWindow(root, orient="horizontal")
        paned.pack(fill="both", expand=True)

        # --- LEFT SIDEBAR (Global Settings) ---
        sidebar_shell = ttk.Frame(paned, padding=(0, 0, 10, 0))
        paned.add(sidebar_shell, weight=0)
        sidebar_canvas = tk.Canvas(
            sidebar_shell,
            width=245,
            background=self.palette["background"],
            highlightthickness=0,
            borderwidth=0,
        )
        sidebar_scroll = ttk.Scrollbar(sidebar_shell, orient="vertical", command=sidebar_canvas.yview)
        sidebar_canvas.configure(yscrollcommand=sidebar_scroll.set)
        self.sidebar_canvas = sidebar_canvas
        sidebar_scroll.pack(side="right", fill="y")
        sidebar_canvas.pack(side="left", fill="both", expand=True)
        sidebar = ttk.Frame(sidebar_canvas, padding=(0, 0, 5, 0))
        sidebar_window = sidebar_canvas.create_window((0, 0), window=sidebar, anchor="nw")
        sidebar.bind("<Configure>", lambda _event: sidebar_canvas.configure(scrollregion=sidebar_canvas.bbox("all")))
        sidebar_canvas.bind("<Configure>", lambda event: sidebar_canvas.itemconfigure(sidebar_window, width=event.width))
        sidebar_shell.bind("<Enter>", lambda _event: self.bind_all("<MouseWheel>", lambda event: sidebar_canvas.yview_scroll(int(-event.delta / 120), "units")))
        sidebar_shell.bind("<Leave>", lambda _event: self.unbind_all("<MouseWheel>"))
        
        ttk.Label(sidebar, text="Global Settings", style="Section.TLabel").pack(anchor="w", pady=(0, 12))
        
        ttk.Label(sidebar, text="Download Folder").pack(anchor="w", pady=(0, 4))
        self.download_dir_entry = ttk.Entry(sidebar, textvariable=self.download_dir, width=28)
        self.download_dir_entry.pack(fill="x", pady=(0, 4))
        ttk.Button(sidebar, text="Choose Folder", command=self.choose_folder).pack(fill="x", pady=(0, 4))
        ttk.Button(sidebar, text="Open Folder", command=self.open_folder).pack(fill="x", pady=(0, 16))
        
        ttk.Label(sidebar, text="Video Quality").pack(anchor="w", pady=(0, 4))
        ttk.Combobox(sidebar, textvariable=self.quality, values=list(QUALITY_FORMATS.keys()), state="readonly").pack(fill="x", pady=(0, 16))
        
        ttk.Label(sidebar, text="Browser Cookies").pack(anchor="w", pady=(0, 4))
        self.cookies_cb = ttk.Combobox(sidebar, textvariable=self.cookies, values=list(COOKIE_SOURCES.keys()), state="readonly")
        self.cookies_cb.pack(fill="x", pady=(0, 4))
        self.cookies_cb.bind("<<ComboboxSelected>>", self.on_cookie_selected)
        ttk.Button(sidebar, text="Cookie Help", command=self.show_cookie_help).pack(fill="x", pady=(0, 16))
        
        ttk.Label(sidebar, text="File Name Template").pack(anchor="w", pady=(0, 4))
        ttk.Combobox(sidebar, textvariable=self.filename_template, values=("title + id", "title only", "platform + title", "date + title"), state="readonly").pack(fill="x", pady=(0, 16))

        ttk.Label(sidebar, text="Folder Organization").pack(anchor="w", pady=(0, 4))
        ttk.Combobox(sidebar, textvariable=self.folder_layout, values=("Single folder", "By platform", "By uploader", "By upload date", "Platform and uploader"), state="readonly").pack(fill="x", pady=(0, 16))
        
        ttk.Checkbutton(sidebar, text="Playlist mode", variable=self.playlist_mode).pack(anchor="w", pady=(0, 4))
        ttk.Checkbutton(sidebar, text="Skip duplicates", variable=self.skip_duplicates).pack(anchor="w", pady=(0, 16))
        ttk.Checkbutton(sidebar, text="Download subtitles", variable=self.download_subtitles).pack(anchor="w", pady=(0, 4))
        ttk.Checkbutton(sidebar, text="Save thumbnail", variable=self.download_thumbnail).pack(anchor="w", pady=(0, 16))
        
        ttk.Button(sidebar, text="Download History", command=self.show_history).pack(fill="x", pady=(0, 8))
        ttk.Button(sidebar, text="Schedule Downloads", command=self.show_schedule_dialog).pack(fill="x", pady=(0, 8))
        ttk.Button(sidebar, text="Channel API Settings", command=self.show_channel_api_settings).pack(fill="x", pady=(0, 8))
        ttk.Button(sidebar, text="Export Diagnostics", command=self.export_diagnostics).pack(fill="x", pady=(0, 8))
        ttk.Button(sidebar, text="Check App Update", command=lambda: self.check_for_app_update(True)).pack(fill="x", pady=(0, 8))
        ttk.Button(sidebar, text="Update yt-dlp", command=self.update_ytdlp).pack(fill="x", side="bottom")

        # --- RIGHT MAIN AREA (Tabs) ---
        main_area = ttk.Frame(paned)
        paned.add(main_area, weight=1)
        main_area.columnconfigure(0, weight=1)
        main_area.rowconfigure(0, weight=1)
        
        notebook = ttk.Notebook(main_area)
        notebook.grid(row=0, column=0, sticky="nsew")
        self.notebook = notebook

        single_tab = ttk.Frame(notebook, padding=12)
        bulk_tab = ttk.Frame(notebook, padding=12)
        channel_tab = ttk.Frame(notebook, padding=12)
        creator_tab = ttk.Frame(notebook, padding=12)
        editor_tab = ttk.Frame(notebook, padding=12)
        self.bulk_tab = bulk_tab
        self.channel_tab = channel_tab
        self.editor_tab = editor_tab
        notebook.add(single_tab, text="Single Video")
        notebook.add(bulk_tab, text="Bulk Video")
        notebook.add(channel_tab, text="Channel Download")
        notebook.add(creator_tab, text="Creator Finder")
        notebook.add(editor_tab, text="Video Editor")

        # -- Single Tab --
        ttk.Label(single_tab, text="Paste a video URL to download:").pack(anchor="w", pady=(0, 5))
        url_row = ttk.Frame(single_tab)
        url_row.pack(fill="x", pady=(0, 12))
        self.url_entry = ttk.Entry(url_row, textvariable=self.url, font=("Segoe UI", 11))
        self.url_entry.pack(side="left", fill="x", expand=True, padx=(0, 8))
        ttk.Button(url_row, text="Paste URL", command=self.paste_url).pack(side="left")
        
        self.download_button = ttk.Button(single_tab, text="Download Video", command=self.start_download, style="Accent.TButton")
        self.download_button.pack(anchor="w")

        clip_section = ttk.Frame(single_tab)
        clip_section.pack(fill="x", pady=(16, 0))
        ttk.Label(clip_section, text="Video Clips", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        ttk.Label(clip_section, textvariable=self.clip_source_status).pack(anchor="w", pady=(2, 6))
        clip_actions = ttk.Frame(clip_section)
        clip_actions.pack(fill="x")
        self.create_clips_button = ttk.Button(clip_actions, text="Create Clips", command=self.show_create_clips_dialog, state="disabled")
        self.create_clips_button.pack(side="left")
        ttk.Button(clip_actions, text="Choose Existing Video", command=self.choose_clip_source).pack(side="left", padx=(8, 0))

        # -- Bulk Tab --
        ttk.Label(bulk_tab, text="Paste one URL per line, or load a .txt file. Empty lines are ignored.").pack(anchor="w", pady=(0, 5))
        self.bulk_text = tk.Text(bulk_tab, height=8, wrap="word", font=("Segoe UI", 10))
        self.style_text_widget(self.bulk_text)
        self.bulk_text.pack(fill="both", expand=True)
        
        bulk_actions = ttk.Frame(bulk_tab)
        bulk_actions.pack(fill="x", pady=(8, 12))
        ttk.Button(bulk_actions, text="Paste URLs", command=self.paste_bulk_urls).pack(side="left")
        ttk.Button(bulk_actions, text="Load TXT", command=self.load_bulk_file).pack(side="left", padx=(8, 0))
        ttk.Button(bulk_actions, text="Retry Failed", command=self.retry_failed_downloads).pack(side="left", padx=(8, 0))
        self.pause_bulk_button = ttk.Button(bulk_actions, text="Pause", command=self.pause_bulk_download, state="disabled")
        self.pause_bulk_button.pack(side="left", padx=(8, 0))
        self.resume_bulk_button = ttk.Button(bulk_actions, text="Resume", command=self.resume_bulk_download, state="disabled")
        self.resume_bulk_button.pack(side="left", padx=(8, 0))
        ttk.Button(bulk_actions, text="Clear List", command=lambda: self.bulk_text.delete("1.0", "end")).pack(side="left", padx=(8, 0))
        
        self.bulk_tree = ttk.Treeview(bulk_tab, columns=("status", "platform", "url"), show="headings", height=5)
        self.bulk_tree.heading("status", text="Status")
        self.bulk_tree.heading("platform", text="Platform")
        self.bulk_tree.heading("url", text="URL")
        self.bulk_tree.column("status", width=110, stretch=False)
        self.bulk_tree.column("platform", width=150, stretch=False)
        self.bulk_tree.column("url", width=300, stretch=True)
        self.bulk_tree.pack(fill="both", expand=True, pady=(0, 12))
        
        self.bulk_button = ttk.Button(bulk_tab, text="Start Bulk Download", command=self.start_bulk_download, style="Accent.TButton")
        self.bulk_button.pack(anchor="w")

        # -- Channel Tab --
        ttk.Label(channel_tab, text="Download all videos from a Channel/Profile:").pack(anchor="w", pady=(0, 5))
        ch_url_row = ttk.Frame(channel_tab)
        ch_url_row.pack(fill="x", pady=(0, 8))
        self.channel_url_entry = ttk.Entry(ch_url_row, textvariable=self.channel_url, font=("Segoe UI", 11))
        self.channel_url_entry.pack(side="left", fill="x", expand=True, padx=(0, 8))
        ttk.Button(ch_url_row, text="Paste URL", command=self.paste_channel_url).pack(side="left", padx=(0, 8))
        self.fetch_channel_btn = ttk.Button(ch_url_row, text="Fetch Videos", command=self.fetch_channel_videos, style="Accent.TButton")
        self.fetch_channel_btn.pack(side="left")

        ttk.Label(channel_tab, textvariable=self.channel_info).pack(anchor="w", pady=(0, 8))

        ch_tree_frame = ttk.Frame(channel_tab)
        ch_tree_frame.pack(fill="both", expand=True)
        self.channel_tree = ttk.Treeview(ch_tree_frame, columns=("num", "title", "duration", "status"), show="headings", height=8)
        self.channel_tree.heading("num", text="#")
        self.channel_tree.heading("title", text="Title")
        self.channel_tree.heading("duration", text="Duration")
        self.channel_tree.heading("status", text="Status")
        self.channel_tree.column("num", width=50, stretch=False)
        self.channel_tree.column("title", width=300, stretch=True)
        self.channel_tree.column("duration", width=80, stretch=False)
        self.channel_tree.column("status", width=110, stretch=False)
        
        ch_tree_scroll = ttk.Scrollbar(ch_tree_frame, orient="vertical", command=self.channel_tree.yview)
        self.channel_tree.configure(yscrollcommand=ch_tree_scroll.set)
        self.channel_tree.pack(side="left", fill="both", expand=True)
        ch_tree_scroll.pack(side="right", fill="y")

        ch_actions = ttk.Frame(channel_tab)
        ch_actions.pack(fill="x", pady=(12, 0))
        ttk.Label(ch_actions, text="Concurrent Downloads:").pack(side="left", padx=(0, 8))
        ttk.Combobox(ch_actions, textvariable=self.channel_batch_size, values=("3 at a time", "5 at a time"), state="readonly", width=12).pack(side="left")
        self.ch_download_btn = ttk.Button(ch_actions, text="Download All", command=self.download_channel_videos, style="Accent.TButton")
        self.ch_download_btn.pack(side="left", padx=(16, 8))
        self.ch_stop_btn = ttk.Button(ch_actions, text="Stop", command=self.stop_channel_download, state="disabled")
        self.ch_stop_btn.pack(side="left")
        self.channel_progress_label = ttk.Label(ch_actions, text="")
        self.channel_progress_label.pack(side="right")

        ttk.Label(creator_tab, text="Find creator channels and profiles", font=("Segoe UI", 13, "bold")).pack(anchor="w")
        ttk.Label(creator_tab, text="Enter a niche, topic, or keyword. Select multiple results with Ctrl or Shift.").pack(anchor="w", pady=(2, 10))
        creator_query_row = ttk.Frame(creator_tab)
        creator_query_row.pack(fill="x", pady=(0, 8))
        ttk.Entry(creator_query_row, textvariable=self.creator_query, font=("Segoe UI", 11)).pack(side="left", fill="x", expand=True, padx=(0, 8))
        ttk.Button(creator_query_row, text="Random Niche", command=self.choose_random_niche).pack(side="left", padx=(0, 8))
        self.creator_search_btn = ttk.Button(creator_query_row, text="Find Creators", command=self.find_creators, style="Accent.TButton")
        self.creator_search_btn.pack(side="left")

        creator_filters = ttk.Frame(creator_tab)
        creator_filters.pack(fill="x", pady=(0, 8))
        ttk.Label(creator_filters, text="Platform:").pack(side="left")
        ttk.Combobox(creator_filters, textvariable=self.creator_platform, values=("All platforms", *CREATOR_PLATFORMS.keys()), state="readonly", width=22).pack(side="left", padx=(6, 16))
        ttk.Label(creator_filters, text="Language:").pack(side="left")
        ttk.Combobox(creator_filters, textvariable=self.creator_language, values=CREATOR_LANGUAGES, state="readonly", width=16).pack(side="left", padx=(6, 16))
        ttk.Label(creator_filters, text="Results:").pack(side="left")
        ttk.Spinbox(creator_filters, from_=5, to=50, increment=5, textvariable=self.creator_limit, width=5).pack(side="left", padx=(6, 0))

        ttk.Label(creator_tab, textvariable=self.creator_status).pack(anchor="w", pady=(0, 6))
        creator_tree_frame = ttk.Frame(creator_tab)
        creator_tree_frame.pack(fill="both", expand=True)
        self.creator_tree = ttk.Treeview(creator_tree_frame, columns=("platform", "creator", "url"), show="headings", selectmode="extended", height=10)
        self.creator_tree.heading("platform", text="Platform")
        self.creator_tree.heading("creator", text="Creator")
        self.creator_tree.heading("url", text="Profile / Channel URL")
        self.creator_tree.column("platform", width=130, stretch=False)
        self.creator_tree.column("creator", width=210, stretch=False)
        self.creator_tree.column("url", width=360, stretch=True)
        creator_scroll = ttk.Scrollbar(creator_tree_frame, orient="vertical", command=self.creator_tree.yview)
        self.creator_tree.configure(yscrollcommand=creator_scroll.set)
        self.creator_tree.pack(side="left", fill="both", expand=True)
        creator_scroll.pack(side="right", fill="y")
        self.creator_tree.bind("<Double-1>", self.open_selected_creator)

        creator_actions = ttk.Frame(creator_tab)
        creator_actions.pack(fill="x", pady=(10, 0))
        ttk.Button(creator_actions, text="Use in Channel Downloader", command=self.use_creator_in_channel).pack(side="left")
        ttk.Button(creator_actions, text="Add to Bulk Queue", command=self.add_creators_to_bulk).pack(side="left", padx=(8, 0))
        ttk.Button(creator_actions, text="Copy URLs", command=self.copy_creator_urls).pack(side="left", padx=(8, 0))
        ttk.Button(creator_actions, text="Open Profile", command=self.open_selected_creator).pack(side="left", padx=(8, 0))

        # -- Video Editor Tab --
        editor_tab.columnconfigure(0, weight=1)
        editor_tab.rowconfigure(1, weight=1)
        editor_header = ttk.Frame(editor_tab)
        editor_header.grid(row=0, column=0, sticky="ew", pady=(0, 7))
        ttk.Label(editor_header, text="Video Editor", style="Section.TLabel").pack(side="left")
        ttk.Label(editor_header, textvariable=self.editor_source_status, style="Muted.TLabel").pack(side="left", padx=(12, 0))
        ttk.Button(editor_header, text="Import Video", command=self.import_editor_video, style="Accent.TButton").pack(side="right")

        editor_workspace = ttk.PanedWindow(editor_tab, orient="horizontal")
        editor_workspace.grid(row=1, column=0, sticky="nsew")

        preview_panel = ttk.Frame(editor_workspace, padding=(0, 0, 10, 0))
        library = ttk.Frame(editor_workspace, width=260)
        inspector = ttk.Frame(editor_workspace, width=335)
        editor_workspace.add(library, weight=0)
        editor_workspace.add(preview_panel, weight=3)
        editor_workspace.add(inspector, weight=1)
        self.editor_workspace = editor_workspace
        library_tabs = ttk.Notebook(library)
        library_tabs.pack(fill="both", expand=True)
        self.editor_library_tabs = library_tabs
        preview_panel.columnconfigure(0, weight=1)
        preview_panel.rowconfigure(0, weight=1)

        preview_surface = tk.Frame(preview_panel, width=560, height=315, background="#11181d")
        preview_surface.grid(row=0, column=0, sticky="nsew")
        preview_surface.pack_propagate(False)
        self.editor_preview_label = tk.Label(
            preview_surface,
            text="Import a video to preview",
            background="#11181d",
            foreground="#d8e2e6",
            font=("Segoe UI", 11),
        )
        self.editor_preview_label.pack(fill="both", expand=True)

        player_bar = ttk.Frame(preview_panel)
        player_bar.grid(row=1, column=0, sticky="ew", pady=(7, 0))
        self.editor_play_btn = ttk.Button(
            player_bar,
            text="Play",
            command=self.toggle_editor_preview,
            state="disabled",
            width=9,
        )
        self.editor_play_btn.pack(side="left")
        ttk.Label(player_bar, textvariable=self.editor_preview_time, style="Muted.TLabel").pack(side="left", padx=(10, 0))
        ratio_bar = ttk.Frame(preview_panel)
        ratio_bar.grid(row=2, column=0, sticky="ew", pady=4)
        ttk.Label(ratio_bar, text="Ratio").pack(side="left")
        ratio_control = ttk.Combobox(ratio_bar, textvariable=self.editor_aspect_ratio,
                                    values=("Original", "16:9", "9:16", "1:1", "4:5", "4:3"), state="readonly", width=9)
        ratio_control.pack(side="left", padx=6)
        ratio_control.bind("<<ComboboxSelected>>", self.on_editor_visual_setting_changed)

        inspector_tabs = ttk.Notebook(inspector)
        inspector_tabs.pack(fill="both", expand=True)
        self.editor_inspector_tabs = inspector_tabs
        self.editor_tool_canvases = []

        def make_scrollable_editor_tab(label: str, tabs=None):
            tabs = tabs if tabs is not None else inspector_tabs
            host = ttk.Frame(tabs)
            canvas = tk.Canvas(
                host,
                width=245,
                background=self.palette["background"],
                highlightthickness=0,
                borderwidth=0,
            )
            scroll = ttk.Scrollbar(host, orient="vertical", command=canvas.yview)
            canvas.configure(yscrollcommand=scroll.set)
            self.editor_tool_canvases.append(canvas)
            scroll.pack(side="right", fill="y")
            canvas.pack(side="left", fill="both", expand=True)
            body = ttk.Frame(canvas, padding=10)
            body.scroll_canvas = canvas
            body_window = canvas.create_window((0, 0), window=body, anchor="nw")
            body.bind("<Configure>", lambda _event, target=canvas: target.configure(scrollregion=target.bbox("all")))
            canvas.bind("<Configure>", lambda event, target=canvas, item=body_window: target.itemconfigure(item, width=event.width))
            host.bind("<Enter>", lambda _event, target=canvas: self.bind_all("<MouseWheel>", lambda event: target.yview_scroll(int(-event.delta / 120), "units")))
            host.bind("<Leave>", lambda _event: self.unbind_all("<MouseWheel>"))
            tabs.add(host, text=label)
            return body

        def route_editor_mousewheel(widget, panel) -> None:
            def scroll_panel(event):
                panel.scroll_canvas.yview_scroll(int(-event.delta / 120), "units")
                return "break"
            widget.bind("<MouseWheel>", scroll_panel)

        clip_tools = make_scrollable_editor_tab("Media", library_tabs)
        look_tools = make_scrollable_editor_tab("Looks", library_tabs)
        lut_folder = Path(__file__).resolve().parent / "editor_luts"
        ttk.Label(look_tools, text="Film looks", style="Section.TLabel").pack(anchor="w", pady=(0, 6))
        for lut_file in sorted(lut_folder.glob("*.cube")):
            def apply_film_lut(path=lut_file):
                self.editor_lut_path.set(str(path))
                self.on_editor_visual_setting_changed()
            ttk.Button(look_tools, text=lut_file.stem.replace("_", " ").title(),
                       command=apply_film_lut).pack(fill="x", pady=3)
        ttk.Button(look_tools, text="Original colour", command=self.remove_editor_lut).pack(fill="x", pady=(3, 10))
        adjust_tools = make_scrollable_editor_tab("Adjust")
        for name, variable in self.editor_adjustments.items():
            ttk.Label(adjust_tools, text=name).pack(anchor="w", pady=(8, 3))
            slider = ttk.Scale(adjust_tools, from_=-100, to=100, variable=variable,
                               command=lambda _value: self.request_editor_preview_refresh())
            slider.pack(fill="x")
            slider.bind("<ButtonRelease-1>", self.commit_editor_setting_change)
        ttk.Button(adjust_tools, text="Reset adjustments", command=self.reset_editor_adjustments).pack(fill="x", pady=12)
        ttk.Button(adjust_tools, text="Import LUT (.cube)", command=self.import_editor_lut).pack(fill="x", pady=4)
        ttk.Button(adjust_tools, text="Remove LUT", command=self.remove_editor_lut).pack(fill="x", pady=4)
        text_tools = make_scrollable_editor_tab("Text")
        export_tools = make_scrollable_editor_tab("Output")

        ttk.Label(clip_tools, text="Editing mode").pack(anchor="w")
        mode_cb = ttk.Combobox(
            clip_tools,
            textvariable=self.editor_mode,
            values=("Single Video", "Automatic Clips", "Manual Clips"),
            state="readonly",
        )
        mode_cb.pack(fill="x", pady=(4, 10))
        mode_cb.bind("<<ComboboxSelected>>", self.on_editor_mode_changed)
        route_editor_mousewheel(mode_cb, clip_tools)

        duration_row = ttk.Frame(clip_tools)
        duration_row.pack(fill="x", pady=(0, 7))
        ttk.Label(duration_row, text="Clip length").pack(side="left")
        self.editor_duration_input = ttk.Spinbox(
            duration_row,
            from_=1,
            to=999999999,
            increment=1,
            textvariable=self.editor_duration_value,
            width=7,
        )
        self.editor_duration_input.pack(side="right")
        route_editor_mousewheel(self.editor_duration_input, clip_tools)
        self.editor_duration_unit_cb = ttk.Combobox(
            clip_tools,
            textvariable=self.editor_duration_unit,
            values=("Seconds", "Minutes", "Hours"),
            state="readonly",
        )
        self.editor_duration_unit_cb.pack(fill="x", pady=(0, 7))
        route_editor_mousewheel(self.editor_duration_unit_cb, clip_tools)
        self.editor_generate_btn = ttk.Button(clip_tools, text="Generate Clips", command=self.generate_editor_auto_clips)
        self.editor_generate_btn.pack(fill="x", pady=(0, 10))

        self.editor_manual_row = ttk.Frame(clip_tools)
        self.editor_manual_row.pack(fill="x", pady=(0, 9))
        ttk.Label(self.editor_manual_row, text="Manual start / end").pack(anchor="w")
        manual_inputs = ttk.Frame(self.editor_manual_row)
        manual_inputs.pack(fill="x", pady=(4, 5))
        ttk.Entry(manual_inputs, textvariable=self.editor_manual_start, width=11).pack(side="left", fill="x", expand=True)
        ttk.Entry(manual_inputs, textvariable=self.editor_manual_end, width=11).pack(side="left", fill="x", expand=True, padx=(6, 0))
        ttk.Button(self.editor_manual_row, text="Add Clip", command=self.add_editor_manual_clip).pack(fill="x")

        self.editor_clip_tree = ttk.Treeview(
            clip_tools,
            columns=("part", "duration"),
            show="headings",
            selectmode="extended",
            height=5,
        )
        self.editor_clip_tree.heading("part", text="Timeline item")
        self.editor_clip_tree.heading("duration", text="Duration")
        self.editor_clip_tree.column("part", width=130, stretch=True)
        self.editor_clip_tree.column("duration", width=92, stretch=False)
        self.editor_clip_tree.pack(fill="both", expand=True)
        self.editor_clip_tree.bind("<<TreeviewSelect>>", self.on_editor_clip_selected)
        clip_actions = ttk.Frame(clip_tools)
        clip_actions.pack(fill="x", pady=(7, 0))
        ttk.Button(clip_actions, text="Remove", command=self.remove_editor_clips).pack(side="left", fill="x", expand=True)
        ttk.Button(clip_actions, text="Clear", command=self.clear_editor_clips).pack(side="left", fill="x", expand=True, padx=(6, 0))

        for label, variable, values in (
            ("Filter", self.editor_filter, ("Original", "Vivid", "Warm", "Cool", "Cinematic", "Black & White", "Retro", "Sharpen", "Soft")),
            ("Effect", self.editor_effect, ("None", "Film Grain", "Vignette", "Mirror", "Slow 0.5x", "Fast 2x")),
            ("Transition", self.editor_transition, ("None", "Fade", "Dip to Black", "Flash")),
        ):
            ttk.Label(look_tools, text=label).pack(anchor="w", pady=(0, 3))
            combo = ttk.Combobox(look_tools, textvariable=variable, values=values, state="readonly")
            combo.pack(fill="x", pady=(0, 11))
            combo.bind("<<ComboboxSelected>>", self.on_editor_visual_setting_changed)
            route_editor_mousewheel(combo, look_tools)

        strength_header = ttk.Frame(look_tools)
        strength_header.pack(fill="x")
        ttk.Label(strength_header, text="Filter strength").pack(side="left")
        ttk.Label(strength_header, textvariable=self.editor_filter_intensity_text, style="Muted.TLabel").pack(side="right")
        filter_strength = ttk.Scale(
            look_tools,
            from_=0,
            to=100,
            variable=self.editor_filter_intensity,
            command=self.on_editor_filter_intensity_changed,
        )
        filter_strength.pack(fill="x", pady=(5, 0))
        filter_strength.bind("<ButtonRelease-1>", self.commit_editor_setting_change)

        ttk.Label(text_tools, text="Overlay text").pack(anchor="w")
        editor_text_entry = ttk.Entry(text_tools, textvariable=self.editor_text)
        editor_text_entry.pack(fill="x", pady=(4, 11))
        editor_text_entry.bind("<KeyRelease>", self.on_editor_visual_setting_changed)
        editor_text_entry.bind("<FocusOut>", self.commit_editor_setting_change)
        ttk.Label(text_tools, text="Position").pack(anchor="w")
        text_position_cb = ttk.Combobox(text_tools, textvariable=self.editor_text_position, values=("Top", "Center", "Bottom"), state="readonly")
        text_position_cb.pack(fill="x", pady=(4, 11))
        text_position_cb.bind("<<ComboboxSelected>>", self.on_editor_visual_setting_changed)
        route_editor_mousewheel(text_position_cb, text_tools)
        ttk.Label(text_tools, text="Text size").pack(anchor="w")
        text_size_input = ttk.Spinbox(text_tools, from_=12, to=200, textvariable=self.editor_text_size)
        text_size_input.pack(fill="x", pady=(4, 0))
        text_size_input.bind("<KeyRelease>", self.on_editor_visual_setting_changed)
        text_size_input.bind("<FocusOut>", self.on_editor_visual_setting_changed)
        route_editor_mousewheel(text_size_input, text_tools)

        ttk.Label(export_tools, text="Resolution").pack(anchor="w")
        resolution_cb = ttk.Combobox(export_tools, textvariable=self.editor_resolution, values=("Original", "720p", "1080p", "2K", "4K"), state="readonly")
        resolution_cb.pack(fill="x", pady=(4, 11))
        resolution_cb.bind("<<ComboboxSelected>>", self.commit_editor_setting_change)
        route_editor_mousewheel(resolution_cb, export_tools)
        ttk.Label(export_tools, text="Canvas ratio").pack(anchor="w")
        aspect_ratio_cb = ttk.Combobox(
            export_tools,
            textvariable=self.editor_aspect_ratio,
            values=("Original", "16:9", "9:16", "1:1", "4:5", "4:3"),
            state="readonly",
        )
        aspect_ratio_cb.pack(fill="x", pady=(4, 11))
        aspect_ratio_cb.bind("<<ComboboxSelected>>", self.on_editor_visual_setting_changed)
        route_editor_mousewheel(aspect_ratio_cb, export_tools)
        ttk.Label(export_tools, text="Frame rate (FPS)").pack(anchor="w")
        fps_cb = ttk.Combobox(export_tools, textvariable=self.editor_fps, values=("Original", "24", "25", "30", "50", "60"), state="readonly")
        fps_cb.pack(fill="x", pady=(4, 0))
        fps_cb.bind("<<ComboboxSelected>>", self.commit_editor_setting_change)
        route_editor_mousewheel(fps_cb, export_tools)

        timeline_header = ttk.Frame(editor_tab)
        timeline_header.grid(row=2, column=0, sticky="ew", pady=(8, 3))
        ttk.Label(timeline_header, text="Timeline", style="Section.TLabel").pack(side="left")
        self.editor_redo_btn = ttk.Button(timeline_header, text="Redo", command=self.redo_editor_change, state="disabled", width=8)
        self.editor_redo_btn.pack(side="right")
        self.editor_undo_btn = ttk.Button(timeline_header, text="Undo", command=self.undo_editor_change, state="disabled", width=8)
        self.editor_undo_btn.pack(side="right", padx=(0, 6))

        self.editor_timeline_canvas = tk.Canvas(
            editor_tab,
            height=92,
            background="#182126",
            highlightthickness=1,
            highlightbackground=self.palette["border"],
            cursor="hand2",
        )
        self.editor_timeline_canvas.grid(row=3, column=0, sticky="ew")
        self.editor_timeline_canvas.bind("<Configure>", lambda _event: self.render_editor_timeline())
        self.editor_timeline_canvas.bind("<Button-1>", self.on_editor_timeline_click)

        editor_footer = ttk.Frame(editor_tab)
        editor_footer.grid(row=4, column=0, sticky="ew", pady=(7, 0))
        ttk.Label(editor_footer, textvariable=self.editor_status).pack(side="left")
        self.editor_export_btn = ttk.Button(editor_footer, text="Export", command=self.start_editor_export, style="Accent.TButton", state="disabled")
        self.editor_export_btn.pack(side="right")
        self.editor_stop_btn = ttk.Button(editor_footer, text="Stop", command=self.stop_editor_export, state="disabled")
        self.editor_stop_btn.pack(side="right", padx=(0, 8))
        self.on_editor_mode_changed()
        self.editor_history_ready = True
        self.reset_editor_history()

        # --- Bottom Status and Logs (Global) ---
        bottom_area = ttk.Frame(main_area)
        bottom_area.grid(row=1, column=0, sticky="ew", pady=(9, 0))
        
        status_row = ttk.Frame(bottom_area)
        status_row.pack(fill="x", pady=(0, 4))
        ttk.Label(status_row, textvariable=self.status, font=("Segoe UI", 10, "bold")).pack(side="left")
        ttk.Label(status_row, textvariable=self.access_status).pack(side="right")
        
        self.progress_bar = ttk.Progressbar(bottom_area, variable=self.progress, maximum=100)
        self.progress_bar.pack(fill="x", pady=(0, 8))

        self.log = tk.Text(bottom_area, height=4, wrap="word", font=("Consolas", 9))
        self.style_text_widget(self.log)
        self.log.pack(fill="x")
        self.write_log("Ready. Use Single Video, Bulk, Channel, Creator Finder, or Video Editor tabs.")
        def select_workspace(_event=None):
            is_editor = notebook.select() == str(editor_tab)
            if is_editor:
                if str(sidebar_shell) in paned.panes():
                    paned.forget(sidebar_shell)
                self.log.configure(height=1)
                def size_editor():
                    width = editor_workspace.winfo_width()
                    if width > 600:
                        editor_workspace.sashpos(0, min(280, int(width * .24)))
                        editor_workspace.sashpos(1, width - min(300, int(width * .26)))
                self.after_idle(size_editor)
            else:
                if str(sidebar_shell) not in paned.panes():
                    paned.insert(0, sidebar_shell, weight=0)
                self.log.configure(height=4)
        notebook.bind("<<NotebookTabChanged>>", select_workspace, add="+")

    def style_text_widget(self, widget: tk.Text) -> None:
        widget.configure(
            background=self.palette["surface"],
            foreground=self.palette["text"],
            insertbackground=self.palette["accent"],
            selectbackground=self.palette["accent_soft"],
            selectforeground=self.palette["text"],
            relief="flat",
            borderwidth=1,
            highlightthickness=1,
            highlightbackground=self.palette["border"],
            highlightcolor=self.palette["accent"],
            padx=8,
            pady=7,
        )

    def close_app(self) -> None:
        self.stop_editor_preview()
        self.stop_editor_preview_refresh()
        for process in (self.current_process,):
            if process and process.poll() is None:
                try:
                    process.terminate()
                except Exception:
                    pass
        self.destroy()

    def on_cookie_selected(self, event=None) -> None:
        if self.cookies.get() == "Use cookies.txt file":
            file_path = filedialog.askopenfilename(title="Select cookies.txt", filetypes=(("Text files", "*.txt"), ("All files", "*.*")))
            if file_path:
                self.settings["cookies_file"] = file_path
                save_settings(self.settings)
                messagebox.showinfo(APP_TITLE, f"Cookies file selected:\n{file_path}")
            else:
                self.cookies.set("No browser cookies")

    def _apply_cookie_options(self, options: dict) -> None:
        cookie_source = COOKIE_SOURCES.get(self.cookies.get(), "")
        if cookie_source == "file":
            cookie_file = self.settings.get("cookies_file")
            if cookie_file and Path(cookie_file).exists():
                options["cookiefile"] = cookie_file
            else:
                raise RuntimeError("The selected cookies.txt file no longer exists. Select it again from Browser Cookies.")
        elif cookie_source:
            options["cookiesfrombrowser"] = (cookie_source,)

    def paste_url(self) -> None:
        try:
            self.url.set(self.clipboard_get().strip())
        except tk.TclError:
            messagebox.showinfo(APP_TITLE, "Clipboard is empty.")

    def paste_bulk_urls(self) -> None:
        try:
            text = self.clipboard_get().strip()
        except tk.TclError:
            messagebox.showinfo(APP_TITLE, "Clipboard is empty.")
            return
        if text:
            if self.bulk_text.get("1.0", "end").strip():
                self.bulk_text.insert("end", "\n")
            self.bulk_text.insert("end", text)

    def load_bulk_file(self) -> None:
        file_path = filedialog.askopenfilename(
            title="Load URLs",
            filetypes=(("Text files", "*.txt"), ("All files", "*.*")),
        )
        if not file_path:
            return
        text = Path(file_path).read_text(encoding="utf-8", errors="ignore")
        self.bulk_text.delete("1.0", "end")
        self.bulk_text.insert("1.0", text)

    def choose_random_niche(self) -> None:
        category = random.choice(tuple(CREATOR_NICHES))
        niche = random.choice(CREATOR_NICHES[category])
        self.creator_query.set(niche)
        self.creator_status.set(f"Random niche: {category} / {niche}")

    def find_creators(self) -> None:
        query = self.creator_query.get().strip()
        if not query:
            self.choose_random_niche()
            query = self.creator_query.get().strip()
        try:
            limit = min(50, max(5, int(self.creator_limit.get())))
        except (TypeError, ValueError, tk.TclError):
            limit = 20
            self.creator_limit.set(limit)
        self.creator_search_btn.configure(state="disabled")
        self.creator_status.set(f"Searching for {query} creators...")
        self.creator_results = []
        for item in self.creator_tree.get_children():
            self.creator_tree.delete(item)
        threading.Thread(
            target=self._creator_search_worker,
            args=(query, self.creator_platform.get(), self.creator_language.get(), limit),
            daemon=True,
        ).start()

    def _creator_search_worker(self, query: str, platform: str, language: str, limit: int) -> None:
        platforms = list(CREATOR_PLATFORMS) if platform == "All platforms" else [platform]
        results: list[dict] = []
        seen: set[str] = set()
        errors: list[str] = []
        if "YouTube" in platforms:
            try:
                self._find_youtube_creators(query, language, limit, results, seen)
            except Exception as exc:
                errors.append(f"YouTube: {exc}")
        per_platform = max(5, limit // max(1, len(platforms)))
        for platform_name in platforms:
            if len(results) >= limit:
                break
            try:
                self._find_web_creators(query, language, platform_name, per_platform, results, seen)
            except Exception as exc:
                errors.append(f"{platform_name}: {exc}")
        self.after(0, self._show_creator_results, query, results[:limit], errors)

    def _find_youtube_creators(self, query: str, language: str, limit: int, results: list[dict], seen: set[str]) -> None:
        search_text = f"{query} creator channel"
        if language != "Any language":
            search_text += f" {language}"
        options = {"extract_flat": True, "quiet": True, "no_warnings": True, "ignoreerrors": True, "skip_download": True}
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(f"ytsearch{min(50, limit * 2)}:{search_text}", download=False) or {}
        for entry in info.get("entries") or []:
            if not isinstance(entry, dict):
                continue
            url = entry.get("channel_url") or entry.get("uploader_url")
            name = entry.get("channel") or entry.get("uploader")
            if url and name:
                self._append_creator_result("YouTube", name, url, results, seen)
            if len(results) >= limit:
                return

    def _find_web_creators(self, query: str, language: str, platform: str, count: int, results: list[dict], seen: set[str]) -> None:
        domains = CREATOR_PLATFORMS[platform]
        domain_filter = " OR ".join(f"site:{domain}" for domain in domains)
        language_hint = "" if language == "Any language" else f" {language}"
        search_query = f"({domain_filter}) {query}{language_hint} creator profile channel"
        added = 0
        for item in DDGS().text(search_query, max_results=min(50, count * 3)):
            link = str(item.get("href") or "").strip()
            title = str(item.get("title") or "Creator")
            title = re.sub(r"\s*[|\-]\s*(YouTube|Instagram|TikTok|Facebook|Snapchat|X|Twitter|Vimeo).*$", "", title, flags=re.IGNORECASE).strip()
            profile_url = self._normalize_creator_url(platform, link)
            if profile_url and self._append_creator_result(platform, title, profile_url, results, seen):
                added += 1
                if added >= count:
                    return

        # RSS is a secondary provider when the primary search returns too few links.
        url = f"https://www.bing.com/search?format=rss&count={min(50, count * 3)}&q={quote_plus(search_query)}"
        request = Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urlopen(request, timeout=20) as response:
            root = ElementTree.fromstring(response.read())
        for item in root.findall(".//item"):
            link = (item.findtext("link") or "").strip()
            title = item.findtext("title") or "Creator"
            title = re.sub(r"\s*[|\-]\s*(YouTube|Instagram|TikTok|Facebook|Snapchat|X|Twitter|Vimeo).*$", "", title, flags=re.IGNORECASE).strip()
            profile_url = self._normalize_creator_url(platform, link)
            if profile_url and self._append_creator_result(platform, title, profile_url, results, seen):
                added += 1
                if added >= count:
                    break

    @staticmethod
    def _normalize_creator_url(platform: str, url: str) -> str | None:
        try:
            parsed = urlparse(url)
            host = parsed.netloc.lower().removeprefix("www.")
            parts = [part for part in parsed.path.split("/") if part]
            if not any(host.endswith(domain) for domain in CREATOR_PLATFORMS[platform]) or not parts:
                return None
            blocked = {"watch", "shorts", "results", "feed", "reel", "reels", "p", "popular", "explore", "accounts", "stories", "videos", "video", "status", "search", "home", "share", "login", "signup"}
            if parts[0].lower() in blocked:
                return None
            if platform == "YouTube":
                if parts[0].startswith("@"):
                    return f"https://www.youtube.com/{parts[0]}"
                if parts[0].lower() in {"channel", "c", "user"} and len(parts) > 1:
                    return f"https://www.youtube.com/{parts[0]}/{parts[1]}"
                return None
            if platform == "TikTok" and not parts[0].startswith("@"):
                return None
            if platform == "Snapchat" and (parts[0].lower() != "add" or len(parts) < 2):
                return None
            keep = 2 if platform in {"Snapchat", "RedNote / Xiaohongshu"} else 1
            return f"https://{host}/{'/'.join(parts[:keep])}"
        except Exception:
            return None

    @staticmethod
    def _append_creator_result(platform: str, name: str, url: str, results: list[dict], seen: set[str]) -> bool:
        clean_url = url.rstrip("/")
        key = clean_url.lower()
        if key in seen:
            return False
        seen.add(key)
        results.append({"platform": platform, "name": name.strip() or "Creator", "url": clean_url})
        return True

    def _show_creator_results(self, query: str, results: list[dict], errors: list[str]) -> None:
        self.creator_results = results
        for item in self.creator_tree.get_children():
            self.creator_tree.delete(item)
        for index, creator in enumerate(results):
            self.creator_tree.insert("", "end", iid=f"creator-{index}", values=(creator["platform"], creator["name"], creator["url"]))
        self.creator_search_btn.configure(state="normal")
        self.creator_status.set(f"{len(results)} creator profiles found for: {query}")
        self.write_log(f"Creator Finder: {len(results)} results for {query}")
        for error in errors:
            self.write_log(f"Creator Finder notice: {error}")
        if not results:
            messagebox.showinfo(APP_TITLE, "No creator profiles found. Try a broader keyword or another platform.")

    def _selected_creator_urls(self) -> list[str]:
        selected = self.creator_tree.selection()
        if not selected and self.creator_tree.get_children():
            selected = (self.creator_tree.get_children()[0],)
        return [str(self.creator_tree.item(item, "values")[2]) for item in selected]

    def use_creator_in_channel(self) -> None:
        urls = self._selected_creator_urls()
        if not urls:
            messagebox.showinfo(APP_TITLE, "Select a creator first.")
            return
        self.channel_url.set(urls[0])
        self.notebook.select(self.channel_tab)
        self.channel_info.set("Creator profile loaded. Click Fetch Videos.")

    def add_creators_to_bulk(self) -> None:
        urls = self._selected_creator_urls()
        if not urls:
            messagebox.showinfo(APP_TITLE, "Select one or more creators first.")
            return
        existing = self.bulk_text.get("1.0", "end").strip()
        self.bulk_text.insert("end", ("\n" if existing else "") + "\n".join(urls))
        self.playlist_mode.set(True)
        self.notebook.select(self.bulk_tab)

    def copy_creator_urls(self) -> None:
        urls = self._selected_creator_urls()
        if not urls:
            messagebox.showinfo(APP_TITLE, "Select one or more creators first.")
            return
        self.clipboard_clear()
        self.clipboard_append("\n".join(urls))
        self.creator_status.set(f"Copied {len(urls)} creator URL(s)")

    def open_selected_creator(self, event=None) -> None:
        urls = self._selected_creator_urls()
        if urls:
            webbrowser.open(urls[0])

    def login_user(self) -> None:
        username = self.username.get().strip()
        password = self.password.get()
        if not username or not password:
            messagebox.showerror(APP_TITLE, "Enter username and password.")
            return
        self.settings["api_url"] = self.api_url.get().strip() or DEFAULT_API_URL
        self.settings["username"] = username
        save_settings(self.settings)
        payload = {"username": username, "password": password, "device_id": self.device_id}
        result = api_post(self.api_url.get(), "/api/login", payload)
        if result.get("ok") and result.get("status") == "approved":
            self.access_granted = True
            self.access_status.set(f"Access approved: {username}")
            messagebox.showinfo(APP_TITLE, "Login successful. Access approved.")
        elif result.get("status") == "pending":
            self.access_granted = False
            self.access_status.set("Access pending admin approval")
            messagebox.showinfo(APP_TITLE, "Your access request is pending admin approval.")
        elif result.get("status") == "denied":
            self.access_granted = False
            self.access_status.set("Access denied by admin")
            messagebox.showerror(APP_TITLE, "Access denied by admin.")
        else:
            self.access_granted = False
            self.access_status.set("Access server unavailable or login failed")
            messagebox.showerror(APP_TITLE, result.get("error", "Login failed."))

    def request_access(self) -> None:
        username = self.username.get().strip()
        password = self.password.get()
        if not username or not password:
            messagebox.showerror(APP_TITLE, "Enter username and password to request access.")
            return
        self.settings["api_url"] = self.api_url.get().strip() or DEFAULT_API_URL
        self.settings["username"] = username
        save_settings(self.settings)
        payload = {"username": username, "password": password, "device_id": self.device_id}
        result = api_post(self.api_url.get(), "/api/request_access", payload)
        if result.get("ok"):
            self.access_status.set("Request sent. Waiting for admin approval.")
            messagebox.showinfo(APP_TITLE, "Access request sent to admin panel.")
        else:
            self.access_status.set("Could not send access request")
            messagebox.showerror(APP_TITLE, result.get("error", "Request failed."))

    def ensure_access(self) -> bool:
        return True

    def show_cookie_help(self) -> None:
        messagebox.showinfo(
            APP_TITLE,
            "If a website asks for sign-in, select the same browser where you are already logged in.\n\n"
            "Use Edge cookies, Chrome cookies, Firefox cookies, or select an exported cookies.txt file, then try again.\n\n"
            "Some platforms can still block complete profile lists. Configure a channel API fallback for those profiles.",
        )

    def show_channel_api_settings(self) -> None:
        dialog = tk.Toplevel(self)
        dialog.title("Channel API Settings")
        dialog.resizable(False, False)
        dialog.transient(self)
        dialog.grab_set()

        content = ttk.Frame(dialog, padding=16)
        content.pack(fill="both", expand=True)
        ttk.Label(content, text="TikTok complete-profile fallback", font=("Segoe UI", 12, "bold")).grid(row=0, column=0, columnspan=2, sticky="w")
        ttk.Label(content, text="Apify API token").grid(row=1, column=0, sticky="w", pady=(14, 4))
        token_var = tk.StringVar(value=self.apify_token.get())
        token_entry = ttk.Entry(content, textvariable=token_var, width=48, show="*")
        token_entry.grid(row=2, column=0, columnspan=2, sticky="ew")
        show_token = tk.BooleanVar(value=False)

        def toggle_token() -> None:
            token_entry.configure(show="" if show_token.get() else "*")

        ttk.Checkbutton(content, text="Show token", variable=show_token, command=toggle_token).grid(row=3, column=0, sticky="w", pady=(5, 0))
        ttk.Label(content, text="Maximum videos per profile").grid(row=4, column=0, sticky="w", pady=(14, 4))
        max_var = tk.IntVar(value=self.apify_max_results.get())
        ttk.Spinbox(content, from_=50, to=5000, increment=50, textvariable=max_var, width=10).grid(row=5, column=0, sticky="w")
        ttk.Label(
            content,
            text="Used automatically for TikTok profile URLs when direct extraction is incomplete.\nThe token is saved only in this Windows user's local app settings.",
            foreground="#5f6b7a",
        ).grid(row=6, column=0, columnspan=2, sticky="w", pady=(14, 0))

        actions = ttk.Frame(content)
        actions.grid(row=7, column=0, columnspan=2, sticky="e", pady=(18, 0))

        def save_api_settings() -> None:
            try:
                maximum = max(50, min(5000, int(max_var.get())))
            except (tk.TclError, ValueError):
                messagebox.showerror(APP_TITLE, "Maximum videos must be a number between 50 and 5000.", parent=dialog)
                return
            token = token_var.get().strip()
            self.apify_token.set(token)
            self.apify_max_results.set(maximum)
            self.settings["apify_token"] = token
            self.settings["apify_max_results"] = maximum
            save_settings(self.settings)
            dialog.destroy()
            messagebox.showinfo(APP_TITLE, "Channel API settings saved.")

        ttk.Button(actions, text="Cancel", command=dialog.destroy).pack(side="left")
        ttk.Button(actions, text="Save", command=save_api_settings, style="Accent.TButton").pack(side="left", padx=(8, 0))
        token_entry.focus_set()

    def show_history(self) -> None:
        if not HISTORY_FILE.exists():
            messagebox.showinfo(APP_TITLE, "No download history yet.")
            return
        window = tk.Toplevel(self)
        window.title("Download History")
        window.geometry("900x460")
        tree = ttk.Treeview(window, columns=("time", "status", "platform", "url", "detail"), show="headings")
        for column, label, width in (("time", "Time", 150), ("status", "Status", 85), ("platform", "Platform", 120), ("url", "URL", 280), ("detail", "File / Error", 230)):
            tree.heading(column, text=label)
            tree.column(column, width=width, stretch=column in ("url", "detail"))
        scrollbar = ttk.Scrollbar(window, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=scrollbar.set)
        tree.pack(side="left", fill="both", expand=True, padx=(12, 0), pady=12)
        scrollbar.pack(side="right", fill="y", padx=(0, 12), pady=12)
        lines = HISTORY_FILE.read_text(encoding="utf-8", errors="ignore").splitlines()
        for line in lines:
            try:
                item = json.loads(line)
                tree.insert("", "end", values=(item.get("time", ""), item.get("status", ""), detect_platform(item.get("url", "")), item.get("url", ""), item.get("detail", "")))
            except Exception:
                pass

    def show_schedule_dialog(self) -> None:
        dialog = tk.Toplevel(self)
        dialog.title("Schedule Downloads")
        dialog.resizable(False, False)
        dialog.transient(self)
        ttk.Label(dialog, text="Daily time (24-hour format HH:MM)").pack(anchor="w", padx=16, pady=(16, 4))
        time_var = tk.StringVar(value=self.scheduled_time.get())
        ttk.Entry(dialog, textvariable=time_var, width=12).pack(anchor="w", padx=16)
        ttk.Label(dialog, text="The current bulk URL list will start once per day at this time.").pack(anchor="w", padx=16, pady=(8, 12))

        def save_schedule() -> None:
            value = time_var.get().strip()
            if value and not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", value):
                messagebox.showerror(APP_TITLE, "Use a time such as 09:30 or 18:45.", parent=dialog)
                return
            self.scheduled_time.set(value)
            self.schedule_enabled.set(bool(value))
            self.settings["scheduled_time"] = value
            save_settings(self.settings)
            dialog.destroy()
            self.write_log("Daily schedule " + (f"set for {value}." if value else "cleared."))

        actions = ttk.Frame(dialog)
        actions.pack(fill="x", padx=16, pady=(0, 16))
        ttk.Button(actions, text="Save", command=save_schedule).pack(side="left")
        ttk.Button(actions, text="Clear", command=lambda: (time_var.set(""), save_schedule())).pack(side="left", padx=(8, 0))

    def check_schedule(self) -> None:
        scheduled = self.scheduled_time.get().strip()
        now = datetime.now()
        day_key = now.strftime("%Y-%m-%d")
        if scheduled and now.strftime("%H:%M") == scheduled and self.last_schedule_run != day_key and not self.bulk_running:
            urls = self.get_bulk_urls()
            if urls:
                self.last_schedule_run = day_key
                self.settings["last_schedule_run"] = day_key
                save_settings(self.settings)
                self.write_log(f"Starting scheduled bulk download: {len(urls)} URL(s)")
                self.start_bulk_download()
        self.after(30000, self.check_schedule)

    def export_diagnostics(self) -> None:
        DIAGNOSTICS_DIR.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        report_dir = DIAGNOSTICS_DIR / f"report-{stamp}"
        report_dir.mkdir()
        for source in (LAST_LOG, HISTORY_FILE, SETTINGS_FILE):
            if source.exists():
                shutil.copy2(source, report_dir / source.name)
        (report_dir / "system.txt").write_text(f"App: {APP_TITLE}\nPython: {sys.version}\nPlatform: {sys.platform}\nyt-dlp: {getattr(getattr(yt_dlp, 'version', None), '__version__', 'unknown')}\n", encoding="utf-8")
        archive = shutil.make_archive(str(report_dir), "zip", report_dir)
        shutil.rmtree(report_dir, ignore_errors=True)
        self.write_log(f"Diagnostics exported: {archive}")
        messagebox.showinfo(APP_TITLE, f"Diagnostics ZIP created:\n{archive}")

    def choose_folder(self) -> None:
        folder = filedialog.askdirectory(initialdir=self.download_dir.get())
        if folder:
            self.download_dir.set(folder)

    def open_folder(self) -> None:
        path = Path(self.download_dir.get())
        path.mkdir(parents=True, exist_ok=True)
        os.startfile(path)

    def start_download(self) -> None:
        if not self.ensure_access():
            return
        url = self.url.get().strip()
        if not URL_RE.match(url):
            messagebox.showerror(APP_TITLE, "Please paste a valid http/https video URL.")
            return
        if not self.ytdlp_available():
            messagebox.showerror(APP_TITLE, "yt-dlp is not available in this build.")
            return
        self._set_busy(True)
        self._reset_for_download()
        platform = detect_platform(url)
        self.write_log(f"Detected: {platform}")
        thread = threading.Thread(target=self.download_video, args=(url,), daemon=True)
        thread.start()

    def start_bulk_download(self) -> None:
        if not self.ensure_access():
            return
        urls = self.get_bulk_urls()
        if not urls:
            messagebox.showerror(APP_TITLE, "Paste at least one valid http/https URL in Bulk Video Download.")
            return
        if not self.ytdlp_available():
            messagebox.showerror(APP_TITLE, "yt-dlp is not available in this build.")
            return
        self.bulk_running = True
        self.failed_urls = []
        self.bulk_pause_event.set()
        self._set_busy(True)
        self._reset_for_download()
        self.write_log(f"Bulk queue started: {len(urls)} URL(s)")
        self.populate_bulk_queue(urls)
        self.pause_bulk_button.configure(state="normal")
        self.resume_bulk_button.configure(state="disabled")
        thread = threading.Thread(target=self.download_bulk_videos, args=(urls,), daemon=True)
        thread.start()

    def retry_failed_downloads(self) -> None:
        if not self.failed_urls:
            messagebox.showinfo(APP_TITLE, "No failed URLs to retry.")
            return
        self.bulk_text.delete("1.0", "end")
        self.bulk_text.insert("1.0", "\n".join(self.failed_urls))
        self.start_bulk_download()

    def pause_bulk_download(self) -> None:
        if self.bulk_running:
            self.bulk_pause_event.clear()
            self.pause_bulk_button.configure(state="disabled")
            self.resume_bulk_button.configure(state="normal")
            self.status.set("Bulk queue paused")

    def resume_bulk_download(self) -> None:
        if self.bulk_running:
            self.bulk_pause_event.set()
            self.pause_bulk_button.configure(state="normal")
            self.resume_bulk_button.configure(state="disabled")
            self.status.set("Bulk queue resumed")

    def populate_bulk_queue(self, urls: list[str]) -> None:
        for item in self.bulk_tree.get_children():
            self.bulk_tree.delete(item)
        self.bulk_item_ids = {}
        for index, url in enumerate(urls, start=1):
            item_id = self.bulk_tree.insert("", "end", values=("Waiting", detect_platform(url), url))
            self.bulk_item_ids[url] = item_id

    def update_bulk_status(self, url: str, status: str) -> None:
        item_id = self.bulk_item_ids.get(url)
        if item_id:
            self.bulk_tree.set(item_id, "status", status)

    def get_bulk_urls(self) -> list[str]:
        raw = self.bulk_text.get("1.0", "end").splitlines()
        urls = []
        seen = set()
        for line in raw:
            value = line.strip()
            if not value or value.startswith("#") or not URL_RE.match(value):
                continue
            if value not in seen:
                urls.append(value)
                seen.add(value)
        return urls

    def _reset_for_download(self) -> None:
        self.status.set("Starting download...")
        self.progress.set(0)
        self.download_start_timestamp = time.time()
        self.latest_downloaded_file = None
        self.log.delete("1.0", "end")
        APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
        LAST_LOG.write_text("", encoding="utf-8")

    def _set_busy(self, busy: bool) -> None:
        state = "disabled" if busy else "normal"
        self.download_button.configure(state=state)
        self.bulk_button.configure(state=state)
        clips_ready = bool(
            self.latest_downloaded_file
            and self.latest_downloaded_file.exists()
            and self.latest_downloaded_file.suffix.lower() in VIDEO_EXTENSIONS
        )
        self.create_clips_button.configure(state="disabled" if busy or not clips_ready else "normal")
        if hasattr(self, "editor_export_btn"):
            editor_ready = bool(self.editor_video_path and self.editor_clips)
            self.editor_export_btn.configure(state="disabled" if busy or not editor_ready else "normal")
        if not busy:
            self.pause_bulk_button.configure(state="disabled")
            self.resume_bulk_button.configure(state="disabled")

    def download_video(self, url: str) -> None:
        try:
            latest_file = self.perform_download(url)
            self.after(0, self.finish_success, latest_file)
        except Exception as exc:
            self.after(0, self.finish_with_error, str(exc))

    def download_bulk_videos(self, urls: list[str]) -> None:
        completed = 0
        failures: list[tuple[str, str]] = []
        latest_file: Path | None = None
        for index, url in enumerate(urls, start=1):
            while not self.bulk_pause_event.wait(timeout=0.25):
                if not self.bulk_running:
                    return
            self.after(0, self.status.set, f"Bulk {index}/{len(urls)}: downloading...")
            self.after(0, self.update_bulk_status, url, "Downloading")
            self.after(0, self.write_log, f"\n[{index}/{len(urls)}] {detect_platform(url)}: {url}")
            try:
                latest_file = self.perform_download(url, prefix=f"{index:03d}-")
                completed += 1
                self.after(0, self.update_bulk_status, url, "Done")
                self.after(0, self.write_log, f"[{index}/{len(urls)}] Done")
                self.write_history(url, "done", str(latest_file) if latest_file else "")
            except Exception as exc:
                failures.append((url, str(exc)))
                self.failed_urls.append(url)
                self.after(0, self.update_bulk_status, url, "Failed")
                self.after(0, self.write_log, f"[{index}/{len(urls)}] Failed: {exc}")
                self.write_history(url, "failed", str(exc))
        self.bulk_running = False
        self.after(0, self.finish_bulk_success, completed, len(urls), failures, latest_file)

    def perform_download(self, url: str, prefix: str = "") -> Path | None:
        base_download_dir = Path(self.download_dir.get())
        download_dir = base_download_dir
        folder_layout = self.folder_layout.get()
        if folder_layout == "By platform":
            download_dir /= "%(extractor)s"
        elif folder_layout == "By uploader":
            download_dir /= "%(uploader|unknown)s"
        elif folder_layout == "By upload date":
            download_dir /= "%(upload_date>%Y-%m|unknown)s"
        elif folder_layout == "Platform and uploader":
            download_dir /= "%(extractor)s" / "%(uploader|unknown)s"
        base_download_dir.mkdir(parents=True, exist_ok=True)
        quality_name = self.quality.get()
        output_template = str(download_dir / self.build_output_template(prefix))
        ydl_options = {
            "format": QUALITY_FORMATS.get(quality_name, QUALITY_FORMATS["Best MP4 compatible"]),
            "outtmpl": output_template,
            "merge_output_format": "mp4",
            "progress_hooks": [self.ytdlp_progress],
            "logger": GuiYtdlpLogger(self),
            "retries": 5,
            "fragment_retries": 5,
            "continuedl": True,
            "restrictfilenames": False,
            "windowsfilenames": True,
            "noplaylist": not self.playlist_mode.get(),
            "ignoreerrors": False,
            "nooverwrites": self.skip_duplicates.get(),
        }
        if quality_name == "Audio only MP3":
            ydl_options["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}]
        elif quality_name == "Audio only M4A":
            ydl_options["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "m4a", "preferredquality": "192"}]
        if self.download_subtitles.get():
            ydl_options.update({"writesubtitles": True, "writeautomaticsub": True, "subtitleslangs": ["all"]})
        if self.download_thumbnail.get():
            ydl_options["writethumbnail"] = True
        ffmpeg = get_ffmpeg_path()
        if ffmpeg:
            ydl_options["ffmpeg_location"] = ffmpeg
        node = get_node_path()
        if node:
            ydl_options["nodejs"] = node
        cookie_source = COOKIE_SOURCES.get(self.cookies.get(), "")
        if cookie_source == "file":
            cookie_file = self.settings.get("cookies_file")
            if cookie_file and Path(cookie_file).exists():
                ydl_options["cookiefile"] = cookie_file
            else:
                self.after(0, messagebox.showwarning, APP_TITLE, "Cookies file not found! Continuing without cookies.")
        elif cookie_source:
            ydl_options["cookiesfrombrowser"] = (cookie_source,)
            
        # Add sleep to prevent 429
        ydl_options["sleep_interval_requests"] = 1
        ydl_options["max_sleep_interval"] = 3
        ydl_options["sleep_interval"] = 1
        ydl_options["extractor_args"] = {"youtube": ["player_client=android,web"]}
        
        code = self.run_ytdlp_download(ydl_options, url)
        if code != 0:
            raise RuntimeError("Download failed. Check the log for details.")
        return self.find_latest_video_file(base_download_dir)

    def build_output_template(self, prefix: str = "") -> str:
        choice = self.filename_template.get()
        if choice == "title only":
            return f"{prefix}%(title).180s.%(ext)s"
        if choice == "platform + title":
            return f"{prefix}%(extractor)s - %(title).170s [%(id)s].%(ext)s"
        if choice == "date + title":
            return f"{prefix}%(upload_date>%Y-%m-%d|unknown)s - %(title).170s [%(id)s].%(ext)s"
        return f"{prefix}%(title).180s [%(id)s].%(ext)s"

    def run_ytdlp_download(self, ydl_options: dict, url: str) -> int:
        try:
            with yt_dlp.YoutubeDL(ydl_options) as ydl:
                ydl.download([url])
            return 0
        except Exception as exc:
            message = str(exc)
            self.after(0, self.handle_output, f"Primary format failed: {message}")
            if ydl_options.get("format") == QUALITY_FORMATS["Only MP4 Video"]:
                raise RuntimeError("Only MP4 Video selected, but MP4 video/audio was not available for this link.")
            if ydl_options.get("format") == FALLBACK_FORMAT:
                raise
            fallback_options = dict(ydl_options)
            fallback_options.pop("postprocessors", None)
            fallback_options["format"] = FALLBACK_FORMAT
            self.after(0, self.handle_output, "Trying fallback format...")
            with yt_dlp.YoutubeDL(fallback_options) as ydl:
                ydl.download([url])
            return 0

    def ytdlp_progress(self, status: dict) -> None:
        state = status.get("status")
        if state == "downloading":
            downloaded = status.get("downloaded_bytes") or 0
            total = status.get("total_bytes") or status.get("total_bytes_estimate") or 0
            percent = (downloaded / total * 100) if total else 0
            speed = status.get("_speed_str", "").strip()
            eta = status.get("_eta_str", "").strip()
            percent_text = status.get("_percent_str", "").strip()
            message = " ".join(part for part in (percent_text, speed, f"ETA {eta}" if eta else "") if part)
            self.after(0, self.progress.set, min(max(percent, 0), 100))
            if message:
                self.after(0, self.status.set, message)
        elif state == "finished":
            filename = status.get("filename")
            if filename:
                self.latest_downloaded_file = Path(filename)
            self.after(0, self.progress.set, 100)
            self.after(0, self.handle_output, "Download finished, processing file...")

    def handle_output(self, line: str) -> None:
        progress_match = re.search(r"(\d+(?:\.\d+)?)%", line)
        if progress_match:
            self.progress.set(float(progress_match.group(1)))
        self.write_log(line)
        APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
        with LAST_LOG.open("a", encoding="utf-8", errors="ignore") as file:
            file.write(line + "\n")

    def find_latest_video_file(self, download_dir: Path) -> Path | None:
        candidates = []
        for file in download_dir.rglob("*"):
            if not file.is_file():
                continue
            if file.suffix.lower() in VIDEO_EXTENSIONS or file.suffix.lower() == ".mp3":
                if not file.name.endswith(".part") and file.stat().st_mtime >= self.download_start_timestamp - 2:
                    candidates.append(file)
        if candidates:
            return max(candidates, key=lambda item: item.stat().st_mtime)
        if self.latest_downloaded_file and self.latest_downloaded_file.exists():
            return self.latest_downloaded_file
        # If yt-dlp skipped an existing file or its merge hook reported a
        # temporary filename, use the newest finished media file in the folder.
        all_media = [
            file
            for file in download_dir.rglob("*")
            if file.is_file()
            and file.suffix.lower() in VIDEO_EXTENSIONS.union({".mp3", ".m4a"})
            and not file.name.endswith(".part")
        ]
        if all_media:
            return max(all_media, key=lambda item: item.stat().st_mtime)
        return None

    def set_clip_source(self, video_path: Path | None) -> None:
        if video_path and video_path.exists() and video_path.suffix.lower() in VIDEO_EXTENSIONS:
            self.latest_downloaded_file = video_path
            display_name = video_path.name if len(video_path.name) <= 70 else video_path.name[:67] + "..."
            self.clip_source_status.set(f"Latest video: {display_name}")
            self.create_clips_button.configure(state="normal")
            return
        self.clip_source_status.set("Latest video: no completed video detected")
        self.create_clips_button.configure(state="disabled")

    def choose_clip_source(self) -> None:
        selected = filedialog.askopenfilename(
            title="Choose a video to create clips",
            initialdir=self.download_dir.get(),
            filetypes=(
                ("Video files", "*.mp4 *.mkv *.webm *.mov *.avi *.flv *.m4v"),
                ("All files", "*.*"),
            ),
        )
        if selected:
            self.set_clip_source(Path(selected))

    def finish_success(self, latest_file: Path | None) -> None:
        self.progress.set(100)
        self.status.set("Download complete")
        self.set_clip_source(latest_file)
        self._set_busy(False)
        self.write_log("Download complete.")
        if latest_file:
            self.write_history(self.url.get().strip(), "done", str(latest_file))
            messagebox.showinfo(APP_TITLE, f"Video downloaded successfully.\n\nSaved in:\n{latest_file}")
        else:
            messagebox.showwarning(
                APP_TITLE,
                "Download completed, but the final video file could not be detected automatically.\n\n"
                "Click Choose Existing Video in the Video Clips section to select it.",
            )
        if latest_file and latest_file.suffix.lower() in VIDEO_EXTENSIONS:
            self.ask_hide_watermark(latest_file)

    def finish_bulk_success(self, completed: int, total: int, failures: list[tuple[str, str]], latest_file: Path | None) -> None:
        self.progress.set(100)
        self.set_clip_source(latest_file)
        self._set_busy(False)
        self.status.set(f"Bulk complete: {completed}/{total} downloaded")
        if failures:
            self.write_log("\nFailures:")
            for url, error in failures:
                self.write_log(f"- {url}: {error}")
        message = f"Bulk download complete.\nDownloaded: {completed}/{total}"
        if failures:
            message += f"\nFailed: {len(failures)}"
        messagebox.showinfo(APP_TITLE, message)

    def editor_snapshot(self) -> dict:
        try:
            duration_value = int(self.editor_duration_value.get())
        except (tk.TclError, ValueError):
            duration_value = 20
        try:
            text_size = int(self.editor_text_size.get())
        except (tk.TclError, ValueError):
            text_size = 42
        return {
            "adjustments": {name: value.get() for name, value in self.editor_adjustments.items()},
            "lut_path": self.editor_lut_path.get(),
            "mode": self.editor_mode.get(),
            "clips": [tuple(clip) for clip in self.editor_clips],
            "duration_value": duration_value,
            "duration_unit": self.editor_duration_unit.get(),
            "filter": self.editor_filter.get(),
            "filter_intensity": int(self.editor_filter_intensity.get()),
            "effect": self.editor_effect.get(),
            "transition": self.editor_transition.get(),
            "aspect_ratio": self.editor_aspect_ratio.get(),
            "resolution": self.editor_resolution.get(),
            "fps": self.editor_fps.get(),
            "text": self.editor_text.get(),
            "text_position": self.editor_text_position.get(),
            "text_size": text_size,
        }

    def reset_editor_history(self) -> None:
        self.editor_undo_stack = []
        self.editor_redo_stack = []
        self.editor_history_current = self.editor_snapshot()
        if hasattr(self, "editor_undo_btn"):
            self.update_editor_history_buttons()

    def commit_editor_history(self) -> None:
        if not self.editor_history_ready:
            return
        current = self.editor_snapshot()
        if self.editor_history_current is None:
            self.editor_history_current = current
            return
        if current == self.editor_history_current:
            return
        self.editor_undo_stack.append(self.editor_history_current)
        self.editor_undo_stack = self.editor_undo_stack[-100:]
        self.editor_redo_stack = []
        self.editor_history_current = current
        self.update_editor_history_buttons()

    def commit_editor_setting_change(self, event=None) -> None:
        self.commit_editor_history()

    def reset_editor_adjustments(self) -> None:
        for variable in self.editor_adjustments.values():
            variable.set(0)
        self.on_editor_visual_setting_changed()

    def remove_editor_lut(self) -> None:
        self.editor_lut_path.set("")
        self.on_editor_visual_setting_changed()

    def import_editor_lut(self) -> None:
        selected = filedialog.askopenfilename(title="Import colour LUT", filetypes=(("3D LUT", "*.cube"),))
        if not selected:
            return
        source = Path(selected)
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "FFmpeg is required to import a LUT.")
            return
        try:
            data = source.read_bytes()
            if len(data) > 32 * 1024 * 1024:
                raise ValueError("LUT exceeds the 32 MB limit.")
            folder = APP_DATA_DIR / "editor_luts"
            folder.mkdir(parents=True, exist_ok=True)
            target = folder / (hashlib.sha256(data).hexdigest() + ".cube")
            shutil.copyfile(source, target)
            escaped = str(target).replace("\\", "/").replace(":", r"\:").replace("'", r"\'")
            result = subprocess.run([ffmpeg, "-v", "error", "-f", "lavfi", "-i", "color=size=16x16", "-frames:v", "1",
                                     "-vf", f"lut3d=file='{escaped}'", "-f", "null", "-"],
                                    capture_output=True, timeout=15,
                                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            if result.returncode:
                raise ValueError("This file is not a supported 3D CUBE LUT.")
            self.editor_lut_path.set(str(target))
            self.on_editor_visual_setting_changed()
        except Exception as exc:
            messagebox.showerror(APP_TITLE, f"Could not import LUT: {exc}")

    def on_editor_visual_setting_changed(self, event=None) -> None:
        self.commit_editor_history()
        self.request_editor_preview_refresh()

    def on_editor_filter_intensity_changed(self, value=None) -> None:
        try:
            intensity = max(0, min(100, round(float(value if value is not None else self.editor_filter_intensity.get()))))
        except (tk.TclError, TypeError, ValueError):
            intensity = 100
        self.editor_filter_intensity_text.set(f"{intensity}%")
        self.request_editor_preview_refresh()

    def update_editor_history_buttons(self) -> None:
        self.editor_undo_btn.configure(state="normal" if self.editor_undo_stack else "disabled")
        self.editor_redo_btn.configure(state="normal" if self.editor_redo_stack else "disabled")

    def restore_editor_snapshot(self, snapshot: dict) -> None:
        self.editor_history_ready = False
        try:
            for name, variable in self.editor_adjustments.items():
                variable.set(snapshot.get("adjustments", {}).get(name, 0))
            self.editor_lut_path.set(snapshot.get("lut_path", ""))
            self.editor_mode.set(snapshot["mode"])
            self.editor_clips = [tuple(clip) for clip in snapshot["clips"]]
            self.editor_duration_value.set(snapshot["duration_value"])
            self.editor_duration_unit.set(snapshot["duration_unit"])
            self.editor_filter.set(snapshot["filter"])
            self.editor_filter_intensity.set(snapshot.get("filter_intensity", 100))
            self.editor_filter_intensity_text.set(f"{int(self.editor_filter_intensity.get())}%")
            self.editor_effect.set(snapshot["effect"])
            self.editor_transition.set(snapshot["transition"])
            self.editor_aspect_ratio.set(snapshot.get("aspect_ratio", "Original"))
            self.editor_resolution.set(snapshot["resolution"])
            self.editor_fps.set(snapshot["fps"])
            self.editor_text.set(snapshot["text"])
            self.editor_text_position.set(snapshot["text_position"])
            self.editor_text_size.set(snapshot["text_size"])
            self.on_editor_mode_changed(keep_clips=True)
            self.refresh_editor_clip_tree()
        finally:
            self.editor_history_ready = True
        self.request_editor_preview_refresh()

    def undo_editor_change(self) -> None:
        if not self.editor_undo_stack:
            return
        current = self.editor_snapshot()
        target = self.editor_undo_stack.pop()
        self.editor_redo_stack.append(current)
        self.restore_editor_snapshot(target)
        self.editor_history_current = target
        self.update_editor_history_buttons()
        self.editor_status.set("Undo applied")

    def redo_editor_change(self) -> None:
        if not self.editor_redo_stack:
            return
        current = self.editor_snapshot()
        target = self.editor_redo_stack.pop()
        self.editor_undo_stack.append(current)
        self.restore_editor_snapshot(target)
        self.editor_history_current = target
        self.update_editor_history_buttons()
        self.editor_status.set("Redo applied")

    def current_editor_settings(self) -> dict:
        try:
            text_size = max(12, min(200, int(self.editor_text_size.get())))
        except (tk.TclError, ValueError):
            text_size = 42
        return {
            "lut_path": self.editor_lut_path.get(),
            "adjustments": {name: value.get() for name, value in self.editor_adjustments.items()},
            "mode": self.editor_mode.get(),
            "filter": self.editor_filter.get(),
            "filter_intensity": max(0, min(100, int(self.editor_filter_intensity.get()))),
            "effect": self.editor_effect.get(),
            "transition": self.editor_transition.get(),
            "aspect_ratio": self.editor_aspect_ratio.get(),
            "resolution": self.editor_resolution.get(),
            "fps": self.editor_fps.get(),
            "text": self.editor_text.get().strip(),
            "text_position": self.editor_text_position.get(),
            "text_size": text_size,
        }

    def on_editor_timeline_changed(self, value=None) -> None:
        try:
            position = float(value if value is not None else self.editor_preview_position.get())
        except (tk.TclError, TypeError, ValueError):
            position = 0.0
        position = max(0.0, min(position, self.editor_video_duration or 0.0))
        self.editor_preview_time.set(
            f"{self.format_editor_time(position)} / {self.format_editor_time(self.editor_video_duration)}"
        )
        self.render_editor_timeline()

    def render_editor_timeline(self) -> None:
        if not hasattr(self, "editor_timeline_canvas"):
            return
        canvas = self.editor_timeline_canvas
        canvas.delete("all")
        width = max(canvas.winfo_width(), 300)
        left = 14
        right = width - 14
        track_top = 30
        track_bottom = 78
        track_width = max(1, right - left)
        canvas.create_rectangle(left, track_top, right, track_bottom, fill="#253139", outline="#3b4a53")

        duration = self.editor_video_duration
        if duration <= 0:
            canvas.create_text(width / 2, 54, text="Import a video to build the timeline", fill="#91a0a9", font=("Segoe UI", 10))
            return

        for tick_index in range(6):
            tick_time = duration * tick_index / 5
            x = left + track_width * tick_index / 5
            canvas.create_line(x, 19, x, 27, fill="#71808a")
            canvas.create_text(x, 10, text=self.format_editor_time(tick_time)[:-3], fill="#aab6bd", font=("Segoe UI", 8))

        selected = set(self.editor_clip_tree.selection()) if hasattr(self, "editor_clip_tree") else set()
        colors = ("#00a89d", "#2f80ed", "#e56b6f", "#e9a23b")
        for index, (start, end) in enumerate(self.editor_clips):
            x1 = left + track_width * start / duration
            x2 = left + track_width * end / duration
            if x2 - x1 < 2:
                x2 = x1 + 2
            is_selected = str(index) in selected
            outline = "#ffffff" if is_selected else "#57707b"
            canvas.create_rectangle(x1, track_top + 3, x2, track_bottom - 3, fill=colors[index % len(colors)], outline=outline, width=2 if is_selected else 1)
            label = "Full Video" if self.editor_mode.get() == "Single Video" else f"Part {index + 1}"
            if x2 - x1 >= 48:
                canvas.create_text((x1 + x2) / 2, (track_top + track_bottom) / 2, text=label, fill="#ffffff", font=("Segoe UI Semibold", 9))

        position = max(0.0, min(float(self.editor_preview_position.get()), duration))
        playhead_x = left + track_width * position / duration
        canvas.create_line(playhead_x, 18, playhead_x, 84, fill="#ff5c5c", width=2)
        canvas.create_polygon(playhead_x - 5, 18, playhead_x + 5, 18, playhead_x, 25, fill="#ff5c5c", outline="")

    def on_editor_timeline_click(self, event) -> None:
        if self.editor_video_duration <= 0:
            return
        if self.editor_preview_playing:
            self.stop_editor_preview(reset_label=False)
            self.editor_status.set("Preview paused")
        width = max(self.editor_timeline_canvas.winfo_width(), 300)
        left = 14
        track_width = max(1, width - 28)
        ratio = max(0.0, min(1.0, (event.x - left) / track_width))
        position = ratio * self.editor_video_duration
        self.editor_preview_position.set(position)
        self.editor_preview_end = self.editor_video_duration
        self.editor_clip_tree.selection_remove(*self.editor_clip_tree.selection())
        selected_end = self.editor_video_duration
        for index, (start, end) in enumerate(self.editor_clips):
            if start <= position <= end:
                selected_end = end
                if self.editor_clip_tree.exists(str(index)):
                    self.editor_clip_tree.selection_set(str(index))
                    self.editor_clip_tree.see(str(index))
                break
        self.after_idle(self.finish_editor_timeline_seek, position, selected_end)

    def finish_editor_timeline_seek(self, position: float, end: float) -> None:
        self.editor_preview_end = end
        self.editor_preview_position.set(position)
        self.on_editor_timeline_changed(position)
        self.request_editor_preview_refresh(delay=40)

    def on_editor_clip_selected(self, event=None) -> None:
        selection = self.editor_clip_tree.selection()
        if not selection:
            return
        index = int(selection[0])
        if not 0 <= index < len(self.editor_clips):
            return
        start, end = self.editor_clips[index]
        self.editor_preview_end = end
        self.editor_preview_position.set(start)
        self.on_editor_timeline_changed(start)
        self.request_editor_preview_refresh(delay=40)

    def stop_editor_preview_refresh(self) -> None:
        if self.editor_preview_refresh_job is not None:
            try:
                self.after_cancel(self.editor_preview_refresh_job)
            except tk.TclError:
                pass
            self.editor_preview_refresh_job = None
        self.editor_preview_refresh_generation += 1
        process = self.editor_preview_refresh_process
        self.editor_preview_refresh_process = None
        if process and process.poll() is None:
            try:
                process.terminate()
            except Exception:
                pass

    def request_editor_preview_refresh(self, delay: int = 140) -> None:
        if not self.editor_video_path or not self.editor_video_path.exists():
            return
        if self.editor_preview_refresh_job is not None:
            try:
                self.after_cancel(self.editor_preview_refresh_job)
            except tk.TclError:
                pass
        self.editor_preview_refresh_job = self.after(delay, self.start_editor_preview_refresh)

    def start_editor_preview_refresh(self) -> None:
        self.editor_preview_refresh_job = None
        video_path = self.editor_video_path
        ffmpeg = get_ffmpeg_path()
        if not video_path or not video_path.exists() or not ffmpeg:
            return
        was_playing = self.editor_preview_playing
        if was_playing:
            self.stop_editor_preview(reset_label=False)
        self.stop_editor_preview_refresh()
        self.editor_preview_refresh_generation += 1
        generation = self.editor_preview_refresh_generation
        position = max(0.0, min(float(self.editor_preview_position.get()), self.editor_video_duration))
        settings = self.current_editor_settings()
        settings["resolution"] = "Original"
        settings["fps"] = "Original"
        settings["transition"] = "None"
        video_filter, _audio_filter = self.build_editor_filters(settings, max(0.1, self.editor_video_duration - position))
        fit_filter = "scale=560:315:force_original_aspect_ratio=decrease,pad=560:315:(ow-iw)/2:(oh-ih)/2:color=0x11181d"
        video_filter = f"{video_filter},{fit_filter}" if video_filter else fit_filter
        command = [
            ffmpeg, "-hide_banner", "-loglevel", "error", "-ss", f"{position:.3f}", "-i", str(video_path),
            "-an", "-frames:v", "1", "-vf", video_filter, "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1",
        ]
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, creationflags=creation_flags)
        self.editor_preview_refresh_process = process
        self.editor_status.set("Updating preview...")
        threading.Thread(
            target=self.read_editor_preview_refresh,
            args=(process, generation, was_playing),
            daemon=True,
        ).start()

    def read_editor_preview_refresh(self, process, generation: int, resume_playback: bool) -> None:
        frame_size = 560 * 315 * 3
        data = process.stdout.read(frame_size) if process.stdout else b""
        try:
            process.wait(timeout=3)
        except Exception:
            if process.poll() is None:
                process.terminate()
        self.after(0, self.finish_editor_preview_refresh, data, generation, resume_playback)

    def finish_editor_preview_refresh(self, data: bytes, generation: int, resume_playback: bool) -> None:
        if generation != self.editor_preview_refresh_generation:
            return
        self.editor_preview_refresh_process = None
        if len(data) == 560 * 315 * 3:
            frame = Image.frombytes("RGB", (560, 315), data)
            photo = ImageTk.PhotoImage(frame)
            self.editor_preview_image = photo
            self.editor_preview_label.configure(image=photo, text="")
            self.editor_status.set("Preview updated")
        if resume_playback and self.editor_video_path:
            self.after(40, self.start_editor_preview)

    def toggle_editor_preview(self) -> None:
        if self.editor_preview_playing:
            self.stop_editor_preview(reset_label=False)
            self.editor_status.set("Preview paused")
            return
        self.start_editor_preview()

    def start_editor_preview(self) -> None:
        video_path = self.editor_video_path
        if not video_path or not video_path.exists():
            messagebox.showerror(APP_TITLE, "Import a video first.")
            return
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "ffmpeg is required for preview.")
            return
        self.stop_editor_preview_refresh()
        self.stop_editor_preview(reset_label=False)
        start = max(0.0, min(float(self.editor_preview_position.get()), self.editor_video_duration))
        end = self.editor_preview_end if self.editor_preview_end > start else self.editor_video_duration
        end = min(end, self.editor_video_duration)
        if start >= end - 0.05:
            selection = self.editor_clip_tree.selection()
            start = self.editor_clips[int(selection[0])][0] if selection else 0.0
            self.editor_preview_position.set(start)
            end = self.editor_preview_end if self.editor_preview_end > start else self.editor_video_duration
        settings = self.current_editor_settings()
        settings["resolution"] = "Original"
        settings["fps"] = "Original"
        video_filter, _audio_filter = self.build_editor_filters(settings, end - start)
        preview_filter = "scale=560:315:force_original_aspect_ratio=decrease,pad=560:315:(ow-iw)/2:(oh-ih)/2:color=0x11181d,fps=15"
        video_filter = f"{video_filter},{preview_filter}" if video_filter else preview_filter
        command = [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            f"{start:.3f}",
            "-t",
            f"{max(0.05, end - start):.3f}",
            "-i",
            str(video_path),
            "-an",
            "-vf",
            video_filter,
            "-f",
            "rawvideo",
            "-pix_fmt",
            "rgb24",
            "pipe:1",
        ]
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            creationflags=creation_flags,
        )
        self.editor_preview_process = process
        self.editor_preview_playing = True
        self.editor_preview_generation += 1
        generation = self.editor_preview_generation
        self.editor_play_btn.configure(text="Pause", state="normal")
        self.editor_status.set("Preview playing")
        source_rate = 0.5 if settings["effect"] == "Slow 0.5x" else 2.0 if settings["effect"] == "Fast 2x" else 1.0
        thread = threading.Thread(
            target=self._read_editor_preview,
            args=(process, start, end, source_rate, generation),
            daemon=True,
        )
        thread.start()

    def _read_editor_preview(self, process, start: float, end: float, source_rate: float, generation: int) -> None:
        frame_size = 560 * 315 * 3
        position = start
        while self.editor_preview_playing and generation == self.editor_preview_generation:
            data = process.stdout.read(frame_size) if process.stdout else b""
            if len(data) != frame_size:
                break
            frame = Image.frombytes("RGB", (560, 315), data)
            position = min(end, position + (source_rate / 15.0))
            self.after(0, self._display_editor_preview_frame, frame, position, generation)
            time.sleep(1 / 15)
        try:
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=2)
        except Exception:
            pass
        self.after(0, self._finish_editor_preview, generation)

    def _display_editor_preview_frame(self, frame: Image.Image, position: float, generation: int) -> None:
        if generation != self.editor_preview_generation or not self.editor_preview_playing:
            return
        photo = ImageTk.PhotoImage(frame)
        self.editor_preview_image = photo
        self.editor_preview_label.configure(image=photo, text="")
        self.editor_preview_position.set(position)
        self.on_editor_timeline_changed(position)

    def _finish_editor_preview(self, generation: int) -> None:
        if generation != self.editor_preview_generation:
            return
        self.editor_preview_playing = False
        self.editor_preview_process = None
        self.editor_play_btn.configure(text="Play", state="normal" if self.editor_video_path else "disabled")
        self.editor_status.set("Preview complete")

    def stop_editor_preview(self, reset_label: bool = False) -> None:
        self.editor_preview_playing = False
        self.editor_preview_generation += 1
        process = self.editor_preview_process
        self.editor_preview_process = None
        if process and process.poll() is None:
            try:
                process.terminate()
            except Exception:
                pass
        if hasattr(self, "editor_play_btn"):
            self.editor_play_btn.configure(text="Play", state="normal" if self.editor_video_path else "disabled")
        if reset_label and hasattr(self, "editor_preview_label"):
            self.editor_preview_label.configure(image="", text="Import a video to preview")
            self.editor_preview_image = None

    def import_editor_video(self) -> None:
        selected = filedialog.askopenfilename(
            title="Import video for editing",
            initialdir=self.download_dir.get(),
            filetypes=(
                ("Video files", "*.mp4 *.mkv *.webm *.mov *.avi *.flv *.m4v"),
                ("All files", "*.*"),
            ),
        )
        if selected:
            self.load_editor_video(Path(selected))

    def probe_video_duration(self, video_path: Path) -> float:
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            raise RuntimeError("ffmpeg is required for video editing.")
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        result = subprocess.run(
            [ffmpeg, "-hide_banner", "-i", str(video_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            errors="ignore",
            creationflags=creation_flags,
        )
        match = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", result.stderr)
        if not match:
            raise RuntimeError("Could not read the video duration.")
        hours, minutes, seconds = match.groups()
        return int(hours) * 3600 + int(minutes) * 60 + float(seconds)

    @staticmethod
    def format_editor_time(seconds: float) -> str:
        seconds = max(0.0, float(seconds))
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        remaining = seconds % 60
        return f"{hours:02d}:{minutes:02d}:{remaining:05.2f}"

    @staticmethod
    def parse_editor_time(value: str) -> float:
        text = value.strip()
        if not text:
            raise ValueError("Time is empty")
        parts = text.split(":")
        try:
            if len(parts) == 1:
                result = float(parts[0])
            elif len(parts) == 2:
                result = float(parts[0]) * 60 + float(parts[1])
            elif len(parts) == 3:
                result = float(parts[0]) * 3600 + float(parts[1]) * 60 + float(parts[2])
            else:
                raise ValueError("Invalid time")
        except ValueError as exc:
            raise ValueError("Use seconds or HH:MM:SS") from exc
        if result < 0:
            raise ValueError("Time cannot be negative")
        return result

    def load_editor_video(self, video_path: Path) -> None:
        if not video_path.exists() or video_path.suffix.lower() not in VIDEO_EXTENSIONS:
            messagebox.showerror(APP_TITLE, "Choose a valid video file.")
            return
        try:
            duration = self.probe_video_duration(video_path)
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))
            return
        self.stop_editor_preview_refresh()
        self.stop_editor_preview(reset_label=True)
        self.editor_video_path = video_path
        self.editor_video_duration = duration
        self.editor_preview_end = duration
        self.editor_preview_position.set(0.0)
        self.on_editor_timeline_changed(0.0)
        self.editor_play_btn.configure(state="normal")
        display_name = video_path.name if len(video_path.name) <= 72 else video_path.name[:69] + "..."
        self.editor_source_status.set(f"{display_name} | {self.format_editor_time(duration)}")
        self.editor_status.set("Video ready for editing")
        self.editor_mode.set("Single Video")
        self.editor_clips = [(0.0, duration)]
        self.refresh_editor_clip_tree()
        self.on_editor_mode_changed(keep_clips=True)
        self.editor_export_btn.configure(state="normal")
        self.notebook.select(self.editor_tab)
        self.reset_editor_history()
        self.request_editor_preview_refresh(delay=30)

    def on_editor_mode_changed(self, event=None, keep_clips: bool = False) -> None:
        mode = self.editor_mode.get()
        automatic = mode == "Automatic Clips"
        manual = mode == "Manual Clips"
        duration_state = "normal" if automatic else "disabled"
        self.editor_duration_input.configure(state=duration_state)
        self.editor_duration_unit_cb.configure(state="readonly" if automatic else "disabled")
        self.editor_generate_btn.configure(state="normal" if automatic else "disabled")
        for child in self.editor_manual_row.winfo_children():
            try:
                child.configure(state="normal" if manual else "disabled")
            except tk.TclError:
                pass
        if keep_clips or not self.editor_video_path:
            if self.editor_history_ready:
                self.commit_editor_history()
            return
        if mode == "Single Video":
            self.editor_clips = [(0.0, self.editor_video_duration)]
        else:
            self.editor_clips = []
        self.refresh_editor_clip_tree()
        self.commit_editor_history()

    def refresh_editor_clip_tree(self) -> None:
        for item in self.editor_clip_tree.get_children():
            self.editor_clip_tree.delete(item)
        mode = self.editor_mode.get()
        for index, (start, end) in enumerate(self.editor_clips, start=1):
            part_name = "Full Video" if mode == "Single Video" else f"Part {index:03d}"
            self.editor_clip_tree.insert(
                "",
                "end",
                iid=str(index - 1),
                values=(part_name, self.format_editor_time(end - start)),
            )
        count = len(self.editor_clips)
        if self.editor_video_path:
            self.editor_status.set(f"{count} export item{'s' if count != 1 else ''} ready")
        self.editor_export_btn.configure(state="normal" if self.editor_video_path and count else "disabled")
        self.render_editor_timeline()

    def generate_editor_auto_clips(self) -> None:
        if not self.editor_video_path:
            messagebox.showerror(APP_TITLE, "Import a video first.")
            return
        try:
            value = int(self.editor_duration_value.get())
        except (tk.TclError, ValueError):
            messagebox.showerror(APP_TITLE, "Enter a valid clip length.")
            return
        if value < 1:
            messagebox.showerror(APP_TITLE, "Clip length must be greater than zero.")
            return
        multiplier = {"Seconds": 1, "Minutes": 60, "Hours": 3600}[self.editor_duration_unit.get()]
        clip_seconds = value * multiplier
        clips: list[tuple[float, float]] = []
        start = 0.0
        while start < self.editor_video_duration:
            end = min(start + clip_seconds, self.editor_video_duration)
            clips.append((start, end))
            start = end
        self.editor_clips = clips
        self.refresh_editor_clip_tree()
        self.commit_editor_history()

    def add_editor_manual_clip(self) -> None:
        if not self.editor_video_path:
            messagebox.showerror(APP_TITLE, "Import a video first.")
            return
        try:
            start = self.parse_editor_time(self.editor_manual_start.get())
            end = self.parse_editor_time(self.editor_manual_end.get())
        except ValueError as exc:
            messagebox.showerror(APP_TITLE, str(exc))
            return
        if end <= start:
            messagebox.showerror(APP_TITLE, "Clip end must be after clip start.")
            return
        if start >= self.editor_video_duration:
            messagebox.showerror(APP_TITLE, "Clip start is outside the video.")
            return
        end = min(end, self.editor_video_duration)
        self.editor_clips.append((start, end))
        self.editor_clips.sort(key=lambda item: item[0])
        self.refresh_editor_clip_tree()
        self.editor_manual_start.set(self.format_editor_time(end))
        self.editor_manual_end.set(self.format_editor_time(min(end + 20, self.editor_video_duration)))
        self.commit_editor_history()

    def remove_editor_clips(self) -> None:
        selected = {int(item) for item in self.editor_clip_tree.selection()}
        if not selected:
            return
        self.editor_clips = [clip for index, clip in enumerate(self.editor_clips) if index not in selected]
        self.refresh_editor_clip_tree()
        self.commit_editor_history()

    def clear_editor_clips(self) -> None:
        if self.editor_mode.get() == "Single Video" and self.editor_video_path:
            self.editor_clips = [(0.0, self.editor_video_duration)]
        else:
            self.editor_clips = []
        self.refresh_editor_clip_tree()
        self.commit_editor_history()

    def prepare_video_in_editor(self, video_path: Path, duration_value: int, unit: str) -> None:
        self.load_editor_video(video_path)
        if not self.editor_video_path:
            return
        self.editor_mode.set("Automatic Clips")
        self.editor_duration_value.set(duration_value)
        self.editor_duration_unit.set(unit)
        self.on_editor_mode_changed()
        self.generate_editor_auto_clips()
        self.notebook.select(self.editor_tab)
        self.reset_editor_history()

    def start_editor_export(self) -> None:
        video_path = self.editor_video_path
        if not video_path or not video_path.exists():
            messagebox.showerror(APP_TITLE, "Import a video first.")
            return
        if not self.editor_clips:
            messagebox.showerror(APP_TITLE, "Create at least one clip or select Single Video mode.")
            return
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "ffmpeg is required for video editing.")
            return
        try:
            text_size = max(12, min(200, int(self.editor_text_size.get())))
        except (tk.TclError, ValueError):
            messagebox.showerror(APP_TITLE, "Text size must be a number.")
            return

        settings = self.current_editor_settings()
        settings["text_size"] = text_size
        download_root = Path(self.download_dir.get())
        download_root.mkdir(parents=True, exist_ok=True)
        date_prefix = datetime.now().strftime("%Y-%m-%d")
        safe_stem = re.sub(r'[<>:"/\\|?*]+', "_", video_path.stem).strip(" .") or "video"
        if settings["mode"] == "Single Video":
            output_path = download_root / f"{date_prefix}_{safe_stem}_Edited.mp4"
            if output_path.exists():
                output_path = download_root / f"{date_prefix}_{safe_stem}_Edited_{datetime.now():%H%M%S}.mp4"
            output_paths = [output_path]
            output_folder = download_root
        else:
            output_folder = download_root / f"{date_prefix}_{safe_stem}_Parts"
            if output_folder.exists():
                output_folder = download_root / f"{date_prefix}_{safe_stem}_Parts_{datetime.now():%H%M%S}"
            output_folder.mkdir(parents=True, exist_ok=False)
            output_paths = [output_folder / f"{date_prefix}_Part_{index:03d}.mp4" for index in range(1, len(self.editor_clips) + 1)]

        self.editor_export_btn.configure(state="disabled")
        self.editor_stop_btn.configure(state="normal")
        self.editor_stop_requested = False
        self.status.set("Exporting edited video...")
        self.editor_status.set("Starting export...")
        self.progress.set(0)
        self.write_log(f"Editor export started: {len(output_paths)} item(s)")
        thread = threading.Thread(
            target=self._run_editor_export,
            args=(ffmpeg, video_path, list(self.editor_clips), output_paths, output_folder, settings),
            daemon=True,
        )
        thread.start()

    @staticmethod
    def _escape_drawtext(value: str) -> str:
        return value.replace("\\", r"\\").replace("'", r"\'").replace(":", r"\:").replace("%", r"\%")

    def build_editor_filters(self, settings: dict, duration: float) -> tuple[str, str]:
        video_filters: list[str] = []
        audio_filters: list[str] = []
        lut_path = settings.get("lut_path", "")
        if lut_path:
            escaped = lut_path.replace("\\", "/").replace(":", r"\:").replace("'", r"\'")
            video_filters.append(f"lut3d=file='{escaped}'")
        intensity = max(0.0, min(1.0, settings.get("filter_intensity", 100) / 100.0))
        filter_presets = {
            "Vivid": f"eq=contrast={1 + 0.08 * intensity:.3f}:brightness={0.02 * intensity:.3f}:saturation={1 + 0.30 * intensity:.3f}",
            "Warm": f"colorbalance=rs={0.08 * intensity:.3f}:gs={0.02 * intensity:.3f}:bs={-0.06 * intensity:.3f}",
            "Cool": f"colorbalance=rs={-0.05 * intensity:.3f}:gs={0.01 * intensity:.3f}:bs={0.09 * intensity:.3f}",
            "Cinematic": f"eq=contrast={1 + 0.15 * intensity:.3f}:brightness={-0.02 * intensity:.3f}:saturation={1 - 0.12 * intensity:.3f},colorbalance=rs={0.03 * intensity:.3f}:bs={0.04 * intensity:.3f}",
            "Black & White": f"hue=s={1 - intensity:.3f}",
            "Retro": f"eq=contrast={1 + 0.05 * intensity:.3f}:saturation={1 - 0.28 * intensity:.3f},colorbalance=rs={0.10 * intensity:.3f}:gs={0.03 * intensity:.3f}:bs={-0.06 * intensity:.3f}",
            "Sharpen": f"unsharp=5:5:{0.8 * intensity:.3f}:3:3:{0.4 * intensity:.3f}",
            "Soft": f"gblur=sigma={0.8 * intensity:.3f}",
        }
        selected_filter = filter_presets.get(settings["filter"]) if intensity > 0 else None
        if selected_filter:
            video_filters.append(selected_filter)

        adjustment = settings.get("adjustments", {})
        brightness = max(-100, min(100, adjustment.get("Brightness", 0))) / 200
        contrast = 1 + max(-100, min(100, adjustment.get("Contrast", 0))) / 125
        saturation = 1 + max(-100, min(100, adjustment.get("Saturation", 0))) / 100
        temperature = max(-100, min(100, adjustment.get("Temperature", 0))) / 500
        if brightness or contrast != 1 or saturation != 1:
            video_filters.append(f"eq=brightness={brightness:.4f}:contrast={contrast:.4f}:saturation={saturation:.4f}")
        if temperature:
            video_filters.append(f"colorbalance=rs={temperature:.4f}:bs={-temperature:.4f}")

        effect = settings["effect"]
        speed_factor = 1.0
        if effect == "Film Grain":
            video_filters.append("noise=alls=10:allf=t+u")
        elif effect == "Vignette":
            video_filters.append("vignette")
        elif effect == "Mirror":
            video_filters.append("hflip")
        elif effect == "Slow 0.5x":
            video_filters.append("setpts=2.0*PTS")
            audio_filters.append("atempo=0.5")
            speed_factor = 2.0
        elif effect == "Fast 2x":
            video_filters.append("setpts=0.5*PTS")
            audio_filters.append("atempo=2.0")
            speed_factor = 0.5

        ratio_text = settings.get("aspect_ratio", "Original")
        ratio_values = {"16:9": (16, 9), "9:16": (9, 16), "1:1": (1, 1), "4:5": (4, 5), "4:3": (4, 3)}
        ratio = ratio_values.get(ratio_text)
        target_height = {"720p": 720, "1080p": 1080, "2K": 1440, "4K": 2160}.get(settings["resolution"])
        if ratio and target_height:
            ratio_width, ratio_height = ratio
            if ratio_width >= ratio_height:
                output_height = target_height
                output_width = round(output_height * ratio_width / ratio_height)
            else:
                output_width = target_height
                output_height = round(output_width * ratio_height / ratio_width)
            output_width += output_width % 2
            output_height += output_height % 2
            video_filters.append(f"scale={output_width}:{output_height}:force_original_aspect_ratio=decrease")
            video_filters.append(f"pad={output_width}:{output_height}:(ow-iw)/2:(oh-ih)/2:color=black")
        elif ratio:
            ratio_width, ratio_height = ratio
            ratio_value = ratio_width / ratio_height
            video_filters.append(
                f"pad=ceil(max(iw\\,ih*{ratio_value:.8f})/2)*2:"
                f"ceil(max(ih\\,iw/{ratio_value:.8f})/2)*2:(ow-iw)/2:(oh-ih)/2:color=black"
            )
        else:
            target_size = {"720p": 1280, "1080p": 1920, "2K": 2560, "4K": 3840}.get(settings["resolution"])
            if target_size:
                video_filters.append(f"scale={target_size}:{target_size}:force_original_aspect_ratio=decrease")
                video_filters.append("scale=trunc(iw/2)*2:trunc(ih/2)*2")
        if settings["fps"] != "Original":
            video_filters.append(f"fps={settings['fps']}")

        effective_duration = max(0.1, duration * speed_factor)
        transition = settings["transition"]
        if transition != "None":
            fade_duration = min(0.7, effective_duration / 3)
            fade_out_start = max(0.0, effective_duration - fade_duration)
            color = "white" if transition == "Flash" else "black"
            video_filters.append(f"fade=t=in:st=0:d={fade_duration:.3f}:color={color}")
            video_filters.append(f"fade=t=out:st={fade_out_start:.3f}:d={fade_duration:.3f}:color={color}")
            audio_filters.append(f"afade=t=in:st=0:d={fade_duration:.3f}")
            audio_filters.append(f"afade=t=out:st={fade_out_start:.3f}:d={fade_duration:.3f}")

        if settings["text"]:
            escaped_text = self._escape_drawtext(settings["text"])
            y_position = {"Top": "40", "Center": "(h-text_h)/2", "Bottom": "h-text_h-40"}[settings["text_position"]]
            font_path = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts" / "arial.ttf"
            escaped_font = str(font_path).replace("\\", "/").replace(":", r"\:")
            video_filters.append(
                f"drawtext=fontfile='{escaped_font}':text='{escaped_text}':fontcolor=white:fontsize={settings['text_size']}:"
                f"borderw=2:bordercolor=black@0.85:x=(w-text_w)/2:y={y_position}"
            )
        return ",".join(video_filters), ",".join(audio_filters)

    def _run_editor_export(
        self,
        ffmpeg: str,
        video_path: Path,
        clips: list[tuple[float, float]],
        output_paths: list[Path],
        output_folder: Path,
        settings: dict,
    ) -> None:
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        total = len(clips)
        for index, ((start, end), output_path) in enumerate(zip(clips, output_paths), start=1):
            duration = end - start
            video_filter, audio_filter = self.build_editor_filters(settings, duration)
            command = [ffmpeg, "-y", "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(video_path), "-map", "0:v:0", "-map", "0:a?"]
            if video_filter:
                command.extend(["-vf", video_filter])
            if audio_filter:
                command.extend(["-af", audio_filter])
            command.extend([
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(output_path),
            ])
            self.after(0, self.editor_status.set, f"Exporting {index}/{total}: {output_path.name}")
            self.after(0, self.progress.set, ((index - 1) / total) * 100)
            process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                errors="ignore",
                creationflags=creation_flags,
            )
            self.current_process = process
            error_tail: list[str] = []
            for line in process.stdout or []:
                clean = line.strip()
                if clean:
                    error_tail.append(clean)
                    error_tail = error_tail[-8:]
            code = process.wait()
            self.current_process = None
            if self.editor_stop_requested:
                self.after(0, self._finish_editor_stopped)
                return
            if code != 0:
                detail = "\n".join(error_tail[-4:])
                self.after(0, self._finish_editor_error, f"Export failed on item {index}.\n{detail}")
                return
        self.after(0, self._finish_editor_export, output_folder, output_paths)

    def stop_editor_export(self) -> None:
        self.editor_stop_requested = True
        process = self.current_process
        if process and process.poll() is None:
            process.terminate()
        self.editor_stop_btn.configure(state="disabled")
        self.editor_status.set("Stopping export...")

    def _finish_editor_stopped(self) -> None:
        self.status.set("Editor export stopped")
        self.editor_status.set("Export stopped")
        self.editor_export_btn.configure(state="normal")
        self.editor_stop_btn.configure(state="disabled")
        self.write_log("Editor export stopped by user.")

    def _finish_editor_export(self, output_folder: Path, output_paths: list[Path]) -> None:
        self.progress.set(100)
        self.status.set("Editor export complete")
        self.editor_status.set(f"Exported {len(output_paths)} file(s)")
        self.editor_export_btn.configure(state="normal")
        self.editor_stop_btn.configure(state="disabled")
        self.write_log(f"Editor export complete: {len(output_paths)} file(s) in {output_folder}")
        messagebox.showinfo(APP_TITLE, f"Export complete.\n\nFiles: {len(output_paths)}\nSaved in:\n{output_folder}")

    def _finish_editor_error(self, message: str) -> None:
        self.status.set("Editor export failed")
        self.editor_status.set("Export failed")
        self.editor_export_btn.configure(state="normal")
        self.editor_stop_btn.configure(state="disabled")
        self.write_log(message)
        messagebox.showerror(APP_TITLE, message)

    def show_create_clips_dialog(self) -> None:
        video_path = self.latest_downloaded_file
        if not video_path or not video_path.exists() or video_path.suffix.lower() not in VIDEO_EXTENSIONS:
            messagebox.showerror(APP_TITLE, "Download a video successfully before creating clips.")
            return
        if not get_ffmpeg_path():
            messagebox.showerror(APP_TITLE, "ffmpeg is required for creating clips.")
            return

        dialog = tk.Toplevel(self)
        dialog.title("Create Video Clips")
        dialog.resizable(False, False)
        dialog.transient(self)
        dialog.grab_set()
        content = ttk.Frame(dialog, padding=16)
        content.pack(fill="both", expand=True)

        ttk.Label(content, text="Split the complete video into clips", font=("Segoe UI", 12, "bold")).pack(anchor="w")
        video_name = video_path.name if len(video_path.name) <= 58 else video_path.name[:55] + "..."
        ttk.Label(content, text=video_name, foreground="#5f6b7a").pack(anchor="w", pady=(4, 14))
        ttk.Label(content, text="Clip duration").pack(anchor="w")
        duration_row = ttk.Frame(content)
        duration_row.pack(fill="x", pady=(4, 10))
        duration_var = tk.IntVar(value=20)
        duration_input = ttk.Spinbox(duration_row, from_=1, to=999999999, increment=1, textvariable=duration_var, width=12)
        duration_input.pack(side="left")
        unit_var = tk.StringVar(value="Seconds")
        ttk.Combobox(
            duration_row,
            textvariable=unit_var,
            values=("Seconds", "Minutes", "Hours"),
            state="readonly",
            width=10,
        ).pack(side="left", padx=(8, 0))
        ttk.Label(
            content,
            text="Clips will be saved in the selected Download Folder.\nThe final clip may be shorter than the selected duration.",
            foreground="#5f6b7a",
        ).pack(anchor="w")

        actions = ttk.Frame(content)
        actions.pack(fill="x", pady=(18, 0))

        def begin_export() -> None:
            try:
                duration_value = int(duration_var.get())
            except (tk.TclError, ValueError):
                messagebox.showerror(APP_TITLE, "Enter a valid clip duration.", parent=dialog)
                return
            if duration_value < 1:
                messagebox.showerror(APP_TITLE, "Clip duration must be greater than zero.", parent=dialog)
                return
            unit = unit_var.get()
            dialog.destroy()
            self.prepare_video_in_editor(video_path, duration_value, unit)

        ttk.Button(actions, text="Cancel", command=dialog.destroy).pack(side="right")
        ttk.Button(actions, text="Create Clips", command=begin_export, style="Accent.TButton").pack(side="right", padx=(0, 8))
        duration_input.focus_set()
        duration_input.selection_range(0, "end")
        dialog.bind("<Return>", lambda _event: begin_export())

    def start_clip_export(self, video_path: Path, seconds: int, duration_label: str) -> None:
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "ffmpeg is required for creating clips.")
            return
        download_root = Path(self.download_dir.get())
        download_root.mkdir(parents=True, exist_ok=True)
        safe_stem = re.sub(r'[<>:"/\\|?*]+', "_", video_path.stem).strip(" .") or "video"
        output_dir = download_root / f"{safe_stem} - {duration_label} clips"
        if output_dir.exists():
            output_dir = download_root / f"{safe_stem} - {duration_label} clips - {datetime.now():%Y%m%d-%H%M%S}"
        output_dir.mkdir(parents=True, exist_ok=False)

        self.status.set(f"Creating {duration_label} clips...")
        self.progress.set(0)
        self._set_busy(True)
        self.write_log(f"Creating {duration_label} clips from: {video_path}")
        self.write_log(f"Clip folder: {output_dir}")
        thread = threading.Thread(
            target=self._run_clip_export,
            args=(ffmpeg, video_path, output_dir, seconds),
            daemon=True,
        )
        thread.start()

    def _run_clip_export(self, ffmpeg: str, video_path: Path, output_dir: Path, seconds: int) -> None:
        output_pattern = output_dir / f"{video_path.stem}_clip_%03d.mp4"
        command = [
            ffmpeg,
            "-y",
            "-i",
            str(video_path),
            "-map",
            "0:v:0",
            "-map",
            "0:a?",
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "20",
            "-pix_fmt",
            "yuv420p",
            "-force_key_frames",
            f"expr:gte(t,n_forced*{seconds})",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-f",
            "segment",
            "-segment_time",
            str(seconds),
            "-segment_time_delta",
            "0.05",
            "-segment_start_number",
            "1",
            "-reset_timestamps",
            "1",
            "-avoid_negative_ts",
            "make_zero",
            str(output_pattern),
        ]
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="ignore",
            creationflags=creation_flags,
        )
        self.current_process = process
        for line in process.stdout or []:
            clean = line.strip()
            if clean and ("time=" in clean or "Error" in clean or "Invalid" in clean):
                self.after(0, self.handle_output, clean)
        code = process.wait()
        self.current_process = None
        clips = sorted(output_dir.glob("*.mp4"))
        if code == 0 and clips:
            self.after(0, self._finish_clip_export, output_dir, len(clips))
            return
        try:
            if not any(output_dir.iterdir()):
                output_dir.rmdir()
        except OSError:
            pass
        self.after(0, self.finish_with_error, "Clip creation failed. Check the download log for FFmpeg details.")

    def _finish_clip_export(self, output_dir: Path, clip_count: int) -> None:
        self.progress.set(100)
        self.status.set(f"Created {clip_count} clips")
        self._set_busy(False)
        self.write_log(f"Created {clip_count} clips: {output_dir}")
        messagebox.showinfo(APP_TITLE, f"Created {clip_count} clips successfully.\n\nSaved in:\n{output_dir}")

    def write_history(self, url: str, status: str, detail: str) -> None:
        APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
        item = {
            "time": time.strftime("%Y-%m-%d %H:%M:%S"),
            "user": self.username.get().strip(),
            "url": url,
            "status": status,
            "detail": detail,
        }
        with HISTORY_FILE.open("a", encoding="utf-8") as file:
            file.write(json.dumps(item, ensure_ascii=False) + "\n")

    def ask_hide_watermark(self, video_path: Path) -> None:
        wants_edit = messagebox.askyesno(
            APP_TITLE,
            "Download complete. Do you want to hide or blur an area in this video?",
        )
        if wants_edit:
            self.open_watermark_selector(video_path)

    def open_watermark_selector(self, video_path: Path) -> None:
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "ffmpeg is required for video editing.")
            return
        APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
        preview_path = APP_DATA_DIR / "preview_frame.png"
        cmd = [
            ffmpeg,
            "-y",
            "-ss",
            "00:00:01",
            "-i",
            str(video_path),
            "-frames:v",
            "1",
            str(preview_path),
        ]
        try:
            subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        except Exception as exc:
            messagebox.showerror(APP_TITLE, f"Could not create preview frame:\n{exc}")
            return
        selector = WatermarkSelector(self, video_path, preview_path)
        selector.grab_set()

    def export_hidden_area_video(self, video_path: Path, rectangles: list[tuple[int, int, int, int]], mode: str) -> None:
        ffmpeg = get_ffmpeg_path()
        if not ffmpeg:
            messagebox.showerror(APP_TITLE, "ffmpeg is required for video editing.")
            return
        if not rectangles:
            messagebox.showinfo(APP_TITLE, "Draw at least one area first.")
            return
        output_path = video_path.with_name(f"{video_path.stem}_clean{video_path.suffix}")
        self.status.set("Exporting edited video...")
        self._set_busy(True)
        self.write_log(f"Exporting clean copy: {output_path}")
        thread = threading.Thread(
            target=self._run_hide_area_export,
            args=(ffmpeg, video_path, output_path, rectangles, mode),
            daemon=True,
        )
        thread.start()

    def _run_hide_area_export(self, ffmpeg: str, video_path: Path, output_path: Path, rectangles: list[tuple[int, int, int, int]], mode: str) -> None:
        filters = []
        current = "[0:v]"
        for index, (x, y, w, h) in enumerate(rectangles):
            output = f"[v{index}]"
            if mode == "cover":
                filters.append(f"{current}drawbox=x={x}:y={y}:w={w}:h={h}:color=black@1:t=fill{output}")
            else:
                crop_base = f"[crop{index}]"
                blurred = f"[blur{index}]"
                filters.append(f"{current}split[base{index}]{crop_base}")
                filters.append(f"{crop_base}crop={w}:{h}:{x}:{y},boxblur=20:1{blurred}")
                filters.append(f"[base{index}]{blurred}overlay={x}:{y}{output}")
            current = output
        filter_complex = ";".join(filters)
        cmd = [
            ffmpeg,
            "-y",
            "-i",
            str(video_path),
            "-filter_complex",
            filter_complex,
            "-map",
            current,
            "-map",
            "0:a?",
            "-c:a",
            "copy",
            str(output_path),
        ]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="ignore")
        for line in process.stdout or []:
            clean = line.strip()
            if clean:
                self.after(0, self.handle_output, clean)
        code = process.wait()
        if code == 0:
            self.after(0, self.finish_export_success, output_path)
        else:
            self.after(0, self.finish_with_error, "Video export failed.")

    def finish_export_success(self, output_path: Path) -> None:
        self.status.set("Edited video exported")
        self._set_busy(False)
        self.write_log(f"Saved clean copy: {output_path}")
        messagebox.showinfo(APP_TITLE, f"Saved clean copy:\n{output_path}")

    def finish_with_error(self, message: str) -> None:
        self.status.set("Error")
        self._set_busy(False)
        self.write_log(f"Error: {message}")
        messagebox.showerror(APP_TITLE, message)

    def write_log(self, message: str) -> None:
        self.log.insert("end", message + "\n")
        self.log.see("end")

    def ytdlp_available(self) -> bool:
        return yt_dlp is not None

    @staticmethod
    def _version_tuple(value: str) -> tuple[int, ...]:
        numbers = re.findall(r"\d+", value or "")
        return tuple(int(number) for number in numbers[:4]) or (0,)

    def check_for_app_update(self, manual: bool = False) -> None:
        if manual:
            self.status.set("Checking for app update...")
        threading.Thread(target=self._check_for_app_update_worker, args=(manual,), daemon=True).start()

    def _check_for_app_update_worker(self, manual: bool) -> None:
        try:
            request = Request(
                GITHUB_LATEST_RELEASE_API,
                headers={
                    "Accept": "application/vnd.github+json",
                    "User-Agent": f"VideoDownloader/{APP_VERSION}",
                    "X-GitHub-Api-Version": "2022-11-28",
                },
            )
            with urlopen(request, timeout=12) as response:
                release = json.loads(response.read().decode("utf-8"))
            latest = str(release.get("tag_name") or "").lstrip("vV")
            if self._version_tuple(latest) <= self._version_tuple(APP_VERSION):
                if manual:
                    self.after(0, self.status.set, "App is up to date")
                    self.after(0, messagebox.showinfo, APP_TITLE, f"You already have the latest version ({APP_VERSION}).")
                return

            assets = release.get("assets") or []
            installers = [
                asset for asset in assets
                if str(asset.get("name", "")).lower().endswith(".exe")
            ]
            if not installers:
                raise RuntimeError("The latest GitHub release has no Windows installer.")
            installers.sort(key=lambda asset: (
                "setup" not in str(asset.get("name", "")).lower()
                and "installer" not in str(asset.get("name", "")).lower(),
                str(asset.get("name", "")),
            ))
            self.after(0, self._offer_app_update, release, installers[0], latest)
        except HTTPError as exc:
            if manual:
                detail = "No GitHub Release has been published yet." if exc.code == 404 else f"GitHub returned HTTP {exc.code}."
                self.after(0, self.status.set, "Update check unavailable")
                self.after(0, messagebox.showinfo, APP_TITLE, detail)
        except Exception as exc:
            if manual:
                self.after(0, self.status.set, "Update check failed")
                self.after(0, messagebox.showerror, APP_TITLE, f"Could not check for updates.\n\n{exc}")

    def _offer_app_update(self, release: dict, asset: dict, latest: str) -> None:
        self.status.set(f"Version {latest} available")
        notes = str(release.get("body") or "New features and fixes are available.").strip()
        if len(notes) > 700:
            notes = notes[:697].rstrip() + "..."
        wants_update = messagebox.askyesno(
            APP_TITLE,
            f"A new update is available.\n\nInstalled: {APP_VERSION}\nLatest: {latest}\n\n{notes}\n\nDownload and install now?",
        )
        if wants_update:
            threading.Thread(target=self._download_app_update, args=(asset,), daemon=True).start()

    def _download_app_update(self, asset: dict) -> None:
        try:
            update_dir = APP_DATA_DIR / "updates"
            update_dir.mkdir(parents=True, exist_ok=True)
            file_name = Path(str(asset.get("name") or "VideoDownloaderSetup.exe")).name
            target = update_dir / file_name
            url = str(asset.get("browser_download_url") or "")
            if not url.startswith("https://"):
                raise RuntimeError("GitHub release asset URL is invalid.")
            self.after(0, self.status.set, "Downloading app update...")
            request = Request(url, headers={"User-Agent": f"VideoDownloader/{APP_VERSION}"})
            with urlopen(request, timeout=30) as response, target.open("wb") as output:
                total = int(response.headers.get("Content-Length") or 0)
                downloaded = 0
                while True:
                    chunk = response.read(1024 * 256)
                    if not chunk:
                        break
                    output.write(chunk)
                    downloaded += len(chunk)
                    if total:
                        self.after(0, self.progress.set, min(downloaded / total * 100, 100))

            digest = str(asset.get("digest") or "")
            if digest.startswith("sha256:"):
                expected = digest.split(":", 1)[1].lower()
                actual = hashlib.sha256(target.read_bytes()).hexdigest().lower()
                if actual != expected:
                    target.unlink(missing_ok=True)
                    raise RuntimeError("Downloaded installer failed the SHA-256 security check.")

            self.after(0, self.status.set, "Starting app update...")
            os.startfile(target)
            self.after(500, self.close_app)
        except Exception as exc:
            self.after(0, self.finish_with_error, f"App update failed.\n\n{exc}")

    def update_ytdlp(self) -> None:
        wants_update = messagebox.askyesno(
            APP_TITLE,
            "Update yt-dlp now?\n\n"
            "The app will download the newest yt-dlp package. Restart the app after the update finishes.",
        )
        if not wants_update:
            return
        self._set_busy(True)
        self.status.set("Updating yt-dlp...")
        current_version = getattr(getattr(yt_dlp, "version", None), "__version__", "unknown")
        self.write_log(f"Current yt-dlp version: {current_version}")
        self.write_log("Updating yt-dlp. Please wait...")
        thread = threading.Thread(target=self._run_update_ytdlp, daemon=True)
        thread.start()

    def _run_update_ytdlp(self) -> None:
        APP_DATA_DIR.mkdir(parents=True, exist_ok=True)
        tmp_dir = APP_DATA_DIR / f"yt_dlp_runtime_new_{int(time.time())}"
        backup_dir = APP_DATA_DIR / "yt_dlp_runtime_previous"
        commands = self._pip_update_commands(tmp_dir)
        last_error = "No Python/pip command was available."

        for cmd in commands:
            self.after(0, self.write_log, "Running: " + " ".join(f'"{part}"' if " " in part else part for part in cmd))
            try:
                process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="ignore")
            except FileNotFoundError:
                last_error = f"Command not found: {cmd[0]}"
                self.after(0, self.write_log, last_error)
                continue
            for line in process.stdout or []:
                clean = line.strip()
                if clean:
                    self.after(0, self.write_log, clean)
            code = process.wait()
            if code == 0 and (tmp_dir / "yt_dlp").exists():
                try:
                    if backup_dir.exists():
                        shutil.rmtree(backup_dir, ignore_errors=True)
                    if YTDLP_UPDATE_DIR.exists():
                        YTDLP_UPDATE_DIR.rename(backup_dir)
                    tmp_dir.rename(YTDLP_UPDATE_DIR)
                    new_version = self._read_ytdlp_version(YTDLP_UPDATE_DIR)
                    self.after(0, self.status.set, "yt-dlp updated. Restart app.")
                    self.after(0, self.write_log, f"yt-dlp updated successfully: {new_version}")
                    self.after(0, self._set_busy, False)
                    self.after(0, messagebox.showinfo, APP_TITLE, "yt-dlp updated successfully.\n\nPlease close and reopen the app before downloading again.")
                    return
                except Exception as exc:
                    last_error = str(exc)
                    break
            last_error = f"Command failed with exit code {code}."

        if tmp_dir.exists():
            shutil.rmtree(tmp_dir, ignore_errors=True)
        self.after(0, self.finish_with_error, f"yt-dlp update failed.\n\n{last_error}\n\nInstall Python with pip, or run the app installer again.")

    def _pip_update_commands(self, target_dir: Path) -> list[list[str]]:
        base_args = [
            "-m",
            "pip",
            "install",
            "--upgrade",
            "--no-warn-script-location",
            "--target",
            str(target_dir),
            "yt-dlp",
        ]
        if getattr(sys, "frozen", False):
            return [
                ["py", *base_args],
                ["python", *base_args],
                ["python3", *base_args],
            ]
        return [[sys.executable, *base_args], ["py", *base_args], ["python", *base_args]]

    def _read_ytdlp_version(self, package_dir: Path) -> str:
        version_file = package_dir / "yt_dlp" / "version.py"
        if not version_file.exists():
            return "updated"
        match = re.search(r"__version__\\s*=\\s*['\"]([^'\"]+)", version_file.read_text(encoding="utf-8", errors="ignore"))
        return match.group(1) if match else "updated"


    # ─── Channel Download Methods ───────────────────────────────────────

    def paste_channel_url(self) -> None:
        try:
            self.channel_url.set(self.clipboard_get().strip())
        except tk.TclError:
            messagebox.showinfo(APP_TITLE, "Clipboard is empty.")

    def fetch_channel_videos(self) -> None:
        url = self.channel_url.get().strip()
        if not url or not URL_RE.match(url):
            messagebox.showerror(APP_TITLE, "Please paste a valid channel or profile URL.\n\nSupported platforms: YouTube, Instagram, TikTok, Facebook, X/Twitter, Vimeo, and more.")
            return
        if not self.ytdlp_available():
            messagebox.showerror(APP_TITLE, "yt-dlp is not available in this build.")
            return
        self.fetch_channel_btn.configure(state="disabled")
        self.ch_download_btn.configure(state="disabled")
        self.channel_info.set("Fetching channel videos... please wait...")
        self.channel_videos = []
        self.channel_video_item_ids = {}
        for item in self.channel_tree.get_children():
            self.channel_tree.delete(item)
        self.write_log(f"Fetching videos from channel: {url}")
        thread = threading.Thread(target=self._fetch_channel_worker, args=(url,), daemon=True)
        thread.start()

    def _fetch_channel_worker(self, url: str) -> None:
        """Background worker: extract all videos from a channel/profile/playlist with full logging and error diagnostics."""
        fetch_url_raw = url.strip().rstrip("/")
        parsed = urlparse(fetch_url_raw)
        host = parsed.netloc.lower().replace("www.", "")
        path = parsed.path.rstrip("/")

        # Candidate URLs to try in order
        candidates: list[str] = []

        if host in ("youtube.com", "m.youtube.com", "youtu.be"):
            # For YouTube channels, try /videos tab first for maximum video coverage
            if not any(path.endswith(t) for t in ("/videos", "/shorts", "/streams", "/featured", "/playlists")) and "list=" not in parsed.query:
                candidates.append(fetch_url_raw + "/videos")
        if host in ("tiktok.com", "m.tiktok.com") and path.startswith("/@"):
            tiktok_input = self._resolve_tiktok_user_input(path.split("/", 2)[1].lstrip("@"))
            if tiktok_input:
                candidates.append(tiktok_input)
        candidates.append(fetch_url_raw)

        ydl_opts_base: dict = {
            "extract_flat": "in_playlist",
            "quiet": True,
            "no_warnings": True,
            "ignoreerrors": True,
            "skip_download": True,
        }
        try:
            self._apply_cookie_options(ydl_opts_base)
        except RuntimeError as exc:
            self.after(0, self._on_channel_fetch_error, str(exc))
            return

        # A configured provider is preferred for TikTok profiles because the
        # public profile endpoint can stop after one page without reporting it.
        if host in ("tiktok.com", "m.tiktok.com") and path.startswith("/@") and str(self.settings.get("apify_token", "")).strip():
            username = path.split("/", 2)[1].lstrip("@")
            try:
                self.after(0, self.write_log, f"Fetching complete TikTok profile for @{username} through channel API...")
                api_videos = self._fetch_tiktok_profile_via_apify(username)
                if api_videos:
                    self.after(0, self._on_channel_fetch_done, f"@{username} (TikTok API)", api_videos)
                    return
                self.after(0, self.write_log, "Channel API returned no videos. Trying direct extraction automatically...")
            except Exception as exc:
                self.after(0, self.write_log, f"Channel API notice: {exc}. Trying direct extraction automatically...")

        info = None
        last_error = ""

        # Try candidate URLs with extract_flat='in_playlist', then extract_flat=True
        for cand_url in candidates:
            self.after(0, self.write_log, f"Extracting from: {cand_url}")
            for flat_mode in ("in_playlist", True):
                opts = dict(ydl_opts_base)
                opts["extract_flat"] = flat_mode
                try:
                    with yt_dlp.YoutubeDL(opts) as ydl:
                        res = ydl.extract_info(cand_url, download=False)
                        if res and (res.get("entries") is not None or res.get("id")):
                            info = res
                            break
                except Exception as exc:
                    last_error = str(exc)
                    self.after(0, self.write_log, f"Notice ({cand_url}): {exc}")
            if info:
                break

        # TikTok often hides the secondary channel ID on profile pages. Resolve it
        # from one indexed public video, then use yt-dlp's stable tiktokuser input.
        if (not info or not info.get("entries")) and host in ("tiktok.com", "m.tiktok.com") and path.startswith("/@"):
            username = path.split("/", 2)[1].lstrip("@")
            try:
                self.after(0, self.write_log, f"Resolving TikTok channel ID for @{username}...")
                search_results = DDGS().text(f"site:tiktok.com/@{username}/video @{username}", max_results=10)
                video_url = next(
                    (
                        str(item.get("href") or "")
                        for item in search_results
                        if re.match(rf"^https?://(?:www\.)?tiktok\.com/@{re.escape(username)}/video/\d+", str(item.get("href") or ""), re.IGNORECASE)
                    ),
                    "",
                )
                if video_url:
                    video_opts = dict(ydl_opts_base)
                    video_opts["extract_flat"] = False
                    with yt_dlp.YoutubeDL(video_opts) as ydl_video:
                        video_info = ydl_video.extract_info(video_url, download=False) or {}
                    channel_id = video_info.get("channel_id")
                    if channel_id:
                        self.after(0, self.write_log, "TikTok channel ID found. Fetching full profile...")
                        with yt_dlp.YoutubeDL(ydl_opts_base) as ydl_profile:
                            info = ydl_profile.extract_info(f"tiktokuser:{channel_id}", download=False)
            except Exception as exc:
                last_error = f"TikTok profile fallback failed: {exc}"
                self.after(0, self.write_log, last_error)

        if not info:
            err_msg = last_error if last_error else "No videos or channel metadata returned."
            tip = "\n\nTip: If this channel or profile requires sign-in (e.g. Instagram, Facebook, private YouTube), select your browser in the Cookies menu (e.g. 'Use Chrome cookies' or 'Use Edge cookies') and try again."
            self.after(0, self._on_channel_fetch_error, f"{err_msg}{tip}")
            return

        # Safely materialize entries (yt-dlp returns generators/LazyList)
        raw_entries = info.get("entries")
        if raw_entries is None:
            # Single video page, not a channel/playlist
            if info.get("id") and (info.get("title") or info.get("webpage_url")):
                raw_entries = [info]
            else:
                self.after(0, self._on_channel_fetch_error,
                           "No videos found. Make sure this is a channel, profile, or playlist URL.")
                return

        try:
            entries_list = [e for e in list(raw_entries) if e is not None]
        except Exception as exc:
            self.after(0, self._on_channel_fetch_error, f"Failed to list entries: {exc}")
            return

        # Expand tab playlists (e.g. if yt-dlp returned a 'Videos' tab entry)
        expanded_entries: list[dict] = []
        with yt_dlp.YoutubeDL(ydl_opts_base) as ydl_sub:
            for entry in entries_list:
                if not isinstance(entry, dict):
                    continue
                # If entry is a sub-playlist tab (like Videos or Uploads)
                if entry.get("_type") in ("url", "playlist") and entry.get("title") in ("Videos", "Uploads") and entry.get("url"):
                    try:
                        sub_info = ydl_sub.extract_info(entry["url"], download=False)
                        if sub_info and sub_info.get("entries"):
                            expanded_entries.extend([s for s in sub_info["entries"] if isinstance(s, dict)])
                            continue
                    except Exception:
                        pass
                expanded_entries.append(entry)

        channel_title = (info.get("channel") or info.get("uploader")
                         or info.get("title") or info.get("playlist_title")
                         or "Unknown Channel")
        extractor = (info.get("extractor_key") or info.get("extractor") or "").lower()

        videos: list[dict] = []
        for entry in expanded_entries:
            try:
                url_cand = entry.get("webpage_url") or entry.get("url") or ""
                vid_id = entry.get("id") or ""

                if url_cand.startswith("http://") or url_cand.startswith("https://"):
                    video_url = url_cand
                elif "youtube" in host or "youtube" in extractor:
                    if vid_id:
                        video_url = f"https://www.youtube.com/watch?v={vid_id}"
                    elif url_cand:
                        video_url = f"https://www.youtube.com/watch?v={url_cand}"
                    else:
                        continue
                elif url_cand:
                    if url_cand.startswith("/"):
                        scheme = parsed.scheme or "https"
                        video_url = f"{scheme}://{host}{url_cand}"
                    else:
                        video_url = url_cand
                elif vid_id:
                    video_url = vid_id
                else:
                    continue

                duration_secs = entry.get("duration")
                if duration_secs and isinstance(duration_secs, (int, float)):
                    mins, secs = divmod(int(duration_secs), 60)
                    hours, mins = divmod(mins, 60)
                    duration_str = f"{hours}:{mins:02d}:{secs:02d}" if hours else f"{mins}:{secs:02d}"
                else:
                    duration_str = "—"

                videos.append({
                    "title": entry.get("title") or "Untitled",
                    "url": video_url,
                    "duration": duration_str,
                    "id": vid_id,
                })
            except Exception:
                continue

        if not videos:
            fallback_videos = self._discover_profile_video_links(fetch_url_raw)
            if fallback_videos:
                self.after(0, self.write_log, f"Partial fallback found only {len(fallback_videos)} indexed public videos; this is not the complete channel.")
                self.after(0, self._on_channel_fetch_done, f"{channel_title} (PARTIAL INDEXED RESULTS)", fallback_videos)
                return
            self.after(0, self._on_channel_fetch_error,
                       f"Found {len(expanded_entries)} items but could not extract video URLs.\n\n"
                       "Automatic fallback also found no indexed public videos. Private or unindexed posts may require browser cookies.")
            return

        self.after(0, self._on_channel_fetch_done, channel_title, videos)

    def _fetch_tiktok_profile_via_apify(self, username: str) -> list[dict]:
        token = str(self.settings.get("apify_token", "")).strip()
        if not token:
            return []
        try:
            maximum = max(50, min(5000, int(self.settings.get("apify_max_results", 1000))))
        except (TypeError, ValueError):
            maximum = 1000

        payload = {
            "profiles": [username],
            "profileScrapeSections": ["videos"],
            "profileSorting": "latest",
            "resultsPerPage": maximum,
            "maxFollowersPerProfile": 0,
            "maxFollowingPerProfile": 0,
            "commentsPerPost": 0,
            "topLevelCommentsPerPost": 0,
            "maxRepliesPerComment": 0,
            "shouldDownloadVideos": False,
            "shouldDownloadCovers": False,
            "shouldDownloadSlideshowImages": False,
            "shouldDownloadSubtitles": False,
        }
        request = Request(
            f"{APIFY_TIKTOK_ACTOR_ENDPOINT}?token={quote_plus(token)}",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request, timeout=300) as response:
                result = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="ignore")[:300]
            raise RuntimeError(f"API request failed ({exc.code}): {detail or exc.reason}") from exc
        except URLError as exc:
            raise RuntimeError(f"API connection failed: {exc.reason}") from exc

        if not isinstance(result, list):
            raise RuntimeError("API returned an unexpected response")

        videos: list[dict] = []
        seen: set[str] = set()
        for item in result:
            if not isinstance(item, dict) or item.get("error"):
                continue
            video_id = str(item.get("id") or item.get("videoId") or "").strip()
            author_meta = item.get("authorMeta") if isinstance(item.get("authorMeta"), dict) else {}
            author = str(author_meta.get("name") or item.get("author") or username).strip().lstrip("@")
            page_url = str(
                item.get("postPage")
                or item.get("postUrl")
                or item.get("webpageUrl")
                or item.get("url")
                or ""
            ).strip()
            if not page_url.startswith(("http://", "https://")) and video_id:
                page_url = f"https://www.tiktok.com/@{author or username}/video/{video_id}"
            if not page_url.startswith(("http://", "https://")):
                continue
            unique_key = video_id or page_url.lower()
            if unique_key in seen:
                continue
            seen.add(unique_key)

            video_meta = item.get("videoMeta") if isinstance(item.get("videoMeta"), dict) else {}
            duration_value = video_meta.get("duration") or item.get("duration")
            if isinstance(duration_value, (int, float)):
                mins, secs = divmod(int(duration_value), 60)
                hours, mins = divmod(mins, 60)
                duration = f"{hours}:{mins:02d}:{secs:02d}" if hours else f"{mins}:{secs:02d}"
            else:
                duration = "—"
            title = str(item.get("text") or item.get("description") or f"TikTok video {video_id}").strip()
            videos.append({"title": title or "TikTok video", "url": page_url, "duration": duration, "id": video_id})

        self.after(0, self.write_log, f"Channel API returned {len(videos)} unique TikTok videos.")
        return videos

    def _resolve_tiktok_user_input(self, username: str) -> str:
        try:
            self.after(0, self.write_log, f"Resolving TikTok channel ID for @{username}...")
            results = DDGS().text(f"site:tiktok.com/@{username}/video @{username}", max_results=10)
            video_url = next(
                (
                    str(item.get("href") or "")
                    for item in results
                    if re.match(rf"^https?://(?:www\.)?tiktok\.com/@{re.escape(username)}/video/\d+", str(item.get("href") or ""), re.IGNORECASE)
                ),
                "",
            )
            if not video_url:
                return ""
            opts = {"quiet": True, "no_warnings": True, "skip_download": True}
            self._apply_cookie_options(opts)
            with yt_dlp.YoutubeDL(opts) as ydl:
                video_info = ydl.extract_info(video_url, download=False) or {}
            channel_id = video_info.get("channel_id")
            if channel_id:
                self.after(0, self.write_log, "TikTok channel ID found. Using full-profile mode.")
                return f"tiktokuser:{channel_id}"
        except Exception as exc:
            self.after(0, self.write_log, f"TikTok channel ID notice: {exc}")
        return ""

    def _discover_profile_video_links(self, profile_url: str, limit: int = 50) -> list[dict]:
        parsed = urlparse(profile_url)
        host = parsed.netloc.lower().removeprefix("www.")
        parts = [part for part in parsed.path.split("/") if part]
        if not parts:
            return []
        identifier = parts[0].lstrip("@")
        if host.endswith("snapchat.com") and parts[0].lower() == "add" and len(parts) > 1:
            identifier = parts[1]
        if "xiaohongshu.com" in host and len(parts) > 2:
            identifier = parts[-1]
        query = f'site:{host} "{identifier}" video OR reel OR post'
        found: list[dict] = []
        seen: set[str] = set()
        try:
            for item in DDGS().text(query, max_results=limit):
                url = str(item.get("href") or "").split("?", 1)[0].rstrip("/")
                candidate = urlparse(url)
                candidate_host = candidate.netloc.lower().removeprefix("www.")
                candidate_parts = [part for part in candidate.path.split("/") if part]
                if not candidate_parts or not candidate_host.endswith(host):
                    continue
                path_lower = "/".join(candidate_parts).lower()
                valid = False
                if host.endswith("tiktok.com"):
                    valid = bool(re.match(rf"^@{re.escape(identifier)}/video/\d+$", path_lower, re.IGNORECASE))
                elif host.endswith("instagram.com"):
                    valid = len(candidate_parts) >= 3 and candidate_parts[0].lower() == identifier.lower() and candidate_parts[1].lower() in {"reel", "p", "tv"}
                elif host.endswith("facebook.com"):
                    valid = candidate_parts[0].lower() == identifier.lower() and any(part.lower() in {"videos", "reel", "reels"} for part in candidate_parts[1:])
                elif host.endswith("x.com") or host.endswith("twitter.com"):
                    valid = len(candidate_parts) >= 3 and candidate_parts[0].lower() == identifier.lower() and candidate_parts[1].lower() == "status"
                elif host.endswith("vimeo.com"):
                    valid = len(candidate_parts) >= 2 and candidate_parts[0].lower() == identifier.lower() and candidate_parts[-1].isdigit()
                elif host.endswith("youtube.com"):
                    valid = candidate_parts[0].lower() in {"watch", "shorts"}
                elif host.endswith("snapchat.com"):
                    valid = identifier.lower() in path_lower and any(part.lower() in {"spotlight", "story", "stories"} for part in candidate_parts)
                elif "xiaohongshu.com" in host or "xhslink.com" in host:
                    valid = identifier.lower() in path_lower or candidate_parts[0].lower() in {"explore", "discovery", "item"}
                if not valid or url.lower() in seen:
                    continue
                seen.add(url.lower())
                found.append({
                    "title": str(item.get("title") or "Public video"),
                    "url": url,
                    "duration": "—",
                    "id": candidate_parts[-1],
                })
        except Exception as exc:
            self.after(0, self.write_log, f"Universal profile fallback notice: {exc}")
        return found

    def _on_channel_fetch_done(self, channel_title: str, videos: list[dict]) -> None:
        self.channel_videos = videos
        total = len(videos)
        self.channel_info.set(f"Channel: {channel_title} — {total} video{'s' if total != 1 else ''} found")
        self.write_log(f"Channel: {channel_title} — {total} videos found")

        for item in self.channel_tree.get_children():
            self.channel_tree.delete(item)
        self.channel_video_item_ids = {}

        for idx, video in enumerate(videos, start=1):
            title_display = video["title"]
            if len(title_display) > 80:
                title_display = title_display[:77] + "..."
            item_id = self.channel_tree.insert(
                "", "end",
                values=(idx, title_display, video["duration"], "Waiting"),
            )
            self.channel_video_item_ids[idx - 1] = item_id

        self.fetch_channel_btn.configure(state="normal")
        if videos:
            self.ch_download_btn.configure(state="normal")

    def _on_channel_fetch_error(self, message: str) -> None:
        self.channel_info.set(f"Error: {message}")
        self.write_log(f"Channel fetch error: {message}")
        self.fetch_channel_btn.configure(state="normal")
        messagebox.showerror(APP_TITLE, f"Failed to fetch channel:\n{message}")

    def download_channel_videos(self) -> None:
        if not self.ensure_access():
            return
        if not self.channel_videos:
            messagebox.showerror(APP_TITLE, "No videos to download. Fetch a channel first.")
            return
        if not self.ytdlp_available():
            messagebox.showerror(APP_TITLE, "yt-dlp is not available in this build.")
            return

        self.channel_downloading = True
        self.channel_stop_requested = False
        self.channel_completed_count = 0
        self.channel_failed_count = 0

        # Reset all statuses to Waiting
        for idx, item_id in self.channel_video_item_ids.items():
            self.channel_tree.set(item_id, "status", "Waiting")

        batch_size = 5 if "5" in self.channel_batch_size.get() else 3
        total = len(self.channel_videos)

        self._set_busy(True)
        self.fetch_channel_btn.configure(state="disabled")
        self.ch_download_btn.configure(state="disabled")
        self.ch_stop_btn.configure(state="normal")
        self._reset_for_download()
        self.write_log(f"Starting channel download: {total} videos, {batch_size} at a time")
        self.channel_progress_label.configure(text=f"0/{total} complete")

        thread = threading.Thread(
            target=self._download_channel_worker,
            args=(list(self.channel_videos), batch_size),
            daemon=True,
        )
        thread.start()

    def _download_channel_worker(self, videos: list[dict], batch_size: int) -> None:
        total = len(videos)
        completed = 0
        failed = 0

        # Process in batches
        for batch_start in range(0, total, batch_size):
            if self.channel_stop_requested:
                self.after(0, self.write_log, "Channel download stopped by user.")
                break

            batch_end = min(batch_start + batch_size, total)
            batch_indices = list(range(batch_start, batch_end))
            batch_num = (batch_start // batch_size) + 1
            total_batches = (total + batch_size - 1) // batch_size

            self.after(0, self.write_log, f"\n— Batch {batch_num}/{total_batches} —")

            # Mark batch as Downloading
            for idx in batch_indices:
                self.after(0, self._update_channel_item_status, idx, "Downloading")

            # Download batch concurrently
            with concurrent.futures.ThreadPoolExecutor(max_workers=batch_size) as executor:
                future_to_idx = {}
                for idx in batch_indices:
                    if self.channel_stop_requested:
                        break
                    video = videos[idx]
                    future = executor.submit(self._download_single_channel_video, video, idx, total)
                    future_to_idx[future] = idx

                for future in concurrent.futures.as_completed(future_to_idx):
                    idx = future_to_idx[future]
                    try:
                        success = future.result()
                        if success:
                            completed += 1
                            self.after(0, self._update_channel_item_status, idx, "Done")
                        else:
                            failed += 1
                            self.after(0, self._update_channel_item_status, idx, "Failed")
                    except Exception:
                        failed += 1
                        self.after(0, self._update_channel_item_status, idx, "Failed")

                    self.channel_completed_count = completed
                    self.channel_failed_count = failed
                    self.after(
                        0, self.channel_progress_label.configure,
                        {"text": f"{completed + failed}/{total} complete ({failed} failed)"},
                    )
                    self.after(0, self.status.set, f"Channel: {completed + failed}/{total} done")

        self.channel_downloading = False
        self.after(0, self._on_channel_download_done, completed, failed, total)

    def _download_single_channel_video(self, video: dict, idx: int, total: int) -> bool:
        """Download a single video from the channel. Returns True on success."""
        url = video["url"]
        self.after(0, self.write_log, f"[{idx + 1}/{total}] Downloading: {video['title']}")
        try:
            self.perform_download(url, prefix=f"{idx + 1:04d}-")
            self.after(0, self.write_log, f"[{idx + 1}/{total}] Done: {video['title']}")
            self.write_history(url, "done", video["title"])
            return True
        except Exception as exc:
            self.after(0, self.write_log, f"[{idx + 1}/{total}] Failed: {video['title']} — {exc}")
            self.write_history(url, "failed", str(exc))
            return False

    def _update_channel_item_status(self, idx: int, status: str) -> None:
        item_id = self.channel_video_item_ids.get(idx)
        if item_id:
            try:
                self.channel_tree.set(item_id, "status", status)
            except tk.TclError:
                pass

    def stop_channel_download(self) -> None:
        self.channel_stop_requested = True
        self.ch_stop_btn.configure(state="disabled")
        self.status.set("Stopping after current batch...")
        self.write_log("Stop requested — finishing current batch...")

    def _on_channel_download_done(self, completed: int, failed: int, total: int) -> None:
        latest_file = self.find_latest_video_file(Path(self.download_dir.get())) if completed else None
        self.set_clip_source(latest_file)
        self._set_busy(False)
        self.fetch_channel_btn.configure(state="normal")
        self.ch_download_btn.configure(state="normal")
        self.ch_stop_btn.configure(state="disabled")
        self.progress.set(100)

        if self.channel_stop_requested:
            msg = f"Channel download stopped.\nCompleted: {completed}/{total}"
        else:
            msg = f"Channel download complete.\nDownloaded: {completed}/{total}"
        if failed:
            msg += f"\nFailed: {failed}"
        self.status.set(f"Channel done: {completed}/{total}")
        self.write_log(f"\n{msg}")
        self.channel_progress_label.configure(text=f"{completed}/{total} done, {failed} failed")
        messagebox.showinfo(APP_TITLE, msg)


class WatermarkSelector(tk.Toplevel):
    def __init__(self, app: DownloaderApp, video_path: Path, preview_path: Path) -> None:
        super().__init__(app)
        self.app = app
        self.video_path = video_path
        self.preview_image = tk.PhotoImage(file=str(preview_path))
        self.rectangles: list[tuple[int, int, int, int]] = []
        self.current_rectangle_id = None
        self.start_x = 0
        self.start_y = 0

        self.title("Hide Area")
        self.geometry("900x650")
        self.minsize(640, 420)

        outer = ttk.Frame(self, padding=12)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text="Draw over the area you want to blur or cover.").pack(anchor="w", pady=(0, 8))

        canvas_frame = ttk.Frame(outer)
        canvas_frame.pack(fill="both", expand=True)
        canvas_frame.rowconfigure(0, weight=1)
        canvas_frame.columnconfigure(0, weight=1)
        self.canvas = tk.Canvas(canvas_frame, bg="#111827", scrollregion=(0, 0, self.preview_image.width(), self.preview_image.height()))
        self.canvas.grid(row=0, column=0, sticky="nsew")
        y_scroll = ttk.Scrollbar(canvas_frame, orient="vertical", command=self.canvas.yview)
        y_scroll.grid(row=0, column=1, sticky="ns")
        x_scroll = ttk.Scrollbar(canvas_frame, orient="horizontal", command=self.canvas.xview)
        x_scroll.grid(row=1, column=0, sticky="ew")
        self.canvas.configure(yscrollcommand=y_scroll.set, xscrollcommand=x_scroll.set)
        self.canvas.create_image(0, 0, anchor="nw", image=self.preview_image)
        self.canvas.bind("<ButtonPress-1>", self.start_selection)
        self.canvas.bind("<B1-Motion>", self.update_selection)
        self.canvas.bind("<ButtonRelease-1>", self.finish_selection)

        controls = ttk.Frame(outer)
        controls.pack(fill="x", pady=(10, 0))
        self.count_label = ttk.Label(controls, text="Areas: 0")
        self.count_label.pack(side="left")
        ttk.Button(controls, text="Undo", command=self.undo_area).pack(side="left", padx=(8, 0))
        ttk.Button(controls, text="Blur Export", command=lambda: self.export("blur")).pack(side="right")
        ttk.Button(controls, text="Cover Export", command=lambda: self.export("cover")).pack(side="right", padx=(0, 8))
        ttk.Button(controls, text="Cancel", command=self.destroy).pack(side="right", padx=(0, 8))

    def canvas_point(self, event) -> tuple[int, int]:
        x = int(self.canvas.canvasx(event.x))
        y = int(self.canvas.canvasy(event.y))
        x = max(0, min(x, self.preview_image.width()))
        y = max(0, min(y, self.preview_image.height()))
        return x, y

    def start_selection(self, event) -> None:
        self.start_x, self.start_y = self.canvas_point(event)
        self.current_rectangle_id = self.canvas.create_rectangle(self.start_x, self.start_y, self.start_x, self.start_y, outline="#ef4444", width=2)

    def update_selection(self, event) -> None:
        if self.current_rectangle_id is None:
            return
        x, y = self.canvas_point(event)
        self.canvas.coords(self.current_rectangle_id, self.start_x, self.start_y, x, y)

    def finish_selection(self, event) -> None:
        if self.current_rectangle_id is None:
            return
        end_x, end_y = self.canvas_point(event)
        x1, x2 = sorted((self.start_x, end_x))
        y1, y2 = sorted((self.start_y, end_y))
        width = x2 - x1
        height = y2 - y1
        if width < 8 or height < 8:
            self.canvas.delete(self.current_rectangle_id)
        else:
            self.rectangles.append((x1, y1, width, height))
            self.count_label.configure(text=f"Areas: {len(self.rectangles)}")
        self.current_rectangle_id = None

    def undo_area(self) -> None:
        items = list(self.canvas.find_all())
        rectangle_items = [item for item in items if self.canvas.type(item) == "rectangle"]
        if rectangle_items:
            self.canvas.delete(rectangle_items[-1])
        if self.rectangles:
            self.rectangles.pop()
        self.count_label.configure(text=f"Areas: {len(self.rectangles)}")

    def export(self, mode: str) -> None:
        if not self.rectangles:
            messagebox.showinfo(APP_TITLE, "Draw at least one area first.")
            return
        rectangles = list(self.rectangles)
        self.destroy()
        self.app.export_hidden_area_video(self.video_path, rectangles, mode)


class GuiYtdlpLogger:
    def __init__(self, app: DownloaderApp) -> None:
        self.app = app

    def debug(self, message: str) -> None:
        if not message.startswith("[debug]"):
            self.app.after(0, self.app.handle_output, message)

    def info(self, message: str) -> None:
        self.app.after(0, self.app.handle_output, message)

    def warning(self, message: str) -> None:
        self.app.after(0, self.app.handle_output, f"Warning: {message}")

    def error(self, message: str) -> None:
        self.app.after(0, self.app.handle_output, f"Error: {message}")


if __name__ == "__main__":
    app = DownloaderApp()
    app.mainloop()
