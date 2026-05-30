import tkinter as tk
import threading
import time
import json
import urllib.request
import subprocess

# ── 설정 ──────────────────────────────────────────────
API_KEY = "YOUR_OPENAI_API_KEY"   # ← 여기에 팀원 API 키 붙여넣기

TARGET_PROCESSES = [
    "KakaoTalk.exe",
    "Instagram.exe",
    "Discord.exe",
    "discord.exe",
]

CHECK_INTERVAL = 2

# ── 색상 ──────────────────────────────────────────────
BG        = "#0D0D18"
SURFACE   = "#16162A"
BORDER    = "#2A2A45"
PURPLE    = "#7B5CF0"
PURPLE_LT = "#A07EF5"
TEXT      = "#FFFFFF"
TEXT_DIM  = "#6B6B9A"
TEXT_MID  = "#A0A0C0"
GREEN     = "#50D282"
RED_DIM   = "#C04040"

TONES = [
    ("😄", "Friend",    "friend",    "Casual"),
    ("🎓", "Professor", "professor", "Formal"),
    ("🤝", "Teammate",  "teammate",  "Collab"),
    ("💼", "Business",  "business",  "Pro"),
]

TONE_PROMPTS = {
    "friend":    "casual and friendly, like texting a close friend.",
    "professor": "polite and respectful, like emailing a professor.",
    "teammate":  "collaborative and approachable, like messaging a teammate.",
    "business":  "professional and formal, suitable for business communication.",
}

SAMPLES = {
    "friend":    ["Hey, any chance you could help me out? 😅", "Bro I could really use a hand lol", "Yo do you think you could cover this for me?"],
    "professor": ["I was wondering if it would be possible to get assistance.", "I hope this finds you well. I wanted to inquire about...", "Would it be alright if I asked for your guidance?"],
    "teammate":  ["Hey, could we look into this together?", "Just checking — can anyone help out with this?", "Would love some input if you have a moment!"],
    "business":  ["I would like to respectfully request your assistance.", "Please allow me to inquire about the possibility of...", "I am reaching out to discuss this matter."],
}

# ── 프로세스 감지 ──────────────────────────────────────
def is_target_running():
    try:
        result = subprocess.run(
            ["tasklist", "/fo", "csv", "/nh"],
            capture_output=True, text=True,
            creationflags=subprocess.CREATE_NO_WINDOW
        )
        for line in result.stdout.splitlines():
            for target in TARGET_PROCESSES:
                if target.lower() in line.lower():
                    return True
        return False
    except Exception:
        return False

# ── OpenAI API ─────────────────────────────────────────
def call_openai(text, tone):
    if API_KEY == "YOUR_OPENAI_API_KEY":
        return SAMPLES.get(tone, SAMPLES["friend"])

    prompt = f"""You are VibeType. Convert this Korean text into 3 natural English expressions.
Input: "{text}"
Tone: {TONE_PROMPTS[tone]}
Respond ONLY with JSON: {{"results":["expr1","expr2","expr3"]}}"""

    payload = json.dumps({
        "model": "gpt-4o-mini",
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 300,
        "temperature": 0.8
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions",
        data=payload,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        content = data["choices"][0]["message"]["content"].strip()
        return json.loads(content)["results"]


# ── UI ────────────────────────────────────────────────
class VibeTypeApp:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("VibeType")
        self.root.configure(bg=BG)
        self.root.resizable(False, False)
        self.root.attributes("-topmost", True)
        self.root.overrideredirect(False)

        self.selected_tone = "professor"
        self.is_visible = False
        self.is_generating = False
        self.was_running = False

        self._build_ui()

        # 처음엔 숨김
        self.root.withdraw()

        self._start_watcher()
        self.root.protocol("WM_DELETE_WINDOW", self._hide)

    def _build_ui(self):
        # 타이틀바
        bar = tk.Frame(self.root, bg=SURFACE, height=44)
        bar.pack(fill="x")
        bar.pack_propagate(False)

        tk.Label(bar, text="🌐  VibeType", bg=SURFACE, fg=TEXT,
                 font=("Segoe UI", 12, "bold")).pack(side="left", padx=14)
        tk.Label(bar, text="✕", bg=SURFACE, fg=TEXT_DIM,
                 font=("Segoe UI", 13), cursor="hand2").pack(
            side="right", padx=14).bind if False else None

        x_btn = tk.Label(bar, text="✕", bg=SURFACE, fg=TEXT_DIM,
                         font=("Segoe UI", 13), cursor="hand2")
        x_btn.pack(side="right", padx=14)
        x_btn.bind("<Button-1>", lambda e: self._hide())

        tk.Frame(self.root, bg=BORDER, height=1).pack(fill="x")

        body = tk.Frame(self.root, bg=BG)
        body.pack(fill="both", expand=True, padx=14, pady=12)

        # 입력
        tk.Label(body, text="YOUR MESSAGE", bg=BG, fg=TEXT_DIM,
                 font=("Segoe UI", 8)).pack(anchor="w", pady=(0, 4))

        inp_border = tk.Frame(body, bg=BORDER)
        inp_border.pack(fill="x")
        inp_inner = tk.Frame(inp_border, bg=SURFACE)
        inp_inner.pack(fill="x", padx=1, pady=1)

        self.text_input = tk.Text(inp_inner, bg=SURFACE, fg=TEXT_DIM,
                                   insertbackground=PURPLE_LT,
                                   font=("Segoe UI", 11), relief="flat",
                                   height=3, wrap="word", padx=10, pady=8)
        self.text_input.pack(fill="x")
        self.text_input.insert("1.0", "한국어로 입력하세요...")
        self.text_input.bind("<FocusIn>", self._focus_in)
        self.text_input.bind("<FocusOut>", self._focus_out)

        # 톤 선택
        tk.Label(body, text="TONE", bg=BG, fg=TEXT_DIM,
                 font=("Segoe UI", 8)).pack(anchor="w", pady=(12, 4))

        tone_frame = tk.Frame(body, bg=BG)
        tone_frame.pack(fill="x")
        self.tone_btns = {}

        for i, (icon, label, key, sub) in enumerate(TONES):
            col = i % 2
            row = i // 2
            tone_frame.columnconfigure(col, weight=1)

            outer = tk.Frame(tone_frame, bg=BORDER, cursor="hand2")
            outer.grid(row=row, column=col,
                       padx=(0, 6) if col == 0 else (0, 0),
                       pady=(0, 6), sticky="ew")

            inner = tk.Frame(outer, bg=SURFACE, padx=8, pady=8, cursor="hand2")
            inner.pack(fill="both", padx=1, pady=1)

            lbl_icon = tk.Label(inner, text=icon, bg=SURFACE, font=("Segoe UI Emoji", 16))
            lbl_icon.pack()
            lbl_name = tk.Label(inner, text=label, bg=SURFACE, fg=TEXT_MID,
                                font=("Segoe UI", 9))
            lbl_name.pack()

            self.tone_btns[key] = (outer, inner, lbl_name)
            for w in [outer, inner, lbl_icon, lbl_name]:
                w.bind("<Button-1>", lambda e, k=key: self._select_tone(k))

        self._select_tone("professor")

        # 버튼
        self.gen_btn = tk.Button(
            body, text="✦  Translate",
            bg=PURPLE, fg=TEXT, font=("Segoe UI", 10, "bold"),
            relief="flat", bd=0, cursor="hand2",
            activebackground=PURPLE_LT, activeforeground=TEXT,
            command=self._generate, pady=10
        )
        self.gen_btn.pack(fill="x", pady=(10, 0))

        self.status_lbl = tk.Label(body, text="", bg=BG, fg=TEXT_DIM,
                                    font=("Segoe UI", 8))
        self.status_lbl.pack(pady=(6, 0))

        # 스크롤 가능한 결과 영역
        canvas_outer = tk.Frame(body, bg=BG)
        canvas_outer.pack(fill="both", expand=True, pady=(4, 0))

        self.canvas = tk.Canvas(canvas_outer, bg=BG, highlightthickness=0, height=220)
        scrollbar = tk.Scrollbar(canvas_outer, orient="vertical", command=self.canvas.yview)
        self.canvas.configure(yscrollcommand=scrollbar.set)

        scrollbar.pack(side="right", fill="y")
        self.canvas.pack(side="left", fill="both", expand=True)

        self.results_frame = tk.Frame(self.canvas, bg=BG)
        self.canvas_window = self.canvas.create_window((0, 0), window=self.results_frame, anchor="nw")

        def on_frame_configure(e):
            self.canvas.configure(scrollregion=self.canvas.bbox("all"))
        self.results_frame.bind("<Configure>", on_frame_configure)

        def on_canvas_configure(e):
            self.canvas.itemconfig(self.canvas_window, width=e.width)
        self.canvas.bind("<Configure>", on_canvas_configure)

        # 마우스 휠 스크롤
        def on_mousewheel(e):
            self.canvas.yview_scroll(int(-1*(e.delta/120)), "units")
        self.canvas.bind_all("<MouseWheel>", on_mousewheel)

    def _focus_in(self, e):
        if self.text_input.get("1.0", "end-1c") == "한국어로 입력하세요...":
            self.text_input.delete("1.0", "end")
            self.text_input.configure(fg=TEXT)

    def _focus_out(self, e):
        if not self.text_input.get("1.0", "end-1c").strip():
            self.text_input.insert("1.0", "한국어로 입력하세요...")
            self.text_input.configure(fg=TEXT_DIM)

    def _select_tone(self, key):
        self.selected_tone = key
        for k, (outer, inner, lbl) in self.tone_btns.items():
            if k == key:
                outer.configure(bg=PURPLE)
                inner.configure(bg="#1E1840")
                lbl.configure(fg=PURPLE_LT)
            else:
                outer.configure(bg=BORDER)
                inner.configure(bg=SURFACE)
                lbl.configure(fg=TEXT_MID)

    def _generate(self):
        if self.is_generating:
            return
        text = self.text_input.get("1.0", "end-1c").strip()
        if not text or text == "한국어로 입력하세요...":
            return

        self.is_generating = True
        self.gen_btn.configure(state="disabled", text="번역 중...")
        self.status_lbl.configure(text="⟳ Finding the right vibe...")
        for w in self.results_frame.winfo_children():
            w.destroy()

        def worker():
            try:
                results = call_openai(text, self.selected_tone)
                self.root.after(0, lambda: self._show_results(results))
            except Exception as ex:
                self.root.after(0, lambda: self.status_lbl.configure(
                    text=f"오류: {str(ex)[:50]}", fg=RED_DIM))
            finally:
                self.root.after(0, self._done)

        threading.Thread(target=worker, daemon=True).start()

    def _done(self):
        self.is_generating = False
        self.gen_btn.configure(state="normal", text="✦  Translate")
        self.status_lbl.configure(text="")

    def _show_results(self, results):
        for w in self.results_frame.winfo_children():
            w.destroy()

        labels = ["A", "B", "C"]
        for i, (label, txt) in enumerate(zip(labels, results)):
            outer = tk.Frame(self.results_frame, bg=BORDER, cursor="hand2")
            outer.pack(fill="x", pady=(0, 6))
            inner = tk.Frame(outer, bg=SURFACE, padx=10, pady=8, cursor="hand2")
            inner.pack(fill="x", padx=1, pady=1)

            top = tk.Frame(inner, bg=SURFACE)
            top.pack(fill="x")
            tk.Label(top, text=f"Option {label}", bg=SURFACE, fg=TEXT_DIM,
                     font=("Segoe UI", 8)).pack(side="left")
            tk.Label(top, text="⧉", bg=SURFACE, fg=TEXT_DIM,
                     font=("Segoe UI", 8)).pack(side="right")

            tk.Label(inner, text=txt, bg=SURFACE, fg=TEXT,
                     font=("Segoe UI", 10), wraplength=280,
                     justify="left", anchor="w").pack(fill="x", pady=(4, 0))

            hint = tk.Label(inner, text="클릭하면 복사", bg=SURFACE,
                            fg=TEXT_DIM, font=("Segoe UI", 8))
            hint.pack(anchor="w", pady=(4, 0))

            def on_click(t=txt, o=outer, h=hint):
                self.root.clipboard_clear()
                self.root.clipboard_append(t)
                o.configure(bg=GREEN)
                h.configure(text="✓ 복사됐어요! Ctrl+V로 붙여넣기", fg=GREEN)
                self.root.after(2500, lambda: (
                    o.configure(bg=BORDER),
                    h.configure(text="클릭하면 복사", fg=TEXT_DIM)
                ))

            for w in [outer, inner] + list(inner.winfo_children()):
                w.bind("<Button-1>", lambda e, fn=on_click: fn())

        # 창 크기 자동 조정
        self.root.update_idletasks()

    def _show(self):
        if not self.is_visible:
            self.root.deiconify()
            self.root.update_idletasks()
            w, h = 320, 640
            sw = self.root.winfo_screenwidth()
            sh = self.root.winfo_screenheight()
            x = sw - w - 20
            y = sh - h - 60
            self.root.geometry(f"{w}x{h}+{x}+{y}")
            self.is_visible = True

    def _hide(self):
        if self.is_visible:
            self.root.withdraw()
            self.is_visible = False

    def _start_watcher(self):
        def watch():
            while True:
                now = is_target_running()
                if now and not self.was_running:
                    self.root.after(0, self._show)
                elif not now and self.was_running:
                    self.root.after(0, self._hide)
                self.was_running = now
                time.sleep(CHECK_INTERVAL)

        threading.Thread(target=watch, daemon=True).start()

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    app = VibeTypeApp()
    app.run()
