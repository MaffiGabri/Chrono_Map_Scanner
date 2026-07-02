import cv2
import numpy as np

class MoleDetector:
    def __init__(self, 
                 min_area_ratio=0.0001, max_area_ratio=0.4, margin_ratio=0.1, center_tolerance_ratio=0.3, min_fill_ratio=0.4,
                 hair_kernel_size=15, hair_thresh=20, inpaint_radius=5,
                 blur_size=21, adaptive_block_size=99, adaptive_c=15,
                 morph_open_size=5, morph_close_size=15,
                 clahe_clip_limit=2.0, clahe_grid_size=8, mask_blur_size=5,
                 homo_gamma_l=0.5, homo_gamma_h=2.0, homo_cutoff=30.0, homo_c=1.0,
                 retinex_sigma_low=15.0, retinex_sigma_high=80.0,
                 bg_kernel_size=151, invariance_angle=45.0,
                 bilateral_d=9, bilateral_sigma_color=75.0, bilateral_sigma_space=75.0):
        """
        Parametri esposti per poter essere modificati live dalla GUI.
        """
        self.min_area_ratio = min_area_ratio
        self.max_area_ratio = max_area_ratio
        self.margin_ratio = margin_ratio
        self.center_tolerance_ratio = center_tolerance_ratio
        self.min_fill_ratio = min_fill_ratio
        
        # Nuovi parametri dinamici
        self.hair_kernel_size = hair_kernel_size
        self.hair_thresh = hair_thresh
        self.inpaint_radius = inpaint_radius
        self.blur_size = blur_size
        self.adaptive_block_size = adaptive_block_size
        self.adaptive_c = adaptive_c
        self.morph_open_size = morph_open_size
        self.morph_close_size = morph_close_size
        self.clahe_clip_limit = clahe_clip_limit
        self.clahe_grid_size = clahe_grid_size
        self.mask_blur_size = mask_blur_size
        
        self.homo_gamma_l = homo_gamma_l
        self.homo_gamma_h = homo_gamma_h
        self.homo_cutoff = homo_cutoff
        self.homo_c = homo_c
        self.retinex_sigma_low = retinex_sigma_low
        self.retinex_sigma_high = retinex_sigma_high
        self.bg_kernel_size = bg_kernel_size
        self.invariance_angle = invariance_angle
        self.bilateral_d = bilateral_d
        self.bilateral_sigma_color = bilateral_sigma_color
        self.bilateral_sigma_space = bilateral_sigma_space
        
        # Nuovi flag per abilitare o disabilitare interi step
        self.use_hair_removal = True
        self.use_light_invariance = True
        self.use_blur = True
        self.use_morphology = True
        self.use_clahe = False
        self.use_mask_blur = False
        self.use_fill_ratio = True
        self.use_homomorphic = False
        self.use_retinex = False
        self.use_bg_subtraction = False
        self.use_color_invariance = False
        self.use_bilateral_filter = False

    def remove_hairs(self, frame):
        """
        Applica l'algoritmo DullRazor per rimuovere i peli scuri dall'immagine.
        """
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # 1. Filtro Black-Hat per trovare le strutture scure e sottili (peli)
        hk = self.hair_kernel_size | 1 if self.hair_kernel_size > 0 else 1
        kernel = cv2.getStructuringElement(cv2.MORPH_CROSS, (hk, hk))
        blackhat = cv2.morphologyEx(gray, cv2.MORPH_BLACKHAT, kernel)
        
        # 2. Binarizzazione della maschera dei peli
        _, hair_mask = cv2.threshold(blackhat, self.hair_thresh, 255, cv2.THRESH_BINARY)
        
        # 3. Inpainting
        inpainted = cv2.inpaint(frame, hair_mask, self.inpaint_radius, cv2.INPAINT_TELEA)
        
        return inpainted, hair_mask

    def apply_finlayson_invariance(self, img_bgr):
        rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB).astype(np.float32)
        rgb[rgb == 0] = 1.0
        r, g, b = rgb[:,:,0], rgb[:,:,1], rgb[:,:,2]
        c1 = np.log(r) - np.log(g)
        c2 = np.log(b) - np.log(g)
        angle_rad = np.radians(self.invariance_angle)
        proj = c1 * np.cos(angle_rad) + c2 * np.sin(angle_rad)
        cv2.normalize(proj, proj, 0, 255, cv2.NORM_MINMAX)
        return np.uint8(proj)

    def apply_bg_subtraction(self, img):
        k = int(self.bg_kernel_size) | 1
        if k < 3: k = 3
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
        bg = cv2.morphologyEx(img, cv2.MORPH_CLOSE, kernel)
        diff = cv2.absdiff(bg, img)
        return cv2.bitwise_not(diff)

    def apply_homomorphic_filter(self, img):
        img_float = np.float32(img)
        img_log = np.log1p(img_float)
        img_fft = np.fft.fft2(img_log)
        img_fft_shift = np.fft.fftshift(img_fft)
        rows, cols = img.shape
        crow, ccol = rows // 2, cols // 2
        u, v = np.meshgrid(np.arange(rows), np.arange(cols), indexing='ij')
        d_sq = (u - crow)**2 + (v - ccol)**2
        d0_sq = self.homo_cutoff ** 2
        if d0_sq == 0: d0_sq = 1
        h_filter = (self.homo_gamma_h - self.homo_gamma_l) * (1 - np.exp(-self.homo_c * (d_sq / d0_sq))) + self.homo_gamma_l
        filtered_fft = np.fft.ifftshift(img_fft_shift * h_filter)
        img_inv_log = np.real(np.fft.ifft2(filtered_fft))
        img_exp = np.expm1(img_inv_log)
        cv2.normalize(img_exp, img_exp, 0, 255, cv2.NORM_MINMAX)
        return np.uint8(img_exp)

    def apply_retinex(self, img):
        img_float = np.float32(img) + 1.0
        sigmas = [self.retinex_sigma_low, self.retinex_sigma_high]
        retinex = np.zeros_like(img_float)
        for s in sigmas:
            s = s if s > 0.1 else 0.1
            blurred = cv2.GaussianBlur(img_float, (0, 0), s)
            retinex += np.log10(img_float) - np.log10(blurred + 1.0)
        retinex = retinex / len(sigmas)
        cv2.normalize(retinex, retinex, 0, 255, cv2.NORM_MINMAX)
        return np.uint8(retinex)

    def apply_light_invariance(self, frame):
        """
        Rende l'immagine invariante alle condizioni di luce sfavorevoli.
        Invece del CLAHE (che appiattiva troppo il contrasto nascondendo i bordi), 
        usiamo il canale L puro.
        """
        lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
        l_channel, a_channel, b_channel = cv2.split(lab)
        return l_channel

    def process_frame(self, frame, return_intermediates=False):
        h, w = frame.shape[:2]
        img_area = h * w
        
        intermediates = {}
        
        # 1. Hair Removal
        if self.use_hair_removal:
            clean_frame, hair_mask = self.remove_hairs(frame)
        else:
            clean_frame = frame.copy()
            hair_mask = np.zeros(frame.shape[:2], dtype=np.uint8)
            
        intermediates['hair_mask'] = hair_mask
        intermediates['clean_frame'] = clean_frame
        
        # 2. Light Invariance & Custom Algorithms
        if self.use_color_invariance:
            working_channel = self.apply_finlayson_invariance(clean_frame)
        elif self.use_light_invariance:
            working_channel = self.apply_light_invariance(clean_frame)
        else:
            working_channel = cv2.cvtColor(clean_frame, cv2.COLOR_BGR2GRAY)
            
        if self.use_bg_subtraction:
            working_channel = self.apply_bg_subtraction(working_channel)
            
        if self.use_homomorphic:
            working_channel = self.apply_homomorphic_filter(working_channel)
            
        if self.use_retinex:
            working_channel = self.apply_retinex(working_channel)
            
        # Optional: CLAHE per esaltare i contrasti su foto molto piatte
        if self.use_clahe:
            cgs = int(self.clahe_grid_size) if self.clahe_grid_size > 0 else 1
            clahe = cv2.createCLAHE(clipLimit=self.clahe_clip_limit, tileGridSize=(cgs, cgs))
            working_channel = clahe.apply(working_channel)
            
        intermediates['l_channel_clahe'] = working_channel
        
        if self.use_bilateral_filter:
            d = int(self.bilateral_d)
            if d < 1: d = 1
            blurred = cv2.bilateralFilter(working_channel, d, self.bilateral_sigma_color, self.bilateral_sigma_space)
        elif self.use_blur:
            bs = int(self.blur_size) | 1 if self.blur_size > 0 else 1
            blurred = cv2.GaussianBlur(working_channel, (bs, bs), 0)
        else:
            blurred = working_channel
        
        # 3. Binarizzazione
        absz = self.adaptive_block_size | 1
        if absz < 3: absz = 3
        thresh = cv2.adaptiveThreshold(blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, absz, self.adaptive_c)
        
        # Operazioni morfologiche
        if self.use_morphology:
            mos = self.morph_open_size | 1 if self.morph_open_size > 0 else 1
            mcs = self.morph_close_size | 1 if self.morph_close_size > 0 else 1
            kernel_morph_open = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (mos, mos))
            kernel_morph_close = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (mcs, mcs))
            thresh = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, kernel_morph_open, iterations=1)
            thresh = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, kernel_morph_close, iterations=2)
            
        # Optional: Mask Edge Smoothing (Sfocatura dei contorni)
        if self.use_mask_blur:
            mbs = self.mask_blur_size | 1 if self.mask_blur_size > 0 else 1
            thresh = cv2.GaussianBlur(thresh, (mbs, mbs), 0)
            _, thresh = cv2.threshold(thresh, 127, 255, cv2.THRESH_BINARY)
            
        intermediates['thresh'] = thresh

        # 4. Troviamo i contorni
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        debug_frame = frame.copy()
        should_capture = False
        
        margin_x = int(w * self.margin_ratio)
        margin_y = int(h * self.margin_ratio)
        center_x, center_y = w // 2, h // 2
        tol_x = int(w * self.center_tolerance_ratio)
        tol_y = int(h * self.center_tolerance_ratio)

        cv2.rectangle(debug_frame, (center_x - tol_x, center_y - tol_y), (center_x + tol_x, center_y + tol_y), (255, 0, 0), 3)
        cv2.rectangle(debug_frame, (margin_x, margin_y), (w - margin_x, h - margin_y), (0, 0, 255), 2)

        valid_moles = []

        for cnt in contours:
            area = cv2.contourArea(cnt)
            x, y, bw, bh = cv2.boundingRect(cnt)
            
            # FILTRO DIMENSIONE
            # Se è microscopico (rumore), lo SCARTIAMO IN SILENZIO, senza disegnare scatole grigie che sporcano lo schermo.
            if area < (img_area * self.min_area_ratio) or area > (img_area * self.max_area_ratio):
                continue
                
            # FILTRO FORMA (Fill Ratio)
            if self.use_fill_ratio:
                mask = np.zeros(thresh.shape, dtype=np.uint8)
                cv2.drawContours(mask, [cnt], -1, 255, -1) 
                pixel_area = cv2.countNonZero(cv2.bitwise_and(thresh, mask)) 
                fill_ratio = pixel_area / area if area > 0 else 0
                
                if fill_ratio < self.min_fill_ratio:
                    cv2.rectangle(debug_frame, (x, y), (x + bw, y + bh), (255, 0, 255), 2) # Magenta per macchie sformate/vuote
                    continue
                
            # Valutazione del neo trovato
            M = cv2.moments(cnt)
            if M["m00"] == 0:
                continue
            cx = int(M["m10"] / M["m00"])
            cy = int(M["m01"] / M["m00"])

            # Coloriamo la massa solida di rosso per farla risaltare
            cv2.drawContours(debug_frame, [cnt], -1, (0, 0, 255), -1) 
            
            if x < margin_x or y < margin_y or (x + bw) > (w - margin_x) or (y + bh) > (h - margin_y):
                cv2.rectangle(debug_frame, (x, y), (x + bw, y + bh), (0, 165, 255), 3) # Arancione
                cv2.putText(debug_frame, "TROPPO VICINO AI BORDI", (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 165, 255), 3)
                continue
                
            if abs(cx - center_x) > tol_x or abs(cy - center_y) > tol_y:
                cv2.rectangle(debug_frame, (x, y), (x + bw, y + bh), (0, 255, 255), 3) # Giallo
                cv2.putText(debug_frame, "CENTRA IL NEO", (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 255), 3)
                continue
            
            # Se supera tutti i test senza essere scartato, è lui!
            valid_moles.append(cnt)
            cv2.rectangle(debug_frame, (x, y), (x + bw, y + bh), (0, 255, 0), 4) # Verde
            cv2.putText(debug_frame, "PERFETTO!", (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 3)
            cv2.circle(debug_frame, (cx, cy), 8, (255, 255, 255), -1)

        if len(valid_moles) > 0:
            should_capture = True
            
        status_color = (0, 255, 0) if should_capture else (0, 0, 255)
        status_text = "SCATTO AUTORIZZATO" if should_capture else "CERCA NEO AL CENTRO..."
        cv2.putText(debug_frame, status_text, (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 1.5, status_color, 4)
            
        if return_intermediates:
            return should_capture, debug_frame, intermediates
        return should_capture, debug_frame

def main():
    detector = MoleDetector()
    cap = cv2.VideoCapture(0)
    
    if not cap.isOpened():
        print("Errore: Impossibile aprire la webcam.")
        return

    print("Premi 'q' sulla finestra del video per uscire.")

    while True:
        ret, frame = cap.read()
        if not ret:
            print("Errore nella lettura del frame.")
            break
            
        should_capture, debug_frame = detector.process_frame(frame)
        
        cv2.imshow('Rilevatore Nei (Mockup Android) - Advanced', debug_frame)
        
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
