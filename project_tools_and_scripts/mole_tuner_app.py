import customtkinter as ctk
from PIL import Image
import cv2
import numpy as np
from tkinter import filedialog
import json
import tkinter.messagebox as messagebox
import threading
from mole_detector import MoleDetector

ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("blue")

class MoleTunerApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("Mole Tuner - Algorithm Visualizer")
        self.geometry("1200x800")
        
        self.detector = MoleDetector()
        
        # State
        self.current_image = None
        self.intermediates = {}
        self.debug_frame = None
        
        self.steps = [
            "1. Originale",
            "2. Maschera Peli",
            "3. Senza Peli",
            "3.5 Luce Avanzata",
            "4. Luce / L Channel",
            "5. Binarizzazione",
            "6. Risultato Finale"
        ]
        self.current_step_idx = 0
        
        # Layout
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # Sidebar
        self.sidebar_frame = ctk.CTkFrame(self, width=300, corner_radius=0)
        self.sidebar_frame.grid(row=0, column=0, sticky="nsew")
        self.sidebar_frame.grid_rowconfigure(6, weight=1)

        self.logo_label = ctk.CTkLabel(self.sidebar_frame, text="Mole Tuner", font=ctk.CTkFont(size=20, weight="bold"))
        self.logo_label.grid(row=0, column=0, padx=20, pady=(20, 10))

        self.load_btn = ctk.CTkButton(self.sidebar_frame, text="Carica Immagine", command=self.load_image)
        self.load_btn.grid(row=1, column=0, padx=20, pady=10)
        
        self.overview_btn = ctk.CTkButton(self.sidebar_frame, text="Vista Panoramica", command=self.open_overview)
        self.overview_btn.grid(row=2, column=0, padx=20, pady=10)
        
        self.save_btn = ctk.CTkButton(self.sidebar_frame, text="Salva Parametri", command=self.save_params)
        self.save_btn.grid(row=3, column=0, padx=20, pady=10)
        
        self.load_param_btn = ctk.CTkButton(self.sidebar_frame, text="Carica Parametri", command=self.load_params)
        self.load_param_btn.grid(row=4, column=0, padx=20, pady=10)

        # Controlli dinamici
        self.controls_scroll = ctk.CTkScrollableFrame(self.sidebar_frame, label_text="Parametri Step")
        self.controls_scroll.grid(row=6, column=0, padx=10, pady=10, sticky="nsew")
        
        self.control_widgets = []

        # Main Frame
        self.main_frame = ctk.CTkFrame(self)
        self.main_frame.grid(row=0, column=1, sticky="nsew", padx=10, pady=10)
        self.main_frame.grid_rowconfigure(1, weight=1)
        self.main_frame.grid_columnconfigure(1, weight=1)

        # Header nav
        self.nav_frame = ctk.CTkFrame(self.main_frame, fg_color="transparent")
        self.nav_frame.grid(row=0, column=0, columnspan=3, pady=10, sticky="ew")
        self.nav_frame.grid_columnconfigure(1, weight=1)

        self.prev_btn = ctk.CTkButton(self.nav_frame, text="< Precedente", command=self.prev_step, width=100)
        self.prev_btn.grid(row=0, column=0, padx=20)

        self.step_label = ctk.CTkLabel(self.nav_frame, text=self.steps[self.current_step_idx], font=ctk.CTkFont(size=24, weight="bold"))
        self.step_label.grid(row=0, column=1)

        self.next_btn = ctk.CTkButton(self.nav_frame, text="Successivo >", command=self.next_step, width=100)
        self.next_btn.grid(row=0, column=2, padx=20)

        # Image display
        self.overview_window = None
        self.after_id = None
        self.is_processing = False
        self.show_recent_images()

    def show_recent_images(self):
        if hasattr(self, 'image_label') and self.image_label.winfo_exists():
            self.image_label.destroy()
            
        self.gallery_frame = ctk.CTkScrollableFrame(self.main_frame, label_text="Scegli un'immagine di esempio (Nei esempio)")
        self.gallery_frame.grid(row=1, column=0, columnspan=3, sticky="nsew", padx=10, pady=10)
        
        import os, glob
        folder = r"C:\Users\Maffione Gabriele\Progetti e lavori\Programmazione\Chrono Map Scanner\Nei esempio"
        images = glob.glob(os.path.join(folder, "*.*"))
        
        col, row = 0, 0
        for img_path in images:
            if not img_path.lower().endswith(('.png', '.jpg', '.jpeg')): continue
            try:
                pil_img = Image.open(img_path)
                pil_img.thumbnail((150, 150))
                ctk_img = ctk.CTkImage(light_image=pil_img, dark_image=pil_img, size=pil_img.size)
                
                btn = ctk.CTkButton(self.gallery_frame, image=ctk_img, text="", 
                                    command=lambda p=img_path: self.load_specific_image(p))
                btn.grid(row=row, column=col, padx=10, pady=10)
                
                lbl = ctk.CTkLabel(self.gallery_frame, text=os.path.basename(img_path))
                lbl.grid(row=row+1, column=col, padx=10, pady=(0, 10))
                
                col += 1
                if col > 3:
                    col = 0
                    row += 2
            except:
                pass

    def load_specific_image(self, filepath):
        self.current_image = cv2.imread(filepath)
        if self.current_image is not None:
            if hasattr(self, 'gallery_frame') and self.gallery_frame.winfo_exists():
                self.gallery_frame.destroy()
            self.image_label = ctk.CTkLabel(self.main_frame, text="")
            self.image_label.grid(row=1, column=0, columnspan=3, sticky="nsew", padx=10, pady=10)
            
            self.process_image()
            self.update_view()

    def load_image(self):
        filepath = filedialog.askopenfilename(filetypes=[("Images", "*.jpg *.jpeg *.png")])
        if filepath:
            self.load_specific_image(filepath)

    def process_image(self):
        if self.current_image is None: return
        self.is_processing = True
        self.process_image_bg(sync=True)

    def process_image_bg(self, sync=False):
        try:
            _, debug_frame, intermediates = self.detector.process_frame(self.current_image, return_intermediates=True)
            self.debug_frame = debug_frame
            self.intermediates = intermediates
            
            self.step_images = [
                self.current_image,
                intermediates.get('hair_mask', np.zeros_like(self.current_image)),
                intermediates.get('clean_frame', np.zeros_like(self.current_image)),
                intermediates.get('l_channel_clahe', np.zeros_like(self.current_image)), # Per lo step 3.5
                intermediates.get('l_channel_clahe', np.zeros_like(self.current_image)), # Per lo step 4
                intermediates.get('thresh', np.zeros_like(self.current_image)),
                self.debug_frame
            ]
            
            if not sync:
                self.after(0, self.update_view_from_bg)
        finally:
            self.is_processing = False
            
    def update_view_from_bg(self):
        if self.overview_window is not None and self.overview_window.winfo_exists():
            self.update_overview()
        self.update_view(rebuild_controls=False)

    def cv2_to_ctk(self, cv_img, max_size=(800, 600)):
        if len(cv_img.shape) == 2:
            cv_img = cv2.cvtColor(cv_img, cv2.COLOR_GRAY2RGB)
        else:
            cv_img = cv2.cvtColor(cv_img, cv2.COLOR_BGR2RGB)
            
        pil_img = Image.fromarray(cv_img)
        pil_img.thumbnail(max_size, Image.Resampling.LANCZOS)
        return ctk.CTkImage(light_image=pil_img, dark_image=pil_img, size=pil_img.size)

    def update_view(self, rebuild_controls=True):
        if self.current_image is None:
            return
            
        self.step_label.configure(text=self.steps[self.current_step_idx])
        
        img = self.step_images[self.current_step_idx]
        ctk_img = self.cv2_to_ctk(img, max_size=(800, 600))
        self.image_label.configure(image=ctk_img, text="")
        
        if rebuild_controls:
            self.build_controls()

    def save_params(self):
        filepath = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JSON files", "*.json")])
        if filepath:
            params = {}
            for k, v in vars(self.detector).items():
                if isinstance(v, (int, float, bool)):
                    params[k] = v
            try:
                with open(filepath, 'w') as f:
                    json.dump(params, f, indent=4)
                messagebox.showinfo("Successo", "Parametri salvati correttamente!")
            except Exception as e:
                messagebox.showerror("Errore", f"Errore durante il salvataggio: {e}")

    def load_params(self):
        filepath = filedialog.askopenfilename(filetypes=[("JSON files", "*.json")])
        if filepath:
            try:
                with open(filepath, 'r') as f:
                    params = json.load(f)
                
                missing_keys = []
                for k, v in params.items():
                    if hasattr(self.detector, k):
                        setattr(self.detector, k, v)
                    else:
                        missing_keys.append(k)
                        
                if missing_keys:
                    messagebox.showwarning("Avviso", f"Alcuni parametri del file salvato non sono più supportati e sono stati ignorati:\n{', '.join(missing_keys)}")
                else:
                    messagebox.showinfo("Successo", "Parametri caricati correttamente!")
                
                if self.current_image is not None:
                    self.process_image()
                    self.update_view()
            except Exception as e:
                messagebox.showerror("Errore", f"Errore durante il caricamento: {e}")

    def prev_step(self):
        if self.current_step_idx > 0:
            self.current_step_idx -= 1
            self.update_view()

    def next_step(self):
        if self.current_step_idx < len(self.steps) - 1:
            self.current_step_idx += 1
            self.update_view()

    def on_param_change(self, *args):
        if self.current_image is not None:
            if self.after_id is not None:
                self.after_cancel(self.after_id)
            self.after_id = self.after(150, self.trigger_background_processing)
            
    def trigger_background_processing(self):
        if self.is_processing:
            self.after_id = self.after(50, self.trigger_background_processing)
            return
        self.is_processing = True
        threading.Thread(target=self.process_image_bg, daemon=True).start()

    def add_checkbox(self, name, attr):
        val = getattr(self.detector, attr)
        var = ctk.BooleanVar(value=val)
        
        def update():
            setattr(self.detector, attr, var.get())
            self.on_param_change()
            
        chk = ctk.CTkCheckBox(self.controls_scroll, text=name, variable=var, command=update)
        chk.pack(pady=(10, 0), anchor="w")
        self.control_widgets.append(chk)

    def add_slider(self, name, attr, min_val, max_val, is_int=True):
        lbl = ctk.CTkLabel(self.controls_scroll, text=f"{name}: {getattr(self.detector, attr)}")
        lbl.pack(pady=(10, 0), anchor="w")
        
        val = getattr(self.detector, attr)
        
        def update(v):
            if is_int:
                v = int(float(v))
            else:
                v = float(v)
            setattr(self.detector, attr, v)
            if is_int:
                lbl.configure(text=f"{name}: {v}")
            else:
                lbl.configure(text=f"{name}: {v:.4f}")
            self.on_param_change()
            
        slider = ctk.CTkSlider(self.controls_scroll, from_=min_val, to=max_val, command=update)
        slider.set(val)
        slider.pack(pady=(0, 10), fill="x")
        self.control_widgets.extend([lbl, slider])

    def reset_current_step(self):
        default_det = MoleDetector()
        idx = self.current_step_idx
        
        if idx == 1 or idx == 2:
            self.detector.use_hair_removal = default_det.use_hair_removal
            self.detector.hair_kernel_size = default_det.hair_kernel_size
            self.detector.hair_thresh = default_det.hair_thresh
            self.detector.inpaint_radius = default_det.inpaint_radius
        elif idx == 3:
            self.detector.use_color_invariance = default_det.use_color_invariance
            self.detector.invariance_angle = default_det.invariance_angle
            self.detector.use_bg_subtraction = default_det.use_bg_subtraction
            self.detector.bg_kernel_size = default_det.bg_kernel_size
            self.detector.use_homomorphic = default_det.use_homomorphic
            self.detector.homo_gamma_l = default_det.homo_gamma_l
            self.detector.homo_gamma_h = default_det.homo_gamma_h
            self.detector.homo_cutoff = default_det.homo_cutoff
            self.detector.use_retinex = default_det.use_retinex
            self.detector.retinex_sigma_low = default_det.retinex_sigma_low
            self.detector.retinex_sigma_high = default_det.retinex_sigma_high
        elif idx == 4:
            self.detector.use_light_invariance = default_det.use_light_invariance
            self.detector.use_clahe = default_det.use_clahe
            self.detector.clahe_clip_limit = default_det.clahe_clip_limit
            self.detector.clahe_grid_size = default_det.clahe_grid_size
            self.detector.use_bilateral_filter = default_det.use_bilateral_filter
            self.detector.bilateral_d = default_det.bilateral_d
            self.detector.bilateral_sigma_color = default_det.bilateral_sigma_color
            self.detector.use_blur = default_det.use_blur
            self.detector.blur_size = default_det.blur_size
        elif idx == 5:
            self.detector.adaptive_block_size = default_det.adaptive_block_size
            self.detector.adaptive_c = default_det.adaptive_c
            self.detector.use_morphology = default_det.use_morphology
            self.detector.morph_open_size = default_det.morph_open_size
            self.detector.morph_close_size = default_det.morph_close_size
            self.detector.use_mask_blur = default_det.use_mask_blur
            self.detector.mask_blur_size = default_det.mask_blur_size
        elif idx == 6:
            self.detector.use_fill_ratio = default_det.use_fill_ratio
            self.detector.min_area_ratio = default_det.min_area_ratio
            self.detector.min_fill_ratio = default_det.min_fill_ratio
            
        self.build_controls()
        self.on_param_change()

    def build_controls(self):
        for w in self.control_widgets:
            w.destroy()
        self.control_widgets.clear()
        
        idx = self.current_step_idx
        if idx > 0:
            btn_reset = ctk.CTkButton(self.controls_scroll, text="Ripristina Default Step", fg_color="#C0392B", hover_color="#922B21", command=self.reset_current_step)
            btn_reset.pack(pady=(5, 15), fill="x")
            self.control_widgets.append(btn_reset)
        
        if idx == 1 or idx == 2: # Hair removal
            self.add_checkbox("Abilita Rimozione Peli", "use_hair_removal")
            self.add_slider("Dimensione Kernel Peli", "hair_kernel_size", 3, 55, True)
            self.add_slider("Soglia Peli", "hair_thresh", 1, 50, True)
            self.add_slider("Raggio Inpainting", "inpaint_radius", 1, 15, True)
        elif idx == 3: # 3.5 Luce Avanzata
            self.add_checkbox("1D Invariance (Sostituisce L)", "use_color_invariance")
            self.add_slider("Angolo Invarianza 1D", "invariance_angle", 0, 180, False)
            
            self.add_checkbox("Sottrazione Sfondo (Rolling Ball)", "use_bg_subtraction")
            self.add_slider("Dimensione Kernel Sfondo", "bg_kernel_size", 11, 301, True)
            
            self.add_checkbox("Homomorphic Filter (FFT)", "use_homomorphic")
            self.add_slider("Homo Gamma Low", "homo_gamma_l", 0.0, 1.0, False)
            self.add_slider("Homo Gamma High", "homo_gamma_h", 1.0, 5.0, False)
            self.add_slider("Homo Cutoff Freq", "homo_cutoff", 1, 100, True)
            
            self.add_checkbox("Multi-Scale Retinex", "use_retinex")
            self.add_slider("Retinex Sigma Low", "retinex_sigma_low", 1, 50, True)
            self.add_slider("Retinex Sigma High", "retinex_sigma_high", 50, 200, True)
            
        elif idx == 4: # L channel
            self.add_checkbox("Usa Invarianza Luce (Canale L)", "use_light_invariance")
            self.add_checkbox("Usa Estrazione Contrasto (CLAHE)", "use_clahe")
            self.add_slider("CLAHE Clip Limit", "clahe_clip_limit", 0.5, 5.0, False)
            self.add_slider("CLAHE Grid Size", "clahe_grid_size", 1, 16, True)
            self.add_checkbox("Usa Bilateral Filter", "use_bilateral_filter")
            self.add_slider("Bilateral Diameter", "bilateral_d", 1, 31, True)
            self.add_slider("Bilateral Sigma Color", "bilateral_sigma_color", 10, 150, True)
            self.add_checkbox("Usa Sfocatura (Blur)", "use_blur")
            self.add_slider("Dimensione Blur", "blur_size", 1, 101, True)
        elif idx == 5: # Threshold
            self.add_slider("Adaptive Block Size", "adaptive_block_size", 3, 999, True)
            self.add_slider("Adaptive C", "adaptive_c", -50, 50, True)
            self.add_checkbox("Abilita Pulizia Morfologica", "use_morphology")
            self.add_slider("Morph Open Kernel", "morph_open_size", 1, 31, True)
            self.add_slider("Morph Close Kernel", "morph_close_size", 1, 51, True)
            self.add_checkbox("Abilita Edge Smoothing", "use_mask_blur")
            self.add_slider("Dimensione Mask Blur", "mask_blur_size", 1, 21, True)
        elif idx == 6: # Final / Contours
            self.add_checkbox("Abilita Filtro Forma (Fill Ratio)", "use_fill_ratio")
            self.add_slider("Min Area Ratio", "min_area_ratio", 0.00001, 0.01, False)
            self.add_slider("Min Fill Ratio", "min_fill_ratio", 0.1, 0.9, False)

    def open_overview(self):
        if self.overview_window is None or not self.overview_window.winfo_exists():
            self.overview_window = ctk.CTkToplevel(self)
            self.overview_window.title("Overview Completa")
            self.overview_window.geometry("1400x900")
            
            self.overview_labels = []
            for i in range(2):
                self.overview_window.grid_rowconfigure(i, weight=1)
            for j in range(3):
                self.overview_window.grid_columnconfigure(j, weight=1)
                
            for i, step_name in enumerate(self.steps):
                row = i // 3
                col = i % 3
                
                frame = ctk.CTkFrame(self.overview_window)
                frame.grid(row=row, column=col, padx=5, pady=5, sticky="nsew")
                
                lbl_title = ctk.CTkLabel(frame, text=step_name, font=ctk.CTkFont(weight="bold"))
                lbl_title.pack(pady=5)
                
                lbl_img = ctk.CTkLabel(frame, text="")
                lbl_img.pack(expand=True, fill="both", padx=5, pady=5)
                
                self.overview_labels.append(lbl_img)
                
            self.update_overview()
        else:
            self.overview_window.focus()

    def update_overview(self):
        if self.current_image is None or not hasattr(self, 'overview_labels'):
            return
            
        for i, img in enumerate(self.step_images):
            ctk_img = self.cv2_to_ctk(img, max_size=(400, 300))
            self.overview_labels[i].configure(image=ctk_img)

if __name__ == "__main__":
    app = MoleTunerApp()
    app.mainloop()
