import cv2
import numpy as np
import sys
import matplotlib.pyplot as plt

def test_algorithm(image_path):
    # 1. Caricamento e ridimensionamento a 200x200
    img = cv2.imread(image_path)
    if img is None:
        print(f"Errore: impossibile caricare {image_path}")
        return

    # Manteniamo una copia per visualizzare i risultati
    img_disp = cv2.resize(img, (200, 200))
    
    # L'algoritmo scala l'immagine ignorando l'aspect ratio
    small_img = cv2.resize(img, (200, 200))
    
    # 2. Grayscale e calcolo luminanza media
    # Algoritmo Kotlin: lum = (0.299 * r + 0.587 * g + 0.114 * b)
    # OpenCV cvtColor COLOR_BGR2GRAY fa una formula simile
    gray = cv2.cvtColor(small_img, cv2.COLOR_BGR2GRAY)
    
    avg_skin_lum = np.mean(gray)
    print(f"Luminanza media della pelle: {avg_skin_lum}")
    
    # 3. Thresholding dinamico
    # moleThreshold = (avgSkinLum * 0.70)
    mole_threshold = avg_skin_lum * 0.70
    print(f"Soglia macchia (70%): {mole_threshold}")
    
    # binaryMap[i] = luminanceArray[i] < moleThreshold
    # In OpenCV creiamo una maschera binaria dove i pixel scuri diventano bianchi (255)
    _, binary_map = cv2.threshold(gray, mole_threshold, 255, cv2.THRESH_BINARY_INV)
    
    # 4. Blobbing (Connected Components)
    # OpenCV connectedComponentsWithStats sostituisce il nostro FloodFill iterativo
    num_labels, labels, stats, centroids = cv2.connectedComponentsWithStats(binary_map, connectivity=8)
    
    blobs = []
    # stats[:, cv2.CC_STAT_AREA] contiene il pixelCount
    # La label 0 è il background (cioè tutto quello che NON è scuro), partiamo da 1
    for i in range(1, num_labels):
        area = stats[i, cv2.CC_STAT_AREA]
        if area > 20: # Filtro rumore
            left = stats[i, cv2.CC_STAT_LEFT]
            top = stats[i, cv2.CC_STAT_TOP]
            width = stats[i, cv2.CC_STAT_WIDTH]
            height = stats[i, cv2.CC_STAT_HEIGHT]
            cx, cy = centroids[i]
            blobs.append({
                'area': area,
                'cx': cx,
                'cy': cy,
                'box': (left, top, width, height)
            })
            
    print(f"Blob trovati con area > 20: {len(blobs)}")
    
    # 5. Trova il blob migliore (più grande)
    best_blob = None
    if blobs:
        best_blob = max(blobs, key=lambda b: b['area'])
        
    if best_blob:
        print(f"\nMIGLIOR BLOB:")
        print(f"Area: {best_blob['area']} pixel")
        print(f"Centro: ({best_blob['cx']:.1f}, {best_blob['cy']:.1f}) su 200x200")
        
        # Disegniamo il rettangolo sul risultato
        l, t, w, h = best_blob['box']
        cv2.rectangle(img_disp, (l, t), (l+w, t+h), (0, 255, 0), 2)
        cv2.circle(img_disp, (int(best_blob['cx']), int(best_blob['cy'])), 3, (0, 0, 255), -1)
        
        # Controllo centratura (30% - 70%)
        cx_norm = best_blob['cx'] / 200.0
        cy_norm = best_blob['cy'] / 200.0
        
        is_centered_x = 0.30 < cx_norm < 0.70
        is_centered_y = 0.30 < cy_norm < 0.70
        is_centered = is_centered_x and is_centered_y
        
        print(f"Centro normalizzato: X={cx_norm:.2f}, Y={cy_norm:.2f}")
        print(f"Centrato X: {is_centered_x}, Centrato Y: {is_centered_y} -> CENTRATO: {is_centered}")
    else:
        print("\nNESSUN BLOB TROVATO.")
        
    # Salviamo i risultati visivi
    cv2.imwrite('test_output_original.jpg', img_disp)
    cv2.imwrite('test_output_binary.jpg', binary_map)
    print("Salvate le immagini di debug: test_output_original.jpg, test_output_binary.jpg")

if __name__ == "__main__":
    image_path = "C:/Users/Maffione Gabriele/.gemini/antigravity/brain/e56297b5-8086-483e-952a-690f74a114a5/media__1781898625925.jpg"
    test_algorithm(image_path)
