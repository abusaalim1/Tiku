/**
 * Compresses and resizes an uploaded image file or base64 string using Canvas.
 * Supports processing high-res camera photos up to 20MB gracefully.
 * Outputs optimized JPEG base64 strings (~150KB - 300KB) compatible with
 * Firestore limits and Vercel serverless request body constraints.
 */

export const MAX_FILE_SIZE_MB = 20;
export const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

export function compressAndResizeImage(
  source: File | string,
  maxWidth = 1000,
  maxHeight = 1000,
  quality = 0.8,
  maxCharLength = 350000
): Promise<string> {
  return new Promise((resolve) => {
    if (!source) {
      resolve('');
      return;
    }

    // Check raw file size limit if it's a File object
    if (source instanceof File && source.size > MAX_FILE_SIZE_BYTES) {
      console.warn(`File exceeds ${MAX_FILE_SIZE_MB}MB limit`);
      resolve('');
      return;
    }

    // If source is a normal HTTP/HTTPS URL, return as is
    if (typeof source === 'string' && (source.startsWith('http://') || source.startsWith('https://'))) {
      resolve(source);
      return;
    }

    // If source is already a short base64 string, return as is
    if (typeof source === 'string' && source.length < maxCharLength) {
      resolve(source);
      return;
    }

    const img = new Image();

    const processCanvas = () => {
      try {
        let width = img.width || 1000;
        let height = img.height || 1000;

        // Calculate aspect ratio scale
        if (width > maxWidth) {
          height = Math.round((height * maxWidth) / width);
          width = maxWidth;
        }
        if (height > maxHeight) {
          width = Math.round((width * maxHeight) / height);
          height = maxHeight;
        }

        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, width);
        canvas.height = Math.max(1, height);

        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve('');
          return;
        }

        ctx.drawImage(img, 0, 0, width, height);

        // Export as compressed JPEG
        let compressedDataUrl = canvas.toDataURL('image/jpeg', quality);

        // Multi-stage compression if still oversized
        if (compressedDataUrl.length > maxCharLength) {
          compressedDataUrl = canvas.toDataURL('image/jpeg', 0.65);
        }

        if (compressedDataUrl.length > maxCharLength) {
          const smallCanvas = document.createElement('canvas');
          smallCanvas.width = Math.max(1, Math.round(width * 0.7));
          smallCanvas.height = Math.max(1, Math.round(height * 0.7));
          const sCtx = smallCanvas.getContext('2d');
          if (sCtx) {
            sCtx.drawImage(canvas, 0, 0, smallCanvas.width, smallCanvas.height);
            compressedDataUrl = smallCanvas.toDataURL('image/jpeg', 0.55);
          }
        }

        resolve(compressedDataUrl);
      } catch (err) {
        console.warn('Canvas compression failed:', err);
        resolve('');
      }
    };

    img.onload = processCanvas;
    img.onerror = () => {
      resolve('');
    };

    if (typeof source === 'string') {
      img.src = source;
    } else {
      const reader = new FileReader();
      reader.onload = (e) => {
        img.src = e.target?.result as string;
      };
      reader.onerror = () => resolve('');
      reader.readAsDataURL(source);
    }
  });
}

export async function compressAvatar(source: File | string): Promise<string> {
  return compressAndResizeImage(source, 300, 300, 0.75, 180000);
}

export async function compressSnapPhoto(source: File | string): Promise<string> {
  return compressAndResizeImage(source, 1000, 1000, 0.8, 350000);
}

export function sanitizePhotoString(url: string, maxLength = 350000): string {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  if (url.length > maxLength * 1.5) return '';
  return url;
}
